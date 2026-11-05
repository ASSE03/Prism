package com.prism.music.download

import android.content.Context
import com.prism.music.AppContainer
import com.prism.music.data.PrismLibrary
import com.prism.music.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloading a whole list: which of its songs count (#221) and what keeping it downloaded still has to fetch (#298).
 * Pure, so what decides a playlist's downloads can be tested without a phone.
 */
object PlaylistDownloads {
    /** [id] was marked to skip, itself or (for a video row) the song it comes down as. */
    fun isSkipped(id: String, skipped: Set<String>, standIns: Map<String, String>): Boolean =
        id in skipped || standIns[id]?.let { it in skipped } == true

    /** The songs a list's download means: all but the skipped. */
    fun wanted(songs: List<Song>, skipped: Set<String>, standIns: Map<String, String>): List<Song> =
        if (skipped.isEmpty()) songs else songs.filterNot { isSkipped(it.id, skipped, standIns) }

    /** A list counts as downloaded when every song it wants is here; one with nothing it wants doesn't. */
    fun allDownloaded(songs: List<Song>, skipped: Set<String>, standIns: Map<String, String>, done: (String) -> Boolean): Boolean {
        val want = wanted(songs, skipped, standIns)
        return want.isNotEmpty() && want.all { done(it.id) }
    }

    /**
     * What keeping a list downloaded still has to fetch: its wanted songs with no download of any kind yet. One that
     * failed before is left for Failed downloads ([failed] and failed entries in [known]), so a song YouTube won't serve
     * isn't asked for again each time the list is looked at.
     */
    fun toFetch(
        songs: List<Song>, skipped: Set<String>, standIns: Map<String, String>, known: Set<String>, failed: Set<String>,
    ): List<Song> = wanted(songs, skipped, standIns).filter { s ->
        val kept = standIns[s.id] ?: s.id
        !s.isLocal && !s.isRss && !s.isLive && s.id !in known && kept !in known && s.id !in failed && kept !in failed
    }

    /** May a kept list be read and fetched now: online, and on Wi-Fi if downloads wait for that. */
    fun mayFetch(online: Boolean, metered: Boolean, wifiOnly: Boolean) = online && (!metered || !wifiOnly)

    /** How a playlist is remembered, whichever way it was opened ("VLPL…" or "PL…"). */
    fun keepKey(id: String) = id.removePrefix("VL")

    /** Whether a playlist can be kept: a mix is different every time it's opened. */
    fun canKeep(id: String) = !PrismLibrary.isMix(id) && keepKey(id) != "LM"

    /** A YouTube playlist is read again at most this often by itself, since each read is a request. */
    const val READ_GAP_MS = 60 * 60_000L

    fun readDue(lastRead: Long, now: Long) = now - lastRead >= READ_GAP_MS || lastRead > now
}

/**
 * Songs marked to skip when a whole list downloads (#221), by song id, wherever they're listed: the same song stays
 * skipped in another playlist, and Failed downloads has no playlist to name. A song downloaded by itself still downloads.
 */
class DownloadSkips(context: Context) {
    private val prefs = context.getSharedPreferences("download_skips", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow<Set<String>>(prefs.all.keys.toSet())
    val ids: StateFlow<Set<String>> = _ids

    fun set(id: String, skip: Boolean) {
        if (skip) prefs.edit().putBoolean(id, true).apply() else prefs.edit().remove(id).apply()
        _ids.value = if (skip) _ids.value + id else _ids.value - id
    }
}

/**
 * Playlists kept downloaded (#298, idea and first version by stuntsnakee in PR #304): new songs in a chosen playlist come
 * down by themselves. Following the Wi-Fi-only setting, this doesn't even read the playlist while it would have to wait.
 * Skipped songs ([DownloadSkips]) and failed ones are left alone. Nothing is ever deleted here: a song taken out of the
 * playlist keeps its download.
 */
class KeptPlaylists(private val c: AppContainer) {
    private val reads by lazy { c.app.getSharedPreferences("kept_playlists", Context.MODE_PRIVATE) }

    private fun kept() = c.settings.current.behavior.keptPlaylists

    private fun mayFetch() = PlaylistDownloads.mayFetch(c.network.isOnline(), c.network.metered.value, c.settings.current.downloadWifiOnly)

    /** Keeps [id] from now on, starting with what it has now (or stops, leaving its downloads as they are). */
    fun set(id: String, keep: Boolean) {
        c.settings.setKeptPlaylist(PlaylistDownloads.keepKey(id), keep)
        if (keep) c.scope.launch(Dispatchers.IO) { runCatching { sync(PlaylistDownloads.keepKey(id), now = true) } }
    }

    /** While any playlist is kept: each time downloads may run again, and when a song goes into one of Prism's own. */
    suspend fun watch(): Unit = coroutineScope {
        // Not with Prism's own start, nor before the first song has come in.
        delay(START_WAIT_MS)
        launch {
            combine(c.network.online, c.network.metered, c.settings.flow.map { it.downloadWifiOnly }.distinctUntilChanged()) { on, metered, wifiOnly ->
                PlaylistDownloads.mayFetch(on, metered, wifiOnly)
            }.distinctUntilChanged().collectLatest { may -> if (may) { c.quietNetwork(); syncAll() } }
        }
        launch {
            c.prismLibrary.lists.drop(1).collectLatest {
                delay(2_000)
                if (mayFetch()) kept().filter { it.startsWith(PrismLibrary.PREFIX) }.forEach { id -> runCatching { sync(id) } }
            }
        }
    }

    /** Every kept playlist, those on YouTube only when they haven't been read lately. */
    suspend fun syncAll() = withContext(Dispatchers.IO) {
        if (!mayFetch()) return@withContext
        for (id in kept()) {
            try { sync(id) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Next time. */ }
        }
    }

    /** The songs an open page has loaded already: no request for the playlist, just what's on it. */
    fun fromPage(id: String, songs: List<Song>) {
        if (PlaylistDownloads.keepKey(id) !in kept() || !mayFetch()) return
        fetch(songs)
    }

    private suspend fun sync(id: String, now: Boolean = false) {
        if (!mayFetch()) return
        val songs = if (id.startsWith(PrismLibrary.PREFIX)) {
            // One of Prism's own, deleted: nothing left to keep.
            val list = c.prismLibrary.list(id)
            if (list == null) c.settings.setKeptPlaylist(id, false)
            list?.songs.orEmpty()
        } else {
            val time = System.currentTimeMillis()
            if (!now && !PlaylistDownloads.readDue(reads.getLong(id, 0L), time)) return
            reads.edit().putLong(id, time).apply()
            c.ytm.playlistAll(id).songs
        }
        fetch(songs)
    }

    private fun fetch(songs: List<Song>) {
        val d = c.downloads
        // What the download list says, not what's been loaded of it yet: a finished song added again would be fetched again.
        val known = d.knownIds() ?: return
        PlaylistDownloads.toFetch(songs, c.downloadSkips.ids.value, c.downloadStandIns.value, known, d.failures.value.keys).forEach(d::download)
    }

    private companion object {
        const val START_WAIT_MS = 20_000L
    }
}
