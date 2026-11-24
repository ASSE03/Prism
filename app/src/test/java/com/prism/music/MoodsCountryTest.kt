package com.prism.music

import com.prism.music.data.MoodsCache
import com.prism.music.data.innertube.InnerTube
import com.prism.music.data.innertube.YouTubeMusic
import com.prism.music.data.prefs.AppSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Moods & genres showed the old country's after a new content location in Settings. The kept page is per language and
 * country; Home's and Search's own copies (kept as long as the app runs) were kept under one name whatever the country.
 */
class MoodsCountryTest {
    @get:Rule val dir = TemporaryFolder()
    private var settings = AppSettings(contentCountry = "US")
    private val ytm = YouTubeMusic(InnerTube(OkHttpClient(), { settings }))

    @Test fun anotherCountryIsAnotherScreenLoad() {
        val us = ytm.moodsLoadKey()
        settings = settings.copy(contentCountry = "DE")
        assertNotEquals("Home and Search ask again for another country", us, ytm.moodsLoadKey())
        settings = settings.copy(contentCountry = "US")
        assertEquals(us, ytm.moodsLoadKey())
    }

    @Test fun anotherCountryDoesNotGetTheKeptPage() = runBlocking {
        val api = InnerTube(OkHttpClient(), { settings })
        val cache = MoodsCache(dir.root) { 1_000_000L }
        val parse: (JsonObject) -> List<String> = { listOf((it["v"] as JsonPrimitive).content) }
        fun page(v: String): suspend () -> JsonObject = { JsonObject(mapOf("v" to JsonPrimitive(v))) }
        assertEquals(listOf("us"), cache.get(api.pageLanguage, page("us"), parse))
        settings = settings.copy(contentCountry = "DE")
        assertEquals(listOf("de"), cache.get(api.pageLanguage, page("de"), parse))
    }
}
