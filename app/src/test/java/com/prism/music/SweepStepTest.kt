package com.prism.music

import com.prism.music.data.lyrics.LyricLine
import com.prism.music.data.lyrics.LyricWord
import com.prism.music.ui.player.sungChars
import com.prism.music.ui.player.sweepStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.8.7: a word-timed line is drawn again only when its singing point moved. The value the drawing reads stands still
 * before the line, in the rests between words and once the line is sung, and moves while a word is sung.
 */
class SweepStepTest {
    private val line = LyricLine(
        1_000, "Kinda dream",
        words = listOf(LyricWord(1_000, 1_400, "Kinda "), LyricWord(2_000, 2_600, "dream")),
    )
    private val offsets = listOf(0 to 6, 6 to 11)
    private fun at(ms: Long) = sweepStep(sungChars(line, offsets, ms))
    private fun distinct(from: Long, to: Long) = (from..to step 11).map(::at).distinct().size

    @Test fun itStandsStillWhereNothingIsSung() {
        assertEquals("before the line", 1, distinct(0, 999))
        assertEquals("the rest between two words", 1, distinct(1_400, 1_999))
        assertEquals("after the line", 1, distinct(2_600, 60_000))
        assertEquals(0f, at(500))
        assertEquals(6f, at(1_700))
        assertEquals(11f, at(9_000))
    }

    @Test fun itMovesWhileAWordIsSung() {
        // One frame apart (11 ms at 90 Hz) inside a word: a new picture nearly every frame.
        assertTrue(distinct(1_000, 1_399) > 30)
        assertTrue(at(1_200) > at(1_100))
        // Steps finer than a pixel: 1/64 of a letter.
        assertEquals(0f, (at(1_133) * 64) % 1f)
    }
}
