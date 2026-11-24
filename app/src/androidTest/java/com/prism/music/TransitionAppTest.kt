package com.prism.music

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.playback.PlaybackServices
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.ui.screens.TRANSITION_IN_TAG
import com.prism.music.ui.screens.TRANSITION_LENGTH_TAG
import com.prism.music.ui.screens.TRANSITION_LISTEN_TAG
import com.prism.music.ui.screens.TRANSITION_OUT_TAG
import com.prism.music.ui.screens.TRANSITION_ROW_MARK_TAG
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 1.8.8 Mix it yourself (plan-mix.md §4.4, C4 and C5), in the app, Crossfade off, no network: three tones written here
 * as a Prism playlist. A song's ⋯ → "Transition to the next song" opens the editor; its handles and length are set (as
 * TalkBack sets them) and saved. Listen plays on its own players: the queue's song pauses and, when the editor closes,
 * plays on from its place. The row gets its mark; played across, the mark is at the saved spot, the second song comes
 * in where it was set to, and the main player ends at full volume.
 *
 * Run on the mid tier and the A16 (muted: media volume 0; the test never changes the volume).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class TransitionAppTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()

    private val c get() = Fixture.container
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val dir get() = java.io.File(Fixture.context.filesDir, "transition-test")
    private var listId: String? = null

    @After fun cleanUp() {
        runCatching { instrumentation.runOnMainSync { c.player.stop() } }
        listId?.let { c.prismLibrary.delete(it) }
        dir.deleteRecursively()
        c.settings.setAutoplay(true)
    }

    private fun wav(file: java.io.File, hz: Double, seconds: Int) {
        val frames = RATE * seconds
        val b = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + frames * 4).put("WAVE".toByteArray()).put("fmt ".toByteArray())
        b.putInt(16).putShort(1.toShort()).putShort(2.toShort()).putInt(RATE).putInt(RATE * 4).putShort(4.toShort()).putShort(16.toShort())
        b.put("data".toByteArray()).putInt(frames * 4)
        for (i in 0 until frames) {
            val v = (32767 / 3.0 * sin(2 * PI * hz * i / RATE)).roundToInt().toShort()
            b.putShort(v).putShort(v)
        }
        file.writeBytes(b.array())
    }

    private fun <T> waitFor(ms: Long, what: () -> T?): T? {
        val until = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < until) { what()?.let { return it }; Thread.sleep(25) }
        return null
    }

    private fun <T> onMain(read: () -> T): T {
        var out: T? = null
        instrumentation.runOnMainSync { out = read() }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    private fun position(): Long = onMain { c.player.position }

    private fun setHandle(tag: String, ms: Long) {
        compose.onAllNodes(hasTestTag(tag)).onFirst().performSemanticsAction(SemanticsActions.SetProgress) { it(ms.toFloat()) }
        compose.waitForIdle()
    }

    @Test fun madeInTheEditorItPlaysAcrossAtTheSavedSpot() {
        Fixture.ready()
        c.settings.setMix(false)
        c.settings.setAutoplay(false)
        c.settings.setPauseFade(false)
        c.settings.setPauseOnMute(false)
        c.settings.setFaceDownPause(false)
        c.settings.setPerformanceMode(false)
        runBlocking {
            withTimeout(10_000) {
                while (c.settings.current.let { it.mix.on || it.autoplay || it.sound.pauseFade || it.behavior.pauseOnMute || it.behavior.faceDownPause || it.performanceMode }) delay(20)
            }
        }
        dir.mkdirs()
        val songs = listOf(330.0, 392.0, 440.0).mapIndexed { i, hz ->
            val f = java.io.File(dir, "tone-${i + 1}.wav").also { wav(it, hz, 40) }
            Song(Song.LOCAL_PREFIX + android.net.Uri.fromFile(f), "Transition tone ${i + 1}", listOf(ArtistRef("Test Artist", null)), durationSec = 40)
        }
        val id = Fixture.prismPlaylist("Transition test", songs).also { listId = it }
        Fixture.launch()
        val exo = waitFor(20_000) { PlaybackServices.playerForTest } ?: error("Prism's playback service didn't start")
        // Something plays first: the third tone, 10 s in (Listen must leave it where it was).
        instrumentation.runOnMainSync { c.player.playQueue(listOf(songs[2]), 0, QueueSource(QueueKind.PLAYLIST, "Elsewhere")) }
        assertTrue("the third tone plays", waitFor(20_000) { (c.player.currentSong.value?.id == songs[2].id && position() > 1_000).takeIf { it } } == true)
        instrumentation.runOnMainSync { c.player.seekTo(10_000) }
        Thread.sleep(500)

        // The playlist's page, the first song's ⋯ → Transition to the next song.
        compose.tapTab("Library")
        for (i in 0 until 8) {
            if (compose.onAllNodes(hasText("Playlists") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) break
            compose.onRoot().performTouchInput { swipeRight(startX = width * 0.15f, endX = width * 0.95f) }
            compose.waitForIdle()
        }
        compose.onAllNodes(hasText("Playlists") and hasClickAction()).onFirst().performClick()
        for (i in 0 until 6) {
            if (compose.onAllNodes(hasText("Transition test") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) break
            compose.onRoot().performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.35f) }
            compose.waitForIdle()
        }
        compose.clickWhenFound(hasText("Transition test") and hasClickAction(), 10_000)
        compose.waitForText("Transition tone 1")
        // The rows' ⋯ under the page's own (the topmost): the first is the first song's.
        val more = compose.onAllNodes(hasContentDescription("More") and hasClickAction())
        val byTop = more.fetchSemanticsNodes().withIndex().sortedBy { it.value.boundsInRoot.top }.map { it.index }
        more[byTop[1]].performClick()
        compose.clickWhenFound(hasText(tr(R.string.transition_to_the_next_song)) and hasClickAction())
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag(TRANSITION_OUT_TAG)).fetchSemanticsNodes().isNotEmpty() }

        // Set as TalkBack sets them: the first tone leaves 25 s in, the second comes in 8 s in, 4 s together.
        setHandle(TRANSITION_OUT_TAG, 25_000)
        setHandle(TRANSITION_IN_TAG, 8_000)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and hasAnyAncestor(hasTestTag(TRANSITION_LENGTH_TAG))).onFirst()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        compose.waitForIdle()
        val saved = waitFor(5_000) { runBlocking { c.transitions.get(id, songs[0].id, songs[1].id) }?.takeIf { it.lengthMs == 4_000L } }
        assertNotNull("saved", saved)
        assertEquals(25_000L, saved!!.outMs)
        assertEquals(8_000L, saved.inMs)
        Fixture.screenshot("TransitionEditor")

        // Listen: the queue's song pauses where it is; Stop; closing the editor brings it back from there.
        val before = position()
        compose.onAllNodes(hasTestTag(TRANSITION_LISTEN_TAG)).onFirst().performClick()
        assertTrue("the queue paused", waitFor(3_000) { (!c.player.isPlaying.value).takeIf { it } } == true)
        Thread.sleep(8_000)
        assertTrue("the queue's song stayed at its place (${position()} vs $before)", abs(position() - before) < 1_500)
        compose.waitForText(tr(R.string.stop))
        compose.onAllNodes(hasTestTag(TRANSITION_LISTEN_TAG)).onFirst().performClick()
        compose.waitForText(tr(R.string.listen))
        // Closed by Back (the editor is a sheet over the page).
        androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressBack()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(TRANSITION_OUT_TAG)).fetchSemanticsNodes().isEmpty() }
        assertTrue("the queue plays again", waitFor(5_000) { c.player.isPlaying.value.takeIf { it } } == true)
        assertTrue("from its place (${position()} vs $before)", abs(position() - before) < 3_000)

        // The row has its mark now.
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(TRANSITION_ROW_MARK_TAG)).fetchSemanticsNodes().size == 1 }

        // Played across: the mark at the saved spot, the second tone in where it was set to, full volume after.
        instrumentation.runOnMainSync { c.player.playQueue(songs, 0, QueueSource(QueueKind.PLAYLIST, "Transition test", originId = id)) }
        assertTrue("the first tone plays", waitFor(20_000) { (c.player.currentSong.value?.id == songs[0].id && position() > 500).takeIf { it } } == true)
        instrumentation.runOnMainSync { c.player.seekTo(12_000) }
        val mark = waitFor(10_000) { c.mixState.mark.value?.takeIf { it.songId == songs[0].id } }
        assertNotNull("the crossfade mark", mark)
        assertEquals(25_000L, mark!!.startMs)
        assertEquals(4_000L, mark.lengthMs)
        assertTrue("on to the second tone", waitFor(25_000) { (c.player.currentSong.value?.id == songs[1].id).takeIf { it } } == true)
        val inAt = position()
        assertTrue("the second tone came in at 8 s ($inAt)", inAt in 7_500..10_500)
        assertTrue("full volume after", waitFor(8_000) { onMain { exo.volume == 1f }.takeIf { it } } == true)
        assertTrue("marked as played", waitFor(5_000) { runBlocking { c.db.transitions().get(id, songs[0].id, songs[1].id) }?.usedAt?.takeIf { it > 0 } } != null)
        // The other way round, the second into the third, is automatic: Crossfade is off, so no mark.
        Thread.sleep(2_000)
        assertFalse("no mark after the second tone", c.mixState.mark.value?.songId == songs[1].id)
    }

    companion object {
        private const val RATE = 44_100
    }
}
