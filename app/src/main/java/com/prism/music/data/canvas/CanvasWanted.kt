package com.prism.music.data.canvas

import com.prism.music.data.prefs.AppSettings
import kotlinx.coroutines.async

/**
 * Whether a song's Canvas loop from its music video is wanted (1.8.6, plan-canvas §3.1, §3.7, §11a R1), in one place, so
 * the player, the next-song preparation ([CanvasAhead]), downloads and diagnostics can never disagree.
 *
 * [settings] are the ones Prism uses ([AppSettings.effective]: Performance mode reads as Canvas off).
 */
data class CanvasWant(
    /** A loop is the picture that would show, if the song has one. */
    val video: Boolean,
    /** The stream height it is cut from: 480 lighter, 1080 full screen, else 720. 0 when not [video]. */
    val height: Int,
    /** The network may be used for it now (else only a loop already on the phone plays). */
    val network: Boolean,
    /** Why no loop is asked for, when not [video]. */
    val notAsked: NotAsked?,
)

/** Why a loop isn't asked for (diagnostics' "not asked: …"). */
enum class NotAsked(val text: String) {
    CANVAS_OFF("Canvas off"),
    VIDEOS_OFF("music videos off"),
    HIDDEN("Canvas loop hidden for this song"),
    NOT_A_SONG("not a song (a file, an upload, an episode or live)"),
    COVER_FIRST("the animated cover shows first"),
}

/**
 * The decision: [hasCover] is whether the song has an animated cover that may play ([canvasCoverWanted]), [songChoice]
 * the listener's "Use:" for it, [hidden] its Canvas loop switched off from the player (not its animated cover), [isSong] [ClipGate.isSong]. [metered]: on mobile data (or any
 * network that charges by use).
 */
fun canvasWant(
    settings: AppSettings, metered: Boolean, hasCover: Boolean, songChoice: CanvasSource?, hidden: Boolean, isSong: Boolean,
): CanvasWant {
    val prefs = settings.behavior.canvas
    val notAsked = when {
        !settings.behavior.canvas.enabled -> NotAsked.CANVAS_OFF
        !prefs.useVideos -> NotAsked.VIDEOS_OFF
        hidden -> NotAsked.HIDDEN
        !isSong -> NotAsked.NOT_A_SONG
        pickSource(hasCover, true, true, prefs.videoFirst, songChoice) != CanvasSource.VIDEO -> NotAsked.COVER_FIRST
        else -> null
    }
    if (notAsked != null) return CanvasWant(false, 0, false, notAsked)
    return CanvasWant(true, canvasHeight(settings), canvasNetworkAllowed(settings, metered), null)
}

/**
 * Whether the loop from the song's music video is the picture whatever its animated cover (1.8.7): the video comes
 * first (the setting, or "Use:" for this song). Then nothing about the cover changes the answer, and the player asks for
 * the loop at once instead of after the cover's lookup ([showCanvas]).
 */
fun canvasLoopFirst(settings: AppSettings, metered: Boolean, songChoice: CanvasSource?, hidden: Boolean, isSong: Boolean): Boolean =
    canvasWant(settings, metered, hasCover = true, songChoice = songChoice, hidden = hidden, isSong = isSong).video

/**
 * The order a song's picture comes in on the player (1.8.7). [cover] looks the animated cover up (showing one already
 * known on the way, through the function it is given) and gives what it found; [loop] gives the song's loop from its
 * music video for whether it has a cover (null: none, or not wanted); [show] puts a picture up.
 *
 * Without [loopFirst] the loop waits for the cover's answer, which decides whether it is wanted. With it the loop is
 * asked for at once, beside the cover's lookup (on the A16 that lookup was two to three seconds of a new song's wait):
 * the cover shows while the loop comes and stays when there is none, and once the loop is up nothing takes its place.
 * Runs on one thread (the player's).
 */
suspend fun showCanvas(
    loopFirst: Boolean,
    cover: suspend (show: (CanvasArtwork?) -> Unit) -> CanvasArtwork?,
    loop: suspend (hasCover: Boolean) -> CanvasArtwork?,
    show: (CanvasArtwork?) -> Unit,
): Unit = kotlinx.coroutines.coroutineScope {
    var loopUp = false
    val early = if (loopFirst) async { loop(true)?.also { loopUp = true; show(it) } } else null
    val coverShows: (CanvasArtwork?) -> Unit = { if (!loopUp) show(it) }
    val found = cover(coverShows)
    coverShows(found)
    if (early != null) early.await() else loop(found != null)?.let(show)
}

/**
 * Whether a song's animated cover may play (1.8.6), in one place for the player, the next-song preparation and
 * diagnostics: Canvas on, "Animated covers" on, and not switched off for this song ([coverHidden], the ⋯ menu's
 * "Animated cover"). Apart from the loop from the music video: a song whose cover may not play counts as having none, so
 * its video's loop may show ([canvasWant] with `hasCover = false`).
 */
fun canvasCoverWanted(settings: AppSettings, coverHidden: Boolean): Boolean =
    settings.behavior.canvas.enabled && settings.behavior.canvas.covers && !coverHidden

/** Sharp where it fills the screen, lighter on the cover (plan §11a R1); "lighter" is 480 everywhere. */
fun canvasHeight(settings: AppSettings): Int = when {
    settings.behavior.canvas.lighter -> 480
    settings.behavior.canvas.fillsBackground -> 1080
    else -> 720
}

/**
 * Whether a loop may be fetched now: on Wi-Fi always; on mobile data only with "Canvas from videos" in "Use mobile data
 * for" and Data saver off (Data saver, like the rest of Prism's, only bites on mobile data).
 */
fun canvasNetworkAllowed(settings: AppSettings, metered: Boolean): Boolean =
    !metered || (settings.behavior.canvas.videosOnCellular && !settings.dataSaver)

/**
 * Whether a loop may be prepared ahead of time ([CanvasAhead]): only on an unmetered connection and never with Data
 * saver, whatever the mobile-data chip says (as today's next-song prefetch keeps to Wi-Fi).
 */
fun canvasMayPrepare(settings: AppSettings, metered: Boolean): Boolean = !metered && !settings.dataSaver
