package com.prism.music.ui.tv

import com.prism.music.data.model.AlbumItem
import com.prism.music.data.model.ArtistItem
import com.prism.music.data.model.BrowseItem
import com.prism.music.data.model.MoodItem
import com.prism.music.data.model.PlaylistItem
import com.prism.music.data.model.Shelf
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import com.prism.music.data.model.quickPicks
import com.prism.music.data.model.speedDial
import com.prism.music.data.prefs.HomeSections

/** One of Home's rows on the TV, with the items behind its cards (what OK opens or plays). */
data class TvHomeRow(val key: String, val title: String, val items: List<BrowseItem>)

/** Most cards a TV row holds (more is a long walk with a remote). */
private const val ROW_SIZE = 20
/** The Speed dial row's size, as on the phone's Speed dial (#267). */
private const val DIAL_SIZE = 18

/**
 * Home's rows on the TV (1.8.0), in the phone's default order: Quick picks, Speed dial (YouTube's tiles, then your most
 * played), Recently played, then YouTube's other shelves as it sends them. Mood and genre tiles aren't cards (they open
 * browse pages the TV doesn't have); empty rows are left out.
 */
fun tvHomeRows(
    shelves: List<Shelf>,
    owner: String?,
    recent: List<Song>,
    mostPlayed: List<BrowseItem>,
    titles: TvHomeTitles,
): List<TvHomeRow> {
    val picks = shelves.quickPicks(owner)
    val dial = shelves.speedDial(owner)
    val rows = ArrayList<TvHomeRow>()
    picks?.items?.filterIsInstance<SongItem>()?.take(ROW_SIZE)?.takeIf { it.isNotEmpty() }?.let { rows += TvHomeRow(KEY_PICKS, titles.quickPicks, it) }
    val dialItems = (dial?.items.orEmpty().filter { it !is MoodItem } + mostPlayed).distinctBy { it.id }.take(DIAL_SIZE)
    if (dialItems.isNotEmpty()) rows += TvHomeRow(KEY_DIAL, titles.speedDial, dialItems)
    recent.distinctBy { it.id }.take(ROW_SIZE).map(::SongItem).takeIf { it.isNotEmpty() }?.let { rows += TvHomeRow(KEY_RECENT, titles.recent, it) }
    for (s in shelves) {
        if (s === picks || s === dial) continue
        val items = s.items.filter { it !is MoodItem }.take(ROW_SIZE)
        if (items.isNotEmpty()) rows += TvHomeRow(HomeSections.shelfKey(s.title), s.title, items)
    }
    return rows
}

/** The row titles Prism writes itself (translated). */
data class TvHomeTitles(val quickPicks: String, val speedDial: String, val recent: String)

/** The rows' keys are the phone's Home sections' (one layout's names on both). */
const val KEY_PICKS = HomeSections.QUICK_PICKS
const val KEY_DIAL = HomeSections.SPEED_DIAL
const val KEY_RECENT = HomeSections.RECENT

/** A card for [this]: an artist's is round, a mix or station is a mix, a music video is a video. */
fun BrowseItem.toTvItem(): TvItem = TvItem(
    id = id, title = title, subtitle = subtitle.takeIf { it.isNotBlank() }, art = thumbnail,
    kind = when (this) {
        is SongItem -> if (song.isVideo) TvItem.Kind.VIDEO else TvItem.Kind.SONG
        is AlbumItem -> TvItem.Kind.ALBUM
        is PlaylistItem -> if (isMix) TvItem.Kind.MIX else TvItem.Kind.PLAYLIST
        is ArtistItem -> TvItem.Kind.ARTIST
        else -> TvItem.Kind.PLAYLIST
    },
)
