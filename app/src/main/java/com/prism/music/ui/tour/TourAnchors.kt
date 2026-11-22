package com.prism.music.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo

/**
 * The controls a coach tour (1.8.8, [Coach]) can point at. Each is marked once in the app's code with
 * [Modifier.tourAnchor] (`TourAnchorsTest` holds it to that).
 */
enum class TourAnchor {
    /** The first song of Quick picks on Home (List and Grid styles). */
    QUICK_PICK,
    /** The mini player, in every place it's drawn (bar, joined, rail). */
    MINI_PLAYER,
    /** The player's Lyrics button in its row of buttons. */
    PLAYER_LYRICS,
    /** The heart beside the song's title in the player. */
    PLAYER_LIKE,
    /** The Settings tab (bar, minimal bar, rail). */
    SETTINGS_TAB,
    /** The Library tab (bar, minimal bar, rail). */
    LIBRARY_TAB,
    /** Settings → Playback → Song to song: the Crossfade row (What's new's "Show me"). */
    CROSSFADE,
    ;

    /** `Modifier.tourAnchor(this)`, for a file that would rather not import it (one line to merge). */
    val mark: Modifier get() = Modifier.tourAnchor(this)
}

/**
 * Where each [TourAnchor] is on the screen, in window pixels, while a coach tour runs; nothing at all otherwise. The
 * marks only start watching their place when [running] turns true, so day to day they cost nothing (no callback on a
 * scroll or a layout).
 */
object TourAnchors {
    /** True while a coach tour runs ([Coach.run]). */
    val running: Boolean get() = Coach.run != null

    private class Mark(val owner: Any, val bounds: Rect)

    private val marks = mutableStateMapOf<TourAnchor, Mark>()

    /** [anchor]'s place in the window, or null when it isn't drawn (or no tour runs). Snapshot state: read it in composition. */
    fun boundsOf(anchor: TourAnchor): Rect? = marks[anchor]?.bounds

    /** What's marked now (tests). */
    val recorded: Set<TourAnchor> get() = marks.keys.toSet()

    internal fun record(anchor: TourAnchor, owner: Any, bounds: Rect) {
        if (!running) return
        val now = marks[anchor]
        if (now != null && now.owner === owner && now.bounds == bounds) return
        marks[anchor] = Mark(owner, bounds)
    }

    /** [owner]'s mark goes (a newer control with the same anchor keeps its own). */
    internal fun forget(anchor: TourAnchor, owner: Any) {
        if (marks[anchor]?.owner === owner) marks.remove(anchor)
    }

    internal fun clear() = marks.clear()
}

/**
 * Marks this control as [anchor] for the coach. A no-op while no tour runs: it only reads [TourAnchors.running], and
 * starts following the control's place when a tour starts. Null marks nothing (for a shared piece of code, as the tabs).
 */
fun Modifier.tourAnchor(anchor: TourAnchor?): Modifier = if (anchor == null) this else this then TourAnchorElement(anchor)

private class TourAnchorElement(val anchor: TourAnchor) : ModifierNodeElement<TourAnchorNode>() {
    override fun create() = TourAnchorNode(anchor)
    override fun update(node: TourAnchorNode) = node.change(anchor)
    override fun equals(other: Any?) = other is TourAnchorElement && other.anchor == anchor
    override fun hashCode() = anchor.hashCode()
    override fun InspectorInfo.inspectableProperties() { name = "tourAnchor"; value = anchor }
}

/** Watches [TourAnchors.running]; only while it's true does it carry the part that hears the control's place. */
private class TourAnchorNode(var anchor: TourAnchor) : DelegatingNode(), ObserverModifierNode {
    private var follower: Follower? = null

    override fun onAttach() = watch()
    override fun onObservedReadsChanged() = watch()
    override fun onDetach() = TourAnchors.forget(anchor, this)

    fun change(to: TourAnchor) {
        if (to == anchor) return
        TourAnchors.forget(anchor, this)
        anchor = to
    }

    private fun watch() {
        var on = false
        observeReads { on = TourAnchors.running }
        val f = follower
        if (on && f == null) {
            follower = delegate(Follower())
        } else if (!on && f != null) {
            undelegate(f)
            follower = null
            TourAnchors.forget(anchor, this)
        }
    }

    private inner class Follower : Modifier.Node(), GlobalPositionAwareModifierNode {
        override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
            val b = if (coordinates.isAttached) coordinates.boundsInWindow() else Rect.Zero
            if (b.width <= 0f || b.height <= 0f) TourAnchors.forget(anchor, this@TourAnchorNode)
            else TourAnchors.record(anchor, this@TourAnchorNode, b)
        }
    }
}

/**
 * What the player tells a coach step, while a tour runs: whether lyrics are showing, and whether the song has any
 * (null while that isn't known yet). Written by [Player], one line in the player.
 */
object TourSignals {
    var lyricsOpen by mutableStateOf(false)
        internal set
    var songHasLyrics by mutableStateOf<Boolean?>(null)
        internal set

    /** Called from the player only while [TourAnchors.running]; forgets what it said when the player goes. */
    @Composable
    fun Player(lyricsOpen: Boolean, hasLyrics: Boolean?) {
        SideEffect {
            this.lyricsOpen = lyricsOpen
            songHasLyrics = hasLyrics
        }
        DisposableEffect(Unit) { onDispose { this@TourSignals.lyricsOpen = false; songHasLyrics = null } }
    }

    internal fun reset() { lyricsOpen = false; songHasLyrics = null }
}
