package com.prism.music.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.Artwork
import com.prism.music.ui.theme.PrismIcons

/**
 * A screen's light behind everything (TV, 1.8.0): the cover's color glowing from the top corner into the background.
 * No blur and no motion (TVs before Android 12 can't blur, and slow ones stutter on moving backgrounds).
 */
fun Modifier.tvBackdrop(accent: Color?, base: Color): Modifier = drawBehind {
    drawRect(base)
    if (accent != null) drawRect(
        Brush.radialGradient(
            listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(size.width * 0.18f, 0f), radius = size.width * 0.75f,
        ),
    )
}

/**
 * Home (TV): what's playing as a large hero at the top, then rows the remote moves through (Quick picks, Speed dial,
 * your mixes, recently played, YouTube's shelves). Focus opens on the hero's "Now playing" (or the first row's first
 * card when nothing plays); each row remembers its column.
 */
@Composable
fun TvHomeScreen(
    greeting: String,
    now: TvNow?,
    shelves: List<TvShelf>,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onItem: (TvItem) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    banner: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // Rows run to the screen's edges (their cards grow when focused, and slide in from the edge); everything else keeps
    // the 48 dp margins.
    LazyColumn(
        modifier.fillMaxSize().tvBackdrop(accent, scheme.background).testTag(TAG_HOME),
        contentPadding = PaddingValues(top = 27.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (banner != null) item { Box(Modifier.padding(horizontal = 48.dp)) { banner() } }
        item {
            Box(Modifier.padding(horizontal = 48.dp)) {
                if (now != null) TvNowHero(now, onPlayPause, onOpenPlayer, onOpenLyrics)
                else Text(greeting, style = MaterialTheme.typography.displaySmall, color = scheme.onBackground)
            }
        }
        itemsIndexed(shelves) { r, shelf ->
            Column {
                TvRowTitle(shelf.title, Modifier.padding(start = 48.dp))
                LazyRow(
                    Modifier.rememberedFocus().testTag("$TAG_HOME/row$r"),
                    contentPadding = TvRowPadding,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(shelf.items) { i, item ->
                        val first = now == null && r == 0 && i == 0
                        TvCard(
                            item.art, item.title, item.subtitle, { onItem(item) },
                            modifier = (if (first) Modifier.entryFocus() else Modifier).testTag("$TAG_HOME/row$r/$i"),
                            round = item.kind == TvItem.Kind.ARTIST,
                        )
                    }
                }
            }
        }
    }
}

/** Home's hero: the playing song's cover, title, artist and progress, with play/pause, the player and its lyrics. */
@Composable
private fun TvNowHero(now: TvNow, onPlayPause: () -> Unit, onOpenPlayer: () -> Unit, onOpenLyrics: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(196.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(now.art, Modifier.size(196.dp), shape = RoundedCornerShape(14.dp), size = 544)
        Spacer(Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(R.string.now_playing).uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = scheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(now.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now.artist, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            TvProgress(now.fraction, now.played, now.total, Modifier.width(420.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(
                    if (now.playing) PrismIcons.Pause else PrismIcons.Play,
                    tr(if (now.playing) R.string.widget_pause else R.string.widget_play), onPlayPause, Modifier.testTag("$TAG_HOME/playpause"),
                )
                TvButton(tr(R.string.now_playing), onOpenPlayer, Modifier.entryFocus().testTag("$TAG_HOME/player"), icon = PrismIcons.PlayCircle, primary = true)
                if (now.hasLyrics) TvButton(tr(R.string.lyrics), onOpenLyrics, Modifier.testTag("$TAG_HOME/lyrics"), icon = PrismIcons.Lyrics)
            }
        }
    }
}

/** The update banner on Home (TV): a new Prism is out; OK downloads and installs it (Android asks once to allow it). */
@Composable
fun TvUpdateBanner(version: String, onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().background(scheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr(R.string.tv_update_ready, version), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(tr(R.string.tv_update_note), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        TvButton(tr(R.string.update), onUpdate, Modifier.testTag("$TAG_HOME/update"), icon = PrismIcons.Update, primary = true)
    }
}

const val TAG_HOME = "tv_home"
