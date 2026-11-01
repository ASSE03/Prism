package com.prism.music

import com.prism.music.data.innertube.Parser
import com.prism.music.data.innertube.SearchFilter
import com.prism.music.data.innertube.SearchRank
import com.prism.music.data.model.AlbumItem
import com.prism.music.data.model.ArtistItem
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.PlaylistItem
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.8.0 (owner, on the TV: "upside down" didn't find Jack Johnson's song): search reads how often songs and videos have
 * been played, in every language, and a title shared by several artists leads with the most played, YouTube's card
 * included. The live side is [SearchAccuracyLiveTest]; these are the rules, offline, as YouTube answered 2026-11-01.
 */
class SearchPlaysTest {
    @Test fun playsAndViewsReadInEveryLanguage() {
        assertEquals(189_000_000L, SearchRank.count("189M plays"))
        assertEquals(9_800_000L, SearchRank.count("9.8M plays"))
        assertEquals(189_000_000L, SearchRank.count("189 M reproducciones"))
        assertEquals(79_630_000L, SearchRank.count("7963만회 재생"))
        assertEquals(36_000L, SearchRank.count("3.6만회 재생"))
        assertEquals(79_000_000L, SearchRank.count("79 Mio. Wiedergaben"))
        assertEquals(36_186L, SearchRank.count("36.186 Wiedergaben"))
        assertEquals(1_900_000L, SearchRank.count("1,9 Mio. Aufrufe"))
        assertEquals(52_000_000L, SearchRank.count("52 M de visualizaciones"))
        assertEquals(280_000L, SearchRank.count("조회수 28만회"))
        // A video's views come after its artist, whose name may hold a digit (Black Gryph0n), and before its length.
        assertEquals(52_000_000L, Parser.countAfterArtist("Black Gryph0n & Baasik • 52M views • 2:44", skip = 1))
        assertEquals(52_000_000L, Parser.countAfterArtist("Video • Black Gryph0n & Baasik • 52M views • 2:44", skip = 2))
        assertEquals(null, Parser.countAfterArtist("Blink-182 • 3:10", skip = 1))
    }

    private fun song(id: String, title: String, artist: String, plays: Long?, video: Boolean = false) =
        SongItem(Song(id, title, listOf(ArtistRef(artist)), isVideo = video), plays)

    private val trending = song("bg", "Upside Down", "Black Gryph0n", 79_000_000)
    private val jack = song("jj", "Upside Down", "Jack Johnson", 189_000_000)
    private val diana = song("dr", "Upside Down", "Diana Ross", 88_000_000)
    private val mix = song("mx", "Upside Down (Monster Mix)", "Dj Monster", 14_000)
    private val trendingVideo = song("bgv", "Upside Down", "Black Gryph0n", 52_000_000, video = true)
    private val jackVideo = song("jjv", "Upside Down", "Jack Johnson", 120_000_000, video = true)
    private val fanVideo = song("fan", "[BLENDER] UPSIDE DOWN short 3D animation", "BlitzOzhnik", 443_000, video = true)
    private val upsideDown = listOf(
        SearchFilter.SONGS to listOf(trending, jack, diana, mix),
        SearchFilter.VIDEOS to listOf(trendingVideo, fanVideo, jackVideo),
    )

    @Test fun aSharedTitleLeadsWithTheMostPlayed() {
        // YouTube's card is the trending video, which Prism turns into its song: 79M plays against Jack Johnson's 189M.
        val r = SearchRank.rank("upside down", upsideDown, card = trendingVideo)
        assertEquals("jj", r.top?.id)
        assertEquals(listOf("dr", "bg", "mx"), r.sections.first { it.first == SearchFilter.SONGS }.second.map { it.id })
        // The videos: Jack Johnson's (120M views) first; the rest where YouTube had them.
        assertEquals(listOf("jjv", "fan", "bgv"), r.sections.first { it.first == SearchFilter.VIDEOS }.second.map { it.id })
    }

    @Test fun theCardHoldsWhenItIsThatSongOrNearlyAsPlayed() {
        // The card is the most played song's own video: it stays (as its song).
        assertEquals("jj", SearchRank.rank("upside down", upsideDown, card = jackVideo).top?.id)
        // A card played nearly as often (2/3 or more) keeps YouTube's say.
        val close = song("bg", "Upside Down", "Black Gryph0n", 150_000_000)
        assertEquals("bg", SearchRank.rank("upside down", listOf(SearchFilter.SONGS to listOf(close, jack)), card = close).top?.id)
        // Without play counts (a request that had none), YouTube's card and order stand as before.
        val bare = listOf(SearchFilter.SONGS to listOf(trending, jack).map { SongItem(it.song) })
        assertEquals("bg", SearchRank.rank("upside down", bare, card = SongItem(trending.song)).top?.id)
    }

    @Test fun aCardThatDoesntNameTheHitGivesWay() {
        // "africa": YouTube's card was a playlist that only has the word in it; Toto's Africa is a hit.
        val toto = song("toto", "Africa", "TOTO", 1_900_000_000)
        val playlist = PlaylistItem("PLx", "Love Out Loud Africa", "Playlist • YouTube Music", null)
        assertEquals("toto", SearchRank.rank("africa", listOf(SearchFilter.SONGS to listOf(toto)), card = playlist).top?.id)
        // A typo's card stays: no song is called what was typed.
        val swift = ArtistItem("UCts", "Taylor Swift", "Artist • 440M monthly audience", null)
        assertEquals("UCts", SearchRank.rank("taylor swfit", listOf(SearchFilter.SONGS to listOf(toto)), card = swift).top?.id)
    }

    @Test fun anArtistOnlyCalledThatGivesWayToAFarBiggerSong() {
        val roarArtist = ArtistItem("UCroar", "ROAR", "Artist • 12M monthly audience", null)
        val roar = song("kp", "Roar", "Katy Perry", 5_000_000_000)
        assertEquals("kp", SearchRank.rank("roar", listOf(SearchFilter.SONGS to listOf(roar), SearchFilter.ARTISTS to listOf(roarArtist))).top?.id)
        // A big artist stays on top of a song of their name ("queen": the band, not a 100M-play song).
        val queen = ArtistItem("UCq", "Queen", "Artist • 50M monthly audience", null)
        val queenSong = song("nm", "Queen", "Nicki Minaj", 100_000_000)
        assertEquals("UCq", SearchRank.rank("queen", listOf(SearchFilter.SONGS to listOf(queenSong), SearchFilter.ARTISTS to listOf(queen))).top?.id)
        // And one the listener plays always does.
        val fan = SearchRank.Taste(listOf(SearchRank.KnownArtist("UCroar", "ROAR", null, 0.5)), emptyMap())
        assertEquals("UCroar", SearchRank.rank("roar", listOf(SearchFilter.SONGS to listOf(roar), SearchFilter.ARTISTS to listOf(roarArtist)), fan).top?.id)
    }

    @Test fun aTitleTrackStillGivesWayToItsAlbum() {
        val thriller = song("mj", "Thriller", "Michael Jackson", 1_000_000_000)
        val other = song("x", "Thriller", "Someone", 5_000_000)
        val album = AlbumItem("MPREb_t", "Thriller", "Album • Michael Jackson • 1982", null)
        val r = SearchRank.rank("thriller", listOf(SearchFilter.SONGS to listOf(other, thriller), SearchFilter.ALBUMS to listOf(album)))
        assertEquals("MPREb_t", r.top?.id)
    }
}
