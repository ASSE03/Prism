package com.prism.music.data

import com.prism.music.data.db.TransitionDao
import com.prism.music.data.db.TransitionEntity
import com.prism.music.playback.Transition
import com.prism.music.playback.TransitionStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import com.prism.music.data.innertube.InnerTube
import java.io.File

/**
 * The listener's own transitions (1.8.8, Mix it yourself; plan-mix.md §4.2), kept in the database's `transitions` table
 * by playlist. The crossfade engine asks [cached] on its loop: it answers from memory, for the playlist the queue is
 * playing, which it keeps read from the table (one query a playlist, followed as rows change). Nothing is read for a
 * playlist nobody asks about. Rows neither changed nor played for a year go ([prune]).
 */
class TransitionStore(
    private val dao: TransitionDao,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Loaded(val playlistId: String, val rows: Map<Pair<String, String>, TransitionEntity>)

    @Volatile private var loaded: Loaded? = null
    @Volatile private var following: String? = null
    private var job: Job? = null

    /**
     * The transition saved for [from] into [to] in [playlistId], from memory (any thread, no database read): null when
     * there is none, or while that playlist is still being read (asked for the first time, it starts being read).
     */
    fun cached(playlistId: String, from: String, to: String): Transition? {
        val l = loaded
        if (l == null || l.playlistId != playlistId) { follow(playlistId); return null }
        return l.rows[from to to]?.toTransition()
    }

    /** Keeps [playlistId]'s rows in memory (the queue's playlist), and no other's. */
    @Synchronized
    fun follow(playlistId: String) {
        if (following == playlistId) return
        following = playlistId
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            dao.forPlaylistFlow(playlistId).collect { rows ->
                loaded = Loaded(playlistId, rows.associateBy { it.fromId to it.toId })
            }
        }
    }

    /** A playlist's saved pairs (from, to), for the marks between its rows: one query, followed as rows change. */
    fun pairs(playlistId: String): Flow<Set<Pair<String, String>>> =
        dao.forPlaylistFlow(playlistId).map { rows -> rows.mapTo(HashSet()) { it.fromId to it.toId } }

    suspend fun get(playlistId: String, from: String, to: String): Transition? = dao.get(playlistId, from, to)?.toTransition()

    /**
     * The listener's changes, one at a time in the order they were asked for: a save reads the row and writes it
     * again, so a Reset between the two would otherwise be undone by the save's write.
     */
    private val writes = Mutex()

    suspend fun save(playlistId: String, from: String, to: String, t: Transition) = writes.withLock {
        val old = dao.get(playlistId, from, to)
        dao.put(TransitionEntity(playlistId, from, to, t.outMs, t.inMs, t.lengthMs, t.style.key, t.snap, now(), old?.usedAt ?: 0))
    }

    /** Back to automatic: the row goes. */
    suspend fun reset(playlistId: String, from: String, to: String) = writes.withLock { dao.delete(playlistId, from, to) }

    /** The playlist was deleted ([ids]: the ids it is known by, its YouTube "VL" one and the queue's): its rows go. */
    suspend fun forgetPlaylist(vararg ids: String) {
        val all = ids.flatMap { listOf(it, it.removePrefix("VL")) }.distinct()
        if (all.isNotEmpty()) writes.withLock { dao.deletePlaylist(all) }
    }

    /** Every saved transition, for a backup's Library (they belong to the playlists, though they live in the database). */
    suspend fun export(): TransitionsFile = TransitionsFile(dao.all())

    /** After a restore: the transitions waiting in [file] ([PENDING]) put in place of these, once. Nothing to do otherwise. */
    suspend fun readPending(file: File) {
        if (!file.isFile) return
        runCatching { writes.withLock { dao.replaceAll(InnerTube.json.decodeFromString(TransitionsFile.serializer(), file.readText()).rows) } }
            .onFailure { android.util.Log.w("PrismTransitions", "the restored transitions didn't read", it) }
        file.delete()
    }

    /** [from] ran into [to] by its saved transition: it is in use (the yearly prune leaves it). */
    fun used(playlistId: String, from: String, to: String) {
        scope.launch(Dispatchers.IO) { runCatching { dao.used(playlistId, from, to, now()) } }
    }

    /** Rows neither changed nor played for [UNUSED_FOR_MS]: how many went. */
    suspend fun prune(): Int = dao.pruneBefore(now() - UNUSED_FOR_MS)

    companion object {
        /** A year. */
        const val UNUSED_FOR_MS = 365L * 24 * 60 * 60 * 1000

        /** A backup's transitions, among the library's files (`files/library/`); read in and removed after a restore. */
        const val PENDING = "transitions.json"

        fun TransitionEntity.toTransition() = Transition(outMs, inMs, lengthMs, TransitionStyle.of(style), snap)

        fun exportJson(file: TransitionsFile): String = InnerTube.json.encodeToString(TransitionsFile.serializer(), file)

        /**
         * The transitions in a database file Room hasn't opened (a restore, before the app starts), read straight from
         * it; null when it has none to read (older than 1.8.8, or not a database).
         */
        fun readFrom(dbFile: File): TransitionsFile? = runCatching {
            if (!dbFile.isFile) return@runCatching null
            android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { sql ->
                sql.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'transitions'", null).use { if (!it.moveToFirst()) return@runCatching null }
                val rows = mutableListOf<TransitionEntity>()
                sql.rawQuery("SELECT playlistId, fromId, toId, outMs, inMs, lengthMs, style, snap, changedAt, usedAt FROM transitions", null).use { c ->
                    while (c.moveToNext()) rows += TransitionEntity(
                        c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4), c.getLong(5),
                        c.getString(6), c.getInt(7) != 0, c.getLong(8), c.getLong(9),
                    )
                }
                TransitionsFile(rows)
            }
        }.getOrNull()
    }
}

/** The listener's transitions as a backup carries them (`files/library/transitions.json`), with the Library. */
@Serializable
data class TransitionsFile(val rows: List<TransitionEntity> = emptyList())
