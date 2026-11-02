package com.prism.music.data.spotify

import androidx.room.Dao
import androidx.room.Query

/**
 * What the Spotify connection asks of the imported plays (1.8.1): no table of its own (live plays are imported plays
 * from "spotify_live"), only the questions that keep a play from being counted twice.
 */
@Dao
interface SpotifyDao {
    /** When a song was played in a data download's history ("spotify"), between two times. */
    @Query("SELECT timestamp FROM imported_plays WHERE origin = 'spotify' AND songKey = :key AND timestamp BETWEEN :from AND :to")
    suspend fun downloadTimes(key: String, from: Long, to: Long): List<Long>

    /**
     * Takes away live plays a data download brought later: the same song within [slackMs] of the play's time, or of its
     * end (whichever Spotify's two clocks name).
     */
    @Query(
        """DELETE FROM imported_plays WHERE origin = 'spotify_live' AND EXISTS (
               SELECT 1 FROM imported_plays d WHERE d.origin = 'spotify' AND d.songKey = imported_plays.songKey
               AND d.timestamp BETWEEN imported_plays.timestamp - :slackMs AND imported_plays.timestamp + imported_plays.playedMs + :slackMs)"""
    )
    suspend fun dropLiveTwins(slackMs: Long): Int

    @Query("SELECT COUNT(*) FROM imported_plays WHERE origin = 'spotify_live'")
    suspend fun livePlays(): Int
}
