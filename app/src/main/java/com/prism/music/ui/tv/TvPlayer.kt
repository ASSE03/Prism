@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.prism.music.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.Artwork
import com.prism.music.ui.player.SungLine
import com.prism.music.ui.player.rememberLyricsLook
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.PrismIcons

/**
 * Now playing (TV): full screen, the cover on the left and the song on the right: title, artist, progress, then the
 * transport row (shuffle, previous, play/pause, next, repeat) and the row under it (lyrics, queue, video, like, more).
 * Focus opens on play/pause; Up/Down move between the two rows; Back returns where it was opened from.
 */
@Composable
fun TvNowPlayingScreen(
    now: TvNow,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onVideo: () -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().tvBackdrop(accent, scheme.background).padding(TvSafe).testTag(TAG_PLAYER)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Artwork(now.art, Modifier.size(340.dp), shape = RoundedCornerShape(18.dp), size = 1080)
            Spacer(Modifier.width(52.dp))
            Column(Modifier.weight(1f)) {
                Text(now.title, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold), color = scheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(now.artist, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now.album != null) Text(now.album, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(32.dp))
                TvProgress(now.fraction, now.played, now.total)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvIconButton(PrismIcons.Shuffle, tr(R.string.shuffle), onShuffle, Modifier.tvItem("$TAG_PLAYER/shuffle"), on = now.shuffle)
                    TvIconButton(PrismIcons.Previous, tr(R.string.previous_song), onPrevious, Modifier.tvItem("$TAG_PLAYER/previous"))
                    TvIconButton(
                        if (now.playing) PrismIcons.Pause else PrismIcons.Play, tr(if (now.playing) R.string.widget_pause else R.string.widget_play),
                        onPlayPause, Modifier.tvItem("$TAG_PLAYER/playpause", entry = true), big = true,
                    )
                    TvIconButton(PrismIcons.Next, tr(R.string.next_song), onNext, Modifier.tvItem("$TAG_PLAYER/next"))
                    TvIconButton(
                        PrismIcons.Repeat, tr(when (now.repeat) { 1 -> R.string.repeat_one; 2 -> R.string.repeat_all; else -> R.string.repeat_off }),
                        onRepeat, Modifier.tvItem("$TAG_PLAYER/repeat"), on = now.repeat != 0,
                    )
                }
                Spacer(Modifier.height(20.dp))
                // Down from the controls lands on this row's first button, every time (as the design's path).
                val rowStart = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
                Row(Modifier.focusProperties { onEnter = { rowStart.requestFocus() } }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (now.hasLyrics) TvButton(tr(R.string.lyrics), onLyrics, Modifier.focusRequester(rowStart).tvItem("$TAG_PLAYER/lyrics"), icon = PrismIcons.Lyrics)
                    TvButton(tr(R.string.queue), onQueue, (if (now.hasLyrics) Modifier else Modifier.focusRequester(rowStart)).tvItem("$TAG_PLAYER/queue"), icon = PrismIcons.Queue)
                    if (now.hasVideo) TvButton(tr(R.string.video), onVideo, Modifier.tvItem("$TAG_PLAYER/video"), icon = PrismIcons.Video)
                    TvIconButton(if (now.liked) PrismIcons.HeartFilled else PrismIcons.Heart, tr(if (now.liked) R.string.liked else R.string.widget_like), onLike, Modifier.tvItem("$TAG_PLAYER/like"), on = now.liked)
                    TvIconButton(PrismIcons.More, tr(R.string.more), onMore, Modifier.tvItem("$TAG_PLAYER/more"))
                }
                if (now.next != null) {
                    Spacer(Modifier.height(28.dp))
                    Text(tr(R.string.tv_up_next_line, now.next), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Lyrics (TV): the song's lyrics large across the screen, the sung line in the listener's lyrics style (all six, word
 * by word where the lyrics have it), the cover and title small in the corner. The lyrics take focus as one piece:
 * Up/Down pick a line (it lights up), OK plays from there, Back returns to the player. Media keys and OK on the sung
 * line pause and play.
 */
@Composable
fun TvLyricsScreen(
    now: TvNow,
    lyrics: TvLyrics,
    clock: State<Long>,
    modifier: Modifier = Modifier,
    picked: Int? = null,
    accent: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val look = rememberLyricsLook(LocalAppSettings.current, now.art)
    // Prism's lyrics font when one is picked, else the app's.
    // Settings → Lyrics → Lyrics size (on the TV).
    val scale = LocalAppSettings.current.tv.lyricsScale
    val big = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp * scale, lineHeight = 52.sp * scale, fontWeight = FontWeight.SemiBold)
        .let { s -> com.prism.music.ui.theme.LocalLyricsFont.current?.let { s.copy(fontFamily = it) } ?: s }
    Box(modifier.fillMaxSize().tvBackdrop(accent, scheme.background).testTag(TAG_LYRICS)) {
        Row(Modifier.padding(TvSafe), verticalAlignment = Alignment.CenterVertically) {
            Artwork(now.art, Modifier.size(56.dp), shape = RoundedCornerShape(8.dp), size = 160)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(now.title, style = MaterialTheme.typography.titleMedium, color = scheme.onBackground, maxLines = 1)
                Text(now.artist, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Column(
            Modifier.fillMaxSize().padding(start = 96.dp, end = 160.dp, top = 120.dp, bottom = 96.dp).tvItem("$TAG_LYRICS/lines", entry = true).focusable(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // The line before the sung one, the sung one, and what comes next.
            val from = ((picked ?: lyrics.current) - 1).coerceAtLeast(0)
            for (i in from until (from + 5).coerceAtMost(lyrics.lines.size)) {
                val line = lyrics.lines[i]
                when {
                    i == lyrics.current -> SungLine(line, clock, big, look, axis = null)
                    i == picked -> Text(line.text, style = big, color = scheme.onBackground, modifier = Modifier.background(scheme.onBackground.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp))
                    else -> Text(line.text, style = big, color = scheme.onBackground.copy(alpha = if (i < lyrics.current) 0.32f else 0.5f))
                }
            }
        }
        TvProgress(now.fraction, now.played, now.total, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(TvSafe))
    }
}

/**
 * The queue (TV): a panel over the player's right side, the playing song first, the rest under "Up next". Focus
 * opens on the next song; OK plays it, holding OK opens its menu (play next, remove); Back or Left closes the panel.
 */
@Composable
fun BoxScope.TvQueuePanel(source: String?, songs: List<TvSong>, current: Int, onSong: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Transparent, scheme.background.copy(alpha = 0.85f)))))
    Column(
        modifier.align(Alignment.CenterEnd).fillMaxHeight().width(460.dp).background(scheme.surface.copy(alpha = 0.96f)).padding(vertical = 27.dp, horizontal = 24.dp).testTag(TAG_QUEUE),
    ) {
        Text(tr(R.string.up_next), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
        if (source != null) Text(tr(R.string.tv_playing_from, source), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.rememberedFocus(), contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(songs) { i, s ->
                TvSongRow(
                    s.title, s.artist, s.length, { onSong(i) },
                    modifier = Modifier.tvItem("$TAG_QUEUE/$i", entry = i == current + 1),
                    art = s.art, playing = i == current,
                )
            }
        }
    }
}

/**
 * A music video (TV): the video fills the screen; any key shows these controls over its bottom for a few seconds
 * (title, progress, play/pause, previous, next, back to the song), then they fade. Focus opens on play/pause.
 */
@Composable
fun BoxScope.TvVideoControls(now: TvNow, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSong: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 48.dp, end = 48.dp, top = 72.dp, bottom = 27.dp).testTag(TAG_VIDEO),
    ) {
        Text(now.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
        Text(now.artist, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        Spacer(Modifier.height(14.dp))
        TvProgress(now.fraction, now.played, now.total)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            TvIconButton(PrismIcons.Previous, tr(R.string.previous_song), onPrevious, Modifier.tvItem("$TAG_VIDEO/previous"))
            TvIconButton(if (now.playing) PrismIcons.Pause else PrismIcons.Play, tr(if (now.playing) R.string.widget_pause else R.string.widget_play), onPlayPause, Modifier.tvItem("$TAG_VIDEO/playpause", entry = true), big = true)
            TvIconButton(PrismIcons.Next, tr(R.string.next_song), onNext, Modifier.tvItem("$TAG_VIDEO/next"))
            Spacer(Modifier.width(12.dp))
            TvButton(tr(R.string.back_to_the_song), onSong, Modifier.tvItem("$TAG_VIDEO/song"), icon = PrismIcons.Song)
        }
    }
}

/**
 * Sing (TV): karaoke night. The lyrics screen with the voice taken out: the song's key under the title and a
 * "Vocals out" mark; the lyrics as large as they go. Only on TVs fast enough (Sing's speed check), else it says so.
 */
@Composable
fun TvSingScreen(now: TvNow, lyrics: TvLyrics, clock: State<Long>, key: String, modifier: Modifier = Modifier, accent: Color? = null) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize()) {
        TvLyricsScreen(now, lyrics, clock, accent = accent)
        Row(Modifier.align(Alignment.TopEnd).padding(TvSafe), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvChip(tr(R.string.tv_vocals_out), PrismIcons.Mic, scheme.primary, scheme.onPrimary)
            TvChip(tr(R.string.tv_key_line, key), null, scheme.surfaceVariant, scheme.onSurface)
        }
    }
}

/** A small rounded label (not focusable): "Vocals out", "Key: +2". */
@Composable
fun TvChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, fill: Color, ink: Color) {
    Row(Modifier.background(fill, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { androidx.tv.material3.Icon(icon, null, Modifier.size(18.dp), tint = ink); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

/** One action in a side panel (the player's ⋯). */
data class TvAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

/**
 * A panel of actions over the right side of a screen (the player's ⋯: go to the album, the artist, Sing…). Focus
 * opens on the first; Back or Left closes it (the caller's BackHandler).
 */
@Composable
fun BoxScope.TvActionPanel(title: String, actions: List<TvAction>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Transparent, scheme.background.copy(alpha = 0.85f)))))
    Column(
        modifier.align(Alignment.CenterEnd).fillMaxHeight().width(400.dp).background(scheme.surface.copy(alpha = 0.96f)).padding(vertical = 27.dp, horizontal = 24.dp).testTag(TAG_ACTIONS),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        actions.forEachIndexed { i, a -> TvButton(a.label, a.onClick, Modifier.fillMaxWidth().tvItem("$TAG_ACTIONS/$i", entry = i == 0), icon = a.icon) }
    }
}

/**
 * The lyrics page's states around [TvLyricsScreen]: looking for lyrics, none found, or the lines. The lines take the
 * remote: ↑ ↓ pick a line (it lights up), OK plays from the picked line (or pauses and plays with none picked).
 */
@Composable
fun TvLyricsPlace(
    now: TvNow,
    lines: List<com.prism.music.data.lyrics.LyricLine>,
    current: Int,
    clock: State<Long>,
    loading: Boolean,
    unavailable: Boolean,
    source: String?,
    accent: Color?,
    onPlayPause: () -> Unit = {},
    onSeek: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var picked by androidx.compose.runtime.remember(lines) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize().tvBackdrop(accent, scheme.background), contentAlignment = Alignment.Center) {
            Text(
                tr(if (loading || !unavailable) R.string.looking_for_lyrics else R.string.no_lyrics_found_for_this_song),
                Modifier.tvItem("$TAG_LYRICS/none", entry = true).focusable(),
                style = MaterialTheme.typography.headlineMedium, color = scheme.onSurfaceVariant,
            )
        }
        return
    }
    TvLyricsScreen(
        now, TvLyrics(lines, current, clock.value, source), clock, picked = picked, accent = accent,
        modifier = Modifier.onPreviewKeyEvent { e ->
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                androidx.compose.ui.input.key.Key.DirectionUp -> { picked = ((picked ?: current) - 1).coerceAtLeast(0); true }
                androidx.compose.ui.input.key.Key.DirectionDown -> { picked = ((picked ?: current) + 1).coerceAtMost(lines.lastIndex); true }
                androidx.compose.ui.input.key.Key.DirectionCenter, androidx.compose.ui.input.key.Key.Enter -> {
                    val p = picked
                    if (p != null) { onSeek(lines[p].timeMs); picked = null } else onPlayPause()
                    true
                }
                else -> false
            }
        },
    )
}

/** How long the video's controls stay after the last key. */
private const val VIDEO_CONTROLS_MS = 4_000L

/**
 * A music video full screen ([video] draws it): any key shows the controls ([TvVideoControls]) for a few seconds.
 * While the video isn't ready ([ready] false) the song's cover stands in.
 */
@Composable
fun TvVideoPlace(
    now: TvNow,
    ready: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSong: () -> Unit,
    video: @Composable () -> Unit,
) {
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var touched by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val screen = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(touched, now.playing) {
        if (now.playing) {
            kotlinx.coroutines.delay(VIDEO_CONTROLS_MS)
            shown = false
            // The controls had focus: the video takes it back.
            kotlinx.coroutines.delay(50)
            runCatching { screen.requestFocus() }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag(TAG_VIDEO)
            .onPreviewKeyEvent { e ->
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                    touched = System.nanoTime()
                    if (!shown) { shown = true; return@onPreviewKeyEvent e.key != androidx.compose.ui.input.key.Key.Back }
                }
                false
            }
            // While the controls are hidden this holds focus (focus is never nowhere).
            .focusRequester(screen).tvItem("$TAG_VIDEO/screen", entry = true).focusable(),
    ) {
        if (ready) video() else Artwork(now.art, Modifier.align(Alignment.Center).size(360.dp), shape = RoundedCornerShape(18.dp), size = 1080)
        if (shown) TvVideoControls(now, onPlayPause, onNext, onPrevious, onSong)
    }
}

const val TAG_ACTIONS = "tv_actions"
const val TAG_PLAYER = "tv_player"
const val TAG_LYRICS = "tv_lyrics"
const val TAG_QUEUE = "tv_queue"
const val TAG_VIDEO = "tv_video"
