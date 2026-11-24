package com.prism.music.playback

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A song's shape a second at a time (1.8.8, Automix; plan-mix.md §3.3.1): how much energy each second has, overall and
 * in the bass (below about [BASS_HZ]), both as loudness (RMS) against the song's loudest second. Made by the song scan
 * from the samples it already decodes ([of]), kept beside the waveform outline ([com.prism.music.data.SongWaves], two
 * bytes a second, about half a kilobyte a song), so [Tempo] and its version stay as they are. From it Automix reads where
 * the song's outro begins ([outroStartMs]) and whether its intro has bass ([introHasBass]).
 */
class SongShape(val energy: FloatArray, val bass: FloatArray) {
    val seconds: Int get() = energy.size

    /** Two bytes a second (energy, bass), each the square root of its level so quiet seconds keep their detail. */
    fun encode(): ByteArray = ByteArray(seconds * 2) { i -> byteOf(if (i % 2 == 0) energy[i / 2] else bass[i / 2]) }

    /**
     * Where the song's outro begins (ms from its start): the second from which its energy, over three seconds, stays below
     * [OUTRO_BELOW] of its body's median until the music ends, when that lasts at least [MIN_OUTRO_S]. -1 when it has
     * none, or the song is too short to tell. [firstSoundMs] and [lastSoundMs] (the scan's [Tempo]) bound the music when
     * known; otherwise its first and last seconds above silence do.
     */
    fun outroStartMs(firstSoundMs: Long = -1, lastSoundMs: Long = -1): Long {
        val (first, last) = music(firstSoundMs, lastSoundMs) ?: return -1
        if (last - first + 1 < MIN_SONG_S) return -1
        val body = median(energy, first, last)
        if (body <= 0f) return -1
        fun smooth(s: Int): Float {
            var sum = 0f; var n = 0
            for (i in maxOf(first, s - 1)..minOf(last, s + 1)) { sum += energy[i]; n++ }
            return sum / n
        }
        var o = last
        while (o >= first && smooth(o) < OUTRO_BELOW * body) o--
        o++
        if (last - o + 1 < MIN_OUTRO_S || o - first < MIN_BODY_S) return -1
        return o * 1000L
    }

    /**
     * Whether the first [INTRO_S] seconds of music carry bass: their median bass at least [INTRO_BASS] of the body's. A song
     * with no bass to speak of anywhere hasn't any in its intro either. Null when the song is too short to tell.
     */
    fun introHasBass(firstSoundMs: Long = -1): Boolean? {
        val (first, last) = music(firstSoundMs, -1) ?: return null
        if (last - first + 1 < MIN_SONG_S) return null
        val body = median(bass, first, last)
        if (body < NO_BASS) return false
        return median(bass, first, minOf(last, first + INTRO_S - 1)) >= INTRO_BASS * body
    }

    /** The music's first and last seconds. */
    private fun music(firstSoundMs: Long, lastSoundMs: Long): Pair<Int, Int>? {
        if (seconds == 0) return null
        val first = if (firstSoundMs >= 0) (firstSoundMs / 1000).toInt() else energy.indexOfFirst { it > SILENT }
        val last = if (lastSoundMs >= 0) ((lastSoundMs - 1).coerceAtLeast(0) / 1000).toInt() else energy.indexOfLast { it > SILENT }
        if (first < 0 || last < 0) return null
        val a = first.coerceIn(0, seconds - 1); val b = last.coerceIn(0, seconds - 1)
        return if (b > a) a to b else null
    }

    companion object {
        /** Stored as `<id>.shape<VERSION>`: bumped when [of] changes what it measures. */
        const val VERSION = 1
        /** The bass is what's below this (two low-passes, 24 dB an octave). */
        const val BASS_HZ = 150.0
        /** An outro is energy below this share of the body's median (RMS)... */
        const val OUTRO_BELOW = 0.6f
        /** ...for at least this long, to the end of the music. */
        const val MIN_OUTRO_S = 6
        /** And no outro takes in a song's first half minute of music. */
        private const val MIN_BODY_S = 30
        /** Shorter music than this has no outro or intro worth telling. */
        private const val MIN_SONG_S = 20
        /** The intro whose bass [introHasBass] looks at. */
        const val INTRO_S = 8
        /** An intro with at least this share of the body's bass has bass. */
        const val INTRO_BASS = 0.5f
        /** Body bass below this (against the loudest second) is no bass at all. */
        private const val NO_BASS = 0.005f
        /** Below this (40 dB under the loudest second) a second is silence. */
        private const val SILENT = 0.01f

        private fun byteOf(v: Float) = (sqrt(v.coerceIn(0f, 1f)) * 255).roundToInt().toByte()
        private fun levelOf(b: Byte) = ((b.toInt() and 0xFF) / 255f).let { it * it }

        /** Null when [bytes] isn't a shape (an odd length, or none). */
        fun decode(bytes: ByteArray): SongShape? {
            if (bytes.isEmpty() || bytes.size % 2 != 0) return null
            val n = bytes.size / 2
            return SongShape(FloatArray(n) { levelOf(bytes[2 * it]) }, FloatArray(n) { levelOf(bytes[2 * it + 1]) })
        }

        /**
         * The shape of mono [pcm] (its first [count] samples, at [rate]): one value a second (a last part-second of under
         * half a second is left out), as stored ([encode]d and read back), so what's read later is what was measured.
         */
        fun of(pcm: FloatArray, count: Int, rate: Int): SongShape {
            val whole = count / rate
            val n = whole + if (count - whole * rate >= rate / 2) 1 else 0
            val e = FloatArray(n); val b = FloatArray(n)
            val lp = Biquad.of(EqFilter(EqFilter.Type.LOW_PASS, BASS_HZ, 0.0, 0.707), rate)
            val z = DoubleArray(4)
            for (s in 0 until n) {
                val from = s * rate; val to = minOf(count, from + rate)
                var sumE = 0.0; var sumB = 0.0
                for (i in from until to) {
                    val x = pcm[i].toDouble()
                    sumE += x * x
                    var y = x
                    if (lp != null) {
                        // Two stages of the same low-pass (transposed direct form II).
                        val y1 = lp.b0 * y + z[0]; z[0] = lp.b1 * y - lp.a1 * y1 + z[1]; z[1] = lp.b2 * y - lp.a2 * y1; y = y1
                        val y2 = lp.b0 * y + z[2]; z[2] = lp.b1 * y - lp.a1 * y2 + z[3]; z[3] = lp.b2 * y - lp.a2 * y2; y = y2
                    }
                    sumB += y * y
                }
                val len = (to - from).coerceAtLeast(1)
                e[s] = sqrt(sumE / len).toFloat(); b[s] = sqrt(sumB / len).toFloat()
            }
            val top = e.maxOrNull()?.takeIf { it > 0f } ?: 1f
            for (s in 0 until n) { e[s] /= top; b[s] = (b[s] / top).coerceAtMost(1f) }
            return decode(SongShape(e, b).encode()) ?: SongShape(e, b)
        }

        private fun median(x: FloatArray, from: Int, to: Int): Float {
            val part = x.copyOfRange(from, to + 1)
            part.sort()
            return part[part.size / 2]
        }
    }
}
