package com.prism.music.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.prism.music.data.prefs.CrossfadeCurve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * What the editor's Listen plays (1.8.8, Mix it yourself; plan-mix.md §4.3), as pure values: the first song from
 * [LEAD_MS] before the transition, the second from where it comes in, to [AFTER_MS] past the overlap. The volumes are
 * [TransitionSound]'s, the very function the crossfade engine plays the saved transition with, and so are the effects
 * ([scripts]).
 */
data class PreviewPlan(val fit: FittedTransition, val style: TransitionStyle, val place: MovePlace, val listener: CrossfadeCurve) {
    /** Where the first song starts playing. */
    val fromMs: Long get() = (fit.startMs - LEAD_MS).coerceAtLeast(0)
    /** Where the second song stops. */
    val untilMs: Long get() = fit.inMs + fit.lengthMs + AFTER_MS
    /** The end of the overlap, in the first song's time. */
    val overlapEndMs: Long get() = fit.startMs + fit.lengthMs

    /** Whether the second song plays with the first at [firstMs]. */
    fun secondPlays(firstMs: Long): Boolean = firstMs >= fit.startMs

    /** The (second song's, first song's) volumes with the first at [firstMs]. */
    fun volumes(firstMs: Long): Pair<Float, Float> = when {
        firstMs < fit.startMs -> 0f to 1f
        fit.cut || firstMs >= overlapEndMs -> 1f to 0f
        else -> TransitionSound.gains(style, (firstMs - fit.startMs).toFloat() / fit.lengthMs, place.pivot, listener)
    }

    /** Each song's effects (the first's, the second's). */
    val scripts: Pair<FxScript, FxScript> get() = TransitionSound.scripts(style, place)

    companion object {
        const val LEAD_MS = 6_000L
        const val AFTER_MS = 6_000L

        /** [t] between songs [fromMs] and [toMs] long; [beatMs]: the first song's beat (0: none clear). */
        fun of(t: Transition, fromMs: Long, toMs: Long?, beatMs: Double, listener: CrossfadeCurve): PreviewPlan {
            val f = Transitions.fit(t, fromMs, toMs)
            return PreviewPlan(f, t.style, TransitionSound.place(t.style, f, beatMs), listener)
        }
    }
}

/**
 * The editor's Listen: its own two small players (never the queue's), each with its own [TransitionFx], built by
 * [build] (the playback service's, with the same sources and sound as the queue's second player). Played by a loop
 * that follows the first song's position: the second starts at the saved spot, the volumes cross by [PreviewPlan].
 * [stop] (or the end) releases both. The queue's own song is the caller's to pause and bring back.
 */
@OptIn(UnstableApi::class)
class MixPreview(
    private val scope: CoroutineScope,
    private val build: (MediaItem, TransitionFx) -> ExoPlayer?,
) {
    enum class Phase { IDLE, LOADING, PLAYING }

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase

    private var first: ExoPlayer? = null
    private var second: ExoPlayer? = null
    private var job: Job? = null

    /** Plays [plan] for [from] into [to]; false when no player could be made. */
    fun play(from: MediaItem, to: MediaItem, plan: PreviewPlan): Boolean {
        stop()
        val fxA = TransitionFx()
        val fxB = TransitionFx()
        val a = build(from, fxA) ?: return false
        val b = build(to, fxB) ?: run { a.release(); return false }
        first = a; second = b
        a.volume = 1f
        a.setMediaItem(from, plan.fromMs)
        a.prepare()
        a.playWhenReady = true
        b.volume = 0f
        b.setMediaItem(to, plan.fit.inMs)
        b.prepare()
        b.playWhenReady = false
        val (sa, sb) = plan.scripts
        if (sa.ramps.isNotEmpty()) fxA.arm(sa, plan.fromMs)
        if (sb.ramps.isNotEmpty()) fxB.arm(sb, plan.fit.inMs)
        _phase.value = Phase.LOADING
        // On the main thread, where the players were made (ExoPlayer is used from its own thread only).
        job = scope.launch(kotlinx.coroutines.Dispatchers.Main) { follow(a, b, plan) }
        return true
    }

    private suspend fun follow(a: ExoPlayer, b: ExoPlayer, plan: PreviewPlan) {
        val asked = SystemClock.elapsedRealtime()
        while (currentCoroutineContext().isActive && !a.isPlaying) {
            if (SystemClock.elapsedRealtime() - asked > LOAD_LIMIT_MS || a.playerError != null || a.playbackState == Player.STATE_ENDED) { stop(); return }
            delay(30)
        }
        _phase.value = Phase.PLAYING
        var firstGone = false
        while (currentCoroutineContext().isActive) {
            if (!firstGone) {
                val pos = a.currentPosition
                if (plan.secondPlays(pos) && !b.playWhenReady) b.playWhenReady = true
                val (incoming, outgoing) = plan.volumes(pos)
                b.volume = incoming
                a.volume = outgoing
                if (pos >= plan.overlapEndMs || (plan.fit.cut && plan.secondPlays(pos)) || a.playbackState == Player.STATE_ENDED) {
                    b.playWhenReady = true
                    b.volume = 1f
                    a.release()
                    first = null
                    firstGone = true
                }
            }
            if (b.playerError != null || b.playbackState == Player.STATE_ENDED || (b.playWhenReady && b.currentPosition >= plan.untilMs)) break
            delay(STEP_MS)
        }
        stop()
    }

    /** Stops what plays, releasing both players. */
    fun stop() {
        val j = job
        job = null
        first?.let { runCatching { it.release() } }
        second?.let { runCatching { it.release() } }
        first = null
        second = null
        _phase.value = Phase.IDLE
        j?.cancel()
    }

    companion object {
        private const val STEP_MS = 20L
        /** The first song not playing this long after Listen (no network, say): nothing plays. */
        private const val LOAD_LIMIT_MS = 15_000L
    }
}
