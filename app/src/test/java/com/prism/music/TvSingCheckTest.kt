package com.prism.music

import com.prism.music.ui.tv.TvSing
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prism TV, slice 8: Sing's speed check on a TV (the whole song must be ready before it's played through). */
class TvSingCheckTest {
    @Test fun aTvIsFastEnoughOnlyWhenTheSongWouldBeReadyInTime() {
        // A 4-minute song: half separated in 40 s → ready in 80 s: fine.
        assertTrue(TvSing.fastEnough(0.5f, 40_000, 240_000))
        // A tenth in 40 s → 400 s for a 240 s song: too slow.
        assertFalse(TvSing.fastEnough(0.1f, 40_000, 240_000))
        // Just under the song's length isn't enough (playback shares the TV): the margin.
        assertFalse(TvSing.fastEnough(0.17f, 40_000, 240_000))
        // Nothing done, or a song without a length: no.
        assertFalse(TvSing.fastEnough(0f, 40_000, 240_000))
        assertFalse(TvSing.fastEnough(0.5f, 40_000, 0))
    }
}
