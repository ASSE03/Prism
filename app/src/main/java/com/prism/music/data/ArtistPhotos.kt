package com.prism.music.data

import com.prism.music.data.innertube.SearchRank

/**
 * Whose photo an artist from play history gets (Replay, Rewind, the Replay card). Photos are kept by the artist's id
 * (and by name for songs saved without one), beside the name of the page each id opened ([pageKey]). YouTube sometimes
 * links a credit to someone else's page (Reddit, 2026-11: "Mehrama" credits "Pritam" with Darshan Raval's), so an id
 * whose page is plainly another artist's isn't used for that name: the name's own photo is, or none (the placeholder),
 * never the other artist's face.
 */
object ArtistPhotos {
    /** Where the name of [id]'s page is kept, among the photos. */
    fun pageKey(id: String) = "page:$id"

    /** Where the photo found by searching for [name] is kept. */
    fun nameKey(name: String) = "name:" + name.lowercase().trim()

    /**
     * Whether a page called [page] can be the artist credited as [credit]: the same name, or one inside the other word
     * for word ("Pritam Chakraborty" is Pritam's page). Names not written in plain Latin letters aren't judged (a page
     * can be named in another script than the credit): they count as the same, as before.
     */
    fun sameArtist(page: String, credit: String): Boolean {
        val a = SearchRank.norm(page)
        val b = SearchRank.norm(credit)
        if (a.isEmpty() || b.isEmpty() || a == b) return true
        if (!plain(a) || !plain(b)) return true
        return " $a ".contains(" $b ") || " $b ".contains(" $a ")
    }

    private fun plain(s: String) = s.all { it == ' ' || it in 'a'..'z' || it in '0'..'9' }

    /** What a page whose title didn't read is called ([com.prism.music.data.innertube.YouTubeMusic.artist]). */
    const val UNNAMED = "Artist"

    /** A page's name as kept: "" for one without a title of its own ([UNNAMED]), which judges no credit. */
    fun pageName(name: String): String = if (name.isBlank() || name == UNNAMED) "" else name

    /**
     * What to keep of [id]'s page called [name]: its name, or "" when it has none, so the page counts as known
     * ([pageKnown]) and isn't asked again at every Replay, Rewind or widget refresh.
     */
    fun pageEntry(id: String, name: String): Pair<String, String> = pageKey(id) to pageName(name)

    /** Whether a page called [page] can give [credit] its photo: an unnamed page isn't judged, as in [photo]. */
    fun couldBe(page: String, credit: String): Boolean = sameArtist(pageName(page), credit)

    /** Whether [id]'s page name is kept (an id from before 1.8.8 isn't checked until its page is asked again). */
    fun pageKnown(thumbs: Map<String, String>, id: String) = pageKey(id) in thumbs

    /** Whether [name]'s page (by [id]) or photo (by name) is still to be asked for: no photo, or an id whose page isn't known. */
    fun wanted(thumbs: Map<String, String>, id: String?, name: String): Boolean =
        photo(thumbs, id, name) == null || (id != null && !pageKnown(thumbs, id))

    /** The photo for [name]: by [id] unless that id's page is someone else's, then the one found by the name. */
    fun photo(thumbs: Map<String, String>, id: String?, name: String): String? {
        val own = id?.takeIf { i -> thumbs[pageKey(i)]?.let { sameArtist(it, name) } != false }
        return own?.let { thumbs[it] } ?: thumbs[nameKey(name)]
    }
}
