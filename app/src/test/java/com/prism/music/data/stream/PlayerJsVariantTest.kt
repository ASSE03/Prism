package com.prism.music.data.stream

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Since 23 November 2026 the desktop copy of YouTube's player stops yt-dlp's EJS ("a.addOnDisposeCallback is not a
 * function", yt-dlp #18123), so no signed-in link was unlocked: Premium's 256 kbps fell back to the usual stream. The
 * TV copy is asked for first, as yt-dlp 2026.11.23 does, with the desktop copy as the fallback.
 */
class PlayerJsVariantTest {
    @Test
    fun theTvCopyOfThePlayerIsAskedForFirst() {
        assertEquals(
            "https://www.youtube.com/s/player/b485e332/tv-player-ias.vflset/tv-player-ias.js",
            PlayerJsSolver.playerUrl("b485e332", PlayerJsSolver.VARIANTS.first()),
        )
    }

    @Test
    fun theDesktopCopyStaysAsTheFallback() {
        assertEquals(
            listOf("tv-player-ias.vflset/tv-player-ias.js", "player_ias.vflset/en_US/base.js"),
            PlayerJsSolver.VARIANTS,
        )
    }
}
