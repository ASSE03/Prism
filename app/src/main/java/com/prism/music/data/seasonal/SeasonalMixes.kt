package com.prism.music.data.seasonal

import android.util.Log
import com.prism.music.AppContainer
import com.prism.music.data.foryou.Mix
import com.prism.music.data.model.Song
import com.prism.music.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.concurrent.ConcurrentHashMap

/**
 * The holiday mixes on this phone (1.8.0): made the first time one is opened in its season (never at start, never for
 * a mix nobody opens), kept in files/seasonal/ for the season and made again about once a week, from YouTube Music
 * signed out ([YouTubeHolidaySources]). Nothing about the listener goes into them, and nothing of theirs goes out.
 */
class SeasonalMixes(private val c: AppContainer) {
    /** The clock the season is read from: the phone's own (its time zone too); a test sets a date of its own. */
    @Volatile var clock: Clock? = null

    fun today(): LocalDate = LocalDate.now(clock ?: Clock.systemDefaultZone())

    /** The season now, or null outside both. */
    fun holiday(): Holiday? = Holiday.on(today())

    /** The mixes Home shows today. */
    fun shown(): List<HolidayMix> = HolidayMixes.on(today())

    @Serializable
    data class Kept(val id: String, val season: String, val made: Long, val songs: List<Song>)

    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = File(c.app.filesDir, "seasonal")
    private val memory = ConcurrentHashMap<String, Kept>()
    /** One mix made at a time: each is a dozen searches, and the phone has better things to do. */
    private val making = Mutex()

    private fun file(mix: HolidayMix) = File(dir, mix.key + ".json")

    /** This season's key: a mix kept last Christmas isn't this Christmas's. */
    private fun season(mix: HolidayMix, date: LocalDate = today()) = "${date.year}-${mix.holiday.name}"

    private fun week(date: LocalDate = today()) = "${date.get(IsoFields.WEEK_BASED_YEAR)}-W${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    /** [mix] as kept for this season, if it is (no network). */
    suspend fun kept(mix: HolidayMix): Kept? {
        val want = season(mix)
        memory[mix.id]?.takeIf { it.season == want }?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(mix)
            val k = runCatching { json.decodeFromString(Kept.serializer(), f.readText()) }.getOrNull()
            if (k != null && k.season != want) { f.delete(); null }
            else k?.also { memory[mix.id] = it }
        }
    }

    /** Kept already in memory, for a menu that opens at once. */
    fun keptNow(id: String): Kept? = HolidayMixes.byId(id)?.let { m -> memory[id]?.takeIf { it.season == season(m) } }

    /**
     * Mix [id] for its page, the car or a pinned section: the kept one (made again behind it once it's a week old), else
     * made now. Null out of its season, or when it couldn't be made.
     */
    suspend fun mix(id: String): Mix? {
        val def = HolidayMixes.byId(id) ?: return null
        if (!def.shownOn(today())) return null
        val k = kept(def)
        if (k != null) {
            if (System.currentTimeMillis() - k.made > WEEK_MS) c.scope.launch { runCatching { make(def) } }
            return toMix(def, k)
        }
        return make(def)?.let { toMix(def, it) }
    }

    /** Makes [mix] again (after any song that's starting, [AppContainer.quietNetwork]); the old one stays if this falls short. */
    private suspend fun make(mix: HolidayMix): Kept? = making.withLock {
        // Made by another call while this one waited.
        kept(mix)?.takeIf { System.currentTimeMillis() - it.made <= WEEK_MS }?.let { return@withLock it }
        c.quietNetwork()
        val songs = withContext(Dispatchers.IO) {
            runCatching { HolidayMixMaker(YouTubeHolidaySources(c.ytm)).build(mix, week()) }
                .onFailure { Log.w(TAG, "${mix.key} not made", it) }.getOrDefault(emptyList())
        }
        Log.i(TAG, "${mix.key}: ${songs.size} songs")
        if (songs.size < MIN_SONGS) return@withLock kept(mix)
        put(mix, songs)
    }

    /** [songs] kept as [id] for this season, made now (also how a test puts one in place). */
    suspend fun keep(id: String, songs: List<Song>) { HolidayMixes.byId(id)?.let { put(it, songs) } }

    private suspend fun put(mix: HolidayMix, songs: List<Song>): Kept {
        val k = Kept(mix.id, season(mix), System.currentTimeMillis(), songs)
        memory[mix.id] = k
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            runCatching { file(mix).writeText(json.encodeToString(Kept.serializer(), k)) }
        }
        return k
    }

    fun toMix(mix: HolidayMix, k: Kept): Mix {
        val artists = k.songs.map { it.primaryArtist }.filter { it.isNotBlank() }.distinct()
        return Mix(mix.id, tr(mix.name), tr(mix.blurb), artists.take(4), mix.top, k.made, k.songs)
    }

    companion object {
        private const val TAG = "PrismHoliday"
        private const val WEEK_MS = 7 * 86_400_000L
        /** Fewer than this and the mix isn't kept (YouTube answered badly): the last one stays. */
        private const val MIN_SONGS = 20
    }
}
