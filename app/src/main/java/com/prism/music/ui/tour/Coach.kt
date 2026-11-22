package com.prism.music.ui.tour

import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.music.R
import com.prism.music.data.model.Song
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.components.PrismButton
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/*
 * The coach (1.8.8, plan-tour.md §3 A): a tour that has the person do things. The screen dims except for a soft-edged
 * hole around one real control, with a one-sentence caption beside it and Skip in the top corner. Taps inside the hole
 * go to the app; taps elsewhere do nothing. A step ends when the app's own state says it's done, never on a tap alone.
 * It never traps anyone: Next appears when the control isn't on screen within 2 s or after 8 s with nothing done, and
 * Back and Skip work on every step. First steps, What's new's "Show me" and the Guide's "Show me how" all run on it:
 *
 *     Coach.start(listOf(CoachStep(TourAnchor.MINI_PLAYER, tr(R.string.…), done = { it.playerOpen })), onEnd = { … })
 */

/**
 * What a step can look at to know it's done (or to be passed over). Built from the app's own state each time it changes,
 * so a step's checks run again then.
 */
class TourState(
    /** The page showing (navigation route), null before the first. */
    val route: String? = null,
    val playerOpen: Boolean = false,
    val song: Song? = null,
    val playing: Boolean = false,
    val liked: Set<String> = emptySet(),
    /** The player shows the lyrics ([TourSignals]). */
    val lyricsOpen: Boolean = false,
    /** The song playing has lyrics; null while that isn't known yet. */
    val songHasLyrics: Boolean? = null,
    val settings: AppSettings = AppSettings(),
) {
    val songLiked: Boolean get() = song != null && song.id in liked
}

/**
 * One step: the hole on [anchor] (none: the caption alone, with Next), [caption] beside it (one sentence, 12 words at
 * most), done when [done] says so. [passOver] true leaves the step out (a song with no lyrics for the Lyrics step).
 * [prep] runs once as the step starts, to open the place it's about (a page, the player).
 */
class CoachStep(
    val anchor: TourAnchor?,
    val caption: String,
    val done: (TourState) -> Boolean,
    val passOver: (TourState) -> Boolean = { false },
    val prep: (CoachActions.(TourState) -> Unit)? = null,
)

/** What a step's [CoachStep.prep] can do in the app. */
class CoachActions(
    val go: (route: String) -> Unit = {},
    val setPlayerOpen: (Boolean) -> Unit = {},
    /** Shows a tab as its tab bar button does (the Guide's "Show me how" starting on Home); [go] where none is given. */
    val tab: (route: String) -> Unit = go,
)

/** How a tour ended: every step done (or passed with Next), or Skip / Back from the first step. */
enum class CoachEnd { FINISHED, SKIPPED }

/** A tour running: its steps and where it is. Moved on with [next] and [back]; [Coach.skip] ends it. */
class CoachRun internal constructor(
    val steps: List<CoachStep>,
    /** Shown over the caption of a one-step tour, where "1 of 1" would say nothing (What's new). */
    val label: String?,
    internal val onEnd: (CoachEnd) -> Unit,
) {
    var index by mutableIntStateOf(0)
        private set
    /** Next is in the caption: the control isn't on screen, nothing was done for a while, or a screen reader is on. */
    var nextOffered by mutableStateOf(false)
        internal set
    /** How this step was reached: false when by Back, so a step already done there doesn't bounce straight on. */
    internal var forward = true
        private set
    /** Touches on the screen, so idling is counted from the last one. */
    internal var touches by mutableIntStateOf(0)
    /** The step whose [CoachStep.prep] has run (not again after a rotation). */
    internal var prepped = -1

    val step: CoachStep get() = steps[index]

    fun next() = goTo(index + 1, forward = true)

    /** The step before; from the first, Back ends the tour as Skip does. */
    fun back() { if (index > 0) goTo(index - 1, forward = false) else Coach.end(CoachEnd.SKIPPED, this) }

    internal fun passOver() = when {
        forward -> goTo(index + 1, forward = true)
        index > 0 -> goTo(index - 1, forward = false)
        else -> goTo(index + 1, forward = true)
    }

    internal fun goTo(i: Int, forward: Boolean) {
        if (Coach.run !== this) return
        if (i >= steps.size) { Coach.end(CoachEnd.FINISHED, this); return }
        this.forward = forward
        nextOffered = false
        index = i.coerceAtLeast(0)
    }
}

/** The one coach tour that may run at a time. Survives a rotation (the layer is drawn again over the same run). */
object Coach {
    var run by mutableStateOf<CoachRun?>(null)
        private set

    /** Starts [steps] (one running is ended as skipped). [onEnd] is called once, however it ends. No-op for no steps. */
    fun start(steps: List<CoachStep>, label: String? = null, onEnd: (CoachEnd) -> Unit = {}): CoachRun? {
        run?.let { end(CoachEnd.SKIPPED, it) }
        if (steps.isEmpty()) return null
        return CoachRun(steps, label, onEnd).also { run = it }
    }

    /** Skip: the tour ends at once, and nothing of it stays on screen. */
    fun skip() { run?.let { end(CoachEnd.SKIPPED, it) } }

    internal fun end(how: CoachEnd, which: CoachRun) {
        if (run !== which) return
        run = null
        TourAnchors.clear()
        which.onEnd(how)
    }
}

/** The never-trap times (plan §3 A). */
internal const val COACH_MISSING_MS = 2_000L
internal const val COACH_IDLE_MS = 8_000L
/** A moment to see the step done before the next one. */
internal const val COACH_DONE_PAUSE_MS = 450L

const val COACH_TAG = "coach"
const val COACH_CAPTION_TAG = "coach:caption"
const val COACH_NEXT_TAG = "coach:next"
const val COACH_BACK_TAG = "coach:back"
const val COACH_SKIP_TAG = "coach:skip"

/**
 * The coach in the app: over everything in MainActivity, drawn only while a tour runs (one state read otherwise). It
 * gathers the app's state for the steps' checks. TV gets no coach: a tour started there ends at once, as skipped.
 */
@Composable
fun CoachHost(route: String?, playerOpen: Boolean, actions: CoachActions) {
    val run = Coach.run ?: return
    val context = LocalContext.current
    if (remember { com.prism.music.ui.tv.TvDevice.isTv(context) }) {
        LaunchedEffect(run) { Coach.end(CoachEnd.SKIPPED, run) }
        return
    }
    val c = LocalContainer.current
    val song by c.player.currentSong.collectAsStateWithLifecycle()
    val playing by c.player.isPlaying.collectAsStateWithLifecycle()
    val liked by c.library.likedIds.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val screenReader = remember { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    val state = TourState(route, playerOpen, song, playing, liked, TourSignals.lyricsOpen, TourSignals.songHasLyrics, settings)
    CoachLayer(run, state, actions, reduceMotion = settings.reduceMotion, screenReader = screenReader)
}

/**
 * The layer itself, for [run] at its current step, given the app's [state]. [screenReader]: TalkBack is on, so Next is
 * offered from the start. [reduceMotion]: the ring doesn't pulse. Place it last in a Box over the app (it is two pieces
 * filling that Box, so touches inside the hole can reach the app beside it).
 */
@Composable
fun CoachLayer(
    run: CoachRun,
    state: TourState,
    actions: CoachActions = CoachActions(),
    reduceMotion: Boolean = false,
    screenReader: Boolean = false,
) {
    val i = run.index
    val step = run.steps.getOrNull(i) ?: return
    val current by rememberUpdatedState(state)

    // The step: its place opened once, then done on the app's state (or passed over).
    LaunchedEffect(run, i) {
        if (run.prepped != i) { run.prepped = i; step.prep?.invoke(actions, current) }
        // Reached by Back: a step already done there waits until it's undone and done again, or Next.
        var armed = run.forward
        if (!armed) run.nextOffered = true
        val (over, _) = snapshotFlow { step.passOver(current) to step.done(current) }
            .first { (over, done) -> if (!done) armed = true; over || (done && armed) }
        if (over) run.passOver() else { delay(COACH_DONE_PAUSE_MS); run.next() }
    }

    // Where the hole goes: the control's place, while the app is in front and the control is drawn.
    var layer by remember { mutableStateOf<Rect?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val life by lifecycle.currentStateFlow.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val margin = with(density) { 4.dp.toPx() }
    val target = step.anchor?.let { TourAnchors.boundsOf(it) }
    val hole = holeFor(target, layer, pad, margin).takeIf { life.isAtLeast(Lifecycle.State.RESUMED) }
    val holeNow by rememberUpdatedState(hole)

    // Never trapped: Next after 2 s without the control, after 8 s without a touch, or at once for a screen reader.
    LaunchedEffect(run, i, hole != null) {
        if (hole == null) { if (step.anchor != null) delay(COACH_MISSING_MS); run.nextOffered = true }
    }
    LaunchedEffect(run, i, run.touches) { delay(COACH_IDLE_MS); run.nextOffered = true }
    if (screenReader) LaunchedEffect(run, i) { run.nextOffered = true }

    // Back is the coach's while it runs. A handler registered later wins (the player's own, once a step opens it), so
    // this one is registered again whenever the step or the place it's on changes, and so stays the last one.
    key(i, state.playerOpen, state.lyricsOpen, state.route) { BackHandler { run.back() } }

    val scheme = MaterialTheme.colorScheme
    val glow = lerp(scheme.primary, Color.White, 0.4f)
    val pulse = if (reduceMotion) null else rememberInfiniteTransition(label = "coachPulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart), label = "coachPulse",
    )
    // Two pieces side by side in the caller's Box (a piece's touches can only be shared with its siblings). First the
    // dim, the soft-edged hole and its ring of light, which also takes the touches: outside the hole they stop here,
    // inside they go on to the app. Every touch counts against idling.
    Canvas(
        Modifier.fillMaxSize().testTag(COACH_TAG).onGloballyPositioned { layer = it.boundsInWindow() }
            .then(CoachTouchElement({ holeNow }, { run.touches++ }))
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(SCRIM)
        hole?.let { drawHole(it, glow, pulse?.value) }
    }
    // Over it the caption and Skip, whose buttons take their own touches (and nothing else here does). Skip sits in the
    // top-end corner, or the top-start one when the hole is there (Home's profile picture), so the control can be tapped.
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val skipZone = with(density) { Size(SKIP_ZONE_W.toPx(), WindowInsets.safeDrawing.getTop(this) + SKIP_ZONE_H.toPx()) }
    val skipStart = skipAtStart(hole, layer?.width ?: 0f, skipZone, rtl)
    Box(Modifier.fillMaxSize()) {
        CaptionPlacement(hole) {
            Caption(run, step, i)
        }
        PrismTextButton(
            onClick = { Coach.end(CoachEnd.SKIPPED, run) },
            modifier = Modifier.align(if (skipStart) Alignment.TopStart else Alignment.TopEnd).statusBarsPadding()
                .padding(top = 6.dp, end = if (skipStart) 0.dp else 10.dp, start = if (skipStart) 10.dp else 0.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).testTag(COACH_SKIP_TAG),
            shape = CircleShape,
        ) { Text(tr(R.string.skip), color = Color.White, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun Caption(run: CoachRun, step: CoachStep, i: Int) {
    val scheme = MaterialTheme.colorScheme
    val n = run.steps.size
    val count = if (n > 1) tr(R.string.of_2, i + 1, n) else run.label
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().shadow(16.dp, shape).clip(shape).background(scheme.surfaceContainerHigh)
            .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 8.dp).testTag(COACH_CAPTION_TAG)
            .semantics { paneTitle = step.caption },
    ) {
        if (!count.isNullOrEmpty()) Text(count, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Text(
            step.caption, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (i > 0) PrismTextButton(onClick = run::back, modifier = Modifier.testTag(COACH_BACK_TAG)) {
                Text(tr(R.string.back), color = scheme.onSurfaceVariant)
            } else Spacer(Modifier)
            if (run.nextOffered) PrismButton(onClick = run::next, modifier = Modifier.testTag(COACH_NEXT_TAG)) { Text(tr(R.string.widget_next)) }
        }
    }
}

/** The caption beside the hole: below it when the hole is in the top half, above it otherwise, always on screen. */
@Composable
private fun CaptionPlacement(hole: Rect?, content: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val side = 16.dp.roundToPx()
        val width = minOf(constraints.maxWidth - 2 * side, 380.dp.roundToPx()).coerceAtLeast(0)
        val caption = measurables.first().measure(Constraints(minWidth = width, maxWidth = width))
        val height = constraints.maxHeight
        // Clear of the status bar and Skip at the top, of the navigation bar at the bottom.
        val top = insets.getTop(this) + 56.dp.roundToPx()
        val bottom = height - insets.getBottom(this) - 8.dp.roundToPx()
        val y = captionTop(hole, caption.height, top, bottom, 16.dp.roundToPx(), height)
        layout(constraints.maxWidth, height) { caption.place((constraints.maxWidth - caption.width) / 2, y) }
    }
}

/**
 * Where the caption's top goes, in the layer's pixels: [gap] below a hole in the top half, above one in the bottom
 * half; the other side when that doesn't fit; pinned to the side with more room when neither does (big text, a short
 * screen). Without a hole, a little above the middle. Never off the screen of [height].
 */
internal fun captionTop(hole: Rect?, captionHeight: Int, top: Int, bottom: Int, gap: Int, height: Int): Int {
    val y = if (hole == null) (height * 0.4f - captionHeight / 2f).toInt() else {
        val below = hole.bottom.toInt() + gap
        val above = hole.top.toInt() - gap - captionHeight
        val fitsBelow = below + captionHeight <= bottom
        val fitsAbove = above >= top
        val preferBelow = hole.center.y < height / 2f
        when {
            preferBelow && fitsBelow -> below
            !preferBelow && fitsAbove -> above
            fitsBelow -> below
            fitsAbove -> above
            bottom - hole.bottom > hole.top - top -> bottom - captionHeight
            else -> top
        }
    }
    val lo = top.coerceAtMost(height - captionHeight).coerceAtLeast(0)
    val hi = (bottom - captionHeight).coerceAtLeast(lo)
    return y.coerceIn(lo, hi).coerceAtMost((height - captionHeight).coerceAtLeast(0))
}

/** Where Skip may be: this wide and this far down (below the status bar) in its top corner. */
private val SKIP_ZONE_W = 140.dp
private val SKIP_ZONE_H = 64.dp

/**
 * Skip goes to the top-start corner: the [hole] (layer pixels, a layer [width] wide) reaches into the top-end corner's
 * [zone] (its width, and its height from the top), where Skip would cover it. [rtl]: the end is on the left.
 */
internal fun skipAtStart(hole: Rect?, width: Float, zone: Size, rtl: Boolean): Boolean {
    if (hole == null || hole.top >= zone.height) return false
    return if (rtl) hole.left < zone.width else hole.right > width - zone.width
}

/**
 * A tour's step may open the player when there's no song in it yet (one is being loaded). The player draws only with a
 * song, so with none it never showed, and the flag stayed: the next song threw the full player open. When a tour ends
 * with the player open and still no song, [close] puts the flag back. Nothing while no tour has run.
 */
@Composable
fun CoachPlayerReset(playerOpen: Boolean, hasSong: () -> Boolean, close: () -> Unit) {
    val running = Coach.run != null
    val open by rememberUpdatedState(playerOpen)
    var ran by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (running) ran = true
        else if (ran) { ran = false; if (open && !hasSong()) close() }
    }
}

/**
 * The hole for a control at [target] (window pixels) in a layer at [layer]: the control's visible part, [pad] wider
 * all round, kept [margin] inside the layer, in the layer's pixels. Null when either isn't known or nothing is visible.
 */
internal fun holeFor(target: Rect?, layer: Rect?, pad: Float, margin: Float): Rect? {
    if (target == null || layer == null) return null
    val seen = target.intersect(layer)
    if (seen.width <= 0f || seen.height <= 0f) return null
    val r = seen.translate(-layer.left, -layer.top).inflate(pad)
    return Rect(
        r.left.coerceAtLeast(margin), r.top.coerceAtLeast(margin),
        r.right.coerceAtMost(layer.width - margin), r.bottom.coerceAtMost(layer.height - margin),
    ).takeIf { it.width > 0f && it.height > 0f }
}

private val SCRIM = Color(0xBD09080E)
private const val FEATHER_STEPS = 8

/** Cuts the hole with a soft edge (nested rounded cuts, an even ramp), then the ring of light, pulsing at [pulse]. */
private fun DrawScope.drawHole(hole: Rect, glow: Color, pulse: Float?) {
    val feather = 10.dp.toPx()
    val radius = minOf(hole.height / 2f, 24.dp.toPx())
    // The m-th cut of N from the outside takes 1/(N-m+1) of what's left: what remains falls evenly to nothing.
    for (m in 1..FEATHER_STEPS) {
        val grow = feather / 2f - feather * (m - 1) / (FEATHER_STEPS - 1)
        val r = hole.inflate(grow)
        drawRoundRect(
            Color.Black.copy(alpha = 1f / (FEATHER_STEPS - m + 1)), Offset(r.left, r.top), Size(r.width, r.height),
            CornerRadius((radius + grow).coerceAtLeast(0f)), blendMode = BlendMode.DstOut,
        )
    }
    val stroke = 2.dp.toPx()
    drawRoundRect(glow.copy(alpha = 0.28f), Offset(hole.left - 3.dp.toPx(), hole.top - 3.dp.toPx()), Size(hole.width + 6.dp.toPx(), hole.height + 6.dp.toPx()),
        CornerRadius(radius + 3.dp.toPx()), style = Stroke(6.dp.toPx()))
    drawRoundRect(glow, Offset(hole.left, hole.top), Size(hole.width, hole.height), CornerRadius(radius), style = Stroke(stroke))
    if (pulse != null) {
        val grow = 14.dp.toPx() * pulse
        drawRoundRect(
            glow.copy(alpha = 0.55f * (1f - pulse)), Offset(hole.left - grow, hole.top - grow), Size(hole.width + 2 * grow, hole.height + 2 * grow),
            CornerRadius(radius + grow), style = Stroke(stroke),
        )
    }
}

/**
 * The layer's touches: a pointer that comes down outside the hole is taken in the first pass (the app never sees it),
 * one that comes down inside goes on to the app, as the layer shares its touches with its siblings. [onTouch] hears
 * every press.
 */
private class CoachTouchElement(val hole: () -> Rect?, val onTouch: () -> Unit) : ModifierNodeElement<CoachTouchNode>() {
    override fun create() = CoachTouchNode(hole, onTouch)
    override fun update(node: CoachTouchNode) { node.hole = hole; node.onTouch = onTouch }
    override fun equals(other: Any?) = other is CoachTouchElement && other.hole === hole && other.onTouch === onTouch
    override fun hashCode() = 31 * hole.hashCode() + onTouch.hashCode()
    override fun InspectorInfo.inspectableProperties() { name = "coachTouches" }
}

private class CoachTouchNode(var hole: () -> Rect?, var onTouch: () -> Unit) : Modifier.Node(), PointerInputModifierNode {
    private val passing = HashSet<PointerId>()

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        // The first pass, before the app under the layer sees anything.
        if (pass != PointerEventPass.Initial) return
        val h = hole()
        for (change in pointerEvent.changes) {
            if (change.pressed && !change.previousPressed) {
                onTouch()
                if (h != null && h.contains(change.position)) passing += change.id else passing -= change.id
            }
            if (change.id !in passing) change.consume()
            if (!change.pressed) passing -= change.id
        }
    }

    override fun onCancelPointerInput() = passing.clear()

    override fun sharePointerInputWithSiblings() = true
}
