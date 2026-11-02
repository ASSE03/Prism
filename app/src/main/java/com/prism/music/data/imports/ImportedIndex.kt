package com.prism.music.data.imports

import com.prism.music.data.db.PrismDatabase
import java.util.BitSet

/**
 * The plays an earlier import of one source brought (1.8.1, repeat imports), to know a play in a newer download that is
 * already here: the same song within [WINDOW_MS] of the same time is the same play. That covers a fresh copy of the same
 * download (the same times), and Spotify's two histories, whose times differ: the account data's to the minute, the
 * extended one's to the second.
 *
 * Each stored play stands for one play of the new download at most (a song skipped and played again a minute later is
 * two plays in both). Between the two Spotify histories the extended play is the whole story: where a newer download's
 * extended play meets an account-data play stored before, [absorb] says to [Hit.Upgrade] that row in place.
 */
internal class ImportedIndex private constructor(private val songs: Map<String, Plays>) {
    /** One song's stored plays, oldest first: their times, rows, and which were account-data plays (no Spotify id). */
    private class Plays(val at: LongArray, val ids: LongArray, val plain: BitSet) {
        val used = BitSet(at.size)
    }

    sealed interface Hit {
        /** Not here yet. */
        data object New : Hit
        /** Already here. */
        data object Same : Hit
        /** Already here as an account-data play; [id]'s row takes the extended play's details. */
        data class Upgrade(val id: Long) : Hit
    }

    val isEmpty: Boolean get() = songs.isEmpty()

    /** Whether [p] (its song as [key]) is a play already stored; once matched, that stored play can't match another. */
    fun absorb(p: ParsedPlay, key: String, upgrades: Boolean): Hit {
        val s = songs[key] ?: return Hit.New
        val from = p.timestamp - WINDOW_MS
        var i = java.util.Arrays.binarySearch(s.at, from).let { if (it < 0) -it - 1 else it }
        // The nearest unused play in the window (a song played twice in a few minutes keeps both).
        var best = -1
        var gap = Long.MAX_VALUE
        while (i < s.at.size && s.at[i] <= p.timestamp + WINDOW_MS) {
            val d = kotlin.math.abs(s.at[i] - p.timestamp)
            if (!s.used[i] && d < gap) { best = i; gap = d }
            i++
        }
        if (best < 0) return Hit.New
        s.used.set(best)
        return if (upgrades && p.sourceId != null && s.plain[best]) Hit.Upgrade(s.ids[best]) else Hit.Same
    }

    companion object {
        /** "About a minute": the account data's times are to the minute, and its clock and the extended one's differ a little. */
        const val WINDOW_MS = 90_000L

        /** [source]'s stored plays, read straight off a cursor (a long history is 100,000+ rows). */
        fun of(db: PrismDatabase, source: ImportSource): ImportedIndex {
            val songs = HashMap<String, Plays>()
            db.query(
                androidx.sqlite.db.SimpleSQLiteQuery(
                    "SELECT songKey, timestamp, id, sourceId IS NULL FROM imported_plays WHERE origin = ? ORDER BY songKey, timestamp",
                    arrayOf(source.key),
                )
            ).use { cur ->
                var key: String? = null
                val at = LongList(); val ids = LongList(); val plain = BitSet()
                fun close() {
                    val k = key ?: return
                    songs[k] = Plays(at.toArray(), ids.toArray(), plain.clone() as BitSet)
                    at.clear(); ids.clear(); plain.clear()
                }
                while (cur.moveToNext()) {
                    val k = cur.getString(0)
                    if (k != key) { close(); key = k }
                    if (cur.getInt(3) != 0) plain.set(at.size)
                    at.add(cur.getLong(1)); ids.add(cur.getLong(2))
                }
                close()
            }
            return ImportedIndex(songs)
        }
    }

    /** A growable list of longs without boxing each. */
    private class LongList {
        private var a = LongArray(8)
        var size = 0; private set
        fun add(v: Long) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        fun clear() { size = 0 }
        fun toArray(): LongArray = a.copyOf(size)
    }
}
