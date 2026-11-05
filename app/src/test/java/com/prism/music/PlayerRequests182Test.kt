package com.prism.music

import com.prism.music.data.AutoplayLength
import com.prism.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure parts of 1.8.2's player requests (#299 and on). */
class PlayerRequests182Test {
    private fun song(id: String, seconds: Int) = Song(id = id, title = id, durationSec = seconds)

    /** #299: no limit is what everyone has today, so it must leave the list alone. */
    @Test fun autoplayWithoutALimitAddsWhatItAlwaysDid() {
        val songs = listOf(song("short", 180), song("mix", 3 * 3600))
        assertEquals(songs, AutoplayLength.keep(songs, 0))
    }

    /** #299: long ones go, in order, and exactly the limit still fits. */
    @Test fun autoplayLeavesOutWhatRunsPastTheLimit() {
        val songs = listOf(song("a", 180), song("mix", 3 * 3600), song("exact", 10 * 60), song("just over", 10 * 60 + 1))
        assertEquals(listOf("a", "exact"), AutoplayLength.keep(songs, 10).map { it.id })
    }

    /** #299: an unknown length isn't a long song: losing it would make the queue run out for no reason. */
    @Test fun autoplayKeepsASongOfUnknownLength() {
        assertTrue(AutoplayLength.fits(song("unknown", 0), 5))
        assertFalse(AutoplayLength.fits(song("long", 5 * 60 + 1), 5))
    }
}
