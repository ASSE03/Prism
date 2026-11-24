package com.prism.music.playback

import com.prism.music.data.prefs.CrossfadeCurve
import kotlin.math.abs

/** How a skip asked for its song (1.8.8, "Blend when I skip"): Next, Previous (or the song before), or a place in the queue. */
enum class SkipKind { NEXT, PREVIOUS, JUMP }

/** What [SkipFadeRules.choose] says to do with a skip. */
enum class SkipChoice {
    /** As today: the main player moves at once. */
    PLAIN,
    /** A skip-blend is under way: it ends, and this skip is a plain cut to its song (skips don't stack). */
    CUT,
    /**
     * An end-of-song blend is being heard: this skip is today's (plan-mix §2.3.6), and the engine's own skip fade takes the
     * blend down over a moment, as with the switch off.
     */
    STOP_MIX_THEN_PLAIN,
    /** Performance mode or battery saver: the song fades out over [SkipFadeRules.FADE_OUT_MS], then the cut. No second player. */
    FADE_OUT,
    /** Fade first, catch up after (plan-mix §2.3, §2.3a). */
    BLEND,
}

/** What a skip is asked under: each a reason for [SkipFadeRules.choose]. */
data class SkipAsk(
    /** "Blend when I skip" is on. */
    val on: Boolean,
    /** A skip-blend (or a skip's short fade-out) is under way. */
    val underWay: Boolean = false,
    /** An end-of-song blend is being heard (MixEngine blending or settling). */
    val mixHeard: Boolean = false,
    /** The music is playing (not paused, not loading). */
    val playing: Boolean = true,
    /** The skip lands on the song playing (Previous starting it over, a tap on it): nothing to blend. */
    val sameSong: Boolean = false,
    /** May these two blend at all: no casting, Listen together, Sing, video mode, live or podcasts. (An album may.) */
    val allowed: Boolean = true,
    /** Performance mode or battery saver: no second player. */
    val light: Boolean = false,
)

/** One look at the two players: what [SkipFadeRules.step] goes by. Positions in the songs' own ms. */
data class SkipNow(
    val now: Long,
    /** The main player is playing (not paused, not loading). */
    val mainPlaying: Boolean,
    /** The main player wants to play (it isn't paused): the head follows it. */
    val mainWants: Boolean,
    val mainIndex: Int,
    val mainPos: Long,
    /** The second player (the head, on the chosen song) is playing. */
    val headPlaying: Boolean = false,
    val headPos: Long = 0L,
    /** The head stopped with an error (or there is none). */
    val headFailed: Boolean = false,
    /** The listener's own playback speed: positions move this fast, and the nudges are shares of it. */
    val speed: Float = 1f,
)

/** What to do now; null and false fields leave things as they are. */
class SkipStep {
    /** The chosen song's place in the queue, as it was when this step was worked out. */
    var target = -1
    var mainVolume: Float? = null
    var headVolume: Float? = null
    /** The head plays (or is paused) with the main player. */
    var headPlay: Boolean? = null
    /** The move: the main player to the chosen song, at this place in it. */
    var moveMs: Long? = null
    /** A plain skip: the main player to the chosen song's start, now. */
    var plain = false
    /** A seek within the chosen song (the catch-up's last resort, when far off). */
    var seekMs: Long? = null
    /** The main player's speed as a share of the listener's (1 = theirs again). */
    var nudge: Float? = null
    /** The short fade-out is over: what waits on it (the cut, a new queue) happens now. */
    var then = false
    /** It's over: the head goes, the main player is at full volume and the listener's speed. */
    var end: SkipEnd? = null
    var waitMs = 10L
}

/** How a skip-blend went, for its one line in Share diagnostics. Times are from the press. */
data class SkipEnd(
    val how: String,
    /** Handed over in step (or faded out and cut as it should). */
    val clean: Boolean,
    /** When the chosen song began to be heard (its fade started); null when it never was. */
    val heardMs: Long?,
    /** When the main player moved to the chosen song (title, notification, history); null when it didn't by the blend. */
    val movedMs: Long?,
    /** How far apart the two were at the hand-over (ms, main ahead positive); null when not measured. */
    val offsetMs: Long?,
    val totalMs: Long,
)

/**
 * 1.8.8 "Blend when I skip" (plan-mix §2.3, with the A16 trial's §2.3a), as a pure state machine on a clock it's given, so
 * every rule is a unit test. [SkipFade] reads the players into a [SkipNow] each step and does what the [SkipStep] says.
 *
 * - LOADING: a second player (the head) loads the chosen song at its first sound; the old song plays on. 4 s at most, then
 *   a plain skip ([LOAD_LIMIT_MS]).
 * - FADING: the head comes up and the main player goes down along the listener's crossfade curve, over [FADE_MS] of music
 *   (a pause holds it). At its end the main player, now silent, moves to the chosen song a learned lead ahead of the head
 *   ([moveMs]): the session, history, lyrics and notification follow from here, as after any skip.
 * - CATCHING: the silent main player is measured against the head over [WINDOW_MS] windows and brought in step by playing
 *   5 to 20 % fast or slow for as long as the gap needs (never by seeks, which on the A16 took 0.24 to 1 s to restart and
 *   overshot), a seek only when over [SEEK_OVER_MS] off. Leads are learned apart for warmed (or downloaded) and cold songs.
 * - HANDOVER: within [IN_STEP_MS] (Mix's own limit for its quick cross-over), head → main over [HANDOVER_MS]; after
 *   [MAX_TRIES] tries, over [SLOW_HANDOVER_MS] within [LAST_CHANCE_MS], else a [DIP_MS] dip. Then the head goes.
 * - FADE_OUT: Performance mode, battery saver and a new queue: the song fades out over [FADE_OUT_MS], then the cut.
 *
 * Whatever happens, [LIMIT_MS] after the press the main player is on the chosen song at full volume and the head is gone
 * ([finishNow]); a second skip ends a run at once ([cut]); a seek by the listener finishes it on the new song.
 */
class SkipFadeRules(private val curve: () -> CrossfadeCurve = { CrossfadeCurve.EQUAL_POWER }) {
    enum class Phase { IDLE, FADE_OUT, LOADING, FADING, CATCHING, HANDOVER }

    var phase = Phase.IDLE
        private set
    /** The chosen song's place in the queue (-1 for a new queue's fade-out). */
    var target = -1
        private set
    /** The chosen song was warmed ahead of time (the next song) or is on the phone. */
    var warm = false
        private set
    /** How far in the fade is (0 … 1), for the player's cover and title. */
    var progress = 0f
        private set

    /** Under way: the main player and the head are the run's until it ends. */
    val active: Boolean get() = phase != Phase.IDLE

    /** The main player hasn't moved to the chosen song yet: a second Next counts from the chosen song, not the one heard. */
    val pending: Boolean get() = phase == Phase.LOADING || phase == Phase.FADING || (phase == Phase.FADE_OUT && target >= 0)

    /** How far ahead of the head the main player is sent at the move, in real ms (the restart it takes), warmed and cold. */
    var leadWarmMs = START_LEAD_WARM_MS
        private set
    var leadColdMs = START_LEAD_COLD_MS
        private set

    private var pressAt = 0L
    private var lastAt = 0L
    private var fadedMs = 0L
    private var heardAt = 0L
    private var movedAt = 0L
    private var settleUntil = 0L
    private var nudgeUntil = 0L
    private var handAt = 0L
    private var handMs = HANDOVER_MS
    private var handHow = ""
    private var handClean = true
    private var tries = 0
    private var seeks = 0
    private var windows = 0
    private var windowFrom = 0L
    private var mainLead = Long.MIN_VALUE
    private var headLead = Long.MIN_VALUE
    /** The lead the last move or seek was sent with (real ms), corrected by what the first window then read. */
    private var seekLeadMs = 0L
    private var lastWasMove = false
    private var lastErr: Long? = null
    private var offsetAtHand: Long? = null

    /** Starts a skip-blend to [target] at [now]. */
    fun start(now: Long, target: Int, warm: Boolean) {
        reset()
        phase = Phase.LOADING
        this.target = target
        this.warm = warm
        pressAt = now
        lastAt = now
    }

    /** Starts a short fade-out at [now]: before a cut to [target], or (-1) before a new queue. */
    fun fadeOut(now: Long, target: Int = -1) {
        reset()
        phase = Phase.FADE_OUT
        this.target = target
        pressAt = now
        lastAt = now
    }

    private fun reset() {
        phase = Phase.IDLE
        target = -1; warm = false; progress = 0f
        pressAt = 0L; lastAt = 0L; fadedMs = 0L; heardAt = 0L; movedAt = 0L; settleUntil = 0L; nudgeUntil = 0L
        handAt = 0L; handMs = HANDOVER_MS; handHow = ""; handClean = true
        tries = 0; seeks = 0; windows = 0; windowFrom = 0L; mainLead = Long.MIN_VALUE; headLead = Long.MIN_VALUE
        seekLeadMs = 0L; lastWasMove = false; lastErr = null; offsetAtHand = null
    }

    /** One step at [r]. */
    fun step(r: SkipNow): SkipStep {
        val s = SkipStep()
        s.target = target
        if (phase == Phase.IDLE) { s.waitMs = 1000; return s }
        val age = r.now - pressAt
        // Whatever happened, 8 s after the press all is normal.
        if (age >= LIMIT_MS) return finishNow(r, "the 8 s limit (${phase.name.lowercase()})", s)
        when (phase) {
            Phase.IDLE -> Unit
            Phase.FADE_OUT -> {
                val x = (age.toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
                if (x >= 1f) {
                    s.then = true
                    s.mainVolume = 1f
                    return end(s, r, "faded out and cut", clean = true)
                }
                s.mainVolume = (1f - x) * (1f - x)
                s.waitMs = 10
            }
            Phase.LOADING -> {
                s.headPlay = r.mainWants
                when {
                    r.headFailed -> { s.plain = true; s.mainVolume = 1f; return end(s, r, "the chosen song didn't load: a plain skip", clean = false) }
                    age >= LOAD_LIMIT_MS -> { s.plain = true; s.mainVolume = 1f; return end(s, r, "the chosen song wasn't ready in ${LOAD_LIMIT_MS / 1000} s: a plain skip", clean = false) }
                    r.headPlaying && r.mainPlaying -> {
                        phase = Phase.FADING
                        heardAt = r.now
                        lastAt = r.now
                        fadedMs = 0L
                        s.headVolume = 0f
                    }
                }
                s.waitMs = 5
            }
            Phase.FADING -> {
                s.headPlay = r.mainWants
                if (r.headFailed) { s.plain = true; s.mainVolume = 1f; return end(s, r, "the chosen song stopped in the fade: a plain skip", clean = false) }
                // Only music counts: a pause (or either one loading) holds the fade where it is.
                if (r.headPlaying && r.mainPlaying) fadedMs += r.now - lastAt
                lastAt = r.now
                val p = (fadedMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
                progress = p
                val (incoming, outgoing) = curve().gains(p)
                s.headVolume = incoming
                s.mainVolume = outgoing
                if (p >= 1f) {
                    s.headVolume = 1f
                    s.mainVolume = 0f
                    seekLeadMs = if (warm) leadWarmMs else leadColdMs
                    s.moveMs = r.headPos + (seekLeadMs * r.speed).toLong()
                    movedAt = r.now
                    settleUntil = r.now + SETTLE_SEEK_MS
                    lastWasMove = true
                    phase = Phase.CATCHING
                }
                s.waitMs = 10
            }
            Phase.CATCHING -> {
                s.headPlay = r.mainWants
                s.mainVolume = 0f
                if (r.headFailed) { s.mainVolume = 1f; s.nudge = 1f; return end(s, r, "the head stopped while catching up: the main player brought up", clean = false) }
                if (r.mainIndex != target) { s.mainVolume = 1f; s.nudge = 1f; return end(s, r, "the main player went to another song", clean = false) }
                if (!r.mainPlaying || !r.headPlaying) {
                    // Paused (or one is loading): readings start again from scratch once both play, a speed nudge is let go.
                    if (nudgeUntil != 0L) { s.nudge = 1f; nudgeUntil = 0L }
                    windowFrom = 0L
                    settleUntil = maxOf(settleUntil, r.now + SETTLE_NUDGE_MS)
                    s.waitMs = 20
                    return s
                }
                // Near the limit, handed over however it stands (within a quarter second slowly, else a dip).
                if (age >= LIMIT_MS - FINAL_MS) {
                    val e = lastErr
                    return if (e != null && abs(e) <= LAST_CHANCE_MS) handover(s, r, SLOW_HANDOVER_MS, "slow hand-over near the limit ($e ms)", e, clean = false)
                    else handover(s, r, DIP_MS, "dip near the limit (${e ?: "?"} ms)", e, clean = false)
                }
                if (nudgeUntil != 0L) {
                    if (r.now < nudgeUntil) { s.waitMs = 5; return s }
                    s.nudge = 1f
                    nudgeUntil = 0L
                    settleUntil = r.now + SETTLE_NUDGE_MS
                    s.waitMs = 10
                    return s
                }
                if (r.now < settleUntil) { s.waitMs = 10; return s }
                val err = window(r) ?: run { s.waitMs = 5; return s }
                lastErr = err
                if (lastWasMove) {
                    // What the move (or seek) fell behind by is what the next one is sent ahead by.
                    seekLeadMs = (seekLeadMs - (err / r.speed).toLong()).coerceIn(0L, MAX_LEAD_MS)
                    if (seeks == 0) learn(seekLeadMs)
                    lastWasMove = false
                }
                when {
                    abs(err) <= IN_STEP_MS -> return handover(s, r, HANDOVER_MS, "in step", err, clean = true)
                    tries >= MAX_TRIES ->
                        return if (abs(err) <= LAST_CHANCE_MS) handover(s, r, SLOW_HANDOVER_MS, "slow hand-over ($err ms apart after $tries tries)", err, clean = false)
                        else handover(s, r, DIP_MS, "dip ($err ms apart after $tries tries)", err, clean = false)
                    abs(err) > SEEK_OVER_MS && seeks < MAX_SEEKS -> {
                        seeks++; tries++
                        s.seekMs = r.headPos + (seekLeadMs * r.speed).toLong()
                        lastWasMove = true
                        settleUntil = r.now + SETTLE_SEEK_MS
                    }
                    else -> {
                        tries++
                        val rate = nudgeRate(err)
                        s.nudge = if (err < 0) 1f + rate else 1f - rate
                        nudgeUntil = r.now + nudgeMs(err, rate, r.speed)
                    }
                }
                s.waitMs = 5
            }
            Phase.HANDOVER -> {
                if (r.mainIndex != target) { s.mainVolume = 1f; s.nudge = 1f; return end(s, r, "the main player went to another song", clean = false) }
                val x = ((r.now - handAt).toFloat() / handMs).coerceIn(0f, 1f)
                s.mainVolume = x
                s.headVolume = 1f - x
                if (x >= 1f) { s.mainVolume = 1f; s.nudge = 1f; return end(s, r, handHow, handClean) }
                s.waitMs = 5
            }
        }
        return s
    }

    /** A second skip while this one is under way: it ends now, the main player at full volume (the caller then cuts). */
    fun cut(r: SkipNow): SkipStep {
        val s = SkipStep()
        s.target = target
        if (!active) return s
        // A short fade-out's cut (or new queue) happens first, then the new skip.
        if (phase == Phase.FADE_OUT) s.then = true
        s.mainVolume = 1f
        s.nudge = 1f
        return end(s, r, "a second skip in ${phase.name.lowercase()}: cut to the newest", clean = false)
    }

    /**
     * Ends the run at once, on the new song: the limit, a seek by the listener, the switch turned off, casting starting.
     * Not moved yet, the main player goes to the chosen song where the head is (to its start if the head isn't playing).
     */
    fun finishNow(r: SkipNow, how: String, s: SkipStep = SkipStep()): SkipStep {
        if (!active) return s
        s.target = target
        when (phase) {
            Phase.FADE_OUT -> s.then = true
            Phase.LOADING -> s.plain = true
            Phase.FADING -> if (r.headPlaying && !r.headFailed) s.moveMs = r.headPos else s.plain = true
            else -> Unit
        }
        s.mainVolume = 1f
        s.nudge = 1f
        return end(s, r, how, clean = false)
    }

    /**
     * The listener moved in the song: finished at once on the new song. Not before the move, if it was the run's own
     * corrective seek: [SkipFade] tells those apart.
     */
    fun seeked(r: SkipNow): SkipStep = finishNow(r, "the listener seeked")

    private fun handover(s: SkipStep, r: SkipNow, ms: Long, how: String, err: Long?, clean: Boolean): SkipStep {
        // At the listener's own speed from here: the two play the same audio side by side for the cross-over.
        s.nudge = 1f
        nudgeUntil = 0L
        offsetAtHand = err
        handAt = r.now
        handMs = ms
        handHow = how
        handClean = clean
        phase = Phase.HANDOVER
        s.mainVolume = 0f
        s.headVolume = 1f
        s.waitMs = 5
        return s
    }

    private fun end(s: SkipStep, r: SkipNow, how: String, clean: Boolean): SkipStep {
        s.end = SkipEnd(
            how, clean,
            heardMs = heardAt.takeIf { it != 0L }?.let { it - pressAt },
            movedMs = movedAt.takeIf { it != 0L }?.let { it - pressAt },
            offsetMs = offsetAtHand,
            totalMs = r.now - pressAt,
        )
        reset()
        s.waitMs = 100
        return s
    }

    private fun learn(leadMs: Long) {
        if (warm) leadWarmMs = (leadWarmMs + leadMs) / 2 else leadColdMs = (leadColdMs + leadMs) / 2
    }

    /** One window of readings, each player at its freshest (a reported position can be stale, never early): main's lead over the head, or null until it's done. */
    private fun window(r: SkipNow): Long? {
        if (windowFrom == 0L) { windowFrom = r.now; mainLead = Long.MIN_VALUE; headLead = Long.MIN_VALUE }
        val moved = ((r.now - windowFrom) * r.speed).toLong()
        mainLead = maxOf(mainLead, r.mainPos - moved)
        headLead = maxOf(headLead, r.headPos - moved)
        if (r.now - windowFrom < WINDOW_MS) return null
        windowFrom = 0L
        windows++
        return mainLead - headLead
    }

    companion object {
        /** What a skip does, in this order (plan-mix §2.1, §2.3.6). */
        fun choose(a: SkipAsk): SkipChoice = when {
            a.underWay -> SkipChoice.CUT
            !a.on -> SkipChoice.PLAIN
            a.mixHeard -> SkipChoice.STOP_MIX_THEN_PLAIN
            a.sameSong || !a.playing || !a.allowed -> SkipChoice.PLAIN
            a.light -> SkipChoice.FADE_OUT
            else -> SkipChoice.BLEND
        }

        /** How much faster or slower the silent main player plays to close [err] ms: 5 % for small gaps, up to 20 %. */
        fun nudgeRate(err: Long): Float = (abs(err) / 2500f).coerceIn(MIN_NUDGE, MAX_NUDGE)

        /** For how long (real ms) a nudge of [rate] at the listener's [speed] closes [err] ms of song. */
        fun nudgeMs(err: Long, rate: Float, speed: Float): Long = (abs(err) / (rate * speed)).toLong().coerceAtLeast(1)

        /** The fade, over this much music (plan-mix §10, decision 3: fixed, no slider). */
        const val FADE_MS = 1_500L
        /** The chosen song not playing by this long after the press: a plain skip. */
        const val LOAD_LIMIT_MS = 4_000L
        /** All is normal this long after the press. */
        const val LIMIT_MS = 8_000L
        /** Still catching up this close to the limit: handed over as it stands. */
        const val FINAL_MS = 500L
        /** Performance mode, battery saver, a new queue: the fade-out before the cut. */
        const val FADE_OUT_MS = 200L
        /** Closer than this the two are heard as one: the quick hand-over (Mix's IN_STEP_DOUBLING_MS). */
        const val IN_STEP_MS = 45L
        const val HANDOVER_MS = 120L
        const val SLOW_HANDOVER_MS = 350L
        /** Not in step after [MAX_TRIES]: within this, the slow hand-over; further apart, the dip. */
        const val LAST_CHANCE_MS = 250L
        const val DIP_MS = 60L
        const val MAX_TRIES = 2
        /** Only this far off is a seek worth its restart. */
        const val SEEK_OVER_MS = 1_500L
        const val MAX_SEEKS = 1
        const val WINDOW_MS = 450L
        /** After the move or a seek, readings wait for the restart; after a nudge, for the speed to take. */
        const val SETTLE_SEEK_MS = 800L
        const val SETTLE_NUDGE_MS = 500L
        const val MIN_NUDGE = 0.05f
        const val MAX_NUDGE = 0.20f
        /** The A16 trial's restarts: 0.3 to 0.4 s warmed or downloaded, 0.5 to 1 s cold. */
        const val START_LEAD_WARM_MS = 350L
        const val START_LEAD_COLD_MS = 700L
        const val MAX_LEAD_MS = 1_500L
    }
}
