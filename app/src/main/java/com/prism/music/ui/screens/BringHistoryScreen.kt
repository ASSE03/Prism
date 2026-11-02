package com.prism.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.data.db.ImportMatchEntity
import com.prism.music.data.imports.HistoryImporter
import com.prism.music.data.imports.HistoryImports
import com.prism.music.data.imports.ImportPreview
import com.prism.music.data.imports.ImportSource
import com.prism.music.data.imports.LibraryJob
import com.prism.music.data.imports.MatchStatus
import com.prism.music.data.model.Song
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.tr
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.components.Artwork
import com.prism.music.ui.components.EmptyState
import com.prism.music.ui.components.PrismButton
import com.prism.music.ui.components.PrismProgressBar
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.components.SubPage
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.LocalUi
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

private fun count(n: Int): String = NumberFormat.getIntegerInstance().format(n)
private fun day(ms: Long?): String = ms?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "–"

/**
 * Bring your history (1.8.1): pick Spotify's data download, a Google Takeout file or Last.fm; see what's in it; choose
 * what to bring; then the imports, how matching is going, "Needs a look", and removing a source.
 */
@Composable
fun BringHistoryScreen(bottomPadding: Dp) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val imports = c.imports
    val summaries by imports.summaries.collectAsState(emptyList())
    val statuses by imports.statuses.collectAsState(emptyList())
    val reading by imports.reading.collectAsState()
    val mobile by imports.matchOnMobile.collectAsState()
    val lastFmUser by c.lastFm.user.collectAsState()

    var source by remember { mutableStateOf<ImportSource?>(null) }
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var looking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<ImportSource?>(null) }
    var history by remember { mutableStateOf(true) }
    var liked by remember { mutableStateOf(true) }
    var playlists by remember { mutableStateOf(true) }
    var albums by remember { mutableStateOf(true) }
    var artists by remember { mutableStateOf(true) }

    fun done(stats: HistoryImporter.Stats?) {
        message = when {
            stats == null -> tr(R.string.imp_cant_read)
            stats.stored == 0 && stats.alreadyThere > 0 -> tr(R.string.imp_already_there, count(stats.alreadyThere))
            else -> tr(R.string.imp_brought, count(stats.stored), count(stats.songs))
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        val s = source ?: return@rememberLauncherForActivityResult
        if (picked.isEmpty()) return@rememberLauncherForActivityResult
        uris = picked; preview = null; message = null; looking = true
        scope.launch {
            val p = runCatching { imports.preview(s, picked) }.getOrNull()
            looking = false
            if (p == null || p.isEmpty) message = tr(R.string.imp_nothing_found) else {
                preview = p
                history = p.plays > 0; liked = p.library.liked.isNotEmpty(); playlists = p.library.playlists.isNotEmpty()
                albums = p.library.albums.isNotEmpty(); artists = p.library.artists.isNotEmpty()
            }
        }
    }

    removing?.let { r ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(tr(R.string.imp_remove_title, r.label)) },
            text = { Text(tr(R.string.imp_remove_text)) },
            confirmButton = { PrismTextButton(onClick = { removing = null; c.scope.launch { imports.remove(r) } }) { Text(tr(R.string.remove)) } },
            dismissButton = { PrismTextButton(onClick = { removing = null }) { Text(tr(R.string.cancel)) } },
        )
    }

    SubPage(tr(R.string.imp_title), bottomPadding, subtitle = tr(R.string.imp_subtitle), horizontalPadding = 16.dp, spacing = 22.dp) {
        item(key = "from") {
            Group(tr(R.string.imp_bring_from), info = tr(R.string.imp_bring_from_info)) {
                Item(ImportSource.SPOTIFY.label, tr(R.string.imp_spotify_detail), onClick = { source = ImportSource.SPOTIFY; picker.launch(arrayOf("*/*")) }, icon = PrismIcons.Library)
                Item(ImportSource.TAKEOUT.label, tr(R.string.imp_takeout_detail), onClick = { source = ImportSource.TAKEOUT; picker.launch(arrayOf("*/*")) }, icon = PrismIcons.History)
                Item(
                    ImportSource.LASTFM.label, lastFmUser?.let { tr(R.string.imp_lastfm_detail, it) } ?: tr(R.string.imp_lastfm_connect),
                    onClick = {
                        if (lastFmUser == null) nav.go(Routes.settings(SettingsSection.LASTFM.key))
                        else if (reading == null) { message = null; preview = null; c.scope.launch { done(imports.bringLastFm()) } }
                    },
                    icon = PrismIcons.Replay,
                )
            }
        }
        reading?.let { r ->
            item(key = "reading") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        r.pages?.let { (done, all) -> tr(R.string.imp_reading_lastfm, count(done), count(all)) } ?: tr(R.string.imp_reading, count(r.plays)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PrismProgressBar(r.pages?.let { (done, all) -> { done / all.coerceAtLeast(1).toFloat() } })
                }
            }
        }
        if (looking) item(key = "looking") { com.prism.music.ui.components.LoadingState() }
        message?.let { m -> item(key = "message") { Text(m, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp)) } }
        preview?.let { p ->
            item(key = "preview") {
                Group(tr(R.string.imp_whats_in_it)) {
                    if (p.plays > 0) {
                        Note(tr(R.string.imp_plays_range, count(p.plays), count(p.songs), day(p.firstAt), day(p.lastAt)))
                        Toggle(tr(R.string.imp_history), tr(R.string.imp_history_detail), history) { history = it }
                    }
                    val lib = p.library
                    if (lib.liked.isNotEmpty()) {
                        Toggle(tr(R.string.imp_liked_songs), tr(R.string.imp_liked_detail, count(lib.liked.size)), liked, info = tr(R.string.imp_liked_info)) { liked = it }
                        // Off unless asked for: the likes stay in Prism, and library sync leaves them be.
                        if (liked && c.settings.current.isLoggedIn) {
                            val onAccount by imports.likeOnAccount.collectAsState()
                            Toggle(tr(R.string.imp_like_on_account), tr(R.string.imp_like_on_account_detail), onAccount, info = tr(R.string.imp_like_on_account_info)) { imports.setLikeOnAccount(it) }
                        }
                    }
                    if (lib.playlists.isNotEmpty()) Toggle(tr(R.string.imp_playlists), tr(R.string.imp_playlists_detail, count(lib.playlists.size)), playlists) { playlists = it }
                    if (lib.albums.isNotEmpty()) Toggle(tr(R.string.imp_albums), tr(R.string.imp_albums_detail, count(lib.albums.size)), albums) { albums = it }
                    if (lib.artists.isNotEmpty()) Toggle(tr(R.string.imp_artists), tr(R.string.imp_artists_detail, count(lib.artists.size)), artists) { artists = it }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrismButton(
                            onClick = {
                                val choice = HistoryImports.Choice(history, LibraryJob.LibraryChoice(liked, playlists, albums, artists))
                                val s = p.source
                                val picked = uris
                                preview = null
                                c.scope.launch { done(runCatching { imports.bring(s, picked, choice, p) }.getOrNull()) }
                            },
                            enabled = reading == null && (history || liked || playlists || albums || artists),
                        ) { Text(tr(R.string.imp_bring_them)) }
                        PrismTextButton(onClick = { preview = null }) { Text(tr(R.string.cancel)) }
                    }
                }
            }
        }
        if (summaries.isNotEmpty()) {
            item(key = "mine") {
                Group(tr(R.string.imp_your_imports)) {
                    summaries.forEach { s ->
                        val src = ImportSource.of(s.origin) ?: return@forEach
                        val pct = if (s.plays > 0) s.matched * 100 / s.plays else 0
                        Item(src.label, tr(R.string.imp_source_line, count(s.plays), day(s.firstAt), day(s.lastAt), pct), onClick = {}) {
                            Icon(
                                PrismIcons.Delete, tr(R.string.remove),
                                Modifier.size(40.dp).clickable { removing = src }.padding(8.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        val byStatus = statuses.associate { MatchStatus.of(it.status) to it.n }
        if (byStatus.isNotEmpty()) {
            item(key = "matching") {
                Group(tr(R.string.imp_matching)) {
                    val found = (byStatus[MatchStatus.SURE] ?: 0) + (byStatus[MatchStatus.FIXED] ?: 0)
                    Note(tr(R.string.imp_matching_line, count(found), count(byStatus[MatchStatus.PENDING] ?: 0), count(byStatus[MatchStatus.NONE] ?: 0)))
                    val review = byStatus[MatchStatus.REVIEW] ?: 0
                    if (review > 0) Item(tr(R.string.imp_needs_a_look), tr(R.string.imp_needs_a_look_n, count(review)), onClick = { nav.go(Routes.IMPORT_REVIEW) }) {
                        Icon(PrismIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Toggle(tr(R.string.imp_match_on_mobile), tr(R.string.imp_match_on_mobile_detail), mobile) { imports.setMatchOnMobile(it) }
                }
            }
        }
    }
}

/** "Needs a look" (1.8.1): each song Prism wasn't sure of, with up to three candidates to play and pick, or none. */
@Composable
fun ImportReviewScreen(bottomPadding: Dp) {
    val c = LocalContainer.current
    val review by c.imports.review.collectAsState(null)
    SubPage(tr(R.string.imp_needs_a_look), bottomPadding, subtitle = tr(R.string.imp_needs_a_look_subtitle), horizontalPadding = 16.dp, spacing = 18.dp) {
        val list = review
        if (list == null) { item { com.prism.music.ui.components.LoadingState() }; return@SubPage }
        if (list.isEmpty()) { item { EmptyState(PrismIcons.Check, tr(R.string.imp_all_sorted), tr(R.string.imp_all_sorted_body)) }; return@SubPage }
        list.forEach { m -> item(key = m.songKey) { ReviewCard(m) } }
    }
}

@Composable
private fun ReviewCard(m: ImportMatchEntity) {
    val c = LocalContainer.current
    val ui = LocalUi.current
    val candidates = remember(m.candidates) { HistoryImporter.candidatesOf(m) }
    val length = m.durationMs.takeIf { it > 0 }?.let { tr(R.string.imp_length, clock(it / 1000)) }
    Group("${m.title} · ${m.artist}") {
        listOfNotNull(m.album, length).joinToString(" · ").takeIf { it.isNotBlank() }?.let { Note(it) }
        candidates.forEach { s -> CandidateRow(s, onPlay = {
            c.player.playQueue(listOf(s), 0, QueueSource(QueueKind.SINGLE, s.title))
        }) { c.scope.launch { c.imports.fix(m.songKey, s) } } }
        Item(tr(R.string.imp_none_of_these), null, onClick = { c.scope.launch { c.imports.fix(m.songKey, null) } }, icon = PrismIcons.Close)
        Spacer(Modifier.size(ui.gap(2.dp)))
    }
}

@Composable
private fun CandidateRow(s: Song, onPlay: () -> Unit, onPick: () -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(s.thumbnail, Modifier.size(48.dp).clickable(onClick = onPlay), ui.smallArt, size = 120)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                listOfNotNull(s.artistText, s.album?.title, s.durationSec.takeIf { it > 0 }?.let { clock(it.toLong()) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            )
        }
        Icon(PrismIcons.Play, tr(R.string.play, s.title), Modifier.size(40.dp).clickable(onClick = onPlay).padding(8.dp), tint = MaterialTheme.colorScheme.onSurface)
        PrismTextButton(onClick = onPick) { Text(tr(R.string.imp_this_one)) }
    }
}

private fun clock(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)
