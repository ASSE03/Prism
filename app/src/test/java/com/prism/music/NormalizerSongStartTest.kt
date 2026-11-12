package com.prism.music

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import com.prism.music.playback.NormalizerProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/**
 * D62 (Reddit, 1.8.5): "static sounds at the start of songs, gone when restarting the song". The player's audio runs
 * ahead of the main thread: the next song's first ~0.65 s went through the normalizer at the last song's gain before
 * the service set the new one (Galaxy A16: a quiet song's +6 dB on a loud one, the limiter cutting up to 7 dB). A
 * restart had the right gain already, so it sounded clean. Now the processor asks for a song's gain when its sound
 * starts (the flush that comes with it).
 */
class NormalizerSongStartTest {
    private val rate = 48_000

    private class Songs(private val ids: List<String>) : Timeline() {
        override fun getWindowCount() = ids.size
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window = window.set(
            ids[windowIndex], MediaItem.Builder().setMediaId(ids[windowIndex]).build(), null, 0, 0, 0, true, false,
            null, 0, 200_000_000L, windowIndex, windowIndex, 0,
        )
        override fun getPeriodCount() = ids.size
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
            period.set(ids[periodIndex], ids[periodIndex], periodIndex, 200_000_000L, 0)
        override fun getIndexOfPeriod(uid: Any) = ids.indexOf(uid).takeIf { it >= 0 } ?: C.INDEX_UNSET
        override fun getUidOfPeriod(periodIndex: Int): Any = ids[periodIndex]
    }

    private val songs = Songs(listOf("quiet", "loud", "unknown"))
    /** What the service knows: the quiet song is lifted 6 dB, the loud one brought down 8; the third isn't known. */
    private val gains = mapOf("quiet" to 6f, "loud" to -8f)

    private fun processor() = NormalizerProcessor().apply {
        songGainDb = { item, _ -> gains[item.mediaId] }
        configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
    }

    private fun startOf(p: NormalizerProcessor, id: String, positionUs: Long = 0) =
        p.flush(AudioProcessor.StreamMetadata.Builder().setTimeline(songs).setPeriodUid(id).setPositionOffsetUs(positionUs).build())

    /** [seconds] of a tone peaking at [peak] through [p], 20 ms at a time: the loudest sample out per 50 ms, in dBFS. */
    private fun play(p: NormalizerProcessor, seconds: Double, peak: Double = 0.98): List<Double> {
        val frames = (seconds * rate).toInt()
        val out = ArrayList<Float>()
        for (from in 0 until frames step 960) {
            val n = minOf(960, frames - from)
            val b = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
            for (i in from until from + n) { val s = (peak * 32767 * sin(2 * PI * 220 * i / rate)).toInt().toShort(); b.putShort(s); b.putShort(s) }
            b.flip()
            p.queueInput(b)
            val o = p.output.order(ByteOrder.nativeOrder())
            while (o.remaining() >= 4) { out += o.short / 32768f; o.short }
        }
        return out.chunked(rate / 20).map { c -> 20 * log10(c.maxOf { abs(it) }.toDouble().coerceAtLeast(1e-6)) }
    }

    @Test fun aLoudSongAfterAQuietOneStartsAtItsOwnGain() {
        val p = processor()
        startOf(p, "quiet")
        p.setGainDb(6f) // the service, for the quiet song
        play(p, 1.0)
        // The next song's sound starts; the service only hears of it later.
        startOf(p, "loud")
        val first = play(p, 0.5)
        // 0.98 at -8 dB is -8.2 dBFS. At the quiet song's +6 dB it hit the limiter's -1 dBFS ceiling.
        assertTrue("the loud song's first 50 ms came out at ${first.first()} dBFS", first.first() < -7.5)
        assertTrue("never near the limiter: $first", first.all { it < -7.5 })
        // The service's own figure a moment later changes nothing.
        p.setGainDb(-8f)
        assertEquals(-8.2, play(p, 0.2).last(), 0.3)
    }

    @Test fun anUnknownSongStartsAsItIs() {
        val p = processor()
        startOf(p, "quiet")
        p.setGainDb(6f)
        play(p, 0.5)
        startOf(p, "unknown")
        // A tone at -10.5 dBFS comes out as it is, not 6 dB up.
        val first = play(p, 0.3, peak = 0.3)
        assertTrue("$first", first.all { abs(it + 10.5) < 0.3 })
    }

    @Test fun aSeekInTheSameSongKeepsItsGain() {
        val p = processor()
        startOf(p, "loud")
        // A better figure came while it played (the service eases it in).
        p.setGainDb(-4f, glideSec = 2f)
        play(p, 0.2)
        // Seeking within the song keeps the service's figure and lands on it at once, without a glide.
        startOf(p, "loud", positionUs = 60_000_000)
        assertEquals(-4.2, play(p, 0.1).first(), 0.3)
    }

    @Test fun withoutAnAskTheGainSetStays() {
        // A Mix blend's ending song: its own processor, given its gain once.
        val p = NormalizerProcessor().apply { configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT)) }
        p.setGainDb(-8f)
        startOf(p, "quiet")
        assertEquals(-8.2, play(p, 0.1).first(), 0.3)
    }
}
