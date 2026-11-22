package com.prism.music

import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.playback.PlaybackServices
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.playback.SkipFade
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 1.8.8 "Blend when I skip" (plan-mix §2.5, A5), in Prism's own player and service, Crossfade off, no network: four tones
 * written here as songs on the phone. Prism's own buttons ([com.prism.music.playback.PlayerConnection], which the A16 trial
 * found going round the session player) blend three Nexts, a tap in the queue, and a double Next (which lands on the song
 * after the chosen one, cut); a Next while paused is today's skip. After each: the right song, the main player at full
 * volume and its own speed, and no second player left. One line per skip with the tag "SkipBlendTest".
 *
 * Run on the mid tier and the A16 (muted: media volume 0; the test never changes the volume).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SkipBlendTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val dir get() = java.io.File(Fixture.context.filesDir, "skip-blend-test")

    @After fun cleanUp() {
        runCatching { instrumentation.runOnMainSync { Fixture.container.player.stop() } }
        dir.deleteRecursively()
        Fixture.container.settings.setSkipBlend(false)
        Fixture.container.settings.setAutoplay(true)
    }

    /** [seconds] of a sine at [hz], 44.1 kHz stereo 16-bit, at a third of full scale. */
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

    private class Now(val index: Int, val id: String?, val volume: Float, val speed: Float, val playing: Boolean, val active: Boolean, val head: Boolean)

    private fun now(exo: ExoPlayer, skip: SkipFade): Now = onMain {
        Now(exo.currentMediaItemIndex, exo.currentMediaItem?.mediaId, exo.volume, exo.playbackParameters.speed, exo.isPlaying, skip.active, skip.headAlive)
    }

    /** Back to normal on [song]: the main player there, playing, at full volume and its own speed, no second player. */
    private fun assertNormalOn(what: String, song: Song, exo: ExoPlayer, skip: SkipFade, playing: Boolean = true) {
        val settled = waitFor(9_000) { now(exo, skip).takeIf { !it.active } }
        assertNotNull("$what: still under way 9 s after the press", settled)
        val n = now(exo, skip)
        assertEquals("$what: the song", song.id, n.id)
        assertEquals("$what: main volume", 1f, n.volume)
        assertEquals("$what: main speed", 1f, n.speed)
        assertFalse("$what: a second player left", n.head)
        if (playing) assertTrue("$what: playing", waitFor(3_000) { now(exo, skip).playing.takeIf { it } } == true)
    }

    /** A skip by Prism's own button, timed until all is normal; true when it was blended (taken over at the press). */
    private fun press(what: String, skip: SkipFade, exo: ExoPlayer, action: () -> Unit): Boolean {
        val from = now(exo, skip).id
        val at = SystemClock.elapsedRealtime()
        var blended = false
        instrumentation.runOnMainSync { action(); blended = skip.active && exo.currentMediaItem?.mediaId == from }
        val done = waitFor(9_000) { now(exo, skip).takeIf { !it.active } }
        android.util.Log.i(TAG, "$what: blended $blended, all normal after ${SystemClock.elapsedRealtime() - at} ms, on ${done?.id}")
        return blended
    }

    @Test fun skipsBlendAndEndWithOnePlayerAtFullVolume() {
        Fixture.ready()
        val c = Fixture.container
        // Crossfade off (skip-blending works without it) and nothing else that would move the song or the volume.
        c.settings.setSkipBlend(true)
        c.settings.setMix(false)
        c.settings.setAutoplay(false)
        c.settings.setPauseFade(false)
        c.settings.setPauseOnMute(false)
        c.settings.setFaceDownPause(false)
        c.settings.setPerformanceMode(false)
        runBlocking {
            withTimeout(10_000) {
                while (c.settings.current.let { !it.sound.skipBlend || it.mix.on || it.autoplay || it.sound.pauseFade || it.behavior.pauseOnMute || it.behavior.faceDownPause || it.performanceMode }) delay(20)
            }
        }
        dir.mkdirs()
        val songs = listOf(330.0, 392.0, 440.0, 494.0).mapIndexed { i, hz ->
            val f = java.io.File(dir, "tone-${i + 1}.wav").also { wav(it, hz, 40) }
            Song(
                id = Song.LOCAL_PREFIX + android.net.Uri.fromFile(f), title = "Skip blend tone ${i + 1}",
                artists = listOf(ArtistRef("Test Artist", null)), durationSec = 40,
            )
        }
        Fixture.launch()
        val exo = waitFor(20_000) { PlaybackServices.playerForTest } ?: error("Prism's playback service didn't start")
        val skip = waitFor(5_000) { PlaybackServices.skipFadeForTest } ?: error("no skip-blend in the service")
        instrumentation.runOnMainSync { c.player.playQueue(songs, 0, QueueSource(QueueKind.PLAYLIST, "Skip blend test")) }
        repeat(3) { if (c.player.repeatMode.value != androidx.media3.common.Player.REPEAT_MODE_OFF) { instrumentation.runOnMainSync { c.player.cycleRepeat() }; Thread.sleep(700) } }
        fun position(): Long = onMain { c.player.position }
        assertTrue("the first tone plays", waitFor(20_000) { (c.player.currentSong.value?.id == songs[0].id && position() > 2_000).takeIf { it } } == true)

        // Three Nexts, each blended.
        for (i in 1..3) {
            assertTrue("Next $i blended", press("next $i", skip, exo) { c.player.next() })
            assertNormalOn("next $i", songs[i], exo, skip)
            Thread.sleep(2_000)
        }
        // A tap in the queue.
        assertTrue("the queue tap blended", press("queue tap", skip, exo) { c.player.skipTo(0) })
        assertNormalOn("queue tap", songs[0], exo, skip)
        Thread.sleep(2_000)
        // A double Next: the second, before the main player has moved, cuts to the song after the chosen one.
        instrumentation.runOnMainSync { c.player.next() }
        Thread.sleep(400)
        assertTrue("the first Next is under way", onMain { skip.active })
        assertEquals("still on the song heard", songs[0].id, onMain { exo.currentMediaItem?.mediaId })
        instrumentation.runOnMainSync { c.player.next() }
        assertFalse("cut at once: nothing stacked", onMain { skip.active })
        assertNormalOn("double next", songs[2], exo, skip)
        Thread.sleep(2_000)
        // Paused: today's skip, at once, no second player.
        instrumentation.runOnMainSync { c.player.setPlaying(false) }
        assertTrue("paused", waitFor(3_000) { (!now(exo, skip).playing).takeIf { it } } == true)
        val blendedPaused = press("next while paused", skip, exo) { c.player.next() }
        assertFalse("a skip while paused isn't blended", blendedPaused)
        assertNormalOn("next while paused", songs[3], exo, skip, playing = false)
        instrumentation.runOnMainSync { c.player.setPlaying(true) }
        assertNormalOn("playing again", songs[3], exo, skip)
    }

    companion object {
        private const val TAG = "SkipBlendTest"
        private const val RATE = 44_100
    }
}
