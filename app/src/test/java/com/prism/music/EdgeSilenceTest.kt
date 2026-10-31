package com.prism.music

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import com.prism.music.playback.EdgeSilenceProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * #210 (1.8.0): Skip silence trims only a song's very start and end, never a pause inside it. Songs went through
 * Media3's own skipper before, which shortened every pause longer than 0.15 s to a fraction of a second (run
 * against it, [pausesInsideASongStay]'s 2 s pause came out 0.17 s long).
 */
class EdgeSilenceTest {
    private val rate = 8_000

    // Started off zero, so its first sample is sound too.
    private fun tone(seconds: Double) = FloatArray((seconds * rate).toInt()) { (0.3 * sin(2 * PI * 440 * it / rate + 1.0)).toFloat() }
    private fun silence(seconds: Double) = FloatArray((seconds * rate).toInt())
    private fun cat(vararg parts: FloatArray) = parts.reduce { a, b -> a + b }

    /** Songs one after another, each [lengths] seconds long, as the player's timeline has them. */
    private class Songs(private val lengths: List<Double>) : Timeline() {
        override fun getWindowCount() = lengths.size
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window = window.set(
            windowIndex, MediaItem.Builder().setMediaId("song$windowIndex").build(), null, 0, 0, 0, true, false, null, 0,
            (lengths[windowIndex] * 1e6).toLong(), windowIndex, windowIndex, 0,
        )
        override fun getPeriodCount() = lengths.size
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
            period.set(periodIndex, periodIndex, periodIndex, (lengths[periodIndex] * 1e6).toLong(), 0)
        override fun getIndexOfPeriod(uid: Any) = uid as? Int ?: C.INDEX_UNSET
        override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex
    }

    private fun processor() = EdgeSilenceProcessor().apply { enabled = true }

    /** [pcm] (mono, 16-bit) through [p] from the start of the first of [songs], a bit at a time as the player feeds it. */
    private fun play(p: AudioProcessor, pcm: FloatArray, songs: List<Double>, startUs: Long = 0): FloatArray {
        p.configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        p.flush(AudioProcessor.StreamMetadata.Builder().setTimeline(Songs(songs)).setPeriodUid(0).setPositionOffsetUs(startUs).build())
        val out = ArrayList<Float>()
        fun drain() { val o = p.output.order(ByteOrder.nativeOrder()); while (o.remaining() >= 2) out += o.short / 32768f }
        for (from in pcm.indices step 1_000) {
            val n = minOf(1_000, pcm.size - from)
            val b = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
            for (i in from until from + n) b.putShort((pcm[i] * 32767).toInt().toShort())
            b.flip()
            p.queueInput(b)
            drain()
        }
        p.queueEndOfStream()
        drain()
        return out.toFloatArray()
    }

    /** The silent stretches in [pcm], in seconds. */
    private fun pauses(pcm: FloatArray): List<Double> {
        val runs = ArrayList<Double>()
        var n = 0
        for (x in pcm) if (abs(x) < 0.002f) n++ else { if (n > 0) runs += n.toDouble() / rate; n = 0 }
        if (n > 0) runs += n.toDouble() / rate
        return runs.filter { it > 0.01 }
    }

    @Test fun pausesInsideASongStay() {
        val p = processor()
        val song = cat(silence(1.0), tone(3.0), silence(2.0), tone(3.0), silence(2.0))
        val out = play(p, song, listOf(11.0))
        assertEquals("the start's and the end's silence left out", 8.0, out.size.toDouble() / rate, 0.01)
        assertEquals("the pause inside it kept whole", listOf(2.0), pauses(out).map { Math.round(it * 100) / 100.0 })
        assertEquals("what's left out is counted (the position stays right)", 3 * rate.toLong(), p.skippedFrames)
    }

    @Test fun theNextSongInAGaplessRunTrimsItsOwnStart() {
        val p = processor()
        val out = play(p, cat(tone(3.0), silence(1.0), silence(1.5), tone(3.0), silence(0.5)), listOf(4.0, 5.0))
        assertEquals(6.0, out.size.toDouble() / rate, 0.01)
        assertTrue("no silence between the songs: ${pauses(out)}", pauses(out).isEmpty())
    }

    @Test fun soundComingBackNearTheEndIsKept() {
        // A stop two seconds before the end, then a last chord: not the end's silence.
        val song = cat(tone(5.0), silence(2.0), tone(1.0))
        val out = play(processor(), song, listOf(8.0))
        assertEquals(song.size, out.size)
        assertEquals(listOf(2.0), pauses(out).map { Math.round(it * 100) / 100.0 })
    }

    @Test fun aLongQuietStartIsOnlySkippedSoFar() {
        val out = play(processor(), cat(silence(14.0), tone(2.0)), listOf(16.0))
        assertEquals(16.0 - EdgeSilenceProcessor.EDGE_SECONDS, out.size.toDouble() / rate, 0.01)
    }

    @Test fun aSeekIntoTheSongPlaysFromThere() {
        // Not the start of the song: nothing before the sound is skipped.
        val out = play(processor(), cat(silence(1.0), tone(2.0)), listOf(20.0), startUs = 5_000_000)
        assertEquals(3.0, out.size.toDouble() / rate, 0.01)
    }

    @Test fun offItPassesEverything() {
        val song = cat(silence(1.0), tone(3.0), silence(2.0))
        val p = EdgeSilenceProcessor()
        assertEquals(song.size, play(p, song, listOf(6.0)).size)
        assertEquals(0L, p.skippedFrames)
    }
}
