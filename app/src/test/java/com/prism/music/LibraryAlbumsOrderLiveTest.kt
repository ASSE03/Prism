package com.prism.music

import com.prism.music.data.innertube.InnerTube
import com.prism.music.data.innertube.YouTubeMusic
import com.prism.music.data.innertube.findAll
import com.prism.music.data.prefs.AppSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import java.util.concurrent.TimeUnit

/**
 * 1.8.4 (D56): what YouTube Music answers for the library's albums where this runs, which is signed out (no account
 * here). It shows that the order of an account's albums can't be checked from a test: without an account the page has
 * no albums at all, with or without a sort asked for. So "Recently added" goes by Prism's own first-seen times
 * ([com.prism.music.data.AlbumFirstSeen]).
 */
@Category(LiveNetwork::class)
class LibraryAlbumsOrderLiveTest {
    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private var settings = AppSettings()
    private val api = InnerTube(http, { settings }, { settings = settings.copy(visitorData = it) })
    private val ytm = YouTubeMusic(api)

    @Test fun signedOutTheLibrarysAlbumsAreNotThere() = runBlocking {
        val asTheAppAsks = runCatching { ytm.libraryAlbums() }
        println("FEmusic_liked_albums as the app asks, signed out: $asTheAppAsks")
        assertTrue("no albums without an account: $asTheAppAsks", asTheAppAsks.getOrNull().isNullOrEmpty())

        // With the sort other clients are said to send for "recently added" (not verified: it needs an account).
        val sorted = runCatching { api.post("browse", buildJsonObject { put("browseId", "FEmusic_liked_albums"); put("params", "ggMGKgQIABAB") }) }
        val rows = sorted.getOrNull()?.findAll("musicTwoRowItemRenderer")?.size
        val sorts = sorted.getOrNull()?.findAll("musicSortFilterButtonRenderer")?.size
        println("FEmusic_liked_albums with a sort param, signed out: ${sorted.exceptionOrNull() ?: "answered"}, album cards $rows, sort buttons $sorts, keys ${sorted.getOrNull()?.keys}")
        assertTrue("no albums without an account, sorted or not: $rows", (rows ?: 0) == 0)
    }
}
