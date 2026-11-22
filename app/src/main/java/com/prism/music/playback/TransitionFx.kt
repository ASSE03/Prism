package com.prism.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What a [FxScript] moves, each with the value that leaves the sound as it is ([neutral]). */
enum class FxParam(val neutral: Float, val logScale: Boolean) {
    /** A high-pass's cut-off (Hz; two stages, 24 dB an octave). 10 Hz: nothing taken. */
    HIGH_PASS_HZ(10f, true),
    /** A low-pass's cut-off (Hz; two stages). 22 kHz: nothing taken. */
    LOW_PASS_HZ(22_000f, true),
    /** The bass's level (dB; a low shelf at [TransitionFx.BASS_SHELF_HZ]): 0 as it is, -30 all but gone. */
    BASS_DB(0f, false),
    /** The sound itself, before the echo is added (0 … 1). */
    DRY(1f, false),
    /** How much of the sound goes into the echo (0 … 1). */
    ECHO_SEND(0f, false),
    /** How much of each repeat comes back again (0 … 0.9). */
    ECHO_FEEDBACK(0f, false),
}

/** [param] moves from where it is to [to], starting at [atMs] in the song's own time, over [overMs] (0: at once). */
data class FxRamp(val param: FxParam, val atMs: Double, val overMs: Double, val to: Float)

/**
 * One transition's effects for one song (1.8.8, Automix): its [ramps] in the song's own time, and the echo's length
 * ([echoMs], a beat of the ending song; 0 for no echo). Ramps of one parameter follow each other without overlapping.
 */
class FxScript(ramps: List<FxRamp>, val echoMs: Double = 0.0) {
    private val byParam: Array<List<FxRamp>> = Array(FxParam.entries.size) { i -> ramps.filter { it.param.ordinal == i }.sortedBy { it.atMs } }
    val ramps: List<FxRamp> = ramps

    /** When the last ramp is over. */
    val endMs: Double = ramps.maxOfOrNull { it.atMs + it.overMs } ?: 0.0

    /** Where [param] stands at [ms] in the song. */
    fun value(param: FxParam, ms: Double): Float {
        var v = param.neutral
        for (r in byParam[param.ordinal]) {
            if (ms < r.atMs) return v
            if (r.overMs > 0 && ms < r.atMs + r.overMs) return between(param, v, r.to, ((ms - r.atMs) / r.overMs).toFloat())
            v = r.to
        }
        return v
    }

    /** Whether everything is back where it leaves the sound as it is, once the script is over. */
    val endsNeutral: Boolean = FxParam.entries.all { value(it, endMs + 1) == it.neutral }

    /** Whether [param] is ever moved. */
    fun moves(param: FxParam) = byParam[param.ordinal].any { it.to != param.neutral }

    companion object {
        private fun between(p: FxParam, a: Float, b: Float, f: Float): Float =
            if (p.logScale && a > 0 && b > 0) exp(ln(a) + (ln(b) - ln(a)) * f) else a + (b - a) * f
    }
}

/**
 * Automix's effects on one player's sound (1.8.8; plan-mix.md §3.3.2): a high-pass and a low-pass (two biquads each,
 * [Biquad]), a low shelf for the bass cut, and a delay line a beat long with feedback for the echo, all moved by a
 * [FxScript] the engine hands it. The script is timed in the song's own time, counted in frames from the last seek
 * ([arm]'s anchor), not by the engine's loop: a change lands on the frame asked for, not a buffer early.
 *
 * With no script it copies the sound through byte for byte: lossless playback is untouched, and nothing is reconfigured
 * mid-song. A flush into another song (or one that doesn't say where it is) ends a script that is running; one within the
 * same song (a seek, or a speed change, which drains and flushes the chain) carries it on from where the player says it
 * is. [disarm] and a script's own end go back to the dry sound over
 * [FADE_MS], then to that copy. 16-bit and float.
 *
 * [shiftMs]: frames moved in the chain before this one (the tail's [ShiftProcessor.shiftedMs]), so the song time it
 * counts is the song's.
 */
@UnstableApi
class TransitionFx(private val shiftMs: () -> Long = { 0L }) : BaseAudioProcessor() {
    private class Armed(val script: FxScript?, val anchorMs: Long, val afterNextFlush: Boolean)

    /** Asked from the main thread, taken on the audio thread (taken whole: an arm can't slip between a read and a clear). */
    private val pending = java.util.concurrent.atomic.AtomicReference<Armed?>(null)
    /** Held for the next flush ([arm] with afterNextFlush). */
    private var waiting: Armed? = null

    private var script: FxScript? = null
    private var anchorMs = 0L
    private var frames = 0L
    private var rate = 48_000
    private var channels = 2
    private var float = false

    /** Frames left of the fade back to the dry sound (after [disarm] or the script's end), and its length. */
    private var fadeLeft = 0
    private var fadeLength = 1

    // Filter state per channel: high-pass ×2, low-pass ×2, bass shelf (z1, z2 each).
    private var z = Array(2) { DoubleArray(10) }
    private var hp: Biquad? = null; private var lp: Biquad? = null; private var shelf: Biquad? = null
    private var hpHz = Float.NaN; private var lpHz = Float.NaN; private var bassDb = Float.NaN
    private var hpOn = false; private var lpOn = false; private var shelfOn = false
    private var delay = Array(2) { FloatArray(0) }
    private var delayAt = 0
    private var delayFrames = 0
    private var echoLevel = 0f
    /** The script's last levels, held while the sound fades back to dry. */
    private var dry = 1f; private var send = 0f; private var feedback = 0f

    /** A script is running (or fading out): the sound is being worked on. */
    val running: Boolean get() = script != null || fadeLeft > 0

    /**
     * Runs [script] (any thread). [anchorMs] is the song position of the first frame after the last flush (where the
     * player was last seeked to), or with [afterNextFlush], after the next one (a seek just asked for, not yet carried
     * out): the script then waits for that flush. A player's own flush says where in the song it is, and that counts
     * over [anchorMs], which is then only the caller's guess (B5).
     */
    fun arm(script: FxScript, anchorMs: Long, afterNextFlush: Boolean = false) {
        asked = script
        pending.set(Armed(script, anchorMs, afterNextFlush))
    }

    /** Back to the dry sound (over [FADE_MS]), then untouched. Any thread. */
    fun disarm() {
        asked = null
        pending.set(Armed(null, 0, false))
    }

    /** The script last armed and not disarmed since (any thread): what the engine has asked of this player's sound. */
    @Volatile var asked: FxScript? = null
        private set

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        // Taken up at the flush that follows ([takeFormat]), where the player starts using it: until then the last song's
        // sound may still come through, and a script waiting for the next song's first frame outlasts this (B5).
        return inputAudioFormat
    }

    /** The format the last flush brought in ([inputAudioFormat]); true when it's a new one. */
    private fun takeFormat(): Boolean {
        val f = inputAudioFormat
        if (f.sampleRate <= 0 || f == format) return false
        format = f
        rate = f.sampleRate
        channels = f.channelCount
        float = f.encoding == C.ENCODING_PCM_FLOAT
        fadeLength = (rate * FADE_MS / 1000).coerceAtLeast(1)
        z = Array(channels) { DoubleArray(10) }
        delay = Array(channels) { FloatArray(0) }
        return true
    }

    private var format: AudioProcessor.AudioFormat? = null
    /** The song position of the first frame after the last flush, as the player gave it (null when it didn't say). */
    private var flushMs: Long? = null
    /** The song ([AudioProcessor.StreamMetadata.periodUid]) the last flush that said where it was is in. */
    private var flushPeriod: Any? = null

    private fun take() {
        val p = pending.getAndSet(null) ?: return
        when {
            p.script == null -> { waiting = null; if (script != null) { script = null; fadeLeft = fadeLength } }
            p.afterNextFlush -> waiting = p
            // The newest ask wins: one waiting for a seek doesn't come back at it.
            else -> { waiting = null; start(p) }
        }
    }

    private fun start(p: Armed) {
        script = p.script
        // Where the player said it was at the last flush; the caller's anchor only when it didn't say (a plain flush).
        anchorMs = flushMs ?: p.anchorMs
        fadeLeft = 0
        clearState()
        fitDelay()
    }

    /** The echo's line for the script running, at the rate in use. */
    private fun fitDelay() {
        val s = script
        delayFrames = if (s != null && s.echoMs > 0) (s.echoMs * rate / 1000).roundToInt().coerceIn(1, rate * MAX_ECHO_S) else 0
        if (delayFrames > 0) for (ch in 0 until channels) if (delay[ch].size < delayFrames) delay[ch] = FloatArray(delayFrames)
    }

    private fun clearState() {
        for (s in z) s.fill(0.0)
        for (d in delay) d.fill(0f)
        delayAt = 0; echoLevel = 0f
        dry = 1f; send = 0f; feedback = 0f
        hpHz = Float.NaN; lpHz = Float.NaN; bassDb = Float.NaN
        hpOn = false; lpOn = false; shelfOn = false
        hp = null; lp = null; shelf = null
    }

    private fun stop() {
        script = null; waiting = null; fadeLeft = 0
        clearState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        take()
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val frameBytes = (if (float) 4 else 2) * channels
        val count = bytes / frameBytes
        val s = script
        if (s == null && fadeLeft <= 0) {
            replaceOutputBuffer(bytes).put(inputBuffer).flip()
            frames += count
            return
        }
        val t0 = if (AudioDiagnostics.enabled) System.nanoTime() else 0L
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val out = replaceOutputBuffer(bytes)
        val base = anchorMs + shiftMs()
        var peakEcho = 0f
        for (f in 0 until count) {
            val ms = base + (frames + f) * 1000.0 / rate
            if (s != null) {
                if (f % COEFF_EVERY == 0) filtersAt(s, ms)
                dry = s.value(FxParam.DRY, ms)
                send = if (delayFrames > 0) s.value(FxParam.ECHO_SEND, ms) else 0f
                feedback = if (delayFrames > 0) s.value(FxParam.ECHO_FEEDBACK, ms).coerceIn(0f, MAX_FEEDBACK) else 0f
            }
            // While fading back to the dry sound: the worked sound's share, down to 0.
            val wet = if (s == null) fadeLeft.toFloat() / fadeLength else 1f
            for (ch in 0 until channels) {
                val x = if (float) input.float.toDouble() else input.short / 32768.0
                var y = x
                val st = z[ch]
                if (hpOn) { y = stage(hp!!, y, st, 0); y = stage(hp!!, y, st, 2) }
                if (lpOn) { y = stage(lp!!, y, st, 4); y = stage(lp!!, y, st, 6) }
                if (shelfOn) y = stage(shelf!!, y, st, 8)
                var v = y * dry
                if (delayFrames > 0) {
                    val line = delay[ch]
                    val d = line[delayAt]
                    line[delayAt] = (send * y + feedback * d).toFloat()
                    v += d
                    val a = abs(d); if (a > peakEcho) peakEcho = a
                }
                if (wet < 1f) v = v * wet + x * (1 - wet)
                if (float) out.putFloat(v.toFloat())
                else out.putShort((v * 32768.0).roundToLong().coerceIn(-32768, 32767).toInt().toShort())
            }
            if (delayFrames > 0 && ++delayAt >= delayFrames) delayAt = 0
            if (s == null && fadeLeft > 0 && --fadeLeft == 0) {
                clearState()
                // The rest of this buffer as it came.
                val rest = count - f - 1
                if (rest > 0) {
                    val tail = input.duplicate().order(ByteOrder.nativeOrder())
                    tail.limit(tail.position() + rest * frameBytes)
                    out.put(tail)
                    input.position(input.position() + rest * frameBytes)
                }
                break
            }
        }
        input.position(input.limit())
        out.flip()
        frames += count
        echoLevel = peakEcho
        // The script is over and has left everything as it was (and its echo has died away): back to the dry sound.
        if (s != null && base + frames * 1000.0 / rate > s.endMs && s.endsNeutral && (delayFrames == 0 || echoLevel < ECHO_GONE)) {
            script = null
            fadeLeft = fadeLength
        }
        if (t0 != 0L) AudioDiagnostics.processed("transition effects", t0, count, rate)
    }

    /** The filters as the script has them at [ms]: worked out again only when a value moved. */
    private fun filtersAt(s: FxScript, ms: Double) {
        val h = s.value(FxParam.HIGH_PASS_HZ, ms)
        if (h != hpHz) {
            hpHz = h
            if (h != FxParam.HIGH_PASS_HZ.neutral || hpOn) {
                hp = Biquad.of(EqFilter(EqFilter.Type.HIGH_PASS, h.toDouble().coerceAtLeast(1.0), 0.0, Q), rate)
                hpOn = hp != null
            }
        }
        val l = s.value(FxParam.LOW_PASS_HZ, ms)
        if (l != lpHz) {
            lpHz = l
            if (l != FxParam.LOW_PASS_HZ.neutral || lpOn) {
                // At or past the top of what the rate carries, the low-pass takes nothing: it stays at its last setting.
                Biquad.of(EqFilter(EqFilter.Type.LOW_PASS, l.toDouble(), 0.0, Q), rate)?.let { lp = it; lpOn = true }
            }
        }
        val b = s.value(FxParam.BASS_DB, ms)
        if (b != bassDb) {
            bassDb = b
            if (b != FxParam.BASS_DB.neutral || shelfOn) {
                shelf = Biquad.of(EqFilter(EqFilter.Type.LOW_SHELF, BASS_SHELF_HZ, b.toDouble(), Q), rate)
                shelfOn = shelf != null
            }
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val newFormat = takeFormat()
        // Where in its song the player starts from here (EdgeSilenceProcessor reads the same): only when it says which song.
        val uid = streamMetadata.periodUid
        val at = if (uid != null && streamMetadata.positionOffsetUs != C.TIME_UNSET) streamMetadata.positionOffsetUs / 1000 else null
        // A second flush with nothing played since (a new song's sound being set up): what started at the first goes on,
        // from where the player now says it is (its times are the song's).
        val again = script != null && frames == 0L
        // 1.8.8 review: a flush that says where it is in the same song as the last one. Without AudioTrack speed, a speed
        // change while playing drains and flushes the chain (Media3 1.11.1's applyAudioProcessorPlaybackParameters…), and
        // the engine nudges the speed mid-move to keep the beats in line: the script goes on from there (its times are the
        // song's), not cut off with no fade. Into another song, or with no position, it ends as at any seek.
        val sameSong = script != null && !again && at != null && flushPeriod != null && uid == flushPeriod
        frames = 0
        if (!again || at != null) { flushMs = at; flushPeriod = if (at != null) uid else null }
        // A seek: the frames count from the new place. A script running ends (its times no longer hold); one waiting for
        // this seek starts, and so does one armed just before it (B5: the editor's Listen and the queue's second player
        // arm theirs before the player's first flush).
        val p = pending.getAndSet(null)
        val next = when {
            p == null -> waiting
            p.script == null -> null
            else -> p
        }
        if ((again || sameSong) && p == null && waiting == null) {
            flushMs?.let { anchorMs = it }
            if (newFormat) { clearState(); fitDelay() }
            return
        }
        stop()
        if (next != null) start(next)
    }

    override fun onReset() {
        pending.set(null)
        stop()
        frames = 0
        flushMs = null
        flushPeriod = null
        format = null
    }

    companion object {
        /** The bass cut's shelf. */
        const val BASS_SHELF_HZ = 150.0
        /** Back to the dry sound over this long, so nothing clicks. */
        const val FADE_MS = 10
        /** The longest echo (a beat at 30 BPM). */
        private const val MAX_ECHO_S = 2
        private const val MAX_FEEDBACK = 0.9f
        /** An echo quieter than this (about -80 dBFS) has died away. */
        private const val ECHO_GONE = 1e-4f
        /** Filters are worked out again at most this often (frames): about a millisecond. */
        private const val COEFF_EVERY = 64
        private const val Q = 0.707

        /** One biquad stage, transposed direct form II, its state at [st][i], [st][i + 1]. */
        private fun stage(f: Biquad, x: Double, st: DoubleArray, i: Int): Double {
            val y = f.b0 * x + st[i]
            st[i] = f.b1 * x - f.a1 * y + st[i + 1]
            st[i + 1] = f.b2 * x - f.a2 * y
            return y
        }
    }
}
