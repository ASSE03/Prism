package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.theme.PrismIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.CarLyricsStyle
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.LocalUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.NavRow
import com.prism.music.ui.screens.SettingsSection
import com.prism.music.ui.screens.Toggle

/**
 * Settings → Devices & connections (1.7.6, "Car & devices" until 1.7.8): Android Auto (its lyrics and its look, from
 * Lyrics before), then speakers, TVs and the browser remote, Discord and Last.fm (owner, 1.7.8: beside Discord, the
 * other account Prism tells what you play), each a page of its own.
 */
@Composable
internal fun DevicesPage() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val nav = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group(tr(R.string.android_auto)) {
            Toggle(tr(R.string.lyrics_in_the_car), tr(R.string.on_the_car_s_player), s.carLyrics, info = tr(R.string.in_place_of_the_song_title)) { prefs.setCarLyrics(it) }
            if (s.carLyrics) {
                Label(tr(R.string.style), info = s.carLyricsStyle.label + ": " + s.carLyricsStyle.detail)
                Chips(CarLyricsStyle.entries, s.carLyricsStyle, { it.label }) { prefs.setCarLyricsStyle(it) }
                CarLyricsPreview(s.carLyricsStyle)
                if (s.carLyricsStyle != CarLyricsStyle.TEXT) Toggle(
                    tr(R.string.keep_the_song_title), tr(R.string.lyrics_on_the_cover_only), s.carLyricsKeepTitle,
                    info = tr(R.string.the_car_s_small_media_card),
                ) { prefs.setCarLyricsKeepTitle(it) }
                Toggle(tr(R.string.on_this_phone_too), tr(R.string.lock_screen_and_notification), s.carLyricsOnPhone) { prefs.setCarLyricsOnPhone(it) }
            }
            Toggle(
                tr(R.string.covers_in_a_grid), tr(R.string.home_and_library_as_cover_tiles), s.autoGrid,
                info = tr(R.string.the_car_shows_home_s_sections),
            ) { prefs.setAutoGrid(it) }
            Item(tr(R.string.buttons_in_the_car), tr(R.string.beside_play_in_the_order_the), onClick = { nav.go(Routes.settings(SettingsSection.CONTROLS.key, tr(R.string.notification_and_car))) }) { Chevron() }
        }
        Group(tr(R.string.connections)) {
            val dark = com.prism.music.ui.theme.LocalIsDark.current
            listOf(SettingsSection.CONNECT, SettingsSection.DISCORD, SettingsSection.LASTFM, SettingsSection.SPOTIFY).forEach { sec ->
                NavRow(sec.icon, sec.title, sec.summary(s, dark)) { nav.go(Routes.settings(sec.key)) }
            }
            // YouTube Music (live) beside Spotify's own (owner, 1.8.1): the switch stays in Bring your history, next to
            // Takeout, which brings the past; this row is where people look for a connection's history.
            val ytLive by LocalContainer.current.imports.youtubeLive.on.collectAsState()
            val detail = when {
                !s.isLoggedIn -> tr(R.string.ytlive_connection_signed_out)
                ytLive -> tr(R.string.ytlive_connection_on)
                else -> tr(R.string.ytlive_connection_off)
            }
            NavRow(PrismIcons.History, tr(R.string.youtube_music_2), detail) { nav.go(Routes.BRING_HISTORY) }
        }
        SharingGroup()
    }
}

/** What the car shows right now (or would), drawn by the same code that draws it for the car. */
@Composable
private fun CarLyricsPreview(style: CarLyricsStyle) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val song by c.player.currentSong.collectAsState()
    var lyrics by remember { mutableStateOf<com.prism.music.data.lyrics.Lyrics?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var bg by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var card by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var line by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(song?.id) {
        lyrics = null; card = null; line = null; loaded = false
        val s = song ?: return@LaunchedEffect
        lyrics = runCatching { c.lyrics.auto(s) }.getOrNull()?.takeIf { it.synced }
        loaded = true
        bg = runCatching { com.prism.music.playback.CarLyricsCard.background(context, s.thumbnail) }.getOrNull()
    }
    LaunchedEffect(lyrics, style, bg) {
        val l = lyrics ?: return@LaunchedEffect
        while (true) {
            val pos = c.player.lyricPosition
            var idx = com.prism.music.playback.CarLyricsCard.lineAt(l.lines, pos)
            if (idx < 0 || l.lines[idx].isGap) idx = l.lines.indexOfFirst { !it.isGap && it.text.isNotBlank() && it.timeMs >= pos }.takeIf { it >= 0 } ?: idx
            val ln = l.lines.getOrNull(idx)
            if (ln != null && !ln.isGap) {
                val sung = if (style == CarLyricsStyle.KARAOKE && ln.isWordSynced) ln.words.count { it.startMs <= pos } else -1
                line = ln.text
                card = if (style == CarLyricsStyle.TEXT) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    com.prism.music.playback.CarLyricsCard.render(bg, l.lines, idx, sung).asImageBitmap()
                }
            }
            kotlinx.coroutines.delay(300)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = LocalUi.current.shape(14.dp)
        val img = card
        if (img != null) androidx.compose.foundation.Image(img, tr(R.string.lyric_card), Modifier.size(132.dp).clip(shape))
        else com.prism.music.ui.components.Artwork(song?.thumbnail, Modifier.size(132.dp), shape, size = 300)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            val s = song
            when {
                s == null -> Text(tr(R.string.play_a_song_to_see_how), style = MaterialTheme.typography.bodyMedium)
                loaded && lyrics == null -> {
                    Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(tr(R.string.no_synced_lyrics_for_this_song), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text(line ?: s.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                    Text("${s.title} · ${s.artistText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
