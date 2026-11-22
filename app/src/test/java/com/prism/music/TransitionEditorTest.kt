package com.prism.music

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.CrossfadeCurve
import com.prism.music.playback.Tempo
import com.prism.music.playback.Transition
import com.prism.music.playback.TransitionStyle
import com.prism.music.playback.Transitions
import com.prism.music.ui.screens.TRANSITION_IN_TAG
import com.prism.music.ui.screens.TRANSITION_OUT_TAG
import com.prism.music.ui.screens.TRANSITION_RESET_TAG
import com.prism.music.ui.screens.TRANSITION_SHORTENED_TAG
import com.prism.music.ui.screens.TransitionEditing
import com.prism.music.ui.screens.TransitionEditorBody
import com.prism.music.ui.screens.TransitionPair
import com.prism.music.ui.screens.TransitionSide
import com.prism.music.ui.theme.PrismTheme
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
import kotlin.math.abs

/**
 * 1.8.8 Mix it yourself (plan-mix.md §4.3, C3): the transition editor. Its handles stay in their windows (the first
 * song's last 90 s, the second's first 90 s), snap lands on a beat, TalkBack can set each handle as a slider, Reset is
 * only there once something is saved, a too-long overlap is said, and the screen is drawn (build/transition-editor/).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1400dp-xhdpi", application = android.app.Application::class)
class TransitionEditorTest {
    @get:Rule val compose = createComposeRule()

    private val from = Song("a", "Simple Man", listOf(ArtistRef("Lynyrd Skynyrd", null)), durationSec = 200)
    private val to = Song("b", "Wonderwall", listOf(ArtistRef("Oasis", null)), durationSec = 240)
    private val pair = TransitionPair("PL1", from, to)
    /** 120 BPM (a beat every 500 ms), the ending's beats on whole seconds, the start's from 250 ms. */
    private val tempoA = Tempo(120.0, 5.0, 0, 199_000, 0, 198_000, fadeOutMs = 0, quietIntroMs = 0)
    private val tempoB = Tempo(120.0, 5.0, 0, 239_000, 250, 238_250, fadeOutMs = 0, quietIntroMs = 0)
    private fun wave(n: Int) = FloatArray(n) { i -> 0.3f + 0.7f * abs(kotlin.math.sin(i / 3.0)).toFloat() }

    private var t by mutableStateOf<Transition?>(null)
    private var saved: Transition? = null
    private var resets = 0
    private var savedRow by mutableStateOf(false)

    private fun show(measured: Boolean = true, start: Transition = Transition(170_000, 5_000, 8_000)) {
        t = start
        compose.setContent {
            PrismTheme(AppSettings(), artworkColor = null) {
                Box(Modifier.fillMaxSize()) {
                    TransitionEditorBody(
                        pair, TransitionSide(from, 200_000, tempoA.takeIf { measured }, null, wave(90).takeIf { measured }),
                        TransitionSide(to, 240_000, tempoB.takeIf { measured }, null, wave(90).takeIf { measured }),
                        t, savedRow, measuring = false, needsData = false, listening = false, listener = CrossfadeCurve.EQUAL_POWER,
                        onChange = { t = it }, onSave = { t = it; saved = it; savedRow = true }, onReset = { resets++; savedRow = false },
                        onListen = {}, onMeasure = {}, onClose = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun setHandle(tag: String, ms: Long) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(ms.toFloat()) }
        compose.waitForIdle()
    }

    @Test fun handlesMoveAndStayInTheirWindows() {
        show()
        setHandle(TRANSITION_OUT_TAG, 150_000)
        assertEquals(150_000L, saved!!.outMs)
        // Before the first song's last 90 s, or past its end: kept inside.
        setHandle(TRANSITION_OUT_TAG, 20_000)
        assertEquals(110_000L, saved!!.outMs)
        setHandle(TRANSITION_OUT_TAG, 260_000)
        assertEquals(199_000L, saved!!.outMs)
        setHandle(TRANSITION_IN_TAG, 30_000)
        assertEquals(30_000L, saved!!.inMs)
        setHandle(TRANSITION_IN_TAG, 200_000)
        assertEquals(90_000L, saved!!.inMs)
        // TalkBack reads each handle as a slider with its value.
        val node = compose.onNodeWithTag(TRANSITION_IN_TAG).fetchSemanticsNode()
        assertEquals("90 s from the start", node.config[SemanticsProperties.StateDescription])
        assertEquals(90_000f, node.config[SemanticsProperties.ProgressBarRangeInfo].current)
    }

    @Test fun snapLandsOnABeat() {
        show(start = Transition(170_000, 5_000, 8_000, snap = true))
        setHandle(TRANSITION_OUT_TAG, 170_230)
        assertEquals("the ending's beat nearest", 170_000L, saved!!.outMs)
        setHandle(TRANSITION_IN_TAG, 4_400)
        assertEquals("the start's beat nearest (from its first beat at 250 ms)", 4_250L, saved!!.inMs)
        // Pure: inside the window, never off it, whatever the grid.
        val out = TransitionEditing.placeOut(500_000, 200_000, tempoA, snap = true)
        assertTrue(out in TransitionEditing.outRange(200_000) && (out - tempoA.outroBeatMs) % 500 == 0L)
        assertEquals("no clear beat: where it's put", 170_123L, TransitionEditing.placeOut(170_123, 200_000, tempoA.copy(confidence = 0.0), snap = true))
    }

    @Test fun snapNeedsBothSongsMeasured() {
        show(measured = false)
        compose.onNodeWithText(tr(R.string.transition_snap)).assertIsNotEnabled()
        assertTrue(compose.onAllNodesWithText(tr(R.string.transition_not_measured)).fetchSemanticsNodes().size == 2)
    }

    @Test fun resetIsThereOnceSomethingIsSavedAndClearsIt() {
        show()
        compose.onNodeWithTag(TRANSITION_RESET_TAG).assertIsNotEnabled()
        setHandle(TRANSITION_OUT_TAG, 160_000)
        compose.onNodeWithTag(TRANSITION_RESET_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, resets)
        compose.onNodeWithTag(TRANSITION_RESET_TAG).assertIsNotEnabled()
    }

    @Test fun aTooLongOverlapIsSaid() {
        show(start = Transition(195_000, 5_000, 20_000))
        compose.onNodeWithTag(TRANSITION_SHORTENED_TAG).assertExists()
        t = Transition(150_000, 5_000, 20_000)
        compose.waitForIdle()
        compose.onNodeWithTag(TRANSITION_SHORTENED_TAG).assertDoesNotExist()
    }

    @Test fun theWaveformWindowComesFromTheShapeOrTheOutline() {
        val energy = FloatArray(200) { if (it >= 150) 1f else 0.04f }
        val w = TransitionEditing.wave(energy, null, 200_000, 110_000, 200_000, 90)!!
        assertEquals(90, w.size)
        assertTrue("loud from 150 s: ${w.toList()}", w[0] < 0.3f && w[89] == 1f && w[39] < 0.3f && w[40] == 1f)
        val outline = FloatArray(100) { it / 99f }
        val o = TransitionEditing.wave(null, outline, 200_000, 0, 90_000, 45)!!
        assertTrue(o.first() < o.last())
        assertNull(TransitionEditing.wave(null, null, 200_000, 0, 90_000, 45))
        assertEquals("12.5 s", TransitionEditing.seconds(12_500))
        assertEquals("8 s", TransitionEditing.seconds(8_000))
    }

    @Test fun theAutomaticStartIsTodaysBlend() {
        val auto = Transitions.automatic(tempoA, tempoB, 200_000)
        assertEquals(TransitionStyle.AUTOMATIC, auto.style)
        assertTrue(auto.snap)
        assertEquals(199_000L - auto.lengthMs, auto.outMs)
    }

    @Test fun theScreenIsDrawn() {
        show(start = Transition(180_000, 4_000, 10_000, TransitionStyle.BASS_SWAP))
        val b = compose.onRoot().captureToImage().asAndroidBitmap()
        File("build/transition-editor").apply { mkdirs() }.resolve("editor.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Something besides the background: the waveforms' bars and the shaded overlap.
        val colors = HashSet<Int>()
        for (y in 0 until b.height step 7) for (x in 0 until b.width step 7) colors += b.getPixel(x, y)
        assertTrue("drawn: ${colors.size} colors", colors.size > 20)
        compose.onNodeWithText(tr(R.string.transition_bass_swap)).assertExists()
    }

}
