package com.prism.music.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.prism.music.AppContainer
import com.prism.music.R
import com.prism.music.data.db.mergeUploads
import com.prism.music.data.foryou.ReleaseRadar
import com.prism.music.data.foryou.YourMusic
import com.prism.music.data.model.AlbumItem
import com.prism.music.data.model.BrowseItem
import com.prism.music.data.model.MoodItem
import com.prism.music.data.model.PlaylistItem
import com.prism.music.data.model.Song
import com.prism.music.data.model.quickPicks
import com.prism.music.data.model.songCount
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.HomeSectionConfig
import com.prism.music.data.prefs.HomeSections
import com.prism.music.data.prefs.HomeShortcut
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.tr
import com.prism.music.ui.screens.OwnSections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

/*
 * The TV Home's rows made from your own music (1.8.0, the owner: "I do not see things like Prism Music mixes or Quick
 * picks etc. that match my phone app"), from the same places the phone's Home reads them: Made for you (ForYou's mixes
 * and YourMusic's), On repeat, Repeat rewind, Forgotten favorites, New from your artists, the Replay card, Moods &
 * genres, the shortcut tiles, Liked songs, Your top genres, Downloads and your pinned items and rules. Each is only
 * worked out while Home shows it (a hidden row costs nothing), off the main thread, and kept for the session so coming
 * back to Home shows it at once.
 */

/** What the rows were last, for this run of Prism (coming back to Home shows them at once). */
internal object TvHomeOwn {
    @Volatile var picks: List<Song>? = null
    @Volatile var yours: YourMusic.Sections? = null
    @Volatile var replay: TvReplay? = null
    @Volatile var moods: List<MoodItem>? = null
    @Volatile var genres: List<Pair<String, List<Song>>>? = null

    /** Signing out: nothing of the account's is kept (its Quick picks, its moods). */
    fun clear() { picks = null; yours = null; replay = null; moods = null; genres = null }
}

/** The year so far: time listened and the top songs (best first), as of [at]. */
internal class TvReplay(val at: Long, val ms: Long, val top: List<Song>)

/** How long the Replay card's year counts as fresh. */
private const val REPLAY_FRESH_MS = 5 * 60_000L

private suspend fun loadReplay(c: AppContainer): TvReplay = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val start = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val ids = runCatching { c.db.plays().topSongs(start, now, 50).mergeUploads().map { it.songId } }.getOrDefault(emptyList())
    val byId = runCatching { c.db.songs().getAll(ids).associateBy { it.id } }.getOrDefault(emptyMap())
    TvReplay(now, runCatching { c.db.plays().totalMs(start, now) }.getOrDefault(0L), ids.mapNotNull { byId[it]?.toSong() })
}

/** A row's name: the listener's own name for it (the phone's Customize Home), else Prism's, in their language. */
internal fun tvRowTitle(phone: List<HomeSectionConfig>, key: String, own: String? = null): String =
    phone.firstOrNull { it.key == key }?.name ?: own ?: HomeSections.inYourLanguage(HomeSectionConfig(key, phone.firstOrNull { it.key == key }?.title ?: key)).title

/**
 * The rows Prism makes from your own music for the TV's Home, for the ones it shows ([TvHomeLayout.isShown]); empty
 * ones are left out. [online]: offline, lists keep to songs that play without a connection, as on the phone.
 */
@Composable
internal fun rememberTvOwnRows(
    c: AppContainer, settings: AppSettings, online: Boolean, recent: List<Song>,
    shelves: List<com.prism.music.data.model.Shelf> = emptyList(), owner: String? = null,
): List<TvOwnRow> {
    val stored = settings.tv.homeLayout
    val phone = settings.homeLayout
    fun want(k: String) = TvHomeLayout.isShown(k, stored, phone, settings.autoAddSections)
    val offline: (Song) -> Boolean = { online || c.availableOffline(it.id) }
    val lastPlayed = recent.firstOrNull()?.id

    // Quick picks as the phone makes them: around the artists you play (favorites, new songs from radios of your top
    // artists, YouTube's own that fit), once a session; until then (or offline) YouTube's own.
    val wantPicks = online && want(HomeSections.QUICK_PICKS)
    val youtubePicks = remember(shelves, owner) {
        shelves.quickPicks(owner)?.items?.filterIsInstance<com.prism.music.data.model.SongItem>()?.map { it.song }.orEmpty()
    }
    val picks by produceState(TvHomeOwn.picks, wantPicks, youtubePicks.isNotEmpty() || recent.isNotEmpty()) {
        if (!wantPicks || TvHomeOwn.picks != null || (youtubePicks.isEmpty() && recent.isEmpty())) return@produceState
        val made = withContext(Dispatchers.IO) {
            runCatching { c.yourMusic.quickPicks(youtubePicks, radioOf = { seed: Song -> c.ytm.next(seed.id, playlistId = "RDAMVM${seed.id}").songs }) }.getOrNull()
        }
        if (!made.isNullOrEmpty()) { value = made; TvHomeOwn.picks = made }
    }

    // Made for you and the lists from your music (On repeat, Repeat rewind, Forgotten favorites).
    val wantMixes = want(HomeSections.MADE_FOR_YOU)
    val wantYours = wantMixes || want(HomeSections.ON_REPEAT) || want(HomeSections.REPEAT_REWIND) || want(HomeSections.FORGOTTEN)
    val mixes by c.forYou.mixes.collectAsState()
    // Made when due (once a day, the time-of-day one as the day moves on), on the app's scope as the phone does.
    LaunchedEffect(wantMixes) { if (wantMixes) c.scope.launch { runCatching { c.forYou.refresh() } } }
    val yours by produceState(TvHomeOwn.yours, wantYours, lastPlayed, online) {
        if (!wantYours) return@produceState
        runCatching { c.yourMusic.sections(offlineOnly = !online) }.getOrNull()?.let { value = it; TvHomeOwn.yours = it }
    }

    // New from your artists (online, as on the phone).
    val wantReleases = online && want(HomeSections.NEW_RELEASES)
    val today = remember { LocalDate.now() }
    val releases by remember(wantReleases) {
        if (!wantReleases) flowOf(emptyList())
        else c.db.releases().freshFlow(today.year - 1).map { l -> l.filter { ReleaseRadar.isRecent(it, today, 30) } }.flowOn(Dispatchers.Default)
    }.collectAsState(emptyList())
    LaunchedEffect(wantReleases) { if (wantReleases && c.releaseRadar.due()) ReleaseRadar.runSoon(c.app) }

    // The Replay card.
    val wantReplay = want(HomeSections.REPLAY)
    val replay by produceState(TvHomeOwn.replay, wantReplay, lastPlayed) {
        if (!wantReplay) return@produceState
        val have = TvHomeOwn.replay
        if (have != null && System.currentTimeMillis() - have.at < REPLAY_FRESH_MS) return@produceState
        runCatching { loadReplay(c) }.getOrNull()?.let { value = it; TvHomeOwn.replay = it }
    }

    // Moods & genres (online).
    val wantMoods = online && want(HomeSections.MOODS)
    val moods by produceState(TvHomeOwn.moods, wantMoods) {
        if (!wantMoods || TvHomeOwn.moods != null) return@produceState
        withContext(Dispatchers.IO) { runCatching { c.taste.rank(c.ytm.moodsAndGenres()).picks }.getOrNull() }?.let { value = it; TvHomeOwn.moods = it }
    }

    // Liked songs, the tiles, your top genres and downloads.
    val liked by c.library.likedShown.collectAsState()
    val dls by c.downloadState.collectAsState()
    val wantDownloads = want(HomeSections.DOWNLOADS) || (want(HomeSections.GREETING) && HomeShortcut.DOWNLOADS in settings.homeShortcuts)
    val downloads by produceState(emptyList<Song>(), wantDownloads, dls.size) {
        if (!wantDownloads || dls.isEmpty()) return@produceState
        value = withContext(Dispatchers.Default) { runCatching { c.downloads.completedSongs() }.getOrDefault(emptyList()) }
    }
    val wantGenres = want(HomeSections.GENRES)
    val genres by produceState(TvHomeOwn.genres, wantGenres, liked.size) {
        if (!wantGenres || liked.isEmpty()) return@produceState
        val sample = liked.take(150)
        runCatching { tvTopGenres(sample, c.meta.cachedGenres(sample)) }.getOrNull()?.let { value = it; TvHomeOwn.genres = it }
    }
    val prismLists by c.prismLibrary.lists.collectAsState()

    // Your pinned albums, playlists, artists and mixes, and the rules over your listening.
    val ownKeys = phone.filter { it.visible && TvHomeLayout.isOwn(it.key) }.map { it.key }
    val own = ownKeys.mapNotNull { k ->
        key(k) {
            val songs by OwnSections.rememberSongs(k, 0)
            tvSongRow(k, tvRowTitle(phone, k), songs.filter(offline))
        }
    }

    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
    return remember(picks, mixes, yours, releases, replay, moods, liked, downloads, genres, prismLists, recent, own, phone, online, settings.homePlaylists, settings.homeShortcuts, settings.homeRecentTiles) {
        buildList {
            if (wantPicks) picks?.let { tvSongRow(HomeSections.QUICK_PICKS, tvRowTitle(phone, HomeSections.QUICK_PICKS), it) }?.let(::add)
            if (wantMixes) {
                val made = mixes.orEmpty().let { list ->
                    if (online) list else list.map { m -> m.copy(songs = m.songs.filter(offline)) }.filter { it.songs.size >= 5 }
                }
                tvMixRow(tvRowTitle(phone, HomeSections.MADE_FOR_YOU), made + yours?.mixes.orEmpty())?.let(::add)
            }
            if (want(HomeSections.ON_REPEAT)) yours?.onRepeat?.let { tvSongRow(HomeSections.ON_REPEAT, tvRowTitle(phone, HomeSections.ON_REPEAT), it) }?.let(::add)
            if (want(HomeSections.REPEAT_REWIND)) yours?.repeatRewind?.let { tvSongRow(HomeSections.REPEAT_REWIND, tvRowTitle(phone, HomeSections.REPEAT_REWIND), it) }?.let(::add)
            if (want(HomeSections.FORGOTTEN)) yours?.forgotten?.let { tvSongRow(HomeSections.FORGOTTEN, tvRowTitle(phone, HomeSections.FORGOTTEN), it) }?.let(::add)
            if (wantReleases) tvReleaseRow(tvRowTitle(phone, HomeSections.NEW_RELEASES), releases)?.let(::add)
            if (wantReplay) replay?.let { r ->
                val top = r.top.firstOrNull()
                tvReplayRow(
                    tr(R.string.replay, year), r.ms, com.prism.music.ui.screens.hoursAndMinutes(r.ms / 60_000), tr(R.string.listened_this_year),
                    r.top, top?.let { tr(R.string.your_top_song, it.artists.joinToString { a -> a.name }) },
                )
            }?.let(::add)
            if (wantMoods) moods?.let { tvMoodRow(tvRowTitle(phone, HomeSections.MOODS), it) }?.let(::add)
            if (want(HomeSections.GREETING)) {
                val likedList = TvPlayList(tr(R.string.liked_songs), liked.filter(offline)).takeIf { HomeShortcut.LIKED in settings.homeShortcuts }
                val dlList = TvPlayList(tr(R.string.downloads), downloads).takeIf { HomeShortcut.DOWNLOADS in settings.homeShortcuts }
                // A Prism playlist shows its current name and cover; a deleted one's pin has nothing to open.
                val pinned: List<BrowseItem> = settings.homePlaylists.mapNotNull { pin ->
                    val prism = pin.id.startsWith(com.prism.music.data.PrismLibrary.PREFIX)
                    val live = prismLists.firstOrNull { it.id == pin.id }?.item()
                    if (prism && live == null) null
                    else PlaylistItem(pin.id, live?.title ?: pin.title, tr(R.string.playlist), c.covers.art(pin.id, live?.thumbnail ?: pin.thumbnail))
                }
                val albums = recent.filter { it.album?.id != null }.distinctBy { it.album!!.id }.take(settings.homeRecentTiles)
                    .map { s -> AlbumItem(s.album!!.id!!, s.album.title, s.primaryArtist, s.thumbnail) }
                tvTilesRow(
                    tvRowTitle(phone, HomeSections.GREETING), likedList, songCount(likedList?.songs?.size ?: 0),
                    dlList, songCount(dlList?.songs?.size ?: 0), pinned, albums,
                )?.let(::add)
            }
            if (want(HomeSections.LIKED)) tvSongRow(HomeSections.LIKED, tvRowTitle(phone, HomeSections.LIKED), liked.filter(offline))?.let(::add)
            if (wantGenres) genres?.let { g -> tvGenreRow(tr(R.string.your_top_genres), g) { songCount(it) } }?.let(::add)
            if (want(HomeSections.DOWNLOADS)) tvSongRow(HomeSections.DOWNLOADS, tvRowTitle(phone, HomeSections.DOWNLOADS), downloads)?.let(::add)
            addAll(own)
        }
    }
}

/**
 * OK on one of the own rows' cards: a [TV_PLAY] card plays its list (the player opens); a mood or genre tile opens its
 * page. False for any other card (it opens as cards do, [open]).
 */
internal fun openOwn(c: AppContainer, nav: TvNav, item: BrowseItem, plays: Map<String, TvPlayList>): Boolean {
    if (item is MoodItem) { nav.go(TvRoute.Mood(item)); return true }
    if (!item.id.startsWith(TV_PLAY)) return false
    val list = plays[item.id]?.takeIf { it.songs.isNotEmpty() } ?: return false
    c.player.playQueue(list.songs, 0, QueueSource(QueueKind.PLAYLIST, list.title, originId = item.id))
    nav.go(TvRoute.Player)
    return true
}
