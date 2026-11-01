package com.prism.music

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.TvFixture.awaitFocusOn
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.ui.tv.TAG_HOME
import com.prism.music.ui.tv.TAG_LYRICS
import com.prism.music.ui.tv.TAG_PLAYER
import com.prism.music.ui.tv.TAG_QUEUE
import com.prism.music.ui.tv.TAG_VIDEO
import com.prism.music.ui.tv.TAG_VIDEO_SCREEN
import com.prism.music.ui.tv.TvVideoControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prism TV's music video with the remote only (the owner's real TV, 1.8.0: "When on a video, you can't move to anything
 * other than the play button" and "The video screen needs to show the queue as well"). Songs are silent tones, which
 * have no video, so the screen shows the cover where the video would be; the controls, their focus and the queue are
 * the same either way.
 */
@TvTest
@RunWith(AndroidJUnit4::class)
class TvVideoTests {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val tones = ArrayList<android.net.Uri>()
    private val player get() = Fixture.container.player

    @Before fun setUp() {
        TvFixture.assumeTv()
        Fixture.ready()
    }

    @After fun cleanUp() {
        runCatching { instrumentation.runOnMainSync { player.setVideoMode(false); player.stop() } }
        tones.forEach { runCatching { Fixture.context.contentResolver.delete(it, null, null) } }
    }

    private fun lineUp() {
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        instrumentation.uiAutomation.grantRuntimePermission(Fixture.context.packageName, perm)
        tones += Fixture.tone("Prism TV video one", 90)
        tones += Fixture.tone("Prism TV video two", 90)
        tones += Fixture.tone("Prism TV video three", 90)
        val local = Fixture.container.localMusic
        runBlocking { local.scan() }
        val songs = listOf("Prism TV video one", "Prism TV video two", "Prism TV video three").map { t -> local.tracks.value.first { it.song.title == t }.song }
        instrumentation.runOnMainSync { player.playQueue(songs, 0, QueueSource(QueueKind.OTHER, "TV video test")) }
        waitFor(20_000) { player.currentIndex.value == 0 && player.isPlaying.value }
    }

    /** The tones have no video: the player is told this one has, so its Video button shows (as for a real song). */
    private fun pretendItHasAVideo() {
        val f = com.prism.music.playback.PlayerConnection::class.java.getDeclaredField("_videoAvailable").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        instrumentation.runOnMainSync { (f.get(player) as MutableStateFlow<Boolean>).value = true }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Home → the player → its Video button → the video, focus on play/pause. */
    private fun openTheVideo() {
        lineUp()
        TvFixture.launch()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/player"), 15_000)
        TvFixture.ok()
        compose.awaitFocusOn(hasTestTag("$TAG_PLAYER/playpause"), 10_000)
        // The tone's own video lookup answers "none" first; then the button is shown.
        val end = System.currentTimeMillis() + 10_000
        while (!exists("$TAG_PLAYER/video")) {
            if (System.currentTimeMillis() > end) throw AssertionError("the player's Video button never showed")
            pretendItHasAVideo(); Thread.sleep(300)
        }
        TvFixture.down()
        compose.awaitFocusOn(hasTestTag("$TAG_PLAYER/lyrics"))
        TvFixture.right(); compose.awaitFocusOn(hasTestTag("$TAG_PLAYER/queue"))
        TvFixture.right(); compose.awaitFocusOn(hasTestTag("$TAG_PLAYER/video"))
        TvFixture.ok()
        compose.awaitFocusOn(hasTestTag(TvVideoControl.PLAYPAUSE.fullTag), 10_000)
    }

    private fun focusOn(c: TvVideoControl) = compose.awaitFocusOn(hasTestTag(c.fullTag))
    private fun position(): Long { var p = 0L; instrumentation.runOnMainSync { p = player.position }; return p }

    /** Every control is reached with the D-pad; ← → on the progress line seek; Lyrics opens over the video and back. */
    @Test fun everyControlOnTheVideoIsReachedWithTheDpad() {
        openTheVideo()
        // Paused, the controls stay up while the remote moves through them.
        instrumentation.runOnMainSync { player.setPlaying(false) }
        waitFor(5_000) { !player.isPlaying.value }
        TvFixture.left(); focusOn(TvVideoControl.PREVIOUS)
        TvFixture.right(); focusOn(TvVideoControl.PLAYPAUSE)
        for (c in listOf(TvVideoControl.NEXT, TvVideoControl.LYRICS, TvVideoControl.QUEUE, TvVideoControl.SONG, TvVideoControl.MORE)) {
            TvFixture.right(); focusOn(c)
        }
        // Up: the progress line. → seeks 10 s ahead, ← back; focus stays on the line.
        TvFixture.up(); focusOn(TvVideoControl.SEEK)
        val before = position()
        TvFixture.right()
        waitFor(5_000) { position() >= before + 8_000 }
        TvFixture.left()
        waitFor(5_000) { position() < before + 5_000 }
        focusOn(TvVideoControl.SEEK)
        // Down: play/pause again. OK plays.
        TvFixture.down(); focusOn(TvVideoControl.PLAYPAUSE)
        TvFixture.ok()
        waitFor(5_000) { player.isPlaying.value }
        TvFixture.ok()
        waitFor(5_000) { !player.isPlaying.value }
        // ⋯ opens the player's actions panel; Back closes it, focus back on ⋯.
        repeat(6) { TvFixture.right(); Thread.sleep(150) }
        focusOn(TvVideoControl.MORE)
        TvFixture.ok()
        compose.waitUntil(5_000) { exists(com.prism.music.ui.tv.TAG_ACTIONS) }
        TvFixture.back()
        focusOn(TvVideoControl.MORE)
        // Lyrics: the lyrics page, and Back comes back to the video on Lyrics.
        TvFixture.left(); TvFixture.left(); TvFixture.left()
        focusOn(TvVideoControl.LYRICS)
        TvFixture.ok()
        compose.awaitFocusOn(hasTestTag("$TAG_LYRICS/lines"), 10_000)
        TvFixture.back()
        focusOn(TvVideoControl.LYRICS)
        // "Back to the song": the player.
        TvFixture.right(); TvFixture.right(); focusOn(TvVideoControl.SONG)
        TvFixture.ok()
        compose.awaitFocusOn(hasTestTag("$TAG_PLAYER/video"), 10_000)
    }

    /**
     * Playing, the controls fade after a few seconds with focus on the screen (never on a hidden control); any key
     * brings them back on the control left; Back hides them, and Back again leaves the video.
     */
    @Test fun theControlsFadeAndComeBackAndFocusIsNeverOnAHiddenOne() {
        openTheVideo()
        TvFixture.right(); focusOn(TvVideoControl.NEXT)
        // NEXT pressed would skip; it's only focused. Now the remote is put down.
        compose.awaitFocusOn(hasTestTag(TAG_VIDEO_SCREEN), 10_000)
        compose.waitUntil(5_000) { !exists(TvVideoControl.NEXT.fullTag) }
        val index = player.currentIndex.value
        // A key brings them back (it doesn't press anything): focus on Next, where it was.
        TvFixture.ok()
        focusOn(TvVideoControl.NEXT)
        Thread.sleep(500)
        assertTrue("the key that showed the controls pressed Next", player.currentIndex.value == index)
        // Back with the controls up hides them; Back again: the player.
        TvFixture.back()
        compose.awaitFocusOn(hasTestTag(TAG_VIDEO_SCREEN), 5_000)
        compose.waitUntil(5_000) { !exists(TvVideoControl.NEXT.fullTag) }
        assertTrue(exists(TAG_VIDEO))
        TvFixture.back()
        compose.awaitFocusOn(hasTestTag("$TAG_PLAYER/video"), 10_000)
    }

    /** The queue on the video: the player's panel, from the Queue button; a song picked plays and the video stays. */
    @Test fun theQueueOpensFromTheVideoAndPlaysTheSongPicked() {
        openTheVideo()
        TvFixture.right(); focusOn(TvVideoControl.NEXT)
        TvFixture.right(); focusOn(TvVideoControl.LYRICS)
        TvFixture.right(); focusOn(TvVideoControl.QUEUE)
        TvFixture.ok()
        // Opens at the playing song, focus on the next one.
        compose.awaitFocusOn(hasTestTag("$TAG_QUEUE/1"), 10_000)
        TvFixture.down()
        compose.awaitFocusOn(hasTestTag("$TAG_QUEUE/2"))
        TvFixture.ok()
        waitFor(5_000) { player.currentIndex.value == 2 && player.isPlaying.value }
        // The panel closes onto the video, Queue focused; the video page stays.
        focusOn(TvVideoControl.QUEUE)
        assertTrue(exists(TAG_VIDEO))
        // Again (paused, so the controls stay), on the last song: it opens on that one. Left closes it.
        instrumentation.runOnMainSync { player.setPlaying(false) }
        TvFixture.ok()
        compose.awaitFocusOn(hasTestTag("$TAG_QUEUE/2"), 10_000)
        TvFixture.left()
        focusOn(TvVideoControl.QUEUE)
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("not after $timeoutMs ms")
            Thread.sleep(100)
        }
    }
}
