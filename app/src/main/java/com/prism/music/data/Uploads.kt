package com.prism.music.data

import android.content.Context
import com.prism.music.AppContainer
import com.prism.music.data.model.AlbumItem
import com.prism.music.data.innertube.Parser
import com.prism.music.data.model.ArtistItem
import com.prism.music.data.model.BrowseItem
import com.prism.music.data.model.Song
import com.prism.music.data.prefs.LibraryTile
import com.prism.music.playback.AudioDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * The music the listener uploaded to YouTube Music, for Library → Uploads (#332, #101). Signed in only. Anything YouTube
 * answers that Prism can't read means no tile and a line in the diagnostics, never a broken page: this was built from
 * ytmusicapi's reading of these answers, not from an account with uploads (see [com.prism.music.data.innertube.Parser]).
 */
class Uploads(private val c: AppContainer) {
    private val prefs = c.app.getSharedPreferences("uploads", Context.MODE_PRIVATE)

    /** This account has uploads, as far as last checked: whether Library offers the tile. */
    val has = MutableStateFlow(false)

    data class Lists(val songs: List<Song> = emptyList(), val albums: List<AlbumItem> = emptyList(), val artists: List<ArtistItem> = emptyList())
    sealed interface State {
        data object Idle : State
        /** Nothing to show yet. */
        data object Loading : State
        /** What's in [lists]; [more]: the rest is still coming, or was left off or didn't come, so the next look asks again. */
        data class Ready(val more: Boolean) : State
        data class Failed(val message: String) : State
    }

    val lists = MutableStateFlow(Lists())
    val state = MutableStateFlow<State>(State.Idle)
    private val loading = Mutex()
    private var listedFor: String? = null

    private fun log(message: String) = runCatching { AudioDiagnostics.log("uploads", message) }

    private fun account() = c.settings.current.let { "${it.authUser}|${it.dataSyncId}" }

    /**
     * Looks once in a while (one request) whether the account has uploads. The first time one does, the tile goes in the
     * listener's tiles, once ever: an account with none sees no change, and a listener who takes it out keeps it out.
     */
    suspend fun check(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        if (!c.settings.current.isLoggedIn) { has.value = false; return@withContext }
        val account = account()
        has.value = prefs.getBoolean("has:$account", false)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checked:$account", 0) < CHECK_EVERY_MS) return@withContext
        if (!c.network.isOnline()) return@withContext
        val page = try {
            c.ytm.uploadedSongsPage()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log("check failed: ${e.javaClass.simpleName}: ${e.message}")
            return@withContext
        }
        prefs.edit().putLong("checked:$account", now).apply()
        noted("check", page.rows, page.items.size)
        found(account, page.items.isNotEmpty())
    }

    private fun found(account: String, any: Boolean) {
        has.value = any
        prefs.edit().putBoolean("has:$account", any).apply()
        if (!any || prefs.getBoolean("tile_added", false)) return
        prefs.edit().putBoolean("tile_added", true).apply()
        c.settings.setLibraryTiles(LibraryTile.withUploads(c.settings.current.libraryTiles))
    }

    /** Rows came but none could be read: YouTube's answer isn't the shape Prism was written from. */
    private fun noted(what: String, rows: Int, read: Int) {
        log("$what: $rows rows, $read read" + if (rows > 0 && read == 0) " (the answer's shape isn't the expected one)" else "")
    }

    /**
     * Lists everything, the first songs as soon as they're in and the rest behind them. Offline, or when asking fails,
     * the lists as last kept. Albums and artists YouTube won't list are read from the songs. A list that stopped on a
     * failure isn't kept: it would replace a fuller copy.
     */
    suspend fun load(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        if (!c.settings.current.isLoggedIn) { state.value = State.Idle; lists.value = Lists(); return@withContext }
        // A visit waits for a look that's still letting go (its page was left a moment ago), then reads on from there.
        if (force) { if (!loading.tryLock()) return@withContext } else loading.lock()
        try {
            // Another account's lists don't stay on show.
            if (listedFor != account()) { lists.value = Lists(); state.value = State.Idle }
            if (!force && (state.value as? State.Ready)?.more == false) return@withContext
            listedFor = account()
            val kept = c.offline.uploads()
            if (kept != null) lists.value = Lists(kept.songs, kept.albums, kept.artists)
            fun fallback(message: String) {
                state.value = if (kept != null) State.Ready(false) else State.Failed(message)
            }
            if (!c.network.isOnline()) return@withContext fallback(com.prism.music.tr(com.prism.music.R.string.you_re_offline_check_your_connection))
            if (kept == null) state.value = State.Loading
            val account = account()
            coroutineScope {
                val albums = async { runCatching { c.ytm.uploadedAlbums() }.onSuccess { noted("albums", it.rows, it.items.size) }.onFailure { log("albums failed: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull() }
                val artists = async { runCatching { c.ytm.uploadedArtists() }.onSuccess { noted("artists", it.rows, it.items.size) }.onFailure { log("artists failed: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull() }
                var page = try {
                    c.ytm.uploadedSongsPage()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    log("songs failed: ${e.javaClass.simpleName}: ${e.message}")
                    albums.cancel(); artists.cancel()
                    return@coroutineScope fallback(e.friendlyMessage())
                }
                noted("songs", page.rows, page.items.size)
                val songs = page.items.toMutableList()
                lists.value = Lists(songs.toList(), kept?.albums.orEmpty(), kept?.artists.orEmpty())
                state.value = State.Ready(true)
                var pages = 1
                var stopped = false
                while (page.next != null && songs.size < OfflineLibrary.MAX_UPLOADS && pages++ < MAX_PAGES) {
                    page = try {
                        c.ytm.uploadedSongsPage(page.next)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        log("songs stopped at ${songs.size}: ${e.javaClass.simpleName}: ${e.message}")
                        stopped = true
                        break
                    }
                    songs += page.items
                    lists.value = lists.value.copy(songs = songs.distinctBy { it.id })
                }
                val all = songs.distinctBy { it.id }
                val albumRows = albums.await()
                val artistRows = artists.await()
                val final = Lists(
                    // What was kept that this look didn't reach stays on show until one does.
                    if (stopped) (all + kept?.songs.orEmpty()).distinctBy { it.id } else all,
                    settled(albumRows, kept?.albums) { LibraryRepository.albumsFrom(all) },
                    settled(artistRows, kept?.artists) { LibraryRepository.artistsFrom(all) },
                )
                val whole = !stopped && albumRows?.next == null && artistRows?.next == null
                lists.value = final
                state.value = State.Ready(!whole)
                found(account, all.isNotEmpty())
                if (whole) c.offline.saveUploads(final.songs, final.albums, final.artists)
                log("listed ${final.songs.size} songs, ${final.albums.size} albums, ${final.artists.size} artists" + if (whole) "" else ", not all of it")
            }
        } finally {
            loading.unlock()
        }
    }

    /** A list as it came: read from the songs when YouTube lists none; what was kept, if asking failed or stopped partway. */
    private fun <T : BrowseItem> settled(rows: Parser.UploadRows<T>?, kept: List<T>?, fromSongs: () -> List<T>): List<T> = when {
        rows == null -> kept.orEmpty().ifEmpty(fromSongs)
        rows.next != null -> (rows.items + kept.orEmpty()).distinctBy { it.id }
        else -> rows.items.ifEmpty(fromSongs)
    }

    private companion object {
        const val CHECK_EVERY_MS = 6 * 3_600_000L
        /** 25 songs a page, as YouTube sends them. */
        const val MAX_PAGES = 200
    }
}
