package com.prism.music

import com.prism.music.data.prefs.AppSettings
import com.prism.music.ui.tv.ambientDrift
import com.prism.music.ui.tv.ambientDue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Prism TV, slice 7: when the ambient screen comes, how far it drifts, and desk mode off by default. */
class TvAmbientLogicTest {
    @Test fun theAmbientScreenComesOnlyWhenTheRoomIsQuietAndMusicPlays() {
        val three = 3 * 60_000L
        assertTrue(ambientDue(playing = true, allowed = true, minutes = 3, idleMs = three))
        assertFalse(ambientDue(playing = true, allowed = true, minutes = 3, idleMs = three - 1))
        // Paused: never (the TV may sleep then).
        assertFalse(ambientDue(playing = false, allowed = true, minutes = 3, idleMs = 10 * three))
        // Switched off (0), or not allowed (a phone without desk mode, or not charging).
        assertFalse(ambientDue(playing = true, allowed = true, minutes = 0, idleMs = 10 * three))
        assertFalse(ambientDue(playing = true, allowed = false, minutes = 3, idleMs = 10 * three))
    }

    @Test fun itDriftsAFewDpAndKeepsMoving() {
        val points = (0 until 600 step 7).map { ambientDrift(it * 1_000L) }
        assertTrue(points.all { abs(it.x.value) <= 14f && abs(it.y.value) <= 10f })
        // It moves (burn-in safe): it isn't at one place for long.
        assertTrue(points.map { it.x.value.toInt() to it.y.value.toInt() }.distinct().size > 20)
    }

    @Test fun deskModeIsOffAndTheTvAmbientScreenOnByDefault() {
        val tv = AppSettings().tv
        assertFalse(tv.deskMode)
        assertEquals(3, tv.ambientMinutes)
        assertEquals(1f, tv.lyricsScale, 0f)
    }
}
