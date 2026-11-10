package com.prism.music

import com.prism.music.ui.screens.SettingsIndex
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings search (#73) only finds what [SettingsIndex] lists: every setting row and group on the settings pages has to
 * be there, so a new setting without a line in the index fails here. Since 1.7.9 the pages read their words from
 * strings.xml (`tr(R.string.…)`), so each row's words are looked up there (in English, as the tests run).
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class SettingsIndexTest {
    /** 1.8.4: the pages are one file each under `ui/screens/settings/`; every file there is read, so a rebuilt page is too. */
    private val pageFiles = File("src/main/java/com/prism/music/ui/screens/settings").listFiles().orEmpty().filter { it.extension == "kt" }.sortedBy { it.name }
    private val sources = listOf("SettingsScreen.kt", "SoundSettings.kt", "BackupUi.kt", "ConnectSettings.kt", "ColorSettings.kt", "LookSettings.kt", "SpotifySettings.kt", "HistorySettings.kt")
        .map { File("src/main/java/com/prism/music/ui/screens/$it").readText() } + pageFiles.map { it.readText() }

    /** The English strings (strings.xml and, until they're folded in, a release's own strings_*.xml): name → text. */
    private val strings: Map<String, String> by lazy {
        File("src/main/res/values").listFiles().orEmpty().filter { it.name.startsWith("strings") && it.name.endsWith(".xml") }.sortedBy { it.name }
            .flatMap { Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(it.readText()) }
            .associate { it.groupValues[1] to I18nConvert.xmlUnescape(it.groupValues[2]) }
    }

    private fun rows(calls: String) = sources.flatMap { src ->
        Regex("""\b(?:$calls)\(\s*tr\(R\.string\.(\w+)""").findAll(src).map { m ->
            // "Space to use: %1$s" is listed by its fixed start.
            strings.getValue(m.groupValues[1]).substringBefore("%1\$s").trim().removeSuffix(":")
        }.toList()
    }.filter { it.isNotBlank() }.distinct()

    @Test
    fun everyRowIsSearchable() {
        val rows = rows("Toggle|Item|SliderSetting|Label")
        assertTrue("the pages' rows were read (${rows.size})", rows.size > 100)
        val missing = rows.filter { row -> SettingsIndex.entries.none { it.title == row || row.startsWith(it.title) } }
        assertTrue("Not in SettingsIndex: $missing", missing.isEmpty())
    }

    @Test
    fun everyGroupIsSearchable() {
        val groups = rows("Group")
        assertTrue("the pages' groups were read (${groups.size})", groups.size > 20)
        val missing = groups.filter { g -> SettingsIndex.entries.none { it.group == g } }
        assertTrue("Groups not in SettingsIndex: $missing", missing.isEmpty())
    }

    /** A setting that's hidden says what to turn on first, and stops saying it once that's on. */
    @Test
    fun hiddenSettingsSayWhatToTurnOn() {
        fun entry(id: Int) = SettingsIndex.entries.first { it.title == com.prism.music.tr(id) }
        val plain = com.prism.music.data.prefs.AppSettings(frostedGlass = false)
        val frost = entry(R.string.glass_frost).needs
        assertTrue("Frost's hint", frost != null && !frost.on(plain))
        assertTrue(frost!!.on(plain.copy(frostedGlass = true)))
        assertTrue(frost.on(plain.copy(liquidGlass = true)))
        for (id in listOf(R.string.lock_screen_layout_label, R.string.lock_screen_show, R.string.lock_screen_paused_label, R.string.lock_screen_hide, R.string.lock_screen_allow, R.string.lock_screen_allow_step, R.string.lock_screen_app_info)) {
            // By its group too: other pages have a "Layout".
            val needs = SettingsIndex.entries.first { it.title == com.prism.music.tr(id) && it.group == com.prism.music.tr(R.string.lock_screen_title) }.needs
            assertTrue(com.prism.music.tr(id), needs != null && !needs.on(plain))
            assertTrue(com.prism.music.tr(id), needs!!.on(plain.copy(behavior = plain.behavior.copy(lockScreen = true))))
        }
        // The lock screen's own switch isn't hidden by anything.
        assertTrue(entry(R.string.lock_screen_title).needs == null)
    }

    /** Its search result opens Home & Library, group Home: the row has to be there, not only under Customize Home. */
    @Test
    fun quickPicksFromIsWhereItsResultOpens() {
        val e = SettingsIndex.entries.first { it.title == com.prism.music.tr(R.string.quick_picks_from) }
        assertTrue(e.section == com.prism.music.ui.screens.SettingsSection.HOME && e.group == com.prism.music.tr(R.string.home))
        // The page's own file (1.8.4: out of SettingsScreen.kt, into ui/screens/settings/).
        val page = (pageFiles + File("src/main/java/com/prism/music/ui/screens/SettingsScreen.kt")).map { it.readText() }.single { "fun HomeLibraryLook()" in it }
        val group = page.substringAfter("fun HomeLibraryLook()").substringAfter("Group(tr(R.string.home))").substringBefore("Group(")
        assertTrue("Quick picks from isn't in that group", "R.string.quick_picks_from" in group)
    }

    /**
     * 1.8.4: "Speed dial: your plays first" is only on Customize Home (its own page, not one of Settings'), so its result
     * opens that page: it used to open Home & Library, which has no such row.
     */
    @Test
    fun speedDialYoursFirstOpensThePageItIsOn() {
        val row = "R.string.speed_dial_your_plays_first)"
        assertTrue("the row is on Customize Home", row in File("src/main/java/com/prism/music/ui/screens/CatalogueScreen.kt").readText())
        assertTrue("and on no settings page", sources.none { row in it })
        val e = SettingsIndex.entries.single { it.title == com.prism.music.tr(R.string.speed_dial_your_plays_first) }
        org.junit.Assert.assertEquals(com.prism.music.ui.Routes.CATALOGUE, e.opens)
        org.junit.Assert.assertEquals(com.prism.music.tr(R.string.customize_home), e.page)
        // Still found by the page's name, and still says what to turn off first.
        assertTrue(SettingsIndex.search("customize home speed dial").contains(e))
        assertTrue(e.needs != null)
        // Every other result opens a settings page at its group, as before.
        val haptics = SettingsIndex.entries.first { it.title == "Haptics" }
        org.junit.Assert.assertEquals(com.prism.music.ui.Routes.settings("controls", haptics.group), haptics.opens)
        org.junit.Assert.assertEquals(null, SettingsIndex.entries.first { it.section == null }.opens)
    }

    /**
     * 1.8.4: no setting is listed twice. A double is two entries that make the same result: the same page, the same
     * group and the same words shown ("Buttons in the row" was there twice, so a search showed two identical rows).
     * The same title in two groups of a page (Speakers' and Handoff's "Pairing code") is two settings, not a double.
     */
    @Test
    fun noSettingIsListedTwice() {
        val doubles = SettingsIndex.entries.groupBy { Triple(it.page, it.group, it.shown) }.filter { it.value.size > 1 }
        assertTrue("Listed more than once in SettingsIndex: ${doubles.map { (k, v) -> "${k.first} › ${k.second} › ${k.third} ×${v.size}" }}", doubles.isEmpty())
        // What each of the two had as search words still finds the one that's left.
        val row = com.prism.music.tr(R.string.buttons_in_the_row)
        assertTrue(SettingsIndex.search("audio only").any { it.title == row })
        assertTrue(SettingsIndex.search("bottom row lyrics").any { it.title == row })
    }

    @Test
    fun findsBySynonymAndTitle() {
        assertTrue(SettingsIndex.search("vibration").any { it.title == "Haptics" })
        assertTrue(SettingsIndex.search("crossfade").first().title.startsWith("Crossfade"))
        assertTrue(SettingsIndex.search("eq").any { it.title == "Equalizer" })
        assertTrue(SettingsIndex.search("zzzz").isEmpty())
    }
}
