package com.prism.music.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The listener's own transition between two songs of a playlist (1.8.8, "Mix it yourself"; plan-mix.md §4.2): kept
 * with the playlist ([playlistId] as the queue knows it, [com.prism.music.playback.QueueSource.originId]), for
 * [fromId] running into [toId] in that order. [outMs]: where in the first song the two start to overlap; [inMs]: where
 * the second song comes in; [lengthMs]: how long they overlap (0 is a cut); [style]: how
 * ([com.prism.music.playback.TransitionStyle.key]). [usedAt]: when it last played (0: never), for the yearly prune.
 * A pair no longer side by side keeps its row: it comes back if they meet again.
 */
@Entity(tableName = "transitions", primaryKeys = ["playlistId", "fromId", "toId"])
data class TransitionEntity(
    val playlistId: String,
    val fromId: String,
    val toId: String,
    val outMs: Long,
    val inMs: Long,
    val lengthMs: Long,
    val style: String,
    val snap: Boolean,
    val changedAt: Long,
    val usedAt: Long = 0,
)

@Dao
interface TransitionDao {
    /** A playlist's transitions: one indexed query (the key starts with the playlist), however many rows it has. */
    @Query("SELECT * FROM transitions WHERE playlistId = :playlistId")
    fun forPlaylistFlow(playlistId: String): Flow<List<TransitionEntity>>

    @Query("SELECT * FROM transitions WHERE playlistId = :playlistId")
    suspend fun forPlaylist(playlistId: String): List<TransitionEntity>

    @Query("SELECT * FROM transitions WHERE playlistId = :playlistId AND fromId = :fromId AND toId = :toId")
    suspend fun get(playlistId: String, fromId: String, toId: String): TransitionEntity?

    @Upsert
    suspend fun put(t: TransitionEntity)

    @Query("DELETE FROM transitions WHERE playlistId = :playlistId AND fromId = :fromId AND toId = :toId")
    suspend fun delete(playlistId: String, fromId: String, toId: String)

    /** The playlist was deleted: its transitions go with it. */
    @Query("DELETE FROM transitions WHERE playlistId IN (:ids)")
    suspend fun deletePlaylist(ids: List<String>)

    @Query("UPDATE transitions SET usedAt = :at WHERE playlistId = :playlistId AND fromId = :fromId AND toId = :toId")
    suspend fun used(playlistId: String, fromId: String, toId: String, at: Long)

    /** Rows neither changed nor played since [before]. */
    @Query("DELETE FROM transitions WHERE changedAt < :before AND usedAt < :before")
    suspend fun pruneBefore(before: Long): Int

    @Query("SELECT COUNT(*) FROM transitions")
    suspend fun count(): Int
}
