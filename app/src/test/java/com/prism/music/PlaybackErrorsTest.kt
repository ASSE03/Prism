package com.prism.music

import com.prism.music.data.stream.RefusedSongs
import com.prism.music.playback.readAheadFailed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a song that won't play does to the player's next steps (1.8.2, Reddit: "Video unavailable" on Prism TV). */
class PlaybackErrorsTest {
    /** Only the next song's own failure is passed on to it: anything else is the playing song's, as the player says. */
    @Test fun aFailureIsTheNextSongsOnlyWhenItNamesIt() {
        assertTrue(readAheadFailed("b", current = "a", next = "b"))
        assertFalse("its own failure", readAheadFailed("a", current = "a", next = "b"))
        assertFalse("repeat one: next is itself", readAheadFailed("a", current = "a", next = "a"))
        assertFalse("not a lookup at all", readAheadFailed(null, current = "a", next = "b"))
        assertFalse("nothing next", readAheadFailed("b", current = "a", next = null))
        assertFalse("a song no longer next", readAheadFailed("c", current = "a", next = "b"))
    }

    @Test fun aRefusedSongIsNotAskedForAgainForFiveMinutes() {
        var now = 1_000L
        val refused = RefusedSongs { now }
        assertNull(refused.reasonFor("a", "me"))
        refused.note("a", "Video unavailable", "me")
        assertEquals("Video unavailable", refused.reasonFor("a", "me"))
        assertNull("only that song", refused.reasonFor("b", "me"))
        now += RefusedSongs.FOR_MS - 1
        assertEquals("Video unavailable", refused.reasonFor("a", "me"))
        now += 1
        assertNull("five minutes on, it's asked again", refused.reasonFor("a", "me"))
        assertNull("and it's gone", run { now -= 1; refused.reasonFor("a", "me") })
    }

    @Test fun anotherAccountAsksAfresh() {
        val refused = RefusedSongs { 0L }
        refused.note("a", "Video unavailable", "me")
        assertNull(refused.reasonFor("a", "someone else"))
    }

    @Test fun aRetryForgetsIt() {
        val refused = RefusedSongs { 0L }
        refused.note("a", "Video unavailable", "me")
        refused.forget("a")
        assertNull(refused.reasonFor("a", "me"))
    }
}
