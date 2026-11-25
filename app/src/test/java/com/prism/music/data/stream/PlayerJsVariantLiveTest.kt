package com.prism.music.data.stream

import com.prism.music.LiveNetwork
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/** The TV copy of today's player is there and carries the signature timestamp Prism sends (yt-dlp #18123). */
@Category(LiveNetwork::class)
class PlayerJsVariantLiveTest {
    private val http = OkHttpClient()

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            assertTrue("HTTP ${r.code} for $url", r.isSuccessful)
            r.body.string()
        }

    @Test
    fun todaysPlayerHasATvCopyWithItsTimestamp() {
        val id = Regex("""player\\?/([0-9a-fA-F]{8})\\?/""").find(get("https://www.youtube.com/iframe_api"))?.groupValues?.get(1)
        assertNotNull("no player id in iframe_api", id)
        val code = get(PlayerJsSolver.playerUrl(id!!, PlayerJsSolver.VARIANTS.first()))
        assertNotNull("no signature timestamp in player $id's TV copy",
            Regex("""(?:signatureTimestamp|sts)\s*:\s*(\d{5})""").find(code))
    }
}
