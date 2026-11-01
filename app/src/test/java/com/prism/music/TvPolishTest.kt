package com.prism.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.HomeSectionConfig
import com.prism.music.data.prefs.HomeSections
import com.prism.music.ui.tv.KEY_DIAL
import com.prism.music.ui.tv.KEY_PICKS
import com.prism.music.ui.tv.KEY_RECENT
import com.prism.music.ui.tv.LocalTvFocusMemory
import com.prism.music.ui.tv.LocalTvScreen
import com.prism.music.ui.tv.TAG_VIDEO
import com.prism.music.ui.tv.TV_SEEK_STEP_MS
import com.prism.music.ui.tv.TvFocusMemory
import com.prism.music.ui.tv.TvNow
import com.prism.music.ui.tv.TvTheme
import com.prism.music.ui.tv.TvVideoControls
import com.prism.music.ui.tv.arrangeTvRows
import com.prism.music.ui.tv.hiddenIn
import com.prism.music.ui.tv.layoutInOrder
import com.prism.music.ui.tv.layoutWithShown
import com.prism.music.ui.tv.moved
import com.prism.music.ui.tv.occurrenceKeys
import com.prism.music.ui.tv.phoneKeyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Prism TV, the owner's second round (1.8.0): the video's controls all reachable, and Home's rows customized. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi", application = android.app.Application::class)
class TvPolishTest {
    @get:Rule val compose = createComposeRule()

    private var focus: FocusManager? = null
    private fun press(d: FocusDirection) = compose.runOnIdle { focus!!.moveFocus(d) }
    private fun focusedOn(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun awaitFocus(tag: String) {
        val end = System.currentTimeMillis() + 5_000
        while (!focusedOn(tag) && System.currentTimeMillis() < end) { Thread.sleep(50); compose.mainClock.advanceTimeBy(50) }
        assertTrue("focus on $tag", focusedOn(tag))
    }

    private val now = TvNow("Glass Horizon", "Mira Vale", null, null, 0.4f, "1:24", "3:41", playing = true, hasLyrics = true)

    /** On a music video every control can be reached with the remote (only play/pause could before), and ← → seek. */
    @Test fun everyVideoControlIsReachable() {
        val seeks = ArrayList<Long>()
        compose.setContent {
            focus = LocalFocusManager.current
            TvTheme(AppSettings()) {
                val memory = remember { TvFocusMemory() }
                CompositionLocalProvider(LocalTvFocusMemory provides memory, LocalTvScreen provides "video") {
                    Box(Modifier.fillMaxSize()) { TvVideoControls(now, {}, {}, {}, {}, onSeek = { seeks += it }, onLyrics = {}, onQueue = {}, onMore = {}) }
                }
            }
        }
        awaitFocus("$TAG_VIDEO/playpause")
        for (tag in listOf("next", "lyrics", "queue", "song", "more")) {
            press(FocusDirection.Right)
            awaitFocus("$TAG_VIDEO/$tag")
        }
        // Up to the seek line: ← → move 10 seconds.
        press(FocusDirection.Up)
        awaitFocus("$TAG_VIDEO/seek")
        compose.onNode(hasTestTag("$TAG_VIDEO/seek")).performKeyInputSafely(androidx.compose.ui.input.key.Key.DirectionRight)
        compose.onNode(hasTestTag("$TAG_VIDEO/seek")).performKeyInputSafely(androidx.compose.ui.input.key.Key.DirectionLeft)
        assertEquals(listOf(TV_SEEK_STEP_MS, -TV_SEEK_STEP_MS), seeks)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performKeyInputSafely(key: androidx.compose.ui.input.key.Key) =
        performKeyInput { pressKey(key) }

    private val layout = listOf(
        HomeSectionConfig(HomeSections.QUICK_PICKS, "Quick picks"),
        HomeSectionConfig(HomeSections.MADE_FOR_YOU, "Made for you"),
        HomeSectionConfig("${HomeSections.YT_PREFIX}mixed for you", "Mixed for you"),
    )
    private val rows = listOf(KEY_PICKS, KEY_DIAL, KEY_RECENT, "yt:Mixed for you", "yt:Throwbacks")

    @Test fun anUntouchedLayoutIsTheTvsHomeAsItWas() {
        assertEquals(HomeSections.QUICK_PICKS, phoneKeyOf(KEY_PICKS))
        assertEquals(HomeSections.SPEED_DIAL, phoneKeyOf(KEY_DIAL))
        assertEquals("${HomeSections.YT_PREFIX}mixed for you", phoneKeyOf("yt:Mixed for you"))
        assertEquals(rows, arrangeTvRows(rows, layout) { it })
        assertEquals(rows, arrangeTvRows(rows, HomeSections.initial) { it })
        assertTrue(rows.none { hiddenIn(HomeSections.initial, it) })
    }

    @Test fun hidingAndMovingRowsOnTheTvKeepsThePhonesLayoutInStep() {
        // Hidden on the TV: hidden in the phone's layout too (added if it wasn't there).
        val noDial = layoutWithShown(layout, KEY_DIAL, "Speed dial", shown = false)
        assertTrue(hiddenIn(noDial, KEY_DIAL))
        assertFalse(hiddenIn(noDial, KEY_PICKS))
        assertEquals(layout.size + 1, noDial.size)
        // Shown again.
        assertFalse(hiddenIn(layoutWithShown(noDial, KEY_DIAL, "Speed dial", shown = true), KEY_DIAL))
        // Moved: Throwbacks up to the top; the phone's own section (Made for you) stays where it was.
        val order = moved(rows.map { it to it }, from = 4, by = -4)
        assertEquals("yt:Throwbacks", order.first().first)
        val newLayout = layoutInOrder(layout, order)
        assertEquals(HomeSections.MADE_FOR_YOU, newLayout[1].key)
        assertEquals(listOf("yt:Throwbacks", KEY_PICKS, KEY_DIAL, KEY_RECENT, "yt:Mixed for you"), arrangeTvRows(rows, newLayout) { it })
        // Moving past the ends does nothing.
        assertEquals(rows, moved(rows, 0, -1))
        assertEquals(rows, moved(rows, rows.lastIndex, 1))
    }

    @Test fun homesRowKeysStayUnique() {
        assertEquals(listOf("Mixes", "Hits", "Mixes#2"), occurrenceKeys(listOf("Mixes", "Hits", "Mixes")))
    }
}
