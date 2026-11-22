package com.prism.music

import com.prism.music.playback.FxParam
import com.prism.music.playback.MixMoves
import com.prism.music.playback.MixSide
import com.prism.music.playback.Move
import com.prism.music.playback.MusicKey
import com.prism.music.playback.SongShape
import com.prism.music.playback.Tempo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Automix's picking and placing (plan-mix.md §3.1, §3.3.3, B3): the table, the phrase, the outro, the moves' sound. */
class MixMovesTest {
    /** A song 200 s long at [bpm] whose music runs 0.2–195 s, first beat at 0.5 s, its last beat on the same grid. */
    private fun tempo(
        bpm: Double = 120.0, confidence: Double = 8.0, fadeOutMs: Long = 400, quietIntroMs: Long = 0, firstBeatMs: Long = 500,
        lastSoundMs: Long = 195_000,
    ): Tempo {
        val beat = 60_000.0 / bpm
        val outroBeat = firstBeatMs + Math.floor((lastSoundMs - firstBeatMs) / beat) * beat
        return Tempo(bpm, confidence, 200, lastSoundMs, firstBeatMs, outroBeat.toLong(), fadeOutMs, quietIntroMs)
    }

    private val cMajor = MusicKey(0, false, 0.5f)
    private val gMajor = MusicKey(7, false, 0.5f) // next to C on the wheel
    private val fSharpMajor = MusicKey(6, false, 0.5f) // across the wheel from C: a clash

    private fun side(t: Tempo?, key: MusicKey? = cMajor, shape: SongShape? = null, dur: Long = 200_000) = MixSide(t, key, shape, dur)

    @Test
    fun unknownScansBlend() {
        assertEquals(Move.BLEND, MixMoves.pick(side(null), side(tempo())))
        assertEquals(Move.BLEND, MixMoves.pick(side(tempo()), side(null)))
        // Scanned before its fade-out and intro were measured.
        assertEquals(Move.BLEND, MixMoves.pick(side(tempo(fadeOutMs = -1)), side(tempo())))
    }

    @Test
    fun steadyMatchedTemposInKeysThatSitTogetherSwapBasses() {
        assertEquals(Move.BASS_SWAP, MixMoves.pick(side(tempo(120.0)), side(tempo(124.0), gMajor)))
        // Octave-free: 62 BPM is 124's half.
        assertEquals(Move.BASS_SWAP, MixMoves.pick(side(tempo(120.0)), side(tempo(62.0), gMajor)))
        // A key that isn't clear doesn't count as a clash.
        assertEquals(Move.BASS_SWAP, MixMoves.pick(side(tempo(120.0)), side(tempo(124.0), MusicKey(6, false, 0.01f))))
    }

    @Test
    fun clashingKeysOrAQuietIntroFilterOut() {
        assertEquals(Move.FILTER_OUT, MixMoves.pick(side(tempo(120.0)), side(tempo(121.0), fSharpMajor)))
        assertEquals(Move.FILTER_OUT, MixMoves.pick(side(tempo(120.0)), side(tempo(121.0, quietIntroMs = 6_000), gMajor)))
    }

    @Test
    fun anEndingThatFadesOutBlends() {
        assertEquals(Move.BLEND, MixMoves.pick(side(tempo(fadeOutMs = 8_000)), side(tempo())))
        assertEquals(Move.BLEND, MixMoves.pick(side(tempo(fadeOutMs = 8_000)), side(tempo(90.0), gMajor)))
        // A last chord ringing for 4 s isn't a fade-out: the echo catches the last bar before it.
        assertEquals(Move.ECHO_OUT, MixMoves.pick(side(tempo(fadeOutMs = 4_000)), side(tempo(90.0), gMajor)))
        val p = MixMoves.place(Move.ECHO_OUT, side(tempo(fadeOutMs = 4_000)), side(tempo(90.0)))!!
        assertTrue(p.pivotMs <= 195_000 - 4_000)
        // A fade with an outro before it: the move leaves at the outro, where the song still has its energy.
        assertEquals(Move.BASS_SWAP, MixMoves.pick(side(tempo(fadeOutMs = 8_000), shape = shape(200, 181)), side(tempo(121.0), gMajor)))
    }

    @Test
    fun farTemposAndAnAbruptEndingEchoOut() {
        assertEquals(Move.ECHO_OUT, MixMoves.pick(side(tempo(120.0)), side(tempo(90.0), gMajor)))
        // The next song unsteady (a spoken intro, a 3/4 song read as unclear): still the echo, it needs only the ending's beat.
        assertEquals(Move.ECHO_OUT, MixMoves.pick(side(tempo(120.0)), side(tempo(120.0, confidence = 1.0), gMajor)))
    }

    @Test
    fun twoHardEdgesClashingAndFarCutOnTheBeat() {
        assertEquals(Move.CUT, MixMoves.pick(side(tempo(120.0)), side(tempo(90.0), fSharpMajor)))
        // A quiet intro isn't a hard edge: the echo instead.
        assertEquals(Move.ECHO_OUT, MixMoves.pick(side(tempo(120.0)), side(tempo(90.0, quietIntroMs = 2_500), fSharpMajor)))
    }

    @Test
    fun anUnsteadyEndingBlends() {
        // 3/4 time and rubato songs read as unsteady: nothing to put a beat on.
        assertEquals(Move.BLEND, MixMoves.pick(side(tempo(120.0, confidence = 1.5)), side(tempo(90.0), fSharpMajor)))
        assertEquals(Move.BLEND, MixMoves.pick(side(tempo(120.0, confidence = 1.5)), side(tempo(120.0), gMajor)))
    }

    @Test
    fun theEchoKeepsTwoBarsOfTheFileAfterItsCatch() {
        // The music runs to the file's very end: the catch moves a bar earlier, so the song's player still plays two bars.
        val tight = side(tempo(120.0, lastSoundMs = 199_900), dur = 200_000)
        val p = MixMoves.place(Move.ECHO_OUT, tight, side(tempo(90.0)))!!
        assertTrue(p.startMs + p.lengthMs <= 200_000)
    }

    @Test
    fun anEffectMoveDoesNotComeAThirdTime() {
        val a = side(tempo(120.0)); val clash = side(tempo(121.0), fSharpMajor)
        assertEquals(Move.FILTER_OUT, MixMoves.pick(a, clash, listOf(Move.FILTER_OUT)))
        assertEquals(Move.BLEND, MixMoves.pick(a, clash, listOf(Move.FILTER_OUT, Move.FILTER_OUT)))
        assertEquals(Move.FILTER_OUT, MixMoves.pick(a, clash, listOf(Move.FILTER_OUT, Move.BASS_SWAP)))
        // The echo's next best is the cut when the cut fits, else a blend.
        val far = side(tempo(90.0), fSharpMajor)
        assertEquals(Move.CUT, MixMoves.pick(a, far))
        assertEquals(Move.ECHO_OUT, MixMoves.pick(a, far, listOf(Move.CUT, Move.CUT)))
        val farFriendly = side(tempo(90.0), gMajor)
        assertEquals(Move.BLEND, MixMoves.pick(a, farFriendly, listOf(Move.ECHO_OUT, Move.ECHO_OUT)))
        // Bass swaps and blends may repeat.
        assertEquals(Move.BASS_SWAP, MixMoves.pick(a, side(tempo(124.0), gMajor), listOf(Move.BASS_SWAP, Move.BASS_SWAP)))
    }

    @Test
    fun theSamePairGetsTheSameMove() {
        val pairs = listOf(
            side(tempo(120.0)) to side(tempo(124.0), gMajor),
            side(tempo(120.0)) to side(tempo(90.0), fSharpMajor),
            side(tempo(98.0, fadeOutMs = 900)) to side(tempo(140.0, quietIntroMs = 2_000), gMajor),
        )
        for ((a, b) in pairs) {
            val first = MixMoves.pick(a, b)
            repeat(5) { assertEquals(first, MixMoves.pick(a.copy(), b.copy())) }
            assertEquals(MixMoves.place(first, a, b), MixMoves.place(first, a.copy(), b.copy()))
        }
    }

    @Test
    fun phrasesAreSixteenBeatsFromTheFirstBeat() {
        val t = tempo(120.0) // a beat is 500 ms, a phrase 8 s, from 0.5 s
        assertEquals(160_500L, MixMoves.phraseStartMs(t, 165_000))
        assertEquals(160_500L, MixMoves.phraseStartMs(t, 160_500))
        assertEquals(152_500L, MixMoves.phraseStartMs(t, 160_499))
        assertNull(MixMoves.phraseStartMs(t, 300))
        assertNull(MixMoves.phraseStartMs(tempo(confidence = 1.0), 165_000))
    }

    @Test
    fun aBassSwapStartsOnAPhraseAndSwapsOnABeat() {
        val a = side(tempo(120.0)); val b = side(tempo(123.0, firstBeatMs = 800), gMajor)
        val p = MixMoves.place(Move.BASS_SWAP, a, b, outroRule = false)!!
        // 16 beats (8 s) against the end of the music (195 s), back to the phrase at or before 187 s.
        assertEquals(8_000L, p.lengthMs)
        assertEquals(184_500L, p.startMs)
        assertEquals(800L, p.inMs)
        assertEquals(120.0 / 123.0, p.ratio.toDouble(), 1e-4)
        assertEquals(0.0, (p.pivotMs - 500.0) % 500.0, 1.0)
        assertTrue(abs(p.pivot - 0.5f) < 0.05f)
    }

    /** A shape whose energy is full until [outroFromS], then a third, to the end ([seconds] long). */
    private fun shape(seconds: Int, outroFromS: Int) = SongShape(
        FloatArray(seconds) { if (it < outroFromS) 1f else 0.2f }, FloatArray(seconds) { if (it < outroFromS) 0.5f else 0.05f },
    )

    @Test
    fun outOfTheOutro() {
        val t = tempo(120.0) // phrases at 176.5 s and 184.5 s
        // An outro from 184 s (music ends at 195 s): the move starts at the phrase that begins it.
        assertEquals(184_500L, MixMoves.outroStartMs(side(t, shape = shape(200, 184)), plannedStartMs = 187_000))
        // From 181 s, well inside a phrase: on its first beat, not back in the body.
        assertEquals(181_000L, MixMoves.outroStartMs(side(t, shape = shape(200, 181)), plannedStartMs = 187_000))
        // Never more than 20 s before the music ends, nor before 60 % of the song.
        assertNull(MixMoves.outroStartMs(side(t, shape = shape(200, 170)), plannedStartMs = 187_000))
        // (Music to 70 s of a 100 s file, an outro from 55 s: inside the last 20 s, but before 60 s.)
        val early = tempo(120.0, lastSoundMs = 70_000)
        assertNull(MixMoves.outroStartMs(side(early, shape = shape(100, 55), dur = 100_000), plannedStartMs = 65_000))
        assertEquals(55_000L, MixMoves.outroStartMs(side(early, shape = shape(100, 55), dur = 90_000), plannedStartMs = 65_000))
        // Only ever earlier than planned; and nothing without a shape (scanned before shapes came in).
        assertNull(MixMoves.outroStartMs(side(t, shape = shape(200, 184)), plannedStartMs = 184_000))
        assertNull(MixMoves.outroStartMs(side(t), plannedStartMs = 187_000))
        // In the move's place.
        val p = MixMoves.place(Move.BASS_SWAP, side(t, shape = shape(200, 181)), side(tempo(121.0), gMajor))!!
        assertEquals(181_000L, p.startMs)
        assertTrue(p.outro)
        assertTrue(!MixMoves.place(Move.BASS_SWAP, side(t, shape = shape(200, 181)), side(tempo(121.0), gMajor), outroRule = false)!!.outro)
    }

    @Test
    fun aSongTooShortForTheEchoGetsAnother() {
        // Two bars at 30 BPM are 16 s: they don't fit after a catch in this half-minute song's second half.
        val slow = side(tempo(30.0, lastSoundMs = 29_900, firstBeatMs = 500), dur = 30_000)
        assertNull(MixMoves.place(Move.ECHO_OUT, slow, side(tempo(90.0))))
        assertEquals(Move.BLEND, MixMoves.pick(slow, side(tempo(90.0), gMajor)))
    }

    @Test
    fun everyMoveGoesFromTheEndingSongToTheNext() {
        for (m in Move.entries) {
            val (in0, out0) = MixMoves.gains(m, 0f)
            val (in1, out1) = MixMoves.gains(m, 1f)
            assertEquals("$m", 0f, in0, 0.01f); assertEquals("$m", 1f, out0, 0.01f)
            assertEquals("$m", 1f, in1, 0.01f); assertEquals("$m", 0f, out1, 0.01f)
        }
        // A bass swap holds both near full before the swap; the filter's next song is up by half-way.
        val (i, o) = MixMoves.gains(Move.BASS_SWAP, 0.4f)
        assertTrue(i >= 0.85f && o >= 0.85f)
        assertEquals(1f, MixMoves.gains(Move.FILTER_OUT, 0.5f).first, 1e-4f)
        // The echo's song stays up while its echo dies away.
        assertEquals(1f, MixMoves.gains(Move.ECHO_OUT, 0.5f).second, 0f)
    }

    @Test
    fun theBassesTradePlacesAtThePivot() {
        val p = MixMoves.place(Move.BASS_SWAP, side(tempo(120.0)), side(tempo(123.0, firstBeatMs = 800), gMajor), outroRule = false)!!
        val (ending, next) = MixMoves.scripts(p)
        val piv = p.pivotMs.toDouble()
        assertEquals(0f, ending.value(FxParam.BASS_DB, piv - 300), 0f)
        assertTrue(ending.value(FxParam.BASS_DB, piv + 300) <= -25f)
        assertTrue(next.value(FxParam.BASS_DB, p.inMs.toDouble()) <= -25f)
        assertTrue(next.value(FxParam.BASS_DB, p.nextAt(piv) - 300) <= -25f)
        assertEquals(0f, next.value(FxParam.BASS_DB, p.nextAt(piv) + 300), 0f)
        // Stronger cuts deeper.
        assertTrue(MixMoves.scripts(p, MixMoves.STRONG).first.value(FxParam.BASS_DB, piv + 300) < ending.value(FxParam.BASS_DB, piv + 300))
    }

    @Test
    fun theFilterSweepsOneAwayAndOpensTheOther() {
        val p = MixMoves.place(Move.FILTER_OUT, side(tempo(120.0)), side(tempo(121.0), fSharpMajor), outroRule = false)!!
        val (ending, next) = MixMoves.scripts(p)
        val end = (p.startMs + p.lengthMs).toDouble()
        assertEquals(FxParam.HIGH_PASS_HZ.neutral, ending.value(FxParam.HIGH_PASS_HZ, p.startMs.toDouble()), 0.01f)
        assertEquals(1960f, ending.value(FxParam.HIGH_PASS_HZ, end), 1f)
        assertEquals(3000f, MixMoves.scripts(p, MixMoves.STRONG).first.value(FxParam.HIGH_PASS_HZ, end), 1f)
        assertEquals(780f, next.value(FxParam.LOW_PASS_HZ, p.inMs.toDouble()), 1f)
        assertEquals(FxParam.LOW_PASS_HZ.neutral, next.value(FxParam.LOW_PASS_HZ, p.nextAt(end)), 1f)
    }

    @Test
    fun theEchoCatchesTheBeatBeforeThePivot() {
        val p = MixMoves.place(Move.ECHO_OUT, side(tempo(120.0)), side(tempo(90.0), gMajor))!!
        val (ending, next) = MixMoves.scripts(p)
        val piv = p.pivotMs.toDouble()
        assertEquals(500.0, ending.echoMs, 0.0)
        assertEquals(1f, ending.value(FxParam.ECHO_SEND, piv - 200), 0f)
        assertEquals(0f, ending.value(FxParam.ECHO_SEND, piv + 20), 0f)
        assertEquals(0f, ending.value(FxParam.DRY, piv + 20), 0f)
        assertEquals(0.53f, ending.value(FxParam.ECHO_FEEDBACK, piv), 1e-4f)
        assertTrue(next.ramps.isEmpty())
        // The catch is a bar line, two bars of the song after it.
        assertEquals(0.0, (p.pivotMs - 500.0) % 2_000.0, 1.0)
        assertEquals(4_000L, p.lengthMs)
    }
}
