package com.prism.music.playback

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.prism.music.data.prefs.CrossfadeCurve

/**
 * 1.8.8 "Blend when I skip" (#481, plan-mix §2): Next, Previous and a tap in the queue no longer cut. The song playing
 * carries on while a second player (the head, made like Mix's tail) loads the chosen song, then the two cross over
 * 1.5 s along the listener's curve; the main player, silent by then, moves to the chosen song (a skip, as any other:
 * the old song counts as skipped) and is brought in step with the head by speed alone, then takes over and the head goes.
 * The decisions are [SkipFadeRules]'; this does what they say to the two players. Driven from [MixEngine]'s loop.
 *
 * Every skip passes through [press]: the session player's ([LyricsOverlayPlayer]: notification, lock screen, headset,
 * car, widgets, the web and TV remotes) and Prism's own buttons ([PlayerConnection]). With the switch off it says no at
 * once and nothing else runs: the skip is today's.
 */
@OptIn(UnstableApi::class)
class SkipFade(
    private val main: ExoPlayer,
    /** A second player for [MediaItem]: the main one's sources and effects, the chosen song's own normalization, no audio focus. */
    private val buildHead: (MediaItem) -> ExoPlayer,
    /** Where a song's music starts (its scan), as blends start it. */
    private val firstSoundMs: (String) -> Long,
    /** "Blend when I skip" is on. */
    private val on: () -> Boolean,
    /** Performance mode or battery saver: a 200 ms fade-out and the cut instead. */
    private val light: () -> Boolean,
    /** These two may blend: mayBlend without its album rule (a skip inside an album isn't gapless listening). */
    private val allowed: (from: MediaItem, to: MediaItem) -> Boolean,
    /** The chosen song was warmed ahead of time (the next one) or is on the phone: it has its own learned lead. */
    private val warm: (item: MediaItem, isNext: Boolean) -> Boolean,
    /** The music plays elsewhere or with others (casting, Listen together): no fade-out for a new queue either. */
    private val held: () -> Boolean = { false },
    /** The listener's own speed: the head plays at it, and the nudges are shares of it. */
    private val speed: () -> Float = { 1f },
    /** The pitch the music plays at (Sing's key, the listener's own): speed changes never move it. */
    private val pitch: () -> Float = { 1f },
    curve: () -> CrossfadeCurve = { CrossfadeCurve.EQUAL_POWER },
    /** What the player screen shows of it: [MixState.blend] from the song heard into the chosen one. */
    private val state: MixState = MixState(),
    /** A run is over and the main player back to normal: the listener's own speed can return. */
    private val onSettled: () -> Unit = {},
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wall: () -> Long = { System.currentTimeMillis() },
    private val note: (String) -> Unit = { Log.i(TAG, it); AudioDiagnostics.log("mix", it) },
) {
    val rules = SkipFadeRules(curve)

    /** Under way: [MixEngine] makes no blend of its own meanwhile, and the Speed button waits. */
    val active: Boolean get() = rules.active

    /** The second player is alive (tests: none is ever left once a run is over). */
    val headAlive: Boolean get() = head != null

    private var head: ExoPlayer? = null
    private var fromId: String? = null
    private var toId: String? = null
    private var kind = SkipKind.NEXT
    private var pressWall = 0L
    /** When the head's sound reached the output (wall clock, Media3's own reading), 0 until it has. */
    private var headPlayoutWall = 0L
    /** What waits on a short fade-out (the cut, a new queue). */
    private var then: (() -> Unit)? = null
    /** Seeks of the run's own still to be told of ([discontinuity]): anything else is the listener's. */
    private var ownSeeks = 0
    /** [blend] set by this, so only this clears it (an end-of-song blend sets its own). */
    private var ownsBlend = false

    // Set by MixEngine (attach): its loop, and the end-of-song blend it may be making.
    internal var wake: () -> Unit = {}
    internal var mixHeard: () -> Boolean = { false }
    internal var dropMix: () -> Unit = {}

    /**
     * A skip to [index] (the place in the queue as the asker worked it out, [kind] saying how). True when it's taken
     * over here (nothing more is to be done with it), false to let it go through as today's skip.
     */
    fun press(kind: SkipKind, index: Int): Boolean {
        if (!rules.active && !on()) return false
        val target = aimed(kind, index)
        val count = main.mediaItemCount
        val current = main.currentMediaItemIndex
        val valid = target in 0 until count
        val from = main.currentMediaItem
        val to = if (valid) main.getMediaItemAt(target) else null
        val ask = SkipAsk(
            on = on(), underWay = rules.active, mixHeard = mixHeard(), playing = main.isPlaying,
            sameSong = !valid || target == current || from == null || to?.mediaId == from.mediaId,
            allowed = from != null && to != null && allowed(from, to),
            light = light(),
        )
        return when (SkipFadeRules.choose(ask)) {
            SkipChoice.PLAIN -> false
            // 1.8.8 review: not aborted here (that let the ending song go at once and put the next at full before the skip's
            // seek, a hard cut). The plain skip goes through and the engine's own skip fade takes the blend down over a
            // moment, exactly as with the switch off.
            SkipChoice.STOP_MIX_THEN_PLAIN -> false
            SkipChoice.CUT -> {
                val newQueue = rules.phase == SkipFadeRules.Phase.FADE_OUT && rules.target < 0
                when {
                    // A new queue waits on its fade-out: it comes first, then this skip on it, as today's.
                    newQueue -> { apply(rules.cut(read())); plainAgain(kind, index) }
                    // A skip to nowhere (Next on the last song) does nothing today: the run goes on.
                    !valid -> Unit
                    else -> {
                        // Straight to the newest choice, before the volume comes back (no blip of what it leaves);
                        // a short fade-out's own skip is overtaken by this one.
                        then = null
                        own { main.seekToDefaultPosition(target) }
                        apply(rules.cut(read()))
                    }
                }
                true
            }
            SkipChoice.FADE_OUT -> {
                dropMix()
                begin(kind, from, to)
                rules.fadeOut(clock(), target)
                then = { if (target in 0 until main.mediaItemCount) main.seekToDefaultPosition(target) }
                wake()
                true
            }
            SkipChoice.BLEND -> {
                dropMix()
                val item = to!!
                begin(kind, from, item)
                val h = buildHead(item)
                h.volume = 0f
                h.addAnalyticsListener(object : AnalyticsListener {
                    override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                        if (headPlayoutWall == 0L && head === h) headPlayoutWall = playoutStartSystemTimeMs
                    }
                })
                h.setMediaItem(item.buildUpon().build())
                h.seekTo(firstSoundMs(item.mediaId).coerceIn(0L, 10_000L))
                h.playbackParameters = PlaybackParameters(speed(), pitch())
                h.playWhenReady = true
                h.prepare()
                head = h
                rules.start(clock(), target, warm(item, target == main.nextMediaItemIndex))
                state.blend.value = BlendNow(from!!.mediaId, item.mediaId, 0f)
                ownsBlend = true
                wake()
                true
            }
        }
    }

    /**
     * A new queue is about to replace what plays ([action]): with the switch on and music playing, the song fades out over
     * 200 ms first ([SkipFadeRules.FADE_OUT_MS]); otherwise (and with the switch off) [action] at once.
     */
    fun fadeOutThen(action: () -> Unit) {
        if (rules.active) {
            // The new queue first, while the run still holds the volume down; what the run waited on is overtaken.
            then = null
            try { action() } finally { apply(rules.cut(read())) }
            return
        }
        if (!on() || !main.isPlaying || mixHeard() || held()) { action(); return }
        dropMix()
        begin(SkipKind.JUMP, main.currentMediaItem, null)
        rules.fadeOut(clock())
        then = action
        wake()
    }

    /** One step of a run ([MixEngine]'s loop); how long to wait before the next. */
    fun tick(): Long {
        if (!rules.active) return 1000
        return runCatching {
            if (!on()) apply(rules.finishNow(read(), "Blend when I skip turned off")) else apply(rules.step(read()))
        }.getOrElse { e ->
            Log.w(TAG, "skip-blend step failed", e)
            note("skip-blend step failed: $e")
            forceNormal("error")
            100L
        }
    }

    /**
     * The main player moved in the song ([Player.Listener.onPositionDiscontinuity]): a seek by the listener finishes the
     * run at once on the new song; the run's own seeks are told apart by count.
     */
    fun discontinuity(reason: Int) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK || !rules.active) return
        if (ownSeeks > 0) { ownSeeks--; return }
        apply(rules.seeked(read()))
    }

    /** Ends a run at once on the new song (casting starts, the service goes). */
    fun stop(why: String) {
        if (rules.active) runCatching { apply(rules.finishNow(read(), why)) }.onFailure { forceNormal(why) }
    }

    fun release() = stop("released")

    /** The place a skip goes to: before the main player has moved, a second Next or Previous counts from the chosen song. */
    private fun aimed(kind: SkipKind, index: Int): Int {
        if (!rules.pending || kind == SkipKind.JUMP) return index
        val from = rules.target
        val tl = main.currentTimeline
        if (from !in 0 until tl.windowCount) return index
        // As Player's own next and previous: repeat one moves on as if it were off.
        val repeat = main.repeatMode.let { if (it == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else it }
        val shuffle = main.shuffleModeEnabled
        val to = if (kind == SkipKind.NEXT) tl.getNextWindowIndex(from, repeat, shuffle) else tl.getPreviousWindowIndex(from, repeat, shuffle)
        return if (to == C.INDEX_UNSET) from else to
    }

    /** After a new queue's fade-out was cut short by a skip: that skip, on the new queue, as today's. */
    private fun plainAgain(kind: SkipKind, index: Int) {
        when (kind) {
            SkipKind.NEXT -> main.seekToNext()
            SkipKind.PREVIOUS -> main.seekToPrevious()
            SkipKind.JUMP -> if (index in 0 until main.mediaItemCount) main.seekToDefaultPosition(index)
        }
    }

    private fun begin(kind: SkipKind, from: MediaItem?, to: MediaItem?) {
        this.kind = kind
        fromId = from?.mediaId
        toId = to?.mediaId
        pressWall = wall()
        headPlayoutWall = 0L
        ownSeeks = 0
        nudged = false
    }

    private fun read(): SkipNow {
        val h = head
        return SkipNow(
            now = clock(),
            mainPlaying = main.isPlaying,
            mainWants = main.playWhenReady,
            mainIndex = main.currentMediaItemIndex,
            mainPos = main.currentPosition,
            headPlaying = h?.isPlaying == true,
            headPos = h?.currentPosition ?: 0L,
            // Prepared, a player is only idle again after an error.
            headFailed = h == null || h.playbackState == Player.STATE_IDLE,
            speed = speed().takeIf { it > 0f } ?: 1f,
        )
    }

    private fun apply(s: SkipStep): Long {
        val h = head
        s.headPlay?.let { if (h != null && h.playWhenReady != it) h.playWhenReady = it }
        val t = s.target
        s.moveMs?.let { ms -> if (t in 0 until main.mediaItemCount) own { main.seekTo(t, ms.coerceAtLeast(0L)) } }
        s.seekMs?.let { ms -> own { main.seekTo(ms.coerceAtLeast(0L)) } }
        if (s.plain && t in 0 until main.mediaItemCount) own { main.seekToDefaultPosition(t) }
        // What waited (a new queue, the cut) has nobody to tell if it fails: noted, and the run still ends normally.
        if (s.then) { val f = then; then = null; runThen(f) }
        s.nudge?.let { setSpeed(it) }
        s.headVolume?.let { v -> h?.let { if (it.volume != v) it.volume = v.coerceIn(0f, 1f) } }
        s.mainVolume?.let { v -> if (main.volume != v) main.volume = v.coerceIn(0f, 1f) }
        if (ownsBlend) {
            val shown = rules.phase == SkipFadeRules.Phase.LOADING || rules.phase == SkipFadeRules.Phase.FADING
            val f = fromId; val to = toId
            if (shown && f != null && to != null) state.blend.value = BlendNow(f, to, rules.progress)
            else { if (state.blend.value?.let { it.fromId == f && it.toId == to } == true) state.blend.value = null; ownsBlend = false }
        }
        s.end?.let { finished(it) }
        return s.waitMs
    }

    /** A seek by the run itself: [discontinuity] is told of it, and doesn't take it for the listener's. */
    private inline fun own(seek: () -> Unit) {
        // Only while the run is on (a run's last step has already reset it): after that a discontinuity isn't looked at.
        if (rules.active) ownSeeks++
        seek()
    }

    private fun runThen(f: (() -> Unit)?) {
        if (f == null) return
        runCatching(f).onFailure { e ->
            Log.w(TAG, "skip-blend: what waited on the fade-out failed", e)
            note("skip-blend: what waited on the fade-out failed: $e")
        }
    }

    /** The main player at [share] of the listener's speed; back to theirs only if a nudge moved it (a new queue may start on an episode). */
    private fun setSpeed(share: Float) {
        if (share == 1f && !nudged) return
        nudged = share != 1f
        main.playbackParameters = PlaybackParameters(speed() * share, pitch())
    }

    /** The main player's speed was nudged off the listener's. */
    private var nudged = false

    private fun finished(e: SkipEnd) {
        head?.let { runCatching { it.release() } }
        head = null
        then = null
        ownSeeks = 0
        if (main.volume != 1f) main.volume = 1f
        setSpeed(1f)
        if (ownsBlend) {
            if (state.blend.value?.let { it.fromId == fromId && it.toId == toId } == true) state.blend.value = null
            ownsBlend = false
        }
        // What was heard first of the chosen song: the later of the fade starting and its sound reaching the output.
        val heard = e.heardMs?.let { h -> maxOf(h, headPlayoutWall.takeIf { it != 0L }?.let { it - pressWall } ?: 0L) }
        note(skipLine(fromId, toId, kind, rules.leadWarmMs, rules.leadColdMs, e, heard))
        onSettled()
    }

    /** Whatever state it's in, back to normal at once: no head, main at full volume and the listener's speed. */
    private fun forceNormal(why: String) {
        // How the two stand, if they can still be read (the head's place, for a move as a normal finish makes it).
        val now = runCatching { read() }.getOrNull() ?: SkipNow(clock(), false, false, -1, 0L, headFailed = true)
        runCatching { head?.release() }
        head = null
        // A new queue (or the cut) that waited still happens: the listener asked for it.
        val f = then
        then = null
        ownSeeks = 0
        runThen(f)
        runCatching { if (main.volume != 1f) main.volume = 1f; setSpeed(1f) }
        if (ownsBlend) { state.blend.value = null; ownsBlend = false }
        val r = rules.finishNow(now, why)
        // 1.8.8 review: not moved yet (an error loading or fading), the skip still lands on the chosen song, as a normal
        // finish would: it was only logged, and the main player stayed on the old song.
        val t = r.target
        runCatching {
            if (t in 0 until main.mediaItemCount) {
                val ms = r.moveMs
                if (ms != null) main.seekTo(t, ms.coerceAtLeast(0L)) else if (r.plain) main.seekToDefaultPosition(t)
            }
        }.onFailure { e -> note("skip-blend: the move after an error failed: $e") }
        r.end?.let { note(skipLine(fromId, toId, kind, rules.leadWarmMs, rules.leadColdMs, it, null)) }
        onSettled()
    }

    companion object {
        private const val TAG = "PrismMix"

        /** The run's one line in Share diagnostics (tag `mix`): press → heard, the move, the gap at the hand-over, how it ended. */
        fun skipLine(from: String?, to: String?, kind: SkipKind, leadWarm: Long, leadCold: Long, e: SkipEnd, heardMs: Long?): String =
            "skip-blend ${from ?: "?"} -> ${to ?: "?"} (${kind.name.lowercase()}): " +
                "heard after ${heardMs?.let { "$it ms" } ?: "-"}, moved at ${e.movedMs?.let { "$it ms" } ?: "-"}, " +
                "${e.offsetMs?.let { "$it ms apart at the hand-over" } ?: "not measured at the hand-over"}, " +
                "${if (e.clean) "ok" else "not clean"}: ${e.how}; all normal after ${e.totalMs} ms (leads $leadWarm / $leadCold ms)"
    }
}
