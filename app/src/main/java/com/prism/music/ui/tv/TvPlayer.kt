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
                    TvIconButton(PrismIcons.Shuffle, tr(R.string.shuffle), onShuffle, Modifier.testTag("$TAG_PLAYER/shuffle"), on = now.shuffle)
                    TvIconButton(PrismIcons.Previous, tr(R.string.previous_song), onPrevious, Modifier.testTag("$TAG_PLAYER/previous"))
                    TvIconButton(
                        if (now.playing) PrismIcons.Pause else PrismIcons.Play, tr(if (now.playing) R.string.widget_pause else R.string.widget_play),
                        onPlayPause, Modifier.entryFocus().testTag("$TAG_PLAYER/playpause"), big = true,
                    )
                    TvIconButton(PrismIcons.Next, tr(R.string.next_song), onNext, Modifier.testTag("$TAG_PLAYER/next"))
                    TvIconButton(
                        PrismIcons.Repeat, tr(when (now.repeat) { 1 -> R.string.repeat_one; 2 -> R.string.repeat_all; else -> R.string.repeat_off }),
                        onRepeat, Modifier.testTag("$TAG_PLAYER/repeat"), on = now.repeat != 0,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (now.hasLyrics) TvButton(tr(R.string.lyrics), onLyrics, Modifier.testTag("$TAG_PLAYER/lyrics"), icon = PrismIcons.Lyrics)
                    TvButton(tr(R.string.queue), onQueue, Modifier.testTag("$TAG_PLAYER/queue"), icon = PrismIcons.Queue)
                    if (now.hasVideo) TvButton(tr(R.string.video), onVideo, Modifier.testTag("$TAG_PLAYER/video"), icon = PrismIcons.Video)
                    TvIconButton(if (now.liked) PrismIcons.HeartFilled else PrismIcons.Heart, tr(if (now.liked) R.string.liked else R.string.widget_like), onLike, Modifier.testTag("$TAG_PLAYER/like"), on = now.liked)
                    TvIconButton(PrismIcons.More, tr(R.string.more), onMore, Modifier.testTag("$TAG_PLAYER/more"))
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
    val big = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold)
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
            Modifier.fillMaxSize().padding(start = 96.dp, end = 160.dp, top = 120.dp, bottom = 96.dp).entryFocus().focusable().testTag("$TAG_LYRICS/lines"),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // The line before the sung one, the sung one, and what comes next.
            val from = (lyrics.current - 1).coerceAtLeast(0)
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
                    modifier = (if (i == current + 1) Modifier.entryFocus() else Modifier).testTag("$TAG_QUEUE/$i"),
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
            TvIconButton(PrismIcons.Previous, tr(R.string.previous_song), onPrevious, Modifier.testTag("$TAG_VIDEO/previous"))
            TvIconButton(if (now.playing) PrismIcons.Pause else PrismIcons.Play, tr(if (now.playing) R.string.widget_pause else R.string.widget_play), onPlayPause, Modifier.entryFocus().testTag("$TAG_VIDEO/playpause"), big = true)
            TvIconButton(PrismIcons.Next, tr(R.string.next_song), onNext, Modifier.testTag("$TAG_VIDEO/next"))
            Spacer(Modifier.width(12.dp))
            TvButton(tr(R.string.back_to_the_song), onSong, Modifier.testTag("$TAG_VIDEO/song"), icon = PrismIcons.Song)
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

const val TAG_PLAYER = "tv_player"
const val TAG_LYRICS = "tv_lyrics"
const val TAG_QUEUE = "tv_queue"
const val TAG_VIDEO = "tv_video"
