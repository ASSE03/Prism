package com.prism.music

import android.content.Context
import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.playback.MixEngine
import com.prism.music.playback.MusicKey
import com.prism.music.playback.PlaybackServices
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.playback.Tempo
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
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 1.8.8 Automix (plan-mix.md §3.5, B5), in Prism's own player and service, no network: a fixed playlist of 13 tones
 * written here, each with a click on every beat at its own tempo, its scan (tempo, edges, key) written as Prism keeps it,
 * so the 12 transitions between them get a spread of moves (bass swaps, filters, echoes, cuts, blends). Crossfade is on
 * and Automix chosen. Each transition is reached by a seek to 22 s before the tone's music ends; after it, the next tone
 * plays, the main player is at full volume and its own speed, no blend is under way, and one move more is logged. One
 * line per transition with the tag "AutomixAppTest" (the move, and how long it took).
 *
 * Until B6's setting is merged, Automix is turned on through the engine's test switch ([MixEngine.testAutomix]).
 * Run on the A16 (muted: media volume 0; the test never changes the volume).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AutomixAppTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val dir get() = java.io.File(Fixture.context.filesDir, "automix-test")

    @After fun cleanUp() {
        runCatching { instrumentation.runOnMainSync { Fixture.container.player.stop() } }
        PlaybackServices.mixForTest?.testAutomix = null
        dir.deleteRecursively()
        Fixture.container.settings.setMix(false)
        Fixture.container.settings.setAutoplay(true)
    }

    /** One tone of the playlist: its pitch, tempo and key (C major = 0; F♯ = 6 clashes with it). */
    private class Tone(val hz: Double, val bpm: Double, val key: Int, val fadeOutMs: Long = 400, val quietIntroMs: Long = 0)

    /**
     * Picked for a spread of moves, in order: matched tempos (bass swap), a clashing key (filter out), a far tempo (echo),
     * a far tempo and a clash with a hard start (cut), a fade-out (blend), and back again.
     */
    private val tones = listOf(
        Tone(330.0, 120.0, 0), Tone(349.0, 123.0, 7), Tone(370.0, 122.0, 1), Tone(392.0, 90.0, 8),
        Tone(415.0, 128.0, 2), Tone(440.0, 96.0, 8, fadeOutMs = 8_000), Tone(466.0, 98.0, 3), Tone(494.0, 100.0, 10),
        Tone(523.0, 140.0, 4), Tone(554.0, 138.0, 4), Tone(587.0, 100.0, 11), Tone(622.0, 126.0, 5), Tone(659.0, 124.0, 0),
    )

    /** [seconds] of a sine at [hz] with a short click on every beat at [bpm], 44.1 kHz stereo 16-bit. */
    private fun wav(file: java.io.File, hz: Double, bpm: Double, seconds: Int) {
        val frames = RATE * seconds
        val beat = RATE * 60.0 / bpm
        val b = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + frames * 4).put("WAVE".toByteArray()).put("fmt ".toByteArray())
        b.putInt(16).putShort(1.toShort()).putShort(2.toShort()).putInt(RATE).putInt(RATE * 4).putShort(4.toShort()).putShort(16.toShort())
        b.put("data".toByteArray()).putInt(frames * 4)
        val first = (0.5 * RATE).toInt()
        for (i in 0 until frames) {
            val sinceBeat = if (i < first) Double.MAX_VALUE else (i - first) % beat
            val click = if (sinceBeat < RATE / 100) 0.4 * (1 - sinceBeat / (RATE / 100.0)) * sin(2 * PI * 60 * i / RATE) else 0.0
            val v = (32767 * (0.25 * sin(2 * PI * hz * i / RATE) + click)).roundToInt().coerceIn(-32768, 32767).toShort()
            b.putShort(v).putShort(v)
        }
        file.writeBytes(b.array())
    }

    /** Song [id]'s scan as Prism keeps it, so the tones aren't scanned again (and Automix knows them at once). */
    private fun writeScan(id: String, t: Tone) {
        val ctx = Fixture.context
        val beat = 60_000.0 / t.bpm
        val last = SECONDS * 1000L - 1_000
        val outro = 500 + floor((last - 500) / beat) * beat
        ctx.getSharedPreferences("mix_tempo", Context.MODE_PRIVATE).edit()
            .putString(id, Tempo(t.bpm, 8.0, 0, last, 500, outro.toLong(), t.fadeOutMs, t.quietIntroMs).encode()).commit()
        ctx.getSharedPreferences("song_loudness", Context.MODE_PRIVATE).edit().putFloat(id, -14f).commit()
        ctx.getSharedPreferences("song_peak", Context.MODE_PRIVATE).edit().putFloat(id, -3f).commit()
        ctx.getSharedPreferences("song_key", Context.MODE_PRIVATE).edit().putString(id, MusicKey(t.key, false, 0.5f).encode()).commit()
        Fixture.container.waves.put(id, FloatArray(com.prism.music.data.SongWaves.POINTS) { 0.5f })
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

    @Test fun twelveTransitionsEachEndOnTheNextSongAtFullVolumeWithItsMove() {
        Fixture.ready()
        val c = Fixture.container
        c.settings.setMix(true)
        c.settings.setMixAuto(true)
        c.settings.setAutoplay(false)
        c.settings.setPauseFade(false)
        c.settings.setPauseOnMute(false)
        c.settings.setFaceDownPause(false)
        c.settings.setPerformanceMode(false)
        c.settings.setSkipBlend(false)
        runBlocking {
            withTimeout(10_000) {
                while (c.settings.current.let { !it.mix.on || !it.mix.auto || it.autoplay || it.sound.pauseFade || it.behavior.pauseOnMute || it.behavior.faceDownPause || it.performanceMode }) delay(20)
            }
        }
        dir.mkdirs()
        val songs = tones.mapIndexed { i, t ->
            val f = java.io.File(dir, "automix-${i + 1}.wav").also { wav(it, t.hz, t.bpm, SECONDS) }
            Song(
                id = Song.LOCAL_PREFIX + android.net.Uri.fromFile(f), title = "Automix tone ${i + 1}",
                // No album: an album playing through isn't blended.
                artists = listOf(ArtistRef("Test Artist", null)), durationSec = SECONDS,
            ).also { writeScan(it.id, t) }
        }
        Fixture.launch()
        val exo: ExoPlayer = waitFor(20_000) { PlaybackServices.playerForTest } ?: error("Prism's playback service didn't start")
        val mix: MixEngine = waitFor(5_000) { PlaybackServices.mixForTest } ?: error("no crossfade engine in the service")
        instrumentation.runOnMainSync { mix.testAutomix = true }
        instrumentation.runOnMainSync { c.player.playQueue(songs, 0, QueueSource(QueueKind.PLAYLIST, "Automix test")) }
        repeat(3) { if (c.player.repeatMode.value != androidx.media3.common.Player.REPEAT_MODE_OFF) { instrumentation.runOnMainSync { c.player.cycleRepeat() }; Thread.sleep(700) } }
        fun position(): Long = onMain { c.player.position }
        assertTrue("the first tone plays", waitFor(20_000) { (c.player.currentSong.value?.id == songs[0].id && position() > 1_000).takeIf { it } } == true)

        for (i in 0 until songs.size - 1) {
            val before = onMain { mix.recentMoves.size }
            val at = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync { c.player.seekTo(SECONDS * 1000L - 23_000) }
            assertTrue("transition ${i + 1}: on to tone ${i + 2}", waitFor(40_000) { (onMain { exo.currentMediaItem?.mediaId } == songs[i + 1].id).takeIf { it } } == true)
            val settled = waitFor(25_000) { onMain { (!mix.blending && exo.volume == 1f && exo.playbackParameters.speed == 1f).takeIf { it } } }
            assertNotNull("transition ${i + 1}: still blending 25 s after the hand-over", settled)
            assertEquals("transition ${i + 1}: the song", songs[i + 1].id, onMain { exo.currentMediaItem?.mediaId })
            assertEquals("transition ${i + 1}: full volume", 1f, onMain { exo.volume })
            assertTrue("transition ${i + 1}: playing", waitFor(3_000) { onMain { exo.isPlaying }.takeIf { it } } == true)
            val moves = onMain { mix.recentMoves }
            assertTrue("transition ${i + 1}: its move logged", moves.size > before || moves.size == MixEngine.RECENT_MOVES)
            assertFalse("transition ${i + 1}: no effect left on the main player", onMain { mix.mainFxArmed })
            android.util.Log.i(TAG, "transition ${i + 1}: ${moves.lastOrNull()} in ${SystemClock.elapsedRealtime() - at} ms")
            Thread.sleep(1_000)
        }
    }

    companion object {
        private const val TAG = "AutomixAppTest"
        private const val RATE = 44_100
        private const val SECONDS = 45
    }
}
