package com.prism.music

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.data.spotify.SpotifyProfile
import com.prism.music.data.spotify.SpotifyTokens
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spotify through your own developer app (1.8.1), end to end without Spotify: the page and its set-up guide show while
 * nothing is connected (and nothing Spotify runs: no job, no request), then a token is put in as a finished sign-in
 * would, Spotify's answers come from the test's own recordings, "Your playlists" lists the playlist in Library with its
 * Spotify mark, opening it matches its song (the YouTube match stands in: a silent tone on the device), it plays, and
 * Disconnect takes everything away.
 */
@RunWith(AndroidJUnit4::class)
class SpotifyConnectTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val c get() = Fixture.container
    private var tone: android.net.Uri? = null

    @After fun clean() {
        runCatching { instrumentation.runOnMainSync { c.player.stop() } }
        c.spotify.matchForTest = null
        RecordedNetwork.answers.keys.removeAll { it.startsWith(API) }
        runCatching { runBlocking { c.spotify.disconnect(clientIdToo = true, keepPlaylists = false) } }
        tone?.let { runCatching { Fixture.context.contentResolver.delete(it, null, null) } }
    }

    private fun track(id: String, name: String, artist: String) = """{"album":{"name":"Test album","images":[],"type":"album","uri":"spotify:album:a$id"},
        "artists":[{"name":"$artist","type":"artist","uri":"spotify:artist:ar$id"}],"duration_ms":60000,"explicit":false,
        "external_ids":{"isrc":"TEST0000$id"},"id":"$id","name":"$name","type":"track","uri":"spotify:track:$id","is_local":false}"""

    @Test fun aPlaylistIsListedPlayedAndDisconnected() {
        Fixture.ready()
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        instrumentation.uiAutomation.grantRuntimePermission(Fixture.context.packageName, perm)
        tone = Fixture.tone("Prism spot tone", 60)

        // Spotify's answers, as its reference shows them (playlist items under "item", February 2026).
        RecordedNetwork.answers["$API/me/playlists"] = """{"href":"","limit":50,"next":null,"offset":0,"previous":null,"total":2,"items":[
            {"collaborative":false,"description":"","id":"pl1","images":[],"name":"Spotify test list","owner":{"id":"tester","display_name":"Tester"},
             "public":false,"snapshot_id":"snap1","items":{"href":"","total":1},"type":"playlist","uri":"spotify:playlist:pl1"},
            {"collaborative":false,"description":"","id":"pl2","images":[],"name":"Followed list","owner":{"id":"spotify"},
             "public":true,"snapshot_id":"snapX","items":{"href":"","total":50},"type":"playlist","uri":"spotify:playlist:pl2"}]}"""
        RecordedNetwork.answers["$API/playlists/pl1/items"] = """{"href":"","limit":50,"next":null,"offset":0,"previous":null,"total":1,"items":[
            {"added_at":"2026-01-01T00:00:00Z","is_local":false,"item":${track("t1", "Prism spot tone", "Test Artist")}}]}"""

        Fixture.launch()
        val local = c.localMusic
        val end = System.currentTimeMillis() + 20_000
        while (local.tracks.value.none { it.song.title == "Prism spot tone" } && System.currentTimeMillis() < end) { runBlocking { local.scan() }; Thread.sleep(500) }
        val song = local.tracks.value.first { it.song.title == "Prism spot tone" }.song
        // The YouTube match, for this test: the tone.
        c.spotify.matchForTest = { t -> song.takeIf { t.title == "Prism spot tone" } }

        // Never connected: nothing Spotify is lined up or asked.
        assertFalse(c.spotify.connected)
        assertTrue(spotifyJobs().isEmpty())

        // The page and its guide.
        compose.openSettings()
        compose.clickWhenFound(hasText("Devices & connections") and hasClickAction())
        compose.clickWhenFound(hasText("Spotify") and hasClickAction())
        compose.waitForText("Create app")
        compose.waitForText("http://127.0.0.1/callback")
        compose.waitForText("Client ID")
        compose.waitForText("Spotify Premium")

        // A finished sign-in.
        c.spotify.connectForTest(SpotifyTokens("test-access", "test-refresh", Long.MAX_VALUE), SpotifyProfile("tester", "Tester"))
        compose.waitForText("Connected as Tester")
        compose.clickWhenFound(hasText("Your playlists") and hasClickAction())
        compose.waitUntil(15_000) { c.spotify.playlists.prismIds().isNotEmpty() }
        val prismId = c.spotify.playlists.prismIds().single()
        assertEquals("only your own", 1, c.spotify.playlists.mirror().notYours)
        assertFalse("a feature on: its job is lined up", spotifyJobs().isEmpty())

        // In Library with its mark; opened, its song is matched and listed.
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Library") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("Library") and hasClickAction()).onFirst().performClick()
        compose.waitForText("Spotify ·", 15_000)
        compose.clickWhenFound(hasText("Spotify test list") and hasClickAction(), 15_000)
        compose.waitUntil(20_000) { c.prismLibrary.list(prismId)?.songs?.any { it.id == song.id } == true }
        compose.waitForText("Prism spot tone", 15_000)

        // It plays: the match stands for the Spotify song.
        compose.clickWhenFound(hasText("Prism spot tone") and hasClickAction())
        compose.waitUntil(20_000) { c.player.currentSong.value?.id == song.id }

        // Disconnect, from the page.
        instrumentation.runOnMainSync { c.player.stop() }
        compose.openSettings()
        if (compose.onAllNodes(hasText("Connected as Tester")).fetchSemanticsNodes().isEmpty()) {
            compose.clickWhenFound(hasText("Devices & connections") and hasClickAction())
            compose.clickWhenFound(hasText("Spotify") and hasClickAction())
        }
        compose.clickWhenFound(hasText("Disconnect") and hasClickAction())
        compose.waitForText("Disconnect Spotify?")
        compose.clickWhenFound(hasText("Disconnect") and hasClickAction() and hasAnyAncestor(isDialog()))
        compose.waitUntil(10_000) { !c.spotify.connected }
        assertTrue("its playlist left Library", c.prismLibrary.list(prismId) == null)
        assertTrue(c.spotify.playlists.prismIds().isEmpty())
        assertTrue("the tokens are gone", c.spotify.store.tokens() == null)
        compose.waitUntil(10_000) { spotifyJobs().isEmpty() }
        compose.waitForText("Create app")
    }

    /** Spotify's background job, unless cancelled or done. */
    private fun spotifyJobs() = androidx.work.WorkManager.getInstance(Fixture.context)
        .getWorkInfosForUniqueWork(com.prism.music.data.spotify.SpotifyWorker.workName()).get()
        .filter { !it.state.isFinished }

    private companion object {
        const val API = "https://api.spotify.com/v1"
    }
}
