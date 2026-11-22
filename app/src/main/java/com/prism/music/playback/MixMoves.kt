package com.prism.music.playback

import com.prism.music.data.prefs.CrossfadeCurve
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sin

/** Automix's moves (1.8.8; plan-mix.md §3.1). [effect]: one of the moves that mustn't come three times running. */
enum class Move(val effect: Boolean) {
    /** The next song comes in without its bass; on a beat half-way the basses trade places; the ending song thins out. */
    BASS_SWAP(false),
    /** The ending song is swept away from its lows up while the next one opens up from muffled to full. */
    FILTER_OUT(true),
    /** The ending song's last beat is caught and repeats, dying away over two bars; the next starts clean on its first beat. */
    ECHO_OUT(true),
    /** Today's crossfade, at the automatic length. */
    BLEND(false),
    /** A straight switch on a strong beat (the DJ cut curve, short). */
    CUT(true),
}

/** What Automix knows of one song: its scan ([tempo], [key], [shape]; any of them may be missing) and its length. */
data class MixSide(val tempo: Tempo?, val key: MusicKey? = null, val shape: SongShape? = null, val durationMs: Long = 0)

/**
 * Where a move sits. All in the ending song's own time but [inMs] (where the next song starts, in its own time):
 * it starts at [startMs] and lasts [lengthMs]; the next song plays at [ratio] meanwhile (beat matching), the ending
 * song's beat is [beatMs] (0 when it has none clear), and [pivotMs] is the move's moment (the bass swap's beat, the
 * echo's catch, the cut; the middle of the others). [outro]: started early, at the outro ([MixMoves.outroStartMs]).
 */
data class MovePlace(
    val move: Move, val startMs: Long, val inMs: Long, val lengthMs: Long, val ratio: Float,
    val beatMs: Double, val pivotMs: Long, val outro: Boolean = false,
) {
    /** Where the next song is, in its own time, when the ending song is at [ms]. */
    fun nextAt(ms: Double): Double = inMs + (ms - startMs) * ratio
    /** How far through the move [ms] (the ending song's time) is, 0 … 1. */
    fun progress(ms: Double): Float = ((ms - startMs) / lengthMs).toFloat().coerceIn(0f, 1f)
    /** The pivot as a share of the move. */
    val pivot: Float get() = ((pivotMs - startMs).toDouble() / lengthMs).toFloat().coerceIn(0f, 1f)
}

/**
 * Automix's choices, as pure functions (1.8.8; plan-mix.md §3.1, §3.3.3): which move a pair of songs gets ([pick]),
 * where it sits ([place]: on a phrase, [phraseStartMs], maybe out of the outro, [outroStartMs]), and how it sounds: the
 * two volumes over the move ([gains]) and each song's effects ([scripts], for [TransitionFx]). The engine and the
 * offline renderer of the listening round both use these, so what was judged by ear is what plays.
 */
object MixMoves {
    /** How strong the effects are by default (0 … 1; the listening round sets it). */
    const val DEFAULT_STRENGTH = 0.6f
    const val STRONG = 1f
    /** A phrase: this many beats from the song's first beat. */
    const val PHRASE_BEATS = 16
    private const val BAR_BEATS = 4
    /** Tempos this close (octave-free) are matched, as Mix's beat matching does. */
    const val MAX_STRETCH = 0.06
    /** The next song's first beat no later than this, for its beats to be lined up (as Mix). */
    private const val FIRST_BEAT_MAX_MS = 4_000L
    /** An ending that fades out over at least this long is blended over its fade. */
    const val FADES_OUT_MS = 6_000L
    /** An intro quieter than full for less than this is a hard edge. */
    const val ABRUPT_MS = 1_500L
    /** An intro quiet for at least this long: the next song opens up from muffled. */
    const val QUIET_INTRO_MS = 4_000L
    /** Out of the outro: never more than this before the music ends... */
    const val OUTRO_MAX_EARLY_MS = 20_000L
    /** ...nor before this share of the song. */
    const val OUTRO_NOT_BEFORE = 0.6
    /** The echo dies away over two bars. */
    const val ECHO_BEATS = 8

    /** How much the two songs' keys clash (0 … 1, [MusicKey.clash]); null when either key isn't clear. */
    fun clash(a: MusicKey?, b: MusicKey?): Double? = if (a != null && b != null && a.sure && b.sure) MusicKey.clash(a, b) else null

    /** The next song's speed that puts its beats on the ending song's (octave-free), or null when they can't be matched. */
    fun matchedRatio(a: Tempo?, b: Tempo?): Float? {
        if (a == null || b == null || !a.steady || !b.steady || b.firstBeatMs > FIRST_BEAT_MAX_MS) return null
        val bBpm = listOf(b.bpm, b.bpm * 2, b.bpm / 2).minBy { abs(ln(it / a.bpm)) }
        val r = a.bpm / bBpm
        return if (abs(r - 1) <= MAX_STRETCH) r.toFloat() else null
    }

    /**
     * The move for [ending] into [next] (plan-mix.md §3.1's table), given the moves of the transitions before it, oldest
     * first ([last]): an effect move doesn't come a third time running; the one that fits next best does. The same
     * pair and the same moves before always get the same move.
     */
    fun pick(ending: MixSide, next: MixSide, last: List<Move> = emptyList()): Move {
        val first = firstChoice(ending, next)
        if (!first.effect || last.size < 2 || last[last.size - 1] != first || last[last.size - 2] != first) return first
        return when (first) {
            Move.ECHO_OUT -> if (cuts(ending, next)) Move.CUT else Move.BLEND
            Move.CUT -> if (echoes(ending, next)) Move.ECHO_OUT else Move.BLEND
            else -> Move.BLEND
        }
    }

    private fun firstChoice(ending: MixSide, next: MixSide): Move {
        val a = ending.tempo; val b = next.tempo
        // Nothing (or not enough) known of either song: today's blend.
        if (a == null || b == null || a.edgesUnknown || b.edgesUnknown) return Move.BLEND
        // An ending that fades out by itself is blended over its fade.
        if (fadesOut(ending)) return Move.BLEND
        val clashes = (clash(ending.key, next.key) ?: 0.0) >= 1.0
        if (matchedRatio(a, b) != null) return if (clashes || b.quietIntroMs >= QUIET_INTRO_MS) Move.FILTER_OUT else Move.BASS_SWAP
        if (cuts(ending, next)) return Move.CUT
        if (echoes(ending, next)) return Move.ECHO_OUT
        return Move.BLEND
    }

    /**
     * The ending song fades out by itself: a fade of [FADES_OUT_MS] or more, which a move can't leave before (no outro
     * found to start in). A shorter dying away (a last chord ringing out) isn't one: the echo and the cut sit on the last
     * bar at full strength, before it ([place]). Tuned on the listening round's real songs (2026-11-22): rock endings ring
     * for 2 to 6 s, and the table's "abrupt" (under 1.5 s) left the echo and the cut for almost none of them.
     */
    private fun fadesOut(ending: MixSide): Boolean {
        val a = ending.tempo ?: return false
        return a.fadeOutMs >= FADES_OUT_MS && outroStartMs(ending, a.lastSoundMs) == null
    }

    /** Hard edges (the next song's intro too), keys that clash, tempos that can't be matched, and a beat to cut on. */
    private fun cuts(ending: MixSide, next: MixSide): Boolean {
        val a = ending.tempo ?: return false; val b = next.tempo ?: return false
        return a.steady && !fadesOut(ending) && b.quietIntroMs in 0 until ABRUPT_MS &&
            (clash(ending.key, next.key) ?: 0.0) >= 1.0 && matchedRatio(a, b) == null && place(Move.CUT, ending, next) != null
    }

    /** An ending that doesn't fade out, with a clear beat, and two bars of its file left after the catch. */
    private fun echoes(ending: MixSide, next: MixSide): Boolean {
        val a = ending.tempo ?: return false
        return a.steady && !fadesOut(ending) && place(Move.ECHO_OUT, ending, next) != null
    }

    /**
     * The phrase start at or before [atOrBeforeMs]: a multiple of [PHRASE_BEATS] beats from the song's first beat, put on
     * the beats of its ending ([Tempo.outroBeatMs]'s, which a blend lines up on). Null for a song without a clear beat,
     * or before its first phrase.
     */
    fun phraseStartMs(t: Tempo, atOrBeforeMs: Long, beats: Int = PHRASE_BEATS): Long? {
        if (!t.steady) return null
        val beat = t.periodMs
        val k = floor((atOrBeforeMs - t.firstBeatMs) / (beats * beat))
        if (k < 0) return null
        val phrase = t.firstBeatMs + k * beats * beat
        // On the ending's own beat grid (the scan's tempo is a hair off over minutes), not later than asked.
        var onGrid = t.outroBeatMs + Math.round((phrase - t.outroBeatMs) / beat) * beat
        if (onGrid > atOrBeforeMs) onGrid -= beat
        return onGrid.roundToLong()
    }

    /** The beat of [t] at or before [ms] (on its ending's grid). */
    private fun beatAtOrBefore(t: Tempo, ms: Double): Double = t.outroBeatMs + floor((ms - t.outroBeatMs) / t.periodMs) * t.periodMs

    /**
     * Out of the outro (plan-mix.md §3.1): an earlier start for a move planned at [plannedStartMs], at the phrase where
     * the ending song's outro begins ([SongShape.outroStartMs]), so the next song comes in while the first still has
     * energy. Only inside an outro the scan found, never more than [OUTRO_MAX_EARLY_MS] before the music ends nor before
     * [OUTRO_NOT_BEFORE] of the song; null when there's none, or it wouldn't be earlier.
     */
    fun outroStartMs(ending: MixSide, plannedStartMs: Long): Long? {
        val t = ending.tempo ?: return null
        val shape = ending.shape ?: return null
        val o = shape.outroStartMs(t.firstSoundMs, t.lastSoundMs).takeIf { it >= 0 } ?: return null
        val at = if (t.steady) {
            val beat = t.periodMs
            // The outro is found to the second: the phrase that starts it, else the first beat in it.
            val phrase = phraseStartMs(t, (o + 2 * beat).toLong())
            if (phrase != null && o - phrase <= BAR_BEATS * beat) phrase
            else beatAtOrBefore(t, o.toDouble()).let { if (it < o) it + beat else it }.roundToLong()
        } else o
        val dur = ending.durationMs.takeIf { it > 0 } ?: t.lastSoundMs
        if (at < t.lastSoundMs - OUTRO_MAX_EARLY_MS || at < dur * OUTRO_NOT_BEFORE || at >= plannedStartMs) return null
        return at
    }

    /** How long [move] lasts: in the ending song's beats when it has a clear one. */
    fun lengthMs(move: Move, ending: Tempo?, next: Tempo?): Long {
        val beat = ending?.takeIf { it.steady }?.periodMs
        return when (move) {
            Move.BLEND -> MixEngine.autoLengthMs(ending, next)
            Move.BASS_SWAP, Move.FILTER_OUT -> if (beat == null) 8_000L else {
                var beats = PHRASE_BEATS
                while (beats * beat < 6_000) beats *= 2
                (beats * beat).roundToLong().coerceAtMost(20_000L)
            }
            Move.ECHO_OUT -> beat?.let { (ECHO_BEATS * it).roundToLong() } ?: 4_000L
            Move.CUT -> beat?.let { (2 * it).roundToLong() } ?: 1_000L
        }
    }

    /**
     * Where [move] sits for [ending] into [next] (as Mix's plan: blends against the end of the music, the next song from its
     * first sound or, beat-matched, its first beat), or null when it can't (a song too short; no room for the echo).
     * [outroRule]: a blend, bass swap or filter may start early, out of the outro ([outroStartMs]).
     */
    fun place(move: Move, ending: MixSide, next: MixSide, outroRule: Boolean = true): MovePlace? {
        val a = ending.tempo; val b = next.tempo
        val dur = ending.durationMs.takeIf { it > 0 } ?: return null
        val nextDur = next.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        if (dur < 30_000) return null
        val end = a?.lastSoundMs?.takeIf { it in dur / 2..dur } ?: dur
        val len = minOf(lengthMs(move, a, b), dur / 3, nextDur / 3)
        if (len < 1_000 && move != Move.CUT) return null
        val beat = a?.takeIf { it.steady }?.periodMs ?: 0.0
        val firstSound = (b?.firstSoundMs ?: 0L).coerceIn(0, 10_000)
        val firstBeat = b?.takeIf { it.steady && it.firstBeatMs <= FIRST_BEAT_MAX_MS }?.firstBeatMs
        // Where the ending song is still at full strength: before its fade, and before its outro when it has one.
        val fullEnd = minOf(end - (a?.fadeOutMs?.coerceAtLeast(0) ?: 0L),
            if (outroRule) outroStartMs(ending, end) ?: end else end)
        val placed = when (move) {
            Move.BASS_SWAP, Move.FILTER_OUT, Move.BLEND -> {
                // A blend sits against the end of the music, as today's; the swap and the filter have their middle no
                // later than the song's last moment at full strength (before its last chord rings out).
                var start = if (move == Move.BLEND) end - len else minOf(end - len, end - (a?.fadeOutMs?.coerceAtLeast(0) ?: 0L) - len / 2)
                var inMs = firstSound
                var ratio = 1f
                val r = matchedRatio(a, b)
                if (r != null && a != null && b != null) {
                    start = beatAtOrBefore(a, start.toDouble()).roundToLong()
                    phraseStartMs(a, start)?.takeIf { it >= dur / 2 }?.let { start = it }
                    inMs = b.firstBeatMs
                    ratio = r
                } else if (beat > 0 && a != null && move != Move.BLEND) {
                    start = beatAtOrBefore(a, start.toDouble()).roundToLong()
                }
                var outro = false
                if (outroRule) outroStartMs(ending, start)?.let { start = it; outro = true }
                val pivot = if (move == Move.BASS_SWAP && beat > 0) start + (Math.round(len / 2.0 / beat) * beat).roundToLong() else start + len / 2
                MovePlace(move, start, inMs, len, ratio, beat, pivot, outro)
            }
            Move.ECHO_OUT -> {
                val b8 = if (beat > 0) beat else 500.0
                // The catch: a bar line, the beat before it the one that repeats, while the song is at full strength;
                // with two bars of the file after it.
                var catch = if (a != null && beat > 0) barAtOrBefore(a, fullEnd - beat) else (fullEnd - b8).toDouble()
                while (catch + ECHO_BEATS * b8 > dur && catch > dur / 2) catch -= BAR_BEATS * b8
                MovePlace(move, catch.roundToLong(), firstBeat ?: firstSound, (ECHO_BEATS * b8).roundToLong(), 1f, beat, catch.roundToLong())
            }
            Move.CUT -> {
                val b1 = if (beat > 0) beat else 500.0
                val cut = if (a != null && beat > 0) barAtOrBefore(a, fullEnd.toDouble()) else fullEnd.toDouble()
                val inMs = ((firstBeat ?: firstSound) - b1.toLong()).coerceAtLeast(0)
                MovePlace(move, (cut - b1).roundToLong(), inMs, (2 * b1).roundToLong(), 1f, beat, cut.roundToLong())
            }
        }
        if (placed.startMs < dur / 2 || placed.startMs + (if (move == Move.ECHO_OUT) placed.lengthMs else 0) > dur) return null
        return placed
    }

    /** The bar line (every [BAR_BEATS] from the first beat) at or before [ms]. */
    private fun barAtOrBefore(t: Tempo, ms: Double): Double {
        val phrase = phraseStartMs(t, ms.toLong(), BAR_BEATS)?.toDouble()
        return phrase ?: beatAtOrBefore(t, ms)
    }

    private fun smooth(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

    /**
     * The (incoming, outgoing) volumes [p] (0 … 1) of the way through [move]. A bass swap holds both near full and lets
     * the bass cut do the work, the swap at [pivot]; a filter brings the next song up by half-way and lets the ending one go
     * after it; the echo's song stays up (its echo fades by itself) while the next one is there at once; a blend follows
     * [curve], the listener's; a cut is the DJ cut curve.
     */
    fun gains(move: Move, p: Float, pivot: Float = 0.5f, curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER): Pair<Float, Float> {
        val x = p.coerceIn(0f, 1f)
        return when (move) {
            Move.BASS_SWAP -> {
                val incoming = 0.9f * smooth(x / 0.25f) + 0.1f * smooth((x - pivot) / 0.1f)
                val outgoing = if (x < pivot) 1f - 0.1f * smooth((x - (pivot - 0.15f)) / 0.15f)
                else 0.9f * cos(((x - pivot) / (1 - pivot).coerceAtLeast(0.01f)).coerceIn(0f, 1f) * PI.toFloat() / 2)
                incoming to outgoing
            }
            Move.FILTER_OUT -> sin(minOf(1f, x / 0.5f) * PI.toFloat() / 2) to (if (x < 0.5f) 1f else cos((x - 0.5f) / 0.5f * PI.toFloat() / 2))
            Move.ECHO_OUT -> smooth(x / 0.02f) to (if (x < 0.8f) 1f else 1f - (x - 0.8f) / 0.2f)
            Move.BLEND -> curve.gains(x)
            Move.CUT -> CrossfadeCurve.DJ_CUT.gains(x)
        }
    }

    /**
     * Each song's effects for [place] at [strength] (0 … 1): (the ending song's, the next song's), each in its own time.
     * A blend and a cut have none.
     */
    fun scripts(place: MovePlace, strength: Float = DEFAULT_STRENGTH): Pair<FxScript, FxScript> {
        val s = strength.coerceIn(0f, 1f)
        val start = place.startMs.toDouble(); val end = start + place.lengthMs; val pivot = place.pivotMs.toDouble()
        val inStart = place.inMs.toDouble()
        val none = FxScript(emptyList())
        return when (place.move) {
            Move.BASS_SWAP -> {
                val kill = -(12f + 28f * s)
                val swap = if (place.beatMs > 0) minOf(place.beatMs / 4, 120.0) else 80.0
                FxScript(listOf(
                    FxRamp(FxParam.BASS_DB, pivot - swap / 2, swap, kill),
                    // After the swap the ending song thins out as it goes.
                    FxRamp(FxParam.HIGH_PASS_HZ, pivot, end - pivot, 150f + 450f * s),
                )) to FxScript(listOf(
                    FxRamp(FxParam.BASS_DB, inStart - 50, 0.0, kill),
                    FxRamp(FxParam.BASS_DB, place.nextAt(pivot) - swap / 2 * place.ratio, swap * place.ratio, 0f),
                ))
            }
            Move.FILTER_OUT -> FxScript(listOf(
                FxRamp(FxParam.HIGH_PASS_HZ, start, place.lengthMs.toDouble(), 400f + 2600f * s),
            )) to FxScript(listOf(
                FxRamp(FxParam.LOW_PASS_HZ, inStart - 50, 0.0, 1500f - 1200f * s),
                FxRamp(FxParam.LOW_PASS_HZ, inStart, 0.75 * place.lengthMs * place.ratio, FxParam.LOW_PASS_HZ.neutral),
            ))
            Move.ECHO_OUT -> {
                val beat = if (place.beatMs > 0) place.beatMs else 500.0
                FxScript(listOf(
                    FxRamp(FxParam.ECHO_FEEDBACK, pivot - beat - 20, 0.0, 0.35f + 0.3f * s),
                    FxRamp(FxParam.ECHO_SEND, pivot - beat, 10.0, 1f),
                    FxRamp(FxParam.ECHO_SEND, pivot, 10.0, 0f),
                    FxRamp(FxParam.DRY, pivot, 10.0, 0f),
                ), echoMs = beat) to none
            }
            Move.BLEND, Move.CUT -> none to none
        }
    }
}
