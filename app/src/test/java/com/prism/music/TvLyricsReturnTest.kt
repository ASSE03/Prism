package com.prism.music

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.prism.music.data.lyrics.LyricLine
import com.prism.music.data.prefs.AppSettings
import com.prism.music.ui.player.MAX_LYRIC_OFFSET_MS
import com.prism.music.ui.tv.LocalTvFocusMemory
import com.prism.music.ui.tv.LocalTvScreen
import com.prism.music.ui.tv.TAG_LYRICS
import com.prism.music.ui.tv.TV_PICK_RETURN_MS
import com.prism.music.ui.tv.TvFocusMemory
import com.prism.music.ui.tv.TvLyricTiming
import com.prism.music.ui.tv.TvLyricsPlace
import com.prism.music.ui.tv.TvNow
import com.prism.music.ui.tv.TvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The TV's lyrics (1.8.0): back to the sung line after picking another, and the timing's steps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi", application = android.app.Application::class)
class TvLyricsReturnTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun timingStepsAndLimits() {
        assertEquals(100L, TvLyricTiming.stepFor(0))
        assertEquals(200L, TvLyricTiming.stepFor(5))
        assertEquals(500L, TvLyricTiming.stepFor(30))
        assertEquals(300L, (1..3).fold(0L) { o, _ -> TvLyricTiming.nudged(o, TvLyricTiming.stepFor(0)) })
        assertEquals(MAX_LYRIC_OFFSET_MS, TvLyricTiming.nudged(MAX_LYRIC_OFFSET_MS - 50, 500))
        assertEquals(-MAX_LYRIC_OFFSET_MS, TvLyricTiming.nudged(-MAX_LYRIC_OFFSET_MS, -TvLyricTiming.BIG_STEP))
        assertEquals("+0.4 s", TvLyricTiming.shown(400))
        assertEquals("−1.2 s", TvLyricTiming.shown(-1_200))
    }

    private val now = TvNow("A song", "An artist", null, null, 0.2f, "0:20", "3:00", playing = true, hasLyrics = true)
    private val lines = (0 until 10).map { i -> LyricLine(i * 4_000L, "Line number ${i + 1}", i * 4_000L + 3_500) }

    private fun chip() = compose.onAllNodes(hasTestTag("$TAG_LYRICS/now"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun show() = compose.setContent {
        TvTheme(AppSettings()) {
            val memory = remember { TvFocusMemory() }
            CompositionLocalProvider(LocalTvFocusMemory provides memory, LocalTvScreen provides "lyrics") {
                TvLyricsPlace(now, lines, 2, remember { mutableLongStateOf(8_500L) }, loading = false, unavailable = false, source = null, accent = null) {}
            }
        }
    }

    private fun pick() {
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        compose.onNode(hasTestTag("$TAG_LYRICS/lines")).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertTrue("a picked line shows Back to now", chip())
    }

    @Test fun backReturnsToTheSungLine() {
        show()
        pick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue("following the song again", !chip())
    }

    @Test fun aPickedLineLetsGoAfterAMoment() {
        show()
        pick()
        compose.mainClock.advanceTimeBy(TV_PICK_RETURN_MS + 500)
        compose.waitForIdle()
        assertTrue("following the song again", !chip())
    }
}
