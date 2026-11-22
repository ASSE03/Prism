package com.prism.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.prism.music.playback.FxParam
import com.prism.music.playback.FxRamp
import com.prism.music.playback.FxScript
import com.prism.music.playback.TransitionFx
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Automix's effects processor (plan-mix.md §3.3.2, B2): untouched when idle, filters and the bass cut where they're set,
 * an echo that comes back on the beat and dies away, and every change landing on the frame its song time asks for.
 */
class TransitionFxTest {
    private val rate = 48_000

    private fun fx(float: Boolean, channels: Int = 2) = TransitionFx().apply {
        configure(AudioProcessor.AudioFormat(rate, channels, if (float) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT))
        flush()
    }

    /** Interleaved samples into a direct buffer in the processor's encoding. */
    private fun buffer(samples: FloatArray, float: Boolean): ByteBuffer {
        val b = ByteBuffer.allocateDirect(samples.size * (if (float) 4 else 2)).order(ByteOrder.nativeOrder())
        for (v in samples) if (float) b.putFloat(v) else b.putShort((v * 32767).toInt().coerceIn(-32768, 32767).toShort())
        b.flip()
        return b
    }

    /** Runs [samples] through [p] in pieces of [piece] frames (odd sizes, as a player hands them over), and reads it all back. */
    private fun run(p: TransitionFx, samples: FloatArray, float: Boolean, channels: Int = 2, piece: Int = 997): FloatArray {
        val out = ArrayList<Float>(samples.size)
        var i = 0
        while (i < samples.size) {
            val n = minOf(piece * channels, samples.size - i)
            p.queueInput(buffer(samples.copyOfRange(i, i + n), float))
            val o = p.output.order(ByteOrder.nativeOrder())
            while (o.hasRemaining()) out += if (float) o.float else o.short / 32768f
            i += n
        }
        return out.toFloatArray()
    }

    private fun bytesOf(p: TransitionFx, input: ByteBuffer): ByteArray {
        p.queueInput(input)
        val o = p.output
        return ByteArray(o.remaining()).also { o.get(it) }
    }

    private fun noise(frames: Int, channels: Int = 2, seed: Int = 7): FloatArray {
        val r = Random(seed)
        return FloatArray(frames * channels) { (r.nextDouble() * 1.6 - 0.8).toFloat() }
    }

    private fun sine(hz: Double, frames: Int, amp: Double = 0.3, channels: Int = 2) =
        FloatArray(frames * channels) { (amp * sin(2 * PI * hz * (it / channels) / rate)).toFloat() }

    /** Level in dB of channel 0 over frames [from, to). */
    private fun db(x: FloatArray, from: Int, to: Int, channels: Int = 2): Double {
        var s = 0.0
        for (f in from until to) s += x[f * channels].toDouble() * x[f * channels]
        return 10 * log10(s / (to - from))
    }

    @Test
    fun idleIsTheSameBytes() {
        for (float in listOf(true, false)) {
            val p = fx(float)
            val samples = noise(20_000)
            val input = buffer(samples, float)
            val copy = ByteArray(input.remaining()).also { input.duplicate().get(it) }
            assertArrayEquals("float=$float", copy, bytesOf(p, input))
            assertTrue(p.isActive)
        }
    }

    @Test
    fun afterItsScriptIsDoneItIsTheSameBytesAgain() {
        for (float in listOf(true, false)) {
            val p = fx(float)
            // A high-pass up and back down, all over in the first second.
            p.arm(FxScript(listOf(
                FxRamp(FxParam.HIGH_PASS_HZ, 100.0, 300.0, 800f),
                FxRamp(FxParam.HIGH_PASS_HZ, 500.0, 300.0, FxParam.HIGH_PASS_HZ.neutral),
            )), anchorMs = 0)
            run(p, noise(rate * 2, seed = 1), float)
            assertTrue("float=$float: still running", !p.running)
            val input = buffer(noise(5_000, seed = 2), float)
            val copy = ByteArray(input.remaining()).also { input.duplicate().get(it) }
            assertArrayEquals("float=$float", copy, bytesOf(p, input))
        }
    }

    @Test
    fun theHighPassSweepTakesTheLowsFirst() {
        val frames = rate * 4
        // From nothing at 1 s to 2 kHz at 3 s.
        val tone = { hz: Double -> run(fx(true).also { it.arm(FxScript(listOf(FxRamp(FxParam.HIGH_PASS_HZ, 1000.0, 2000.0, 2000f))), 0) }, sine(hz, frames), true) }
        val low = tone(100.0); val mid = tone(1000.0); val high = tone(8000.0)
        val inDb = db(sine(100.0, frames), 0, rate)
        val before = 0 until rate / 2
        val after = (rate * 3 + rate / 10) until (rate * 4)
        // Before the sweep, all three as they came in.
        for (x in listOf(low, mid, high)) assertEquals(inDb, db(x, before.first, before.last), 0.2)
        // After it: the 100 Hz tone all but gone, 1 kHz down (below the cut-off), 8 kHz still there.
        assertTrue(db(low, after.first, after.last) < inDb - 40)
        assertTrue(db(mid, after.first, after.last) < inDb - 9)
        assertEquals(inDb, db(high, after.first, after.last), 0.5)
        // Half-way (2 s, the cut-off near 200 Hz on its log sweep): the low tone is going, the mid one not yet.
        val half = (rate * 2 - rate / 20) until (rate * 2 + rate / 20)
        assertTrue(db(low, half.first, half.last) < inDb - 6)
        assertEquals(inDb, db(mid, half.first, half.last), 1.0)
    }

    @Test
    fun theLowPassMuffles() {
        val script = FxScript(listOf(FxRamp(FxParam.LOW_PASS_HZ, 0.0, 0.0, 400f)))
        val frames = rate
        val low = run(fx(true).also { it.arm(script, 0) }, sine(80.0, frames), true)
        val high = run(fx(true).also { it.arm(script, 0) }, sine(4000.0, frames), true)
        val inDb = db(sine(80.0, frames), 0, frames)
        assertEquals(inDb, db(low, rate / 2, frames), 0.5)
        assertTrue(db(high, rate / 2, frames) < inDb - 30)
    }

    @Test
    fun theBassCutTakesTheBassOnly() {
        for (float in listOf(true, false)) {
            val script = FxScript(listOf(FxRamp(FxParam.BASS_DB, 0.0, 0.0, -30f)))
            val frames = rate
            val bass = run(fx(float).also { it.arm(script, 0) }, sine(45.0, frames), float)
            val voice = run(fx(float).also { it.arm(script, 0) }, sine(2000.0, frames), float)
            val inDb = db(sine(45.0, frames), 0, frames)
            assertTrue("float=$float: bass at ${db(bass, rate / 2, frames) - inDb} dB", db(bass, rate / 2, frames) < inDb - 20)
            assertEquals("float=$float", inDb, db(voice, rate / 2, frames), 0.5)
        }
    }

    @Test
    fun anEchoComesBackOnTheBeatAndDiesAway() {
        val beatMs = 500.0
        val beat = (beatMs * rate / 1000).toInt()
        val p = fx(float = true, channels = 1)
        // Caught from 0.9 s to 1 s (a click at 0.95 s), the dry sound gone at 1 s: only the echo goes on.
        p.arm(FxScript(listOf(
            FxRamp(FxParam.ECHO_FEEDBACK, 0.0, 0.0, 0.5f),
            FxRamp(FxParam.ECHO_SEND, 900.0, 0.0, 1f),
            FxRamp(FxParam.ECHO_SEND, 1000.0, 0.0, 0f),
            FxRamp(FxParam.DRY, 1000.0, 0.0, 0f),
        ), echoMs = beatMs), anchorMs = 0)
        val input = FloatArray(rate * 5)
        val click = (0.95 * rate).toInt()
        input[click] = 0.8f
        val out = run(p, input, float = true, channels = 1)
        assertEquals(0.8f, out[click], 1e-6f)
        // Back at every beat after it, each half as loud, and nothing between.
        for (k in 1..6) {
            val at = click + k * beat
            assertEquals("repeat $k", 0.8 * Math.pow(0.5, (k - 1).toDouble()), out[at].toDouble(), 1e-4)
            assertEquals(0.0, out[at - beat / 2].toDouble(), 1e-6)
        }
    }

    @Test
    fun aChangeLandsOnTheFrameItsSongTimeSays() {
        for (float in listOf(true, false)) {
            val p = fx(float)
            // The frames since the last seek start at 10 s in the song: the dry sound goes at 10.25 s, frame 12 000.
            p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 10_250.0, 0.0, 0f))), anchorMs = 10_000)
            val out = run(p, FloatArray(rate * 2) { 0.5f }, float, piece = 333)
            val at = rate / 4
            assertEquals("float=$float", 0.5f, out[(at - 1) * 2], 1e-3f)
            assertEquals("float=$float", 0f, out[at * 2], 1e-6f)
        }
    }

    @Test
    fun aScriptForAfterTheSeekWaitsForIt() {
        val p = fx(float = true)
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 30_100.0, 0.0, 0f))), anchorMs = 30_000, afterNextFlush = true)
        // Before the seek's flush: untouched, whatever the time.
        val before = run(p, FloatArray(rate) { 0.5f }, true)
        assertTrue(before.all { it == 0.5f })
        p.flush()
        val out = run(p, FloatArray(rate) { 0.5f }, true)
        val at = rate / 10
        assertEquals(0.5f, out[(at - 1) * 2], 1e-6f)
        assertEquals(0f, out[at * 2], 1e-6f)
    }

    @Test
    fun aScriptStartedAtOnceOvertakesOneWaitingForTheSeek() {
        // Review of B5 (Gemini, checked): one waiting for the next seek, then a newer one started at once. The seek ends
        // the newer one as any seek does; the older one must not come back with it.
        val p = fx(float = true)
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 0.0, 0.0, 0f))), anchorMs = 0, afterNextFlush = true)
        run(p, FloatArray(200) { 0.5f }, true)
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 0.0, 0.0, 0.5f))), anchorMs = 0)
        run(p, FloatArray(200) { 0.5f }, true)
        p.flush()
        assertTrue(run(p, FloatArray(2000) { 0.5f }, true).all { it == 0.5f })
        assertFalse(p.running)
    }

    @Test
    fun aSeekEndsTheScriptRunning() {
        val p = fx(float = true)
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 0.0, 0.0, 0f))), anchorMs = 0)
        assertTrue(run(p, FloatArray(2000) { 0.5f }, true).all { it == 0f })
        p.flush()
        assertTrue(run(p, FloatArray(2000) { 0.5f }, true).all { it == 0.5f })
    }

    @Test
    fun theSongTimeCountsTheShiftedFrames() {
        // In the tail's chain after ShiftProcessor: 200 ms skipped there means the song is 200 ms further on.
        val p = TransitionFx(shiftMs = { 200L }).apply {
            configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT)); flush()
        }
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 1_000.0, 0.0, 0f))), anchorMs = 0)
        val out = run(p, FloatArray(rate * 2) { 0.5f }, true)
        val at = (0.8 * rate).toInt()
        assertEquals(0.5f, out[(at - 1) * 2], 1e-6f)
        assertEquals(0f, out[at * 2], 1e-6f)
    }

    @Test
    fun disarmedItFadesBackToTheDrySoundWithoutAClick() {
        val p = fx(float = true)
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 0.0, 0.0, 0f))), anchorMs = 0)
        run(p, FloatArray(rate / 10) { 0.5f }, true)
        p.disarm()
        val out = run(p, FloatArray(rate / 10) { 0.5f }, true)
        // Up over about 10 ms, never jumping more than a small step at a time.
        var biggest = 0f
        for (i in 2 until out.size step 2) biggest = maxOf(biggest, abs(out[i] - out[i - 2]))
        assertTrue("step $biggest", biggest < 0.01f)
        assertEquals(0.5f, out[out.size - 2], 1e-6f)
        val input = buffer(noise(3000, seed = 5), true)
        val copy = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        assertArrayEquals(copy, bytesOf(p, input))
    }

    // --- B5: armed by the engine around a real player's flushes ---

    /** A flush as the player makes one: with the song it's in and where in it ([AudioProcessor.StreamMetadata]). */
    private fun playerFlush(p: TransitionFx, atMs: Long) =
        p.flush(AudioProcessor.StreamMetadata.Builder().setPositionOffsetUs(atMs * 1000).setPeriodUid("song").build())

    @Test
    fun aScriptArmedJustBeforeThePlayersFirstFlushStartsThere() {
        // As the editor's Listen and the queue's second player arm it: set up, armed, and only then flushed by the player.
        val p = TransitionFx().apply { configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT)) }
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 30_100.0, 0.0, 0f))), anchorMs = 30_000)
        playerFlush(p, 30_000)
        val out = run(p, FloatArray(rate) { 0.5f }, true)
        val at = rate / 10
        assertEquals(0.5f, out[(at - 1) * 2], 1e-6f)
        assertEquals(0f, out[at * 2], 1e-6f)
    }

    @Test
    fun aSecondFlushAtTheSamePlaceKeepsTheScript() {
        // A new song's sound being set up flushes twice before anything plays: the script waiting for the seek holds.
        val p = fx(float = true)
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 20_100.0, 0.0, 0f))), anchorMs = 20_000, afterNextFlush = true)
        playerFlush(p, 20_000)
        playerFlush(p, 20_000)
        val out = run(p, FloatArray(rate) { 0.5f }, true)
        assertEquals(0f, out[(rate / 10) * 2], 1e-6f)
        // A real seek after sound has played still ends it.
        playerFlush(p, 5_000)
        assertTrue(run(p, FloatArray(2000) { 0.5f }, true).all { it == 0.5f })
    }

    @Test
    fun aNewSongsFormatDoesntDropTheScriptWaitingForIt() {
        // The next song is 44.1 kHz where the last was 48: the format comes between the arm and the seek's flush.
        val p = fx(float = true)
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 1_100.0, 0.0, 0f))), anchorMs = 1_000, afterNextFlush = true)
        // The ending song's last buffer still comes through (and the script is taken up, waiting for the seek).
        assertTrue(run(p, FloatArray(2000) { 0.5f }, true).all { it == 0.5f })
        p.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        playerFlush(p, 1_000)
        val out = run(p, FloatArray(44_100) { 0.5f }, true)
        val at = 4_410
        assertEquals(0.5f, out[(at - 1) * 2], 1e-6f)
        assertEquals(0f, out[at * 2], 1e-6f)
    }

    @Test
    fun theSongTimeIsThePlayersWhenItSaysWhereItIs() {
        // The engine's guess of where the player last seeked to is only a fallback: the player's own flush says.
        val p = TransitionFx().apply { configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT)) }
        playerFlush(p, 12_000)
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 12_500.0, 0.0, 0f))), anchorMs = 11_000)
        val out = run(p, FloatArray(rate * 2) { 0.5f }, true)
        val at = rate / 2
        assertEquals(0.5f, out[(at - 1) * 2], 1e-6f)
        assertEquals(0f, out[at * 2], 1e-6f)
    }

    /** A flush as [playerFlush], in song [uid]. */
    private fun flushIn(p: TransitionFx, uid: String, atMs: Long) =
        p.flush(AudioProcessor.StreamMetadata.Builder().setPositionOffsetUs(atMs * 1000).setPeriodUid(uid).build())

    @Test
    fun aSpeedChangeMidMoveKeepsTheScriptGoing() {
        // 1.8.8 review: without AudioTrack speed, a speed change while playing drains and flushes the chain, saying where in
        // the same song it is. The beat matching nudges the speed mid-move: the move goes on from there, its times the song's.
        // Fails on the old code: every flush with sound played since ended the script, with no fade.
        val p = TransitionFx().apply { configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT)) }
        flushIn(p, "next", 10_000)
        // The dry sound down from 1 at 10.5 s to 0 at 11.5 s.
        p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 10_500.0, 1_000.0, 0f))), anchorMs = 10_000)
        run(p, FloatArray(rate / 2 * 2) { 0.5f }, true)
        // The nudge's flush, half a second on.
        flushIn(p, "next", 10_500)
        assertTrue(p.running)
        val out = run(p, FloatArray(rate * 2 * 2) { 0.5f }, true)
        // Half-way down at 11 s (half a second after the flush), gone after 11.5 s.
        assertEquals(0.25f, out[(rate / 2) * 2], 0.01f)
        assertEquals(0f, out[(rate + rate / 10) * 2], 1e-6f)
    }

    @Test
    fun aFlushIntoAnotherSongOrWithNoPlaceStillEndsIt() {
        for (other in listOf(true, false)) {
            val p = TransitionFx().apply { configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT)) }
            flushIn(p, "next", 0)
            p.arm(FxScript(listOf(FxRamp(FxParam.DRY, 0.0, 0.0, 0f))), anchorMs = 0)
            assertTrue(run(p, FloatArray(2000) { 0.5f }, true).all { it == 0f })
            if (other) flushIn(p, "third", 0) else p.flush()
            assertTrue("other song=$other", run(p, FloatArray(2000) { 0.5f }, true).all { it == 0.5f })
            assertFalse(p.running)
        }
    }

    @Test
    fun theMainChainIsUntouchedWithNoScript() {
        // The main player's chain as PlaybackService has it: the silence trimmer, then this, before the EQ. With no
        // script (never armed, or disarmed after a transition) what leaves it is what came from the decoder.
        for (float in listOf(true, false)) {
            val format = AudioProcessor.AudioFormat(rate, 2, if (float) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT)
            val edges = com.prism.music.playback.EdgeSilenceProcessor()
            val p = TransitionFx(shiftMs = { edges.skippedMs })
            edges.configure(format); edges.flush()
            p.configure(format); p.flush()
            fun through(samples: FloatArray): Pair<ByteArray, ByteArray> {
                val input = buffer(samples, float)
                val copy = ByteArray(input.remaining()).also { input.duplicate().get(it) }
                edges.queueInput(input)
                return copy to bytesOf(p, edges.output)
            }
            through(noise(20_000, seed = 3)).let { (a, b) -> assertArrayEquals("float=$float", a, b) }
            // Armed for a seek, then disarmed before it (a skip mid-move): the seek leaves it untouched still.
            p.arm(FxScript(listOf(FxRamp(FxParam.BASS_DB, 0.0, 0.0, -30f))), anchorMs = 0, afterNextFlush = true)
            p.disarm()
            edges.flush(); p.flush()
            through(noise(20_000, seed = 4)).let { (a, b) -> assertArrayEquals("float=$float, after a disarm", a, b) }
            assertTrue(p.asked == null && !p.running)
        }
    }

    @Test
    fun scriptValuesFollowTheirRamps() {
        val s = FxScript(listOf(
            FxRamp(FxParam.DRY, 1000.0, 1000.0, 0f),
            FxRamp(FxParam.HIGH_PASS_HZ, 0.0, 1000.0, 1000f),
        ))
        assertEquals(1f, s.value(FxParam.DRY, 500.0), 0f)
        assertEquals(0.5f, s.value(FxParam.DRY, 1500.0), 1e-6f)
        assertEquals(0f, s.value(FxParam.DRY, 5000.0), 0f)
        // Frequencies sweep on a log scale: half-way from 10 Hz to 1 kHz is 100 Hz.
        assertEquals(100.0, s.value(FxParam.HIGH_PASS_HZ, 500.0).toDouble(), 0.5)
        assertEquals(2000.0, s.endMs, 0.0)
    }
}
