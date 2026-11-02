package com.prism.music

import com.prism.music.data.prefs.AppSettings
import com.prism.music.ui.player.BACKDROP_SHOW_MIN
import com.prism.music.ui.player.backdropBlackout
import com.prism.music.ui.player.backdropShowFromSlider
import com.prism.music.ui.player.backdropShowOnSlider
import com.prism.music.ui.screens.previewVeil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 1.8.1's library, player-look and settings requests from GitHub and Reddit: each one's logic, and that every new
 * choice is off (today's way) until it's picked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Requests181Test {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()

    private val eps = 1e-4f

    // ------------------------------------------------ #310 album names in Downloads

    @Test fun aSongRowCanLeaveTheAlbumOut() {
        val song = com.prism.music.data.model.Song(
            "s1", "Northern Lights", listOf(com.prism.music.data.model.ArtistRef("Ava Lune")), album = com.prism.music.data.model.AlbumRef("Polar"),
        )
        var showAlbum by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            val settings = AppSettings()
            androidx.compose.runtime.CompositionLocalProvider(com.prism.music.ui.theme.LocalAppSettings provides settings) {
                com.prism.music.ui.theme.PrismTheme(settings, artworkColor = null) {
                    com.prism.music.ui.components.SongRowLayout(song, showAlbum = showAlbum)
                }
            }
        }
        compose.onNodeWithText("Ava Lune · Polar").assertExists()
        showAlbum = false
        compose.onNodeWithText("Ava Lune").assertExists()
        compose.onNodeWithText("Ava Lune · Polar").assertDoesNotExist()
    }

    private fun repo() = com.prism.music.data.prefs.SettingsRepository(
        org.robolectric.RuntimeEnvironment.getApplication(),
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
    )

    // ------------------------------------------------ #309 Show background at 0% = fully dark

    @Test fun showBackgroundsStartIsFullyDark() {
        // The slider's start is solid black, and its end as bright as before.
        assertEquals(BACKDROP_SHOW_MIN, backdropShowFromSlider(0f), eps)
        assertEquals(1f, backdropBlackout(backdropShowFromSlider(0f)), eps)
        assertEquals(1f, backdropShowFromSlider(1f), eps)
        assertEquals(1f, previewVeil(BACKDROP_SHOW_MIN), eps)
    }

    @Test fun showBackgroundKeepsEveryOldValueAsItWas() {
        // Values from before (0..1) draw no veil and come back from the slider unchanged.
        for (old in listOf(0f, 0.2f, AppSettings().playerBackdropShow, 0.7f, 1f)) {
            assertEquals(0f, backdropBlackout(old), eps)
            assertEquals(old, backdropShowFromSlider(backdropShowOnSlider(old)), eps)
            assertEquals(1f - 0.55f * old, previewVeil(old), eps)
        }
        // Halfway into the new stretch, half dark.
        assertEquals(0.5f, backdropBlackout(BACKDROP_SHOW_MIN / 2), eps)
    }

    @Test fun showBackgroundIsReadWithinItsRange(): Unit = kotlinx.coroutines.runBlocking {
        val repo = repo()
        repo.setPlayerBackdropShow(-3f)
        val s = kotlinx.coroutines.withTimeout(5_000) { repo.flow.first { it.playerBackdropShow < 0f } }
        assertEquals(BACKDROP_SHOW_MIN, s.playerBackdropShow, eps)
        repo.setPlayerBackdropShow(AppSettings().playerBackdropShow)
        kotlinx.coroutines.withTimeout(5_000) { repo.flow.first { it.playerBackdropShow > 0f } }
        assertTrue(true)
    }

    // ------------------------------------------------ Every new choice keeps today's way until picked

    @Test fun theNewChoicesAreOffUntilPicked() {
        val d = AppSettings()
        assertTrue("#310 album names stay", d.view.downloadsAlbums)
        assertTrue("#289 one tap still asks", !d.view.artistLinks)
        assertTrue("#283 the background still cuts", !d.view.backdropFade)
    }

    @Test fun theNewChoicesAreKeptAndRead(): Unit = kotlinx.coroutines.runBlocking {
        val repo = repo()
        repo.setDownloadsAlbums(false); repo.setArtistLinks(true); repo.setBackdropFade(true)
        val s = kotlinx.coroutines.withTimeout(5_000) { repo.flow.first { !it.view.downloadsAlbums && it.view.artistLinks && it.view.backdropFade } }
        assertTrue(s.view.artistLinks)
        repo.setDownloadsAlbums(true); repo.setArtistLinks(false); repo.setBackdropFade(false)
        kotlinx.coroutines.withTimeout(5_000) { repo.flow.first { it.view.downloadsAlbums && !it.view.artistLinks && !it.view.backdropFade } }
    }

    // ------------------------------------------------ #283 the Now playing background's fade

    @Test fun nowPlayingFadesOnlyWhenAskedAtTheColorsPace() {
        val off = AppSettings()
        assertEquals(null, com.prism.music.ui.theme.nowPlayingFadeMs(off))
        val on = off.copy(view = off.view.copy(backdropFade = true))
        assertEquals(on.artworkChange.ms.takeIf { it > 0 } ?: com.prism.music.data.prefs.ArtworkChange.FADE.ms, com.prism.music.ui.theme.nowPlayingFadeMs(on))
        assertEquals(2400, com.prism.music.ui.theme.nowPlayingFadeMs(on.copy(artworkChange = com.prism.music.data.prefs.ArtworkChange.SLOW)))
        // Colors that change at once still get a fade (the switch asks for one); Reduce motion has none.
        assertEquals(900, com.prism.music.ui.theme.nowPlayingFadeMs(on.copy(artworkChange = com.prism.music.data.prefs.ArtworkChange.INSTANT)))
        assertEquals(null, com.prism.music.ui.theme.nowPlayingFadeMs(on.copy(reduceMotion = true)))
    }

    // ------------------------------------------------ #289 each artist its own link

    private fun ref(name: String, id: String? = "UC$name") = com.prism.music.data.model.ArtistRef(name, id)

    @Test fun eachArtistsNameIsFoundInTheLine() {
        val a = listOf(ref("Daft Punk"), ref("Pharrell Williams"), ref("Nile Rodgers"))
        val text = "Daft Punk, Pharrell Williams, Nile Rodgers"
        val spans = com.prism.music.ui.components.artistSpans(text, a)!!
        assertEquals(listOf("Daft Punk", "Pharrell Williams", "Nile Rodgers"), spans.map { (r, _) -> text.substring(r) })
        assertEquals(a, spans.map { it.second })
        // A name inside another's ("Kid" in "Kid Cudi") is found where it stands, after the one before it.
        val b = listOf(ref("Kid Cudi"), ref("Kid"))
        val t2 = "Kid Cudi, Kid"
        assertEquals(listOf("Kid Cudi", "Kid"), com.prism.music.ui.components.artistSpans(t2, b)!!.map { (r, _) -> t2.substring(r) })
    }

    @Test fun oneArtistOrAMissingNameKeepsTheMenu() {
        assertEquals(null, com.prism.music.ui.components.artistSpans("Adele", listOf(ref("Adele"))))
        assertEquals(null, com.prism.music.ui.components.artistSpans("Someone & Else", listOf(ref("Someone"), ref("Nobody"))))
    }

    // ------------------------------------------------ #246 Add to playlist says where it is already

    @Test fun playlistsThatHoldTheSongAreCounted() {
        com.prism.music.data.PlaylistMembership.forget()
        val contents = mapOf("VLPL1" to listOf("a", "b"), "prism:x" to listOf("c"), "VLPL2" to emptyList())
        assertEquals(mapOf("VLPL1" to 1), com.prism.music.data.PlaylistMembership.counts(listOf("a"), contents))
        assertEquals(mapOf("VLPL1" to 2, "prism:x" to 1), com.prism.music.data.PlaylistMembership.counts(listOf("a", "b", "c"), contents))
        // Added from this phone since: known at once, however the playlist's id is written.
        com.prism.music.data.PlaylistMembership.noteAdded("PL2", listOf("a"))
        assertEquals(mapOf("VLPL1" to 1, "VLPL2" to 1), com.prism.music.data.PlaylistMembership.counts(listOf("a"), contents))
        com.prism.music.data.PlaylistMembership.forget()
    }

    @Test fun aPlaylistsSongsComeFromWhatsOnThePhone() {
        val lists = listOf(
            com.prism.music.data.model.PlaylistItem("prism:x", "Mine", "", null),
            com.prism.music.data.model.PlaylistItem("VLPL1", "Kept", "", null),
            com.prism.music.data.model.PlaylistItem("VLPL9", "Never opened", "", null),
        )
        val asked = mutableListOf<String>()
        val known = com.prism.music.data.PlaylistMembership.known(lists, mapOf("prism:x" to listOf("c"))) { id ->
            asked += id
            if (id == "VLPL1") listOf("a") else null
        }
        assertEquals(listOf("c"), known["prism:x"])
        assertEquals(listOf("a"), known["VLPL1"])
        assertEquals(emptyList<String>(), known["VLPL9"])
        // Prism playlists are never looked up on disk.
        assertTrue("prism:x" !in asked)
    }

    // ------------------------------------------------ #216 explicit songs

    private fun song(id: String, explicit: Boolean = false) = com.prism.music.data.model.Song(id, "Song $id", explicit = explicit)
    private val SHOW = com.prism.music.data.prefs.ExplicitContent.SHOW
    private val SKIP = com.prism.music.data.prefs.ExplicitContent.SKIP
    private val HIDE = com.prism.music.data.prefs.ExplicitContent.HIDE

    @Test fun explicitSongsPlayAsBeforeUnlessTheFilterIsOn() {
        assertEquals(SHOW, AppSettings().behavior.explicitContent)
        val e = song("e", explicit = true)
        val f = com.prism.music.data.ExplicitFilter
        assertTrue(f.plays(SHOW, e) && f.shows(SHOW, e))
        // Skip: listed, never played; Hide: neither.
        assertTrue(!f.plays(SKIP, e) && f.shows(SKIP, e))
        assertTrue(!f.plays(HIDE, e) && !f.shows(HIDE, e))
        // A clean song is never touched.
        assertTrue(f.plays(HIDE, song("c")) && f.shows(HIDE, song("c")))
    }

    @Test fun aQueueStartsFromTheNextSongThatMayPlay() {
        val f = com.prism.music.data.ExplicitFilter
        val list = listOf(song("a"), song("b", true), song("c"), song("d", true))
        // Nothing changes with the filter off.
        assertEquals(list to 1, f.queue(SHOW, list, 1))
        // The tapped explicit song gives way to the next clean one; the rest keep their order.
        assertEquals(listOf("a", "c") to 1, f.queue(SKIP, list, 1)!!.let { (s, i) -> s.map { it.id } to i })
        // Tapped past the last clean song: from the first.
        assertEquals(0, f.queue(SKIP, list, 3)!!.second)
        assertEquals(null, f.queue(HIDE, listOf(song("x", true)), 0))
    }

    @Test fun hideLeavesExplicitSongsOutOfSearch() {
        val f = com.prism.music.data.ExplicitFilter
        val e = com.prism.music.data.model.SongItem(song("e", true))
        val c = com.prism.music.data.model.SongItem(song("c"))
        val album = com.prism.music.data.model.AlbumItem("MPREb_1", "An album", "Album", null, null)
        val top = com.prism.music.data.innertube.TopSearch(
            e, listOf(com.prism.music.data.innertube.SearchFilter.SONGS to listOf(e, c), com.prism.music.data.innertube.SearchFilter.ALBUMS to listOf(album)),
        )
        assertEquals(top, f.top(SKIP, top))
        val hidden = f.top(HIDE, top)
        assertEquals(c, hidden.top)
        assertEquals(listOf(listOf(c), listOf(album)), hidden.sections.map { it.second })
        assertEquals(listOf(c, album), f.items(HIDE, listOf(e, c, album)))
    }

    @Test fun theExplicitChoiceIsKept(): Unit = kotlinx.coroutines.runBlocking {
        val repo = repo()
        repo.setExplicitContent(HIDE)
        kotlinx.coroutines.withTimeout(5_000) { repo.flow.first { it.behavior.explicitContent == HIDE } }
        repo.setExplicitContent(SHOW)
        kotlinx.coroutines.withTimeout(5_000) { repo.flow.first { it.behavior.explicitContent == SHOW } }
    }

    // ------------------------------------------------ #311 new releases from favorite artists

    private fun release(id: String, artist: String, name: String = artist) = com.prism.music.data.db.ReleaseEntity(
        id = id, artistId = artist, artistName = name, title = "T$id", kind = "Single", year = 2026,
        releaseDate = null, thumbnail = null, playlistId = null, firstSeenAt = 0, baseline = false, notifiedAt = 0,
    )

    @Test fun favoritesOnlyKeepsTheStarredArtists() {
        val all = listOf(release("1", "a"), release("2", "b"), release("3", "a"))
        assertEquals(listOf("1", "3"), com.prism.music.data.foryou.ReleaseRadar.fromFavorites(all, setOf("a")).map { it.id })
        assertTrue(com.prism.music.data.foryou.ReleaseRadar.fromFavorites(all, emptySet()).isEmpty())
    }

    @Test fun theArtistsToPickLeadWithTheMostPlayed() {
        val all = listOf(release("1", "z", "Zed"), release("2", "b", "Bea"), release("3", "a", "Ana"), release("4", "b", "Bea"))
        val picks = com.prism.music.data.foryou.ReleaseRadar.artistsToPick(all, mostPlayed = listOf("a"))
        assertEquals(listOf("a" to "Ana", "b" to "Bea", "z" to "Zed"), picks)
    }
}
