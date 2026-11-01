package com.prism.music.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.prism.music.R
import com.prism.music.TvMediaKeys
import com.prism.music.tr
import com.prism.music.ui.components.Artwork
import com.prism.music.ui.player.MusicVideoSurface
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.delay

/*
 * Prism TV's music video (1.8.0): the video full screen, and over it, on any key, the controls: the progress line
 * (← → seek), previous / play-pause / next, Lyrics, Queue, "Back to the song" and ⋯. They fade after a few seconds
 * without the remote; while they're hidden the screen itself holds focus, so focus is never on something you can't see.
 * The queue is the player's own side panel.
 */

/** The video screen's controls: [tag] is each one's test tag under [TAG_VIDEO] ("tv_video/queue"). */
enum class TvVideoControl(val tag: String) {
    SEEK("seek"), PREVIOUS("previous"), PLAYPAUSE("playpause"), NEXT("next"), LYRICS("lyrics"), QUEUE("queue"), SONG("song"), MORE("more");

    val fullTag: String get() = "$TAG_VIDEO/$tag"
}

/**
 * The controls a video shows, in D-pad order (the progress line above the row): no progress line for a live stream
 * (nothing to seek in), no Lyrics for an episode.
 */
fun tvVideoControls(hasLyrics: Boolean, seekable: Boolean): List<TvVideoControl> =
    TvVideoControl.entries.filter { c ->
        when (c) {
            TvVideoControl.SEEK -> seekable
            TvVideoControl.LYRICS -> hasLyrics
            else -> true
        }
    }

/** Where focus goes when the controls come back: the control left last, or play/pause when that one isn't shown. */
fun tvVideoFocusTarget(last: TvVideoControl, shown: List<TvVideoControl>): TvVideoControl =
    if (last in shown) last else TvVideoControl.PLAYPAUSE

/**
 * Holding ← or → on the progress line: a step on the press, then every [SEEK_REPEAT_EVERY]th key repeat (a remote
 * repeats a held key ~20 times a second; seeking 10 s on each would fly through the song).
 */
fun tvSeekStep(repeatCount: Int): Boolean = repeatCount % SEEK_REPEAT_EVERY == 0

private const val SEEK_REPEAT_EVERY = 3

/** How long the video's controls stay after the last key. */
const val TV_VIDEO_CONTROLS_MS = 4_000L

/** How far a step on the seek line moves (the remote's ← →, as its rewind and fast forward). */
const val TV_SEEK_STEP_MS = TvMediaKeys.SEEK_MS

/**
 * The controls over the bottom of a video: title, the seek line (← → seek), then the row; a null [onLyrics],
 * [onQueue] or [onMore] leaves that button out. [enabled] false (hidden, fading out, a panel over them): none of them
 * can take focus. [requesters] put focus on one; [onFocused] says which one has it. Drawn on their own (the design
 * pages), focus opens on [entry]; [TvVideoPlace] passes null and puts focus itself.
 */
@Composable
fun TvVideoControls(
    now: TvNow,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSong: () -> Unit,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {},
    onLyrics: (() -> Unit)? = null,
    onQueue: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    enabled: Boolean = true,
    requesters: Map<TvVideoControl, FocusRequester> = remember { TvVideoControl.entries.associateWith { FocusRequester() } },
    onFocused: (TvVideoControl) -> Unit = {},
    entry: TvVideoControl? = TvVideoControl.PLAYPAUSE,
) {
    val shown = tvVideoControls(now.hasLyrics && onLyrics != null, seekable = now.total.isNotEmpty())
        .filter { (it != TvVideoControl.QUEUE || onQueue != null) && (it != TvVideoControl.MORE || onMore != null) }
    if (entry != null) LaunchedEffect(Unit) {
        val target = tvVideoFocusTarget(entry, shown)
        repeat(40) { if (runCatching { requesters.getValue(target).requestFocus() }.getOrDefault(false)) return@LaunchedEffect; delay(50) }
    }
    fun Modifier.control(c: TvVideoControl): Modifier = this
        .focusProperties { canFocus = enabled }
        .focusRequester(requesters.getValue(c))
        .onFocusChanged { if (it.isFocused) onFocused(c) }
        .testTag(c.fullTag)
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 48.dp, end = 48.dp, top = 72.dp, bottom = 27.dp).testTag(TAG_VIDEO_CONTROLS),
    ) {
        Text(now.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
        Text(now.artist, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        Spacer(Modifier.height(10.dp))
        if (TvVideoControl.SEEK in shown) {
            // Down from the line lands on play/pause, whichever button is under it.
            TvSeekLine(now, onSeek, onPlayPause, Modifier.focusProperties { down = requesters.getValue(TvVideoControl.PLAYPAUSE) }.control(TvVideoControl.SEEK))
        } else TvProgress(now.fraction, now.played, now.total, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            TvIconButton(PrismIcons.Previous, tr(R.string.previous_song), onPrevious, Modifier.control(TvVideoControl.PREVIOUS))
            TvIconButton(
                if (now.playing) PrismIcons.Pause else PrismIcons.Play, tr(if (now.playing) R.string.widget_pause else R.string.widget_play),
                onPlayPause, Modifier.control(TvVideoControl.PLAYPAUSE), big = true,
            )
            TvIconButton(PrismIcons.Next, tr(R.string.next_song), onNext, Modifier.control(TvVideoControl.NEXT))
            Spacer(Modifier.width(12.dp))
            if (onLyrics != null && TvVideoControl.LYRICS in shown) TvButton(tr(R.string.lyrics), onLyrics, Modifier.control(TvVideoControl.LYRICS), icon = PrismIcons.Lyrics)
            if (onQueue != null) TvButton(tr(R.string.queue), onQueue, Modifier.control(TvVideoControl.QUEUE), icon = PrismIcons.Queue)
            TvButton(tr(R.string.back_to_the_song), onSong, Modifier.control(TvVideoControl.SONG), icon = PrismIcons.Song)
            if (onMore != null) TvIconButton(PrismIcons.More, tr(R.string.more), onMore, Modifier.control(TvVideoControl.MORE))
        }
    }
}

/**
 * The song's progress as one focusable line: ← and → move 10 s back or ahead (as the remote's rewind and fast
 * forward), OK pauses and plays. Focused, it's outlined.
 */
@Composable
fun TvSeekLine(now: TvNow, onSeek: (Long) -> Unit, onPlayPause: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val description = tr(R.string.tv_seek_line, now.played, now.total)
    val ring = tvRing()
    Box(
        modifier
            .semantics { contentDescription = description }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = tvSeekStep(e.nativeKeyEvent.repeatCount)
                when (e.key) {
                    Key.DirectionLeft -> { if (step) onSeek(-TV_SEEK_STEP_MS); true }
                    Key.DirectionRight -> { if (step) onSeek(TV_SEEK_STEP_MS); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (e.nativeKeyEvent.repeatCount == 0) onPlayPause(); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            // Focused, the split light rings it (as every focused thing on the TV).
            .then(if (focused) Modifier.border(TV_RING_THIN, ring, TvShapes.card) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        TvProgress(now.fraction, now.played, now.total, Modifier.fillMaxWidth())
    }
}

/**
 * A music video full screen ([video] draws it; while it isn't [ready] the song's cover stands in). Any key shows
 * [TvVideoControls]; they fade [TV_VIDEO_CONTROLS_MS] after the last key while the video plays (paused, they stay).
 * Back hides them; with them hidden, Back is the page's (back to the song). [panelOpen]: a side panel ([panel], the
 * queue or ⋯) is over the screen and has focus; when it closes, focus returns to the control that opened it.
 * [panelCovers]: the panel covers the whole screen (the lyrics over the video), so the controls hide under it.
 */
@Composable
fun TvVideoPlace(
    now: TvNow,
    ready: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onSong: () -> Unit,
    onMore: () -> Unit,
    panelOpen: Boolean = false,
    panelCovers: Boolean = false,
    panel: @Composable BoxScope.() -> Unit = {},
    video: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(true) }
    // The control that had focus last (kept while Lyrics are open over the video, so coming back lands there).
    var last by rememberSaveable { mutableStateOf(TvVideoControl.PLAYPAUSE) }
    // A control waiting to take focus as soon as it's placed (the controls just came back, a panel closed).
    var wanted by remember { mutableStateOf<TvVideoControl?>(last) }
    var touched by remember { mutableLongStateOf(0L) }
    // A key whose key-down showed the controls: its key-up goes too (a button under it would click on key-up).
    var swallowUp by remember { mutableStateOf<Key?>(null) }
    var screenFocused by remember { mutableStateOf(false) }
    val screen = remember { FocusRequester() }
    val requesters = remember { TvVideoControl.entries.associateWith { FocusRequester() } }
    val available = tvVideoControls(now.hasLyrics, seekable = now.total.isNotEmpty())
    val availableNow by rememberUpdatedState(available)
    val hide: () -> Unit = {
        // Focus first goes to the screen, then the controls fade: it's never left on one fading away.
        runCatching { screen.requestFocus() }
        shown = false
        wanted = null
    }
    val show: () -> Unit = { shown = true; wanted = last }

    androidx.compose.runtime.LaunchedEffect(touched, now.playing, shown, panelOpen) {
        if (shown && now.playing && !panelOpen) {
            delay(TV_VIDEO_CONTROLS_MS)
            hide()
        }
    }
    // A panel closed: the controls are back, with the button that opened it focused.
    var wasOpen by remember { mutableStateOf(panelOpen) }
    LaunchedEffect(panelOpen) {
        if (wasOpen && !panelOpen) { shown = true; wanted = last; touched = System.nanoTime() }
        wasOpen = panelOpen
    }
    // Puts focus on the wanted control once it's placed (a few frames after the controls appear).
    LaunchedEffect(wanted, shown, panelOpen) {
        val w = wanted ?: return@LaunchedEffect
        if (!shown || panelOpen) return@LaunchedEffect
        val target = tvVideoFocusTarget(w, availableNow)
        repeat(40) {
            if (runCatching { requesters.getValue(target).requestFocus() }.getOrDefault(false)) { wanted = null; return@LaunchedEffect }
            delay(50)
        }
        wanted = null
    }
    // Back with the controls up hides them (as TV players do); with them hidden Back leaves (the shell's).
    BackHandler(enabled = shown && !panelOpen) { hide() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag(TAG_VIDEO)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp) {
                    if (swallowUp == e.key) { swallowUp = null; return@onPreviewKeyEvent true }
                    return@onPreviewKeyEvent false
                }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                touched = System.nanoTime()
                if (panelOpen || e.key == Key.Back) return@onPreviewKeyEvent false
                // Hidden: the key only brings the controls back.
                if (!shown) { show(); swallowUp = e.key; return@onPreviewKeyEvent true }
                // Shown but the screen still has focus (they're just coming back): focus goes onto them.
                if (screenFocused) { wanted = last; swallowUp = e.key; return@onPreviewKeyEvent true }
                false
            },
    ) {
        // The screen: holds focus while the controls are hidden (focus is never nowhere, never on hidden controls).
        Box(
            Modifier.fillMaxSize()
                .focusProperties { canFocus = !panelOpen }
                .focusRequester(screen)
                .onFocusChanged { screenFocused = it.isFocused }
                .testTag(TAG_VIDEO_SCREEN)
                .focusable(),
        ) {
            if (ready) video() else Artwork(now.art, Modifier.align(Alignment.Center).size(360.dp), shape = TvShapes.art, size = 1080)
        }
        AnimatedVisibility(shown && !(panelOpen && panelCovers), Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            TvVideoControls(
                now, onPlayPause, onNext, onPrevious, onSong,
                onSeek = onSeek, onLyrics = onLyrics, onQueue = onQueue, onMore = onMore,
                enabled = shown && !panelOpen, requesters = requesters,
                onFocused = { last = it }, entry = null,
            )
        }
        if (panelOpen) panel()
    }
}

/** The panels over the video (the lyrics over all of it). */
private enum class TvVideoPanel { NONE, QUEUE, MORE, LYRICS }

/**
 * A music video, full screen, on the engine. Opening the page turns video mode on (and again when coming back to it
 * from an album or Sing); leaving it ends video mode, the song plays on as audio. Lyrics open over the video, which
 * plays on under them. The queue is the player's panel: a song picked plays as its video.
 */
@Composable
fun TvVideoPage(nav: TvNav) {
    val c = LocalContainer.current
    val now = rememberTvNow(c)
    val video by c.player.videoMode.collectAsState()
    val song by c.player.currentSong.collectAsState()
    val settings by c.settings.flow.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { TvSing.load(context) }
    val singReady = TvSing.state.collectAsState().value.sings
    DisposableEffect(Unit) { onDispose { c.player.setVideoMode(false) } }
    LaunchedEffect(Unit) { if (!c.player.videoMode.value && !c.player.videoLoading.value) c.player.setVideoMode(true) }
    // Another song (the queue, next): with "Keep playing videos" on the service lines each up as its video; with it off
    // this page asks, since it's a video page.
    val firstSong = remember { song?.id }
    LaunchedEffect(song?.id) {
        if (song?.id == firstSong || song == null) return@LaunchedEffect
        if (settings.videoContinues && settings.showVideoSwitch) return@LaunchedEffect
        delay(300)
        if (!c.player.videoMode.value && !c.player.videoLoading.value) c.player.setVideoMode(true)
    }
    if (now == null) { TvStarting { nav.back() }; return }
    var panel by rememberSaveable { mutableStateOf(TvVideoPanel.NONE) }
    val close: () -> Unit = { panel = TvVideoPanel.NONE }
    TvVideoPlace(
        now, video,
        onPlayPause = { c.player.togglePlay() },
        onNext = { c.player.next() },
        onPrevious = { c.player.previous() },
        onSeek = { ms -> c.player.seekBy(ms) },
        onLyrics = { panel = TvVideoPanel.LYRICS },
        onQueue = { panel = TvVideoPanel.QUEUE },
        onSong = { nav.back() },
        onMore = { panel = TvVideoPanel.MORE },
        panelOpen = panel != TvVideoPanel.NONE,
        panelCovers = panel == TvVideoPanel.LYRICS,
        panel = {
            when (panel) {
                TvVideoPanel.NONE -> Unit
                TvVideoPanel.LYRICS -> TvVideoLyrics(close)
                // A song picked plays (as its video) and the panel closes, so it's watched.
                TvVideoPanel.QUEUE -> TvPanel("queue", close) { TvQueuePlace(onPicked = close) }
                TvVideoPanel.MORE -> TvPanel("more", close) { TvActionPanel(song?.title ?: "", songActions(c, nav, song, singReady, close)) }
            }
        },
    ) {
        MusicVideoSurface(Modifier.fillMaxSize(), fill = false)
    }
}

/** Test tags: the video's controls (their bar) and the screen under them (focus while they're hidden). */
const val TAG_VIDEO_CONTROLS = "tv_video/controls"
const val TAG_VIDEO_SCREEN = "tv_video/screen"
