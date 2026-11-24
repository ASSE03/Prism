package com.prism.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import com.prism.music.playback.Downmix
import com.prism.music.playback.MixMoves
import com.prism.music.playback.MixSide
import com.prism.music.playback.MixTempo
import com.prism.music.playback.Move
import com.prism.music.playback.MovePlace
import com.prism.music.playback.NormalizerProcessor
import com.prism.music.playback.TransitionFx
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

/**
 * Automix's listening round (plan-mix.md §3.4, B4): the offline renderer. Each of the owner's ten pairs, every move,
 * through [TransitionFx] and the move's volume law ([MixMoves.gains], [MixMoves.scripts]: what the engine will use),
 * from about 12 s before the move's moment to 10 s after, as a WAV. Also the test harness for B2 on real songs.
 *
 * Skipped unless the songs are there: `MixListeningFetch` (in-app, on a phone) fetches them with Prism's own stream
 * resolver; they're decoded on the PC to 48 kHz stereo 16-bit (`NNa-<id>.s16`, `NNb-<id>.s16`, beside its `songs.tsv`)
 * in the folder `PRISM_LISTENING_PCM` names (default build/mix-listening/pcm). The clips go to `PRISM_LISTENING_OUT`
 * (default build/mix-listening/out) with `clips.tsv` and `scans.tsv`, and each song's shape and tempo (for saved-scan tests).
 *
 * Each song is scanned as Prism scans it (mono at a quarter of 48 kHz, [MixTempo.analyse]) and brought to -14 LUFS as
 * Normalize's Standard does. The next song plays at the move's tempo ratio throughout the clip (Prism eases it back to
 * normal over 4 s after the move; left out here).
 */
class MixListeningRender {
    private val rate = 48_000
    private val pcmDir = File(System.getenv("PRISM_LISTENING_PCM") ?: "build/mix-listening/pcm")
    private val outDir = File(System.getenv("PRISM_LISTENING_OUT") ?: "build/mix-listening/out")

    private class Song(val tag: String, val id: String, val title: String, val pcm: ShortArray, val side: MixSide, val gain: Float, val scan: MixTempo.Scan)

    @Test
    fun renderTheTenPairs() {
        val songs = File(pcmDir, "songs.tsv")
        assumeTrue("no songs in ${pcmDir.absolutePath}", songs.isFile)
        outDir.mkdirs()
        val rows = songs.readLines().drop(1).map { it.split('\t') }.filter { it.size >= 10 && it[4].isNotBlank() }
        val clips = StringBuilder("pair\tmove\tstrength\tfile\tpicked\tstartMs\tpivotMs\tlengthMs\tinMs\tratio\toutro\tclipFromMs\tlimitedMs\n")
        val scans = StringBuilder("tag\tid\ttitle\tdurationMs\tbpm\tconfidence\tsteady\tfirstSoundMs\tlastSoundMs\tfirstBeatMs\tfadeOutMs\tquietIntroMs\tkey\tkeySure\tlufs\tpeakDb\toutroStartMs\tintroHasBass\n")
        for (pair in rows.map { it[0].toInt() }.distinct().sorted()) {
            val a = rows.firstOrNull { it[0].toInt() == pair && it[1] == "a" } ?: continue
            val b = rows.firstOrNull { it[0].toInt() == pair && it[1] == "b" } ?: continue
            val ending = load(a, scans) ?: continue
            val next = load(b, scans) ?: continue
            val picked = MixMoves.pick(ending.side, next.side)
            println("pair $pair: ${ending.title} -> ${next.title}: Prism would pick $picked")
            val takes = listOf(
                Move.BASS_SWAP to MixMoves.DEFAULT_STRENGTH, Move.BASS_SWAP to MixMoves.STRONG,
                Move.FILTER_OUT to MixMoves.DEFAULT_STRENGTH, Move.FILTER_OUT to MixMoves.STRONG,
                Move.ECHO_OUT to MixMoves.DEFAULT_STRENGTH, Move.BLEND to MixMoves.DEFAULT_STRENGTH, Move.CUT to MixMoves.DEFAULT_STRENGTH,
            )
            for ((move, strength) in takes) {
                val place = MixMoves.place(move, ending.side, next.side) ?: MixMoves.place(move, ending.side, next.side, outroRule = false)
                val name = "%02d-%s%s".format(pair, move.name.lowercase().replace('_', '-'), if (strength == MixMoves.STRONG && move != Move.ECHO_OUT) "-strong" else "")
                if (place == null) {
                    println("  $name: can't be placed")
                    clips.append(listOf(pair, move, strength, "", move == picked, "", "", "", "", "", "", "", "").joinToString("\t")).append('\n')
                    continue
                }
                val (audio, clipFrom, limited) = render(ending, next, place, strength)
                writeWav(File(outDir, "$name.wav"), audio)
                println("  $name: start ${place.startMs} ms, pivot ${place.pivotMs}, ${place.lengthMs} ms, next from ${place.inMs} ms at ×%.3f%s, limited %d ms"
                    .format(place.ratio, if (place.outro) ", out of the outro" else "", limited))
                clips.append(listOf(pair, move, strength, "$name.wav", move == picked, place.startMs, place.pivotMs, place.lengthMs, place.inMs,
                    "%.4f".format(place.ratio), place.outro, clipFrom, limited).joinToString("\t")).append('\n')
            }
        }
        File(outDir, "clips.tsv").writeText(clips.toString())
        File(outDir, "scans.tsv").writeText(scans.toString())
    }

    /** A song from its row in songs.tsv: read, scanned as Prism scans, its shape and tempo kept beside the clips. */
    private fun load(row: List<String>, scans: StringBuilder): Song? {
        val tag = row[0].padStart(2, '0') + row[1]
        val id = row[4]
        val file = pcmDir.listFiles { f -> f.name.startsWith("$tag-") && f.name.endsWith(".s16") }?.firstOrNull() ?: return null
        val bytes = file.readBytes()
        val pcm = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        // As MixTempo.decodeAll: mono, a quarter of 48 kHz, through Downmix.
        val factor = rate / 11_025
        val mono = FloatArray(pcm.size / 2 / factor + 1)
        var n = 0
        val down = Downmix()
        val chunk = ByteBuffer.allocate(8192 * 4).order(ByteOrder.nativeOrder())
        var at = 0
        while (at < pcm.size) {
            val frames = minOf(8192, (pcm.size - at) / 2)
            if (frames <= 0) break
            chunk.clear()
            for (i in 0 until frames * 2) chunk.putShort(pcm[at + i])
            chunk.flip()
            down.add(chunk, frames, 2, false, factor) { v -> if (n < mono.size) mono[n++] = v }
            at += frames * 2
        }
        val scan = MixTempo.analyse({ MixTempo.Decoded(mono, n, rate / factor, down.peak) }, step = {})
        val durationMs = pcm.size / 2 * 1000L / rate
        val side = MixSide(scan.tempo, scan.key, scan.shape, durationMs)
        val gainDb = scan.lufs?.let { NormalizerProcessor.gainFor(-14f, it, scan.peakDb) } ?: 0f
        val t = scan.tempo
        val shape = scan.shape
        File(outDir, "scans").mkdirs()
        shape?.let { File(outDir, "scans/$id.shape1").writeBytes(it.encode()) }
        t?.let { File(outDir, "scans/$id.tempo").writeText(it.encode()) }
        scans.append(listOf(tag, id, row[5], durationMs, t?.bpm?.let { "%.2f".format(it) }, t?.confidence?.let { "%.1f".format(it) }, t?.steady,
            t?.firstSoundMs, t?.lastSoundMs, t?.firstBeatMs, t?.fadeOutMs, t?.quietIntroMs, scan.key?.camelot, scan.key?.sure,
            scan.lufs?.let { "%.1f".format(it) }, scan.peakDb?.let { "%.1f".format(it) },
            shape?.outroStartMs(t?.firstSoundMs ?: -1, t?.lastSoundMs ?: -1), shape?.introHasBass(t?.firstSoundMs ?: -1)).joinToString("\t")).append('\n')
        println("$tag ${row[5]}: ${t?.let { "%.1f BPM (conf %.1f), music %d-%d, fade %d, intro %d".format(it.bpm, it.confidence, it.firstSoundMs, it.lastSoundMs, it.fadeOutMs, it.quietIntroMs) }}, " +
            "key ${scan.key?.camelot}${if (scan.key?.sure == true) "" else "?"}, ${scan.lufs} LUFS -> gain %.1f dB, outro ${shape?.outroStartMs(t?.firstSoundMs ?: -1, t?.lastSoundMs ?: -1)}, intro bass ${shape?.introHasBass(t?.firstSoundMs ?: -1)}".format(gainDb))
        return Song(tag, id, row[5], pcm, side, 10f.pow(gainDb / 20), scan)
    }

    /**
     * [place] heard: the ending song through its [TransitionFx] until the move ends, the next song from the move's start
     * through its own and then its tempo ratio (Sonic, as the player's speed), each at its normalized level, the two by
     * the move's volumes, a limiter at -1 dBFS over both (the normalizer's ceiling). Returns the stereo clip, where it
     * starts (ending song's ms) and how long the limiter worked more than 1 dB.
     */
    private fun render(ending: Song, next: Song, place: MovePlace, strength: Float): Triple<ShortArray, Long, Long> {
        val (outScript, inScript) = MixMoves.scripts(place, strength)
        val from = (place.pivotMs - 12_000).coerceAtLeast(0)
        val to = place.pivotMs + 10_000
        val frames = ((to - from) * rate / 1000).toInt()
        val moveEnd = place.startMs + place.lengthMs
        // The ending song, worked, from the clip's start to the move's end (its player is let go there).
        val outFx = TransitionFx().also { it.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT)); it.flush(); it.arm(outScript, from) }
        val endFrames = ((minOf(moveEnd, to) - from) * rate / 1000).toInt().coerceAtLeast(0)
        val a = through(outFx, slice(ending.pcm, msToFrame(from), endFrames))
        // The next song from its start in the move, worked, then at its tempo ratio.
        val inFx = TransitionFx().also { it.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT)); it.flush(); it.arm(inScript, place.inMs) }
        val nextFrames = ((to - place.startMs) * rate / 1000).toInt()
        val sourceFrames = (nextFrames * place.ratio + rate).toInt()
        var b = through(inFx, slice(next.pcm, msToFrame(place.inMs), sourceFrames))
        if (abs(place.ratio - 1f) > 1e-4f) b = stretch(b, place.ratio)
        val out = ShortArray(frames * 2)
        val startFrame = ((place.startMs - from) * rate / 1000).toInt()
        var env = 1.0
        var limitedFrames = 0L
        val release = 1 - 1.0 / (0.1 * rate)
        for (f in 0 until frames) {
            val ms = from + f * 1000.0 / rate
            val (gIn, gOut) = when {
                ms < place.startMs -> 0f to 1f
                ms < moveEnd -> MixMoves.gains(place.move, place.progress(ms), place.pivot)
                else -> 1f to 0f
            }
            val bf = f - startFrame
            var peak = 0.0
            val mix = DoubleArray(2)
            for (ch in 0 until 2) {
                val x = if (f < endFrames && f * 2 + ch < a.size) a[f * 2 + ch] / 32768.0 * ending.gain * gOut else 0.0
                val y = if (bf >= 0 && bf * 2 + ch < b.size) b[bf * 2 + ch] / 32768.0 * next.gain * gIn else 0.0
                mix[ch] = x + y
                peak = maxOf(peak, abs(mix[ch]))
            }
            // Fast attack, slow release, never above the ceiling.
            env = if (peak * env > CEILING) CEILING / peak else minOf(1.0, env / release)
            if (env < LIMITED) limitedFrames++
            for (ch in 0 until 2) out[f * 2 + ch] = (mix[ch] * env * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Triple(out, from, limitedFrames * 1000 / rate)
    }

    private fun msToFrame(ms: Long) = (ms * rate / 1000).toInt()

    /** [count] stereo frames of [pcm] from [frame] (silence past its end). */
    private fun slice(pcm: ShortArray, frame: Int, count: Int): ShortArray {
        val out = ShortArray(count * 2)
        val from = frame * 2
        val n = (pcm.size - from).coerceIn(0, out.size)
        if (from >= 0 && n > 0) System.arraycopy(pcm, from, out, 0, n)
        return out
    }

    /** [pcm] through [fx] in pieces of a player's size (4096 frames), as 16-bit. */
    private fun through(fx: TransitionFx, pcm: ShortArray): ShortArray {
        val out = ShortArray(pcm.size)
        var at = 0; var w = 0
        val buf = ByteBuffer.allocateDirect(4096 * 4).order(ByteOrder.nativeOrder())
        while (at < pcm.size) {
            val n = minOf(4096 * 2, pcm.size - at)
            buf.clear()
            for (i in 0 until n) buf.putShort(pcm[at + i])
            buf.flip()
            fx.queueInput(buf)
            val o = fx.output.order(ByteOrder.nativeOrder())
            while (o.remaining() >= 2 && w < out.size) out[w++] = o.short
            at += n
        }
        return out
    }

    /** [pcm] played at [speed] with its pitch kept (Media3's Sonic, as the player's speed is). */
    private fun stretch(pcm: ShortArray, speed: Float): ShortArray {
        val sonic = SonicAudioProcessor()
        sonic.setSpeed(speed)
        sonic.setPitch(1f)
        sonic.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        sonic.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val out = ArrayList<Short>((pcm.size / speed).toInt() + 8192)
        val buf = ByteBuffer.allocateDirect(4096 * 4).order(ByteOrder.nativeOrder())
        var at = 0
        fun drain() { val o = sonic.output.order(ByteOrder.nativeOrder()); while (o.remaining() >= 2) out += o.short }
        while (at < pcm.size) {
            val n = minOf(4096 * 2, pcm.size - at)
            buf.clear()
            for (i in 0 until n) buf.putShort(pcm[at + i])
            buf.flip()
            sonic.queueInput(buf)
            drain()
            at += n
        }
        sonic.queueEndOfStream()
        drain()
        return out.toShortArray()
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val data = pcm.size * 2
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(36 + data); h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(2); h.putInt(rate); h.putInt(rate * 4); h.putShort(4); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(data)
            f.write(h.array())
            val body = ByteBuffer.allocate(data).order(ByteOrder.LITTLE_ENDIAN)
            body.asShortBuffer().put(pcm)
            f.write(body.array())
        }
    }

    private companion object {
        /** The normalizer's limiter ceiling, -1 dBFS. */
        const val CEILING = 0.89
        /** The limiter counts as working past 1 dB of reduction. */
        val LIMITED = 10.0.pow(-1.0 / 20)
    }
}
