package com.prism.music

import com.prism.music.data.prefs.PlayerButton
import com.prism.music.ui.player.lyricsShowSing
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #501: with Sing among the player's row buttons, the open lyrics don't show a second Sing button. */
class LyricsSingButtonTest {
    @Test fun singOnTheRowHidesTheLyricsOne() {
        assertFalse(lyricsShowSing(true, listOf(PlayerButton.LYRICS, PlayerButton.SING, PlayerButton.QUEUE)))
    }

    @Test fun withoutItOnTheRowTheLyricsKeepTheirs() {
        assertTrue(lyricsShowSing(true, listOf(PlayerButton.LYRICS, PlayerButton.QUEUE)))
        // The row hidden: nothing on it.
        assertTrue(lyricsShowSing(true, emptyList()))
    }

    @Test fun nothingForWhatCantBeSung() {
        assertFalse(lyricsShowSing(false, listOf(PlayerButton.LYRICS)))
        assertFalse(lyricsShowSing(false, listOf(PlayerButton.SING)))
    }

    /** Reddit (1.8.8): Settings → Lyrics & Sing → "Show the Sing button" off hides it; on as it comes, as it was. */
    @Test fun theListenerCanHideIt() {
        assertFalse(lyricsShowSing(true, listOf(PlayerButton.LYRICS), shown = false))
        assertFalse(lyricsShowSing(true, emptyList(), shown = false))
        assertTrue(lyricsShowSing(true, listOf(PlayerButton.LYRICS), shown = true))
        assertTrue("on for a fresh install: the button shows as before", com.prism.music.data.prefs.AppSettings().lyrics.singButton)
        assertTrue(lyricsShowSing(true, listOf(PlayerButton.LYRICS), com.prism.music.data.prefs.AppSettings().lyrics.singButton))
    }

    /** The one place the button is drawn (phone and wide player both use it) reads the setting. */
    @Test fun theLyricsButtonsReadTheSetting() {
        val src = java.io.File("src/main/java/com/prism/music/ui/player/SingControls.kt").readText()
        assertTrue(src.contains("lyricsShowSing(c.sing.canSing(song), settings.rowButtons(), settings.lyrics.singButton)"))
        assertTrue("SingButton is drawn only by LyricsButtonsRow", Regex("""\bSingButton\(song,""").findAll(src).count() == 1)
    }
}
