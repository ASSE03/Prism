package com.prism.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.prism.music.ui.player.ThinSlider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 1.8.8 review: the crossfade mark's tap strip on the progress bar sat mirrored in right-to-left languages (the bar
 * itself runs left to right), and it took every touch that came down on it, so a drag starting there never seeked.
 * Now the bar's own gesture tells them apart: a tap on the mark opens it, a drag from it and a tap anywhere else seek.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp", application = android.app.Application::class)
class PlayerControlsMarkTest {
    @get:Rule val compose = createComposeRule()

    private var markTaps = 0
    private var seeks = 0
    private var value = -1f

    private fun show(dir: LayoutDirection) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                Box(Modifier.width(300.dp)) {
                    ThinSlider(
                        0.1f, { value = it }, Modifier.testTag("bar"), onValueChangeFinished = { seeks++ },
                        marks = listOf(0.8f), markTap = 0.8f, onMarkTap = { markTaps++ }, markTag = "mark", markLabel = "Edit",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun walk(dir: LayoutDirection) {
        show(dir)
        val bar = compose.onNodeWithTag("bar").getBoundsInRoot()
        val mark = compose.onNodeWithTag("mark").getBoundsInRoot()
        val markCenter = (mark.left + mark.right) / 2
        assertEquals("$dir: the mark's node at 80% from the left, as the bar runs", (bar.left + (bar.right - bar.left) * 0.8f).value, markCenter.value, 1f)
        // A tap on the mark opens it and doesn't seek.
        compose.onNodeWithTag("mark").performClick()
        compose.waitForIdle()
        assertEquals("$dir", 1, markTaps)
        assertEquals("$dir: no seek for the tap on the mark", 0, seeks)
        // A tap elsewhere seeks there.
        compose.onNodeWithTag("bar").performTouchInput { click(Offset(width * 0.2f, centerY)) }
        compose.waitForIdle()
        assertEquals("$dir", 1, seeks)
        assertEquals("$dir", 0.2f, value, 0.02f)
        // A drag from the mark seeks too, to where it ends.
        compose.onNodeWithTag("bar").performTouchInput { swipe(Offset(width * 0.8f, centerY), Offset(width * 0.3f, centerY), 300) }
        compose.waitForIdle()
        assertEquals("$dir: the drag from the mark seeked", 2, seeks)
        assertEquals("$dir", 0.3f, value, 0.02f)
        assertEquals("$dir: and didn't open it", 1, markTaps)
        // TalkBack's double tap opens it.
        compose.onNodeWithTag("mark").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("$dir", 2, markTaps)
        assertTrue(seeks == 2)
    }

    @Test fun leftToRight() = walk(LayoutDirection.Ltr)

    @Test fun rightToLeft() = walk(LayoutDirection.Rtl)

    /** Without a mark to open (no playlist queue), the bar is the bar: no node, every touch seeks. */
    @Test fun noMarkNoNode() {
        var on by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                ThinSlider(0.1f, { value = it }, Modifier.testTag("bar"), onValueChangeFinished = { seeks++ }, marks = listOf(0.8f), markTap = 0.8f, onMarkTap = if (on) ({ markTaps++ }) else null, markTag = "mark")
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("mark").assertDoesNotExist()
        compose.onNodeWithTag("bar").performTouchInput { click(Offset(width * 0.8f, centerY)) }
        compose.waitForIdle()
        assertEquals(1, seeks)
        assertEquals(0, markTaps)
        on = true
        compose.waitForIdle()
        compose.onNodeWithTag("mark").assertExists()
    }
}
