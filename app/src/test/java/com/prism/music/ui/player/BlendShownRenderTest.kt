package com.prism.music.ui.player

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.music.playback.BlendNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 1.8.8 (plan-mix §2.4): what the player shows while songs blend. The half-way frame drawn: the song before's cover over
 * the new one at half, and its title fading out as the new one comes in; and the rules for what is shown when.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h200dp-mdpi", application = android.app.Application::class)
class BlendShownRenderTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String): Bitmap {
        compose.waitForIdle()
        val b = compose.onRoot().captureToImage().asAndroidBitmap()
        File("build/blend-shown").apply { mkdirs() }.resolve("$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return b
    }

    /** The brightest of the red channel over a strip of the picture (x from [x0] to [x1], all of its height). */
    private fun brightest(b: Bitmap, x0: Int, x1: Int): Int = (x0 until x1).maxOf { x -> (0 until b.height).maxOf { y -> AColor.red(b.getPixel(x, y)) } }

    /** How far the title blend is ([TitleBlend]'s over); null for none under way. Set content once, then changed. */
    private var over by androidx.compose.runtime.mutableStateOf<Float?>(null)

    private fun drawTitles(at: Float?) {
        val first = !drawn
        over = at
        if (!first) { compose.waitForIdle(); return }
        drawn = true
        compose.setContent {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val v = over
            TitleBlend(
                v?.let { x -> { x } }, centred = false, Modifier.width(360.dp),
                // The song before's title is long, the new one short: the right of the picture holds the old one alone.
                old = { Text("WWWWWWWWWWWWWW", color = Color.White, fontSize = 40.sp, maxLines = 1, softWrap = false) },
            ) { Text("I", color = Color.White, fontSize = 40.sp) }
        }
        }
    }

    private var drawn = false

    @Test fun halfwayTheTitleBeforeIsAtHalfOverTheNewOne() {
        drawTitles(0.5f)
        val half = brightest(shot("title-half"), 200, 360)
        assertTrue("the old title at half: $half", half in 105..150)
        // The new title is drawn too (left), as bright as the old at half or brighter where they overlap.
        val left = brightest(shot("title-half"), 0, 30)
        assertTrue("the new title at half: $left", left >= 105)
    }

    @Test fun atTheStartOnlyTheOldTitleShowsAndAtTheEndOnlyTheNew() {
        drawTitles(1f)
        assertTrue(brightest(shot("title-start"), 200, 360) > 230)
        drawTitles(0f)
        assertTrue(brightest(shot("title-end"), 200, 360) < 20)
        // No blend: the title as it always was.
        drawTitles(null)
        val b = shot("title-none")
        assertTrue(brightest(b, 200, 360) < 20)
        assertTrue(brightest(b, 0, 30) > 230)
    }

    @Test fun halfwayTheCoverBeforeIsAtHalfOverTheNewOne() {
        compose.setContent {
            Box(Modifier.size(100.dp).background(Color.Blue)) {
                BlendOver({ blendOverAlpha(0.5f, reduceMotion = false) }, Modifier.fillMaxSize()) {
                    Box(Modifier.matchParentSize().background(Color.Red))
                }
            }
        }
        val px = shot("cover-half").getPixel(50, 50)
        assertEquals("red at half: ${Integer.toHexString(px)}", 128.0, AColor.red(px).toDouble(), 6.0)
        assertEquals("blue at half: ${Integer.toHexString(px)}", 128.0, AColor.blue(px).toDouble(), 6.0)
    }

    @Test fun reduceMotionSwitchesHalfway() {
        assertEquals(1f, blendOverAlpha(0f, reduceMotion = true))
        assertEquals(1f, blendOverAlpha(0.49f, reduceMotion = true))
        assertEquals(0f, blendOverAlpha(0.5f, reduceMotion = true))
        assertEquals(0.5f, blendOverAlpha(0.5f, reduceMotion = false))
        assertEquals(0f, blendOverAlpha(1.2f, reduceMotion = false))
    }

    @Test fun duringASkipBlendThePlayerShowsTheChosenSongAtOnce() {
        // A skip-blend: the main player is still on "a", fading into "b": "b" is shown.
        assertEquals("b", skipShownId("a", BlendNow("a", "b", 0f)))
        assertEquals("b", skipShownId("a", BlendNow("a", "b", 0.7f)))
        // An end-of-song blend has moved the main player already: nothing in its place.
        assertNull(skipShownId("b", BlendNow("a", "b", 0.3f)))
        assertNull(skipShownId("a", null))
        assertNull(skipShownId("a", BlendNow("a", "a", 0.3f)))
    }

    @Test fun theCanvasLoopComesAndGoesWithTheBlend() {
        val b = BlendNow("a", "b", 0.25f)
        assertEquals(0.25f, canvasBlendAlpha(b, "b"))
        assertEquals(0.75f, canvasBlendAlpha(b, "a"))
        assertEquals(1f, canvasBlendAlpha(b, "c"))
        assertEquals(1f, canvasBlendAlpha(null, "b"))
    }
}
