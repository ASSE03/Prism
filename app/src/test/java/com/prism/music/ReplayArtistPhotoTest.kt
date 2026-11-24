package com.prism.music

import androidx.room.Room
import com.prism.music.data.ArtistPhotos
import com.prism.music.data.db.PlayEventEntity
import com.prism.music.data.db.PrismDatabase
import com.prism.music.data.db.toEntity
import com.prism.music.data.imports.CountedPlays
import com.prism.music.data.imports.PlayStats
import com.prism.music.data.imports.PrismPlays
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Reddit (this_sg, 1.8.8): Replay showed the top artist Pritam with Darshan Raval's photo. YouTube links the credit
 * "Pritam" on "Mehrama" (Pritam with Darshan Raval and Antara Mitra) to Darshan Raval's page (seen live 2026-11-22),
 * and the top artists took the highest id among an artist's songs, which was Darshan's. Now the id most of the artist's
 * songs link to is theirs, and an id whose page is someone else's never gives that name its photo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReplayArtistPhotoTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PrismDatabase::class.java).allowMainThreadQueries().build()

    @After fun close() = db.close()

    private companion object {
        // Their real ids: Darshan Raval's sorts above Pritam's.
        const val PRITAM = "UCCTN01plFzn4npREHKT2_9Q"
        const val DARSHAN = "UCNMewQFJlBeyXpWwo2dezSw"
    }

    private suspend fun history() {
        fun song(id: String, title: String, vararg artists: Pair<String, String?>) =
            Song(id, title, artists.map { (n, i) -> ArtistRef(n, i) }, durationSec = 240, thumbnail = "https://i/$id.jpg")
        listOf(
            // Two artists, the first credit linked to the second artist's page, as YouTube has it.
            song("mehrama", "Mehrama", "Pritam" to DARSHAN, "Antara Mitra" to "UCantara"),
            song("parda", "Parda", "Pritam" to PRITAM, "Sunidhi Chauhan" to "UCsunidhi"),
            song("tumMile", "Tum Mile", "Pritam" to PRITAM, "Neeraj Shridhar" to "UCneeraj"),
            song("judaiyaan", "Judaiyaan", "Darshan Raval" to DARSHAN, "Shreya Ghoshal" to "UCshreya"),
        ).forEach { db.songs().upsert(it.toEntity()) }
        // The mislinked song is the most played of Pritam's.
        var t = 1_000L
        fun play(id: String, times: Int) = repeat(times) { runBlocking { db.plays().insert(PlayEventEntity(songId = id, timestamp = t++, playedMs = 200_000)) } }
        play("mehrama", 6); play("parda", 1); play("tumMile", 1); play("judaiyaan", 2)
    }

    private fun stats(): List<Pair<String, PlayStats>> = listOf(
        "Prism's plays" to PrismPlays(db.plays()),
        "counted plays" to CountedPlays(emptyList(), db.counted(), db.plays()),
    )

    @Test fun aTopArtistKeepsTheirOwnIdWhenOneSongLinksTheirNameToSomeoneElse() = runBlocking {
        history()
        for ((what, s) in stats()) {
            val top = s.topArtists(0, Long.MAX_VALUE, 10).associateBy { it.artistName }
            assertEquals("$what: Pritam", PRITAM, top.getValue("Pritam").artistId)
            assertEquals("$what: Darshan Raval", DARSHAN, top.getValue("Darshan Raval").artistId)
            assertEquals("$what: every play still counts", 8, top.getValue("Pritam").plays)
            assertEquals("$what: listening time", 1_600_000L, top.getValue("Pritam").ms)
            assertEquals("$what: most listened first", listOf("Pritam", "Darshan Raval"), s.topArtists(0, Long.MAX_VALUE, 10).map { it.artistName })
            val new = s.newArtists(0, Long.MAX_VALUE).associateBy { it.artistName }
            assertEquals("$what: new artists", PRITAM, new.getValue("Pritam").artistId)
        }
        val taste = db.plays().tasteTopArtists(0, Long.MAX_VALUE, 10).associateBy { it.artistName }
        assertEquals("taste", PRITAM, taste.getValue("Pritam").artistId)
        val counted = db.counted().tasteTopArtists(listOf(CountedPlays.PRISM), 0, Long.MAX_VALUE, 10).associateBy { it.artistName }
        assertEquals("counted taste", PRITAM, counted.getValue("Pritam").artistId)
    }

    @Test fun anArtistWhoseOnlySongsHaveNoIdHasNone() = runBlocking {
        db.songs().upsert(Song("x", "X", listOf(ArtistRef("Someone")), durationSec = 100).toEntity())
        db.plays().insert(PlayEventEntity(songId = "x", timestamp = 5, playedMs = 90_000))
        for ((what, s) in stats()) assertNull(what, s.topArtists(0, Long.MAX_VALUE, 5).single().artistId)
    }

    @Test fun aPageThatIsPlainlySomeoneElsesIsntThisArtist() {
        assertFalse(ArtistPhotos.sameArtist("Darshan Raval", "Pritam"))
        assertTrue(ArtistPhotos.sameArtist("Pritam", "Pritam"))
        assertTrue("case and accents", ArtistPhotos.sameArtist("BEYONCÉ", "Beyoncé"))
        assertTrue("a longer credit of the same name", ArtistPhotos.sameArtist("Pritam", "Pritam Chakraborty"))
        assertTrue(ArtistPhotos.sameArtist("The Weeknd", "Weeknd"))
        assertFalse("not inside a word", ArtistPhotos.sameArtist("Pritamjit", "Pritam"))
        assertTrue("another script isn't judged", ArtistPhotos.sameArtist("प्रीतम", "Pritam"))
    }

    @Test fun anIdWhosePageIsAnotherArtistsGivesTheNamesOwnPhotoOrNone() {
        val darshanFace = "https://yt3/darshan"
        val pritamFace = "https://yt3/pritam"
        val thumbs = mapOf(DARSHAN to darshanFace, ArtistPhotos.pageKey(DARSHAN) to "Darshan Raval")
        assertNull("the placeholder, not Darshan's face", ArtistPhotos.photo(thumbs, DARSHAN, "Pritam"))
        assertEquals(pritamFace, ArtistPhotos.photo(thumbs + (ArtistPhotos.nameKey("Pritam") to pritamFace), DARSHAN, "Pritam"))
        assertEquals("Darshan Raval keeps his own", darshanFace, ArtistPhotos.photo(thumbs, DARSHAN, "Darshan Raval"))
        // A photo kept before the page's name was: used as before, until the page is asked again.
        assertEquals(darshanFace, ArtistPhotos.photo(mapOf(DARSHAN to darshanFace), DARSHAN, "Darshan Raval"))
        assertEquals("no id: by name", pritamFace, ArtistPhotos.photo(mapOf(ArtistPhotos.nameKey("Pritam") to pritamFace), null, "Pritam"))
    }

    /**
     * A page whose title doesn't read is called "Artist" (YouTubeMusic.artist). Its name wasn't kept, so the page was
     * never known and was fetched again at every Replay, Rewind and widget refresh; and Rewind judged "Artist" as
     * another artist and dropped the photo, where Replay used it.
     */
    @Test fun aPageWithoutATitleIsKnownOnceFetchedAndKeepsItsPhoto() {
        val face = "https://yt3/pritam"
        // What fetching it keeps: the photo by id, and the page as known (no name).
        val kept = mapOf(PRITAM to face, ArtistPhotos.pageEntry(PRITAM, ArtistPhotos.UNNAMED))
        assertTrue(ArtistPhotos.pageKnown(kept, PRITAM))
        assertFalse("not fetched again", ArtistPhotos.wanted(kept, PRITAM, "Pritam"))
        assertEquals("its photo is used", face, ArtistPhotos.photo(kept, PRITAM, "Pritam"))
        // Before it's fetched it is wanted; a named page is known by its name as before.
        assertTrue(ArtistPhotos.wanted(mapOf(PRITAM to face), PRITAM, "Pritam"))
        assertEquals(ArtistPhotos.pageKey(DARSHAN) to "Darshan Raval", ArtistPhotos.pageEntry(DARSHAN, "Darshan Raval"))
        assertEquals(ArtistPhotos.pageKey(DARSHAN) to "", ArtistPhotos.pageEntry(DARSHAN, " "))
        // Rewind treats it as Replay does: an unnamed page isn't someone else's; a plainly other one still is.
        assertTrue(ArtistPhotos.couldBe(ArtistPhotos.UNNAMED, "Pritam"))
        assertTrue(ArtistPhotos.couldBe("", "Pritam"))
        assertFalse(ArtistPhotos.couldBe("Darshan Raval", "Pritam"))
        assertTrue(ArtistPhotos.couldBe("Pritam", "Pritam"))
    }
}
