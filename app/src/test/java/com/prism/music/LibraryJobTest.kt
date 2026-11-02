package com.prism.music

import com.prism.music.data.imports.LibraryAlbum
import com.prism.music.data.imports.LibraryJob
import com.prism.music.data.imports.LibraryPlaylist
import com.prism.music.data.imports.LibraryTrack
import com.prism.music.data.imports.ParsedLibrary
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bringing a Spotify library (1.8.1): Liked Songs are liked as their songs are found, a playlist is made once every song
 * of it has been looked up (with the ones found) and gets later picks added, albums and artists once each. Nothing is
 * done twice, and a job carries on from a file after a restart.
 */
class LibraryJobTest {
    private val a = LibraryTrack("Africa", "TOTO")
    private val b = LibraryTrack("Mr. Brightside", "The Killers")
    private val c = LibraryTrack("Blinding Lights", "The Weeknd")
    private val lib = ParsedLibrary(
        liked = listOf(c, a),
        playlists = listOf(LibraryPlaylist("Road trip", listOf(a, b))),
        albums = listOf(LibraryAlbum("Rumours", "Fleetwood Mac"), LibraryAlbum("Nowhere", "Nobody")),
        artists = listOf("Radiohead"),
    )

    private class Hands(val songs: MutableMap<String, Song>, val pending: MutableSet<String>) : LibraryJob.Hands {
        val liked = ArrayList<String>()
        val playlists = LinkedHashMap<String, MutableList<String>>()
        val albums = ArrayList<String>()
        val starred = ArrayList<String>()
        var albumFails = true
        override suspend fun matched(keys: Collection<String>) = songs.filterKeys { it in keys }
        override suspend fun pending(keys: Collection<String>) = pending.filter { it in keys }.toSet()
        override suspend fun like(songs: List<Song>) { liked += songs.map { it.id } }
        override fun createPlaylist(name: String, songs: List<Song>): String = "prism:${playlists.size}".also { playlists[it] = songs.map { s -> s.id }.toMutableList() }
        override fun addToPlaylist(id: String, songs: List<Song>) { playlists.getValue(id) += songs.map { it.id } }
        override fun playlistExists(id: String) = id in playlists
        override suspend fun saveAlbum(album: LibraryAlbum): Boolean {
            if (album.title == "Rumours" && albumFails) { albumFails = false; throw java.io.IOException("offline") }
            if (album.title == "Rumours") albums += album.title
            return album.title == "Rumours"
        }
        override suspend fun starArtist(name: String): Boolean { starred += name; return true }
    }

    private fun s(id: String) = Song(id, id, listOf(ArtistRef("x")))

    @Test fun bringsWhatsFoundAndCarriesOn() = runBlocking {
        var job = LibraryJob().plus(lib, LibraryJob.LibraryChoice())
        val hands = Hands(mutableMapOf(c.key to s("c")), mutableSetOf(a.key, b.key))
        job = job.apply(hands)
        assertEquals("liked as found", listOf("c"), hands.liked)
        assertTrue("the playlist waits for its songs to be looked up", hands.playlists.isEmpty())
        assertEquals("an album search that failed is tried again", emptySet<String>(), job.albumsDone.filter { it.startsWith("rumours") }.toSet())
        assertEquals(listOf("Radiohead"), hands.starred)

        // Africa found; Mr. Brightside needs a look (looked up, not matched).
        hands.songs[a.key] = s("a"); hands.pending.clear()
        job = job.apply(hands)
        assertEquals(listOf("c", "a"), hands.liked)
        assertEquals(listOf("a"), hands.playlists.values.single())
        assertEquals(listOf("Rumours"), hands.albums)
        assertEquals("not found isn't asked again either", 2, job.albumsDone.size)
        assertEquals("starred once", listOf("Radiohead"), hands.starred)

        // Picked later: added to the playlist, nothing else again.
        hands.songs[b.key] = s("b")
        val file = File.createTempFile("library", ".json").also { it.deleteOnExit() }
        LibraryJob.save(file, job)
        job = LibraryJob.load(file).apply(hands)
        assertEquals(listOf("a", "b"), hands.playlists.values.single())
        assertEquals(listOf("c", "a"), hands.liked)
        assertTrue(job.settled)
        // The same download again adds nothing new.
        assertEquals(job.liked, job.plus(lib, LibraryJob.LibraryChoice()).liked)
        assertEquals(1, job.plus(lib, LibraryJob.LibraryChoice()).playlists.size)
    }

    @Test fun onlyWhatWasChosen() {
        val job = LibraryJob().plus(lib, LibraryJob.LibraryChoice(liked = false, playlists = true, albums = false, artists = false))
        assertTrue(job.liked.isEmpty() && job.albums.isEmpty() && job.artists.isEmpty())
        assertEquals(setOf(a.key, b.key), job.keys)
    }
}
