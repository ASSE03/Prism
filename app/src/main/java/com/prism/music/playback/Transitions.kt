package com.prism.music.playback

import com.prism.music.data.prefs.CrossfadeCurve

/**
 * How a saved transition sounds (1.8.8, Mix it yourself; plan-mix.md §4.1): "Automatic", one of the volume curves, or one
 * of Automix's moves ([MixMoves]). [key] is what the database keeps ([com.prism.music.data.db.TransitionEntity.style]).
 *
 * Automix's own Blend and Cut on the beat are here as the curves they are (Smooth, Straight, Gentle; DJ cut), and a
 * length of 0 is a plain cut: the same sound twice in the list would only be confusing.
 */
enum class TransitionStyle(val key: String, val curve: CrossfadeCurve? = null, val move: Move? = null) {
    AUTOMATIC("auto"),
    SMOOTH("smooth", curve = CrossfadeCurve.EQUAL_POWER),
    STRAIGHT("straight", curve = CrossfadeCurve.LINEAR),
    GENTLE("gentle", curve = CrossfadeCurve.S_CURVE),
    DJ_CUT("dj_cut", curve = CrossfadeCurve.DJ_CUT),
    BASS_SWAP("bass_swap", move = Move.BASS_SWAP),
    FILTER_OUT("filter_out", move = Move.FILTER_OUT),
    ECHO_OUT("echo_out", move = Move.ECHO_OUT),
    ;

    companion object {
        /**
         * The editor's list, in its order: the ONE place it is kept. A move the owner strikes after the listening round
         * (plan-mix.md §3.4) is one line out of here; transitions already saved with it then play as Automatic ([of]).
         */
        val offered: List<TransitionStyle> = listOf(
            AUTOMATIC,
            SMOOTH, STRAIGHT, GENTLE, DJ_CUT,
            BASS_SWAP,
            FILTER_OUT,
            ECHO_OUT,
        )

        /** The style stored as [key]; Automatic for one no longer offered (or unknown). */
        fun of(key: String?): TransitionStyle = offered.firstOrNull { it.key == key } ?: AUTOMATIC
    }
}

/**
 * A transition the listener made for two songs (plan-mix.md §4.1): the two start to overlap [outMs] into the first song,
 * the second comes in [inMs] into itself, they overlap for [lengthMs] (0 is a cut), in [style]. [snap]: the handles
 * were kept on the beat (the editor's; the engine plays the points as they are).
 */
data class Transition(
    val outMs: Long,
    val inMs: Long,
    val lengthMs: Long,
    val style: TransitionStyle = TransitionStyle.AUTOMATIC,
    val snap: Boolean = false,
)

/**
 * A [Transition] as it can play between two songs of these lengths ([Transitions.fit]): where the overlap starts in the
 * first song ([startMs]), where the second comes in ([inMs]), and for how long ([lengthMs]). [shortened]: the overlap
 * had to be shorter than the one asked for (the editor says so rather than saving it silently different).
 */
data class FittedTransition(val startMs: Long, val inMs: Long, val lengthMs: Long, val shortened: Boolean) {
    val cut: Boolean get() = lengthMs == 0L
}

object Transitions {
    /** The longest overlap the editor offers (plan-mix.md §4.1). */
    const val MAX_LENGTH_MS = 30_000L
    /** How much of each song the editor shows: the first one's last 90 s, the second's first 90 s. */
    const val WINDOW_MS = 90_000L
    /** At most this share of either song overlaps (the cap that protects every blend, as Mix's own: a third). */
    const val MAX_SHARE = 3L
    /** A transition leaves at least this much of each song's file around it. */
    private const val SPARE_MS = 1_000L

    /**
     * [t] between a first song [fromMs] long and a second [toMs] long (null: not known yet). The overlap is never longer
     * than [MAX_LENGTH_MS], a third of either song, or what is left of either song from its point on; the points stay in
     * their songs.
     */
    fun fit(t: Transition, fromMs: Long, toMs: Long?): FittedTransition {
        val from = fromMs.coerceAtLeast(3 * SPARE_MS)
        val to = toMs?.takeIf { it > 0 }
        val asked = t.lengthMs.coerceIn(0, MAX_LENGTH_MS)
        var len = minOf(asked, from / MAX_SHARE)
        if (to != null) len = minOf(len, to / MAX_SHARE)
        val start = t.outMs.coerceIn(0, from - SPARE_MS)
        len = minOf(len, from - start)
        val inMs = if (to == null) t.inMs.coerceAtLeast(0) else t.inMs.coerceIn(0, (to - SPARE_MS).coerceAtLeast(0))
        if (to != null) len = minOf(len, (to - inMs).coerceAtLeast(0))
        len = len.coerceAtLeast(0)
        return FittedTransition(start, inMs, len, shortened = len < asked)
    }

    /** The plain transition a pair gets before the listener moves anything: today's automatic blend's points. */
    fun automatic(from: Tempo?, to: Tempo?, fromMs: Long): Transition {
        val len = MixEngine.autoLengthMs(from, to)
        val end = from?.lastSoundMs?.takeIf { it in fromMs / 2..fromMs } ?: fromMs
        return Transition(
            outMs = (end - len).coerceAtLeast(0),
            inMs = (to?.firstSoundMs ?: 0L).coerceIn(0, 10_000),
            lengthMs = len,
            snap = from?.steady == true && to?.steady == true,
        )
    }

    /**
     * The beat of [t] nearest [ms], or [ms] when it has no clear beat. On its ending's grid ([Tempo.outroBeatMs], where a
     * song is left), or with [fromStart] on its start's ([Tempo.firstBeatMs], where a song comes in): the scan's tempo
     * is a hair off over minutes, so each end is counted from its own beat.
     */
    fun nearestBeat(t: Tempo?, ms: Long, fromStart: Boolean = false): Long {
        if (t == null || !t.steady) return ms
        val beat = t.periodMs
        val anchor = if (fromStart) t.firstBeatMs else t.outroBeatMs
        return (anchor + Math.round((ms - anchor) / beat) * beat).toLong()
    }

    /** [t]'s beats inside [fromMs]..[toMs] (the editor's ticks), on the grid [nearestBeat] uses; empty without a clear beat. */
    fun beats(t: Tempo?, fromMs: Long, toMs: Long, fromStart: Boolean = false): List<Long> {
        if (t == null || !t.steady || toMs <= fromMs) return emptyList()
        val beat = t.periodMs
        val anchor = if (fromStart) t.firstBeatMs else t.outroBeatMs
        var b = anchor + Math.ceil((fromMs - anchor) / beat) * beat
        val out = ArrayList<Long>()
        while (b <= toMs && out.size < 1000) { if (b >= 0) out += b.toLong(); b += beat }
        return out
    }
}

/**
 * How a saved transition sounds, for the real engine ([MixEngine]) and the editor's Listen ([MixPreview]) alike: one
 * shared function, so what is heard in the editor is what plays later (plan-mix.md §4.3). The volume laws and the
 * effects are [MixMoves]'.
 */
object TransitionSound {
    /**
     * Whether a style whose sound IS its effects (bass swap, filter out, echo out) plays as made: on since B5 put
     * [TransitionFx] in both of the queue's players ([MixEngine] arms them), as the editor's Listen has its own two.
     * Off, such a style would play with the blend's volume law (the listener's curve) and no effects, in the queue and
     * the editor alike. Automatic stays the listener's curve at the saved points.
     */
    const val EFFECTS_PLAY = true

    /** The moves that are nothing without their effects. */
    private val FX_MOVES = setOf(Move.BASS_SWAP, Move.FILTER_OUT, Move.ECHO_OUT)

    /** Whether [style]'s own sound plays (false: it falls back to the blend's law until [EFFECTS_PLAY]). */
    fun playsAsMade(style: TransitionStyle): Boolean = style.move == null || style.move !in FX_MOVES || EFFECTS_PLAY

    /**
     * The (incoming, outgoing) volumes [p] (0 … 1) of the way through a transition in [style]; [pivot]: the move's moment
     * as a share of it ([MovePlace.pivot]); [listener]: the listener's own curve (Settings → Crossfade → How they cross).
     */
    fun gains(style: TransitionStyle, p: Float, pivot: Float = 0.5f, listener: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER): Pair<Float, Float> {
        style.curve?.let { return it.gains(p) }
        val move = style.move
        return if (move != null && playsAsMade(style)) MixMoves.gains(move, p, pivot, listener) else MixMoves.gains(Move.BLEND, p, curve = listener)
    }

    /** Where [style]'s move sits for [fit] ([MixMoves.scripts]' input): the pivot on the first song's beat when it has one. */
    fun place(style: TransitionStyle, fit: FittedTransition, beatMs: Double = 0.0): MovePlace {
        val move = style.move ?: if (fit.cut) Move.CUT else Move.BLEND
        val pivot = if (move == Move.BASS_SWAP && beatMs > 0) fit.startMs + (Math.round(fit.lengthMs / 2.0 / beatMs) * beatMs).toLong()
            else fit.startMs + fit.lengthMs / 2
        return MovePlace(move, fit.startMs, fit.inMs, fit.lengthMs.coerceAtLeast(1), 1f, beatMs, pivot)
    }

    /** Each song's effects for [style] at [place] (the first song's, the second's); none for a curve, or with [EFFECTS_PLAY] off. */
    fun scripts(style: TransitionStyle, place: MovePlace): Pair<FxScript, FxScript> {
        val none = FxScript(emptyList())
        if (style.move == null || !playsAsMade(style) || style.move !in FX_MOVES) return none to none
        return MixMoves.scripts(place)
    }
}
