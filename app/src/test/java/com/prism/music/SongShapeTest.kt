package com.prism.music

import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.SongWaves
import com.prism.music.playback.MixTempo
import com.prism.music.playback.SongShape
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Automix's song shape (plan-mix.md §3.3.1, B1): a second-by-second outline of energy and bass from the scan, where the
 * outro starts, and whether the intro has bass. Synthetic songs with known parts, and saved shapes of real songs.
 */
@RunWith(RobolectricTestRunner::class)
class SongShapeTest {
    private val sr = 12_000

    /**
     * A song in parts, each (seconds, has drums and bass, level): a kick and a 55 Hz bass on every beat at 120 BPM where
     * there are drums, a 660 Hz pad and a little noise everywhere, all scaled by the part's level.
     */
    private fun song(vararg parts: Triple<Int, Boolean, Double>, lead: Double = 0.5, seed: Int = 3): FloatArray {
        val rnd = Random(seed)
        val total = parts.sumOf { it.first }
        val out = FloatArray(((lead + total + 0.5) * sr).toInt())
        var at = (lead * sr).toInt()
        for ((seconds, drums, level) in parts) {
            val n = seconds * sr
            for (i in 0 until n) {
                val t = i.toDouble() / sr
                var v = 0.12 * sin(2 * PI * 660 * (at + i) / sr) + 0.02 * (rnd.nextDouble() * 2 - 1)
                if (drums) {
                    val inBeat = t % 0.5
                    v += 0.7 * exp(-inBeat * 14) * sin(2 * PI * (50 + 80 * exp(-inBeat * 30)) * inBeat)
                    v += 0.35 * sin(2 * PI * 55 * (at + i) / sr)
                }
                out[at + i] = (v * level).toFloat()
            }
            at += n
        }
        return out
    }

    @Test
    fun aThinOutroIsFoundWhereItStarts() {
        // Two minutes of body, then twenty seconds of pad alone, a third as loud.
        val pcm = song(Triple(120, true, 1.0), Triple(20, false, 0.35))
        val shape = SongShape.of(pcm, pcm.size, sr)
        assertEquals(141, shape.seconds)
        val outro = shape.outroStartMs()
        assertTrue("outro at $outro", abs(outro - 120_500) <= 1_500)
        assertEquals(true, shape.introHasBass())
    }

    @Test
    fun aSongThatPlaysToTheEndHasNoOutro() {
        val pcm = song(Triple(150, true, 1.0))
        assertEquals(-1L, SongShape.of(pcm, pcm.size, sr).outroStartMs())
    }

    @Test
    fun aShortDipAtTheEndIsNotAnOutro() {
        // Three quiet seconds before the last hit: below the bar, but too short to count.
        val pcm = song(Triple(120, true, 1.0), Triple(3, false, 0.3))
        assertEquals(-1L, SongShape.of(pcm, pcm.size, sr).outroStartMs())
    }

    @Test
    fun anIntroWithoutBassIsSaidSo() {
        // Twelve seconds of pad before the drums and bass come in.
        val pcm = song(Triple(12, false, 0.8), Triple(120, true, 1.0))
        val shape = SongShape.of(pcm, pcm.size, sr)
        assertEquals(false, shape.introHasBass())
        // With the music's start given (the scan's firstSoundMs), the intro is read from there.
        assertEquals(false, shape.introHasBass(firstSoundMs = 500))
        assertEquals(true, shape.introHasBass(firstSoundMs = 13_000))
    }

    @Test
    fun theOutroStopsWhereTheMusicDoes() {
        // A silent tail after the outro: lastSoundMs bounds it, and the outro is still found where it starts.
        val pcm = song(Triple(120, true, 1.0), Triple(15, false, 0.35), Triple(10, false, 0.0))
        val shape = SongShape.of(pcm, pcm.size, sr)
        val outro = shape.outroStartMs(firstSoundMs = 500, lastSoundMs = 135_500)
        assertTrue("outro at $outro", abs(outro - 120_500) <= 1_500)
    }

    @Test
    fun tooShortToTellIsUnknown() {
        val pcm = song(Triple(15, true, 1.0))
        val shape = SongShape.of(pcm, pcm.size, sr)
        assertEquals(-1L, shape.outroStartMs())
        assertNull(shape.introHasBass())
    }

    @Test
    fun twoBytesASecondAndBackTheSame() {
        val pcm = song(Triple(60, true, 1.0), Triple(10, false, 0.4))
        val shape = SongShape.of(pcm, pcm.size, sr)
        val bytes = shape.encode()
        assertEquals(shape.seconds * 2, bytes.size)
        val back = SongShape.decode(bytes)!!
        assertArrayEquals(shape.energy, back.energy, 0f)
        assertArrayEquals(shape.bass, back.bass, 0f)
        assertNull(SongShape.decode(ByteArray(3)))
        // The loudest second is 1; the bass is on the same scale, so it's below the whole.
        assertEquals(1f, shape.energy.max(), 0.01f)
        for (i in 0 until shape.seconds) assertTrue(shape.bass[i] <= shape.energy[i] + 0.02f)
    }

    @Test
    fun theScanReadsTheShapeFromTheSameSamples() {
        // MixTempo's scan hands back the shape beside the tempo (no second pass over the song, no Tempo.VERSION bump).
        val pcm = song(Triple(100, true, 1.0), Triple(16, false, 0.3))
        val scan = MixTempo.analyse({ MixTempo.Decoded(pcm, pcm.size, sr, 0.9f) }, step = {})
        val shape = scan.shape
        assertNotNull(shape)
        assertEquals(117, shape!!.seconds)
        assertTrue(abs(shape.outroStartMs(scan.tempo!!.firstSoundMs, scan.tempo!!.lastSoundMs) - 100_500) <= 1_500)
    }

    private fun saved(id: String): Pair<SongShape, com.prism.music.playback.Tempo> {
        val shape = SongShape.decode(javaClass.getResourceAsStream("/mix-shapes/$id.shape1")!!.readBytes())!!
        val tempo = com.prism.music.playback.Tempo.decode(javaClass.getResourceAsStream("/mix-shapes/$id.tempo")!!.readBytes().decodeToString().trim())!!
        return shape to tempo
    }

    /** Three real songs' saved scans (the listening round's, 2026-11-22: MixListeningRender's scans). */
    @Test
    fun realSongs() {
        // Lynyrd Skynyrd, "Simple Man": a long fade-out. The outro is where the scan's own fade measure has it begin.
        val (simple, simpleT) = saved("l-5aPNxv-pU")
        val outro = simple.outroStartMs(simpleT.firstSoundMs, simpleT.lastSoundMs)
        assertEquals(339_000L, outro)
        assertTrue(abs(outro - (simpleT.lastSoundMs - simpleT.fadeOutMs)) <= 2_000)
        assertEquals(false, simple.introHasBass(simpleT.firstSoundMs)) // it opens on guitar alone
        // Dance Gavin Dance, "Man Of The Year": seven thinner seconds before its last chord.
        val (year, yearT) = saved("I2AcToLhESE")
        assertEquals(323_000L, year.outroStartMs(yearT.firstSoundMs, yearT.lastSoundMs))
        // Pierce The Veil, "I'm Low On Gas And You Need A Jacket": at full tilt to its last 4 s: no outro, bass from the start.
        val (gas, gasT) = saved("cRGqiBjnEqc")
        assertEquals(-1L, gas.outroStartMs(gasT.firstSoundMs, gasT.lastSoundMs))
        assertEquals(true, gas.introHasBass(gasT.firstSoundMs))
    }

    @Test
    fun keptBesideTheWaveformOutline() {
        val waves = SongWaves(ApplicationProvider.getApplicationContext())
        val pcm = song(Triple(40, true, 1.0))
        val shape = SongShape.of(pcm, pcm.size, sr)
        assertFalse(waves.hasShape("abc-123"))
        assertNull(waves.shape("abc-123"))
        waves.putShape("abc-123", shape)
        assertTrue(waves.hasShape("abc-123"))
        assertArrayEquals(shape.energy, waves.shape("abc-123")!!.energy, 0f)
        // A fresh store reads it from the file.
        assertArrayEquals(shape.bass, SongWaves(ApplicationProvider.getApplicationContext()).shape("abc-123")!!.bass, 0f)
        waves.forget("abc-123")
        assertFalse(waves.hasShape("abc-123"))
    }
}
