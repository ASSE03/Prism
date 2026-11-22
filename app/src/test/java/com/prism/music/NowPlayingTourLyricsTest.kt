package com.prism.music

import com.prism.music.ui.player.LyricsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.8.8 review: the player told the coach "no lyrics" for a song whose lyrics wait for the lyrics to be opened (on
 * mobile data with "Lyrics on mobile data" off, #95), so First steps passed its Lyrics step over, though opening them
 * would have looked them up. Waiting counts as having lyrics; still loading is "not known yet". (Plain logic: no app
 * container, so no database left running for the next test class.)
 */
class NowPlayingTourLyricsTest {
    /** What NowPlaying told the coach before 1.8.8's review fix. */
    private fun before(loading: Boolean, found: Boolean): Boolean? = found.takeIf { !loading }

    @Test fun lyricsWaitingForMobileDataCountAsThere() {
        // The saved-only look-up found nothing and waits (LyricsState.loadAuto(savedOnly = true)): not loading, waiting.
        assertEquals("before: passed over", false, before(loading = false, found = false))
        assertEquals("waiting: the Lyrics step stays", true, LyricsState.forTour(loading = false, waiting = true, found = false))
    }

    @Test fun theOtherCasesAreAsBefore() {
        assertNull("still loading: not known yet", LyricsState.forTour(loading = true, waiting = false, found = false))
        assertEquals("found", true, LyricsState.forTour(loading = false, waiting = false, found = true))
        assertEquals("none anywhere: passed over", false, LyricsState.forTour(loading = false, waiting = false, found = false))
        for (l in listOf(true, false)) for (f in listOf(true, false)) {
            assertEquals("loading $l, found $f", before(l, f), LyricsState.forTour(l, waiting = false, found = f))
        }
    }
}
