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
    /**
     * 1.8.4: the pages are one file each under `ui/screens/settings/` (`<Page>Page.kt`); every file there is read, so a
     * rebuilt page is too. An `Old*.kt` file (a page's code of before the rework) is read for as long as a page still
     * draws something of it: once nothing outside the old files calls any of its composables, its rows are on no page,
     * and it waits to be deleted.
     */
    private val pageFiles: List<File> = run {
        val all = File("src/main/java/com/prism/music/ui/screens/settings").listFiles().orEmpty().filter { it.extension == "kt" }.sortedBy { it.name }
        val (old, current) = all.partition { it.name.startsWith("Old") }
        val live = (current + File("src/main/java/com/prism/music/ui/screens/SettingsScreen.kt")).toMutableList()
        // Lyrics & Sing is rebuilt (LyricsSingPage.kt + LyricsSingParts.kt). Of OldLyrics.kt only `LyricsLookGroup` is
        // still called, by the player's Customize sheet (SettingsScreen.kt): a title around LyricsYourWay.kt's animation
        // choices, no row of a settings page. So its old rows and groups are on no page, and aren't read.
        val waiting = old.filter { it.name != "OldLyrics.kt" }.toMutableList()
        // Theme & colors and Looks are rebuilt: nothing calls their old files' composables any more (a comment in
        // SettingsInterim.kt still names `appearance()`, which would read as a call below).
        waiting.removeAll { it.name == "OldTheme.kt" || it.name == "OldLooks.kt" }
        // What an old file offers the others: its composables that aren't private.
        fun offers(f: File) = Regex("""^internal fun (?:[\w.]+\.)?(\w+)\(""", RegexOption.MULTILINE).findAll(f.readText()).map { it.groupValues[1] }.toList()
        while (true) {
            val text = live.joinToString("\n") { it.readText() }
            val used = waiting.filter { f -> offers(f).any { Regex("""\b$it\(""").containsMatchIn(text) } }
            if (used.isEmpty()) break
            live += used
            waiting -= used
        }
        // OldPlayer.kt: the Player page is rebuilt (PlayerPage.kt, PlayerParts.kt) and draws none of it; only the
        // Customize sheet over the player (SettingsScreen.kt) still shows its Look and Cover groups, which are on no page.
        live.filter { it.parentFile.name == "settings" && it.name != "OldPlayer.kt" }.sortedBy { it.name }
    }
    private val sources = listOf("SettingsScreen.kt", "SoundSettings.kt", "BackupUi.kt", "ConnectSettings.kt", "SpotifySettings.kt", "HistorySettings.kt")
        .map { File("src/main/java/com/prism/music/ui/screens/$it").readText() } + pageFiles.map { it.readText() }

    /** The old rows, and the kit's five (1.8.4), by the composable that draws them. */
    private val rowCalls = "Toggle|Item|SliderSetting|Label|SwitchRow|PickRow|PickCardsRow|PickListRow|SliderRow|ChoiceRow|ShowChipsRow|LinkRow"

    /**
     * Groups that only list other pages (the old hubs' link lists, still drawn inside the pages that aren't rebuilt):
     * they hold no setting, and the pages they link to are found by their own names and the old pages' names
     * ([SettingsMapTest]). They go with the old pages.
     */
    private val linkLists = setOf(R.string.the_whole_look, R.string.each_part, R.string.connections)

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

    /**
     * Text & icons and Tabs & mini player are rebuilt (1.8.4), but `OldTextIcons.kt` and `OldTabs.kt` are still read:
     * "Customize this" draws some of their groups. Rows of those two files that their pages no longer hold under these
     * words: renamed ("App text size", "Card and cover corners"), or gone to another page, which lists them (the lyrics
     * font's rows to Lyrics & Sing, "Swipe between tabs" to Gestures & buttons).
     */
    private val leftTextIconsAndTabs = setOf(
        R.string.text_size, R.string.corners,
        R.string.installed_fonts_for_lyrics, R.string.lyrics_font_file_2, R.string.remove_the_lyrics_font_file,
        R.string.swipe_between_tabs,
    ).map { com.prism.music.tr(it).substringBefore("%1\$s").trim().removeSuffix(":") }.toSet()

    /** The old group names those two pages answer to (their `…OldGroups`): the groups' rows are in other groups now. */
    private val regroupedTextIconsAndTabs: Set<String> =
        com.prism.music.ui.screens.settings.textIconsOldGroups.keys + com.prism.music.ui.screens.settings.tabsMiniPlayerOldGroups.keys

    @Test
    fun everyRowIsSearchable() {
        val rows = rows(rowCalls)
        assertTrue("the pages' rows were read (${rows.size})", rows.size > 100)
        val missing = rows.filter { row -> row !in leftTextIconsAndTabs && SettingsIndex.entries.none { it.title == row || row.startsWith(it.title) } }
        assertTrue("Not in SettingsIndex: $missing", missing.isEmpty())
        // The kit's rows are read too (the first rebuilt page and the root).
        assertTrue(com.prism.music.tr(R.string.content_country) in rows && com.prism.music.tr(R.string.performance_mode) in rows)
    }

    @Test
    fun everyGroupIsSearchable() {
        // The old groups, and the kit's (`group(...)` in a page's scaffold, `SettingsGroup(...)`).
        val groups = rows("Group|group|SettingsGroup") - linkLists.map { com.prism.music.tr(it) }.toSet()
        assertTrue("the pages' groups were read (${groups.size})", groups.size > 20)
        val missing = groups.filter { g -> g !in regroupedTextIconsAndTabs && SettingsIndex.entries.none { it.group == g } }
        assertTrue("Groups not in SettingsIndex: $missing", missing.isEmpty())
    }

    /** The files of a page that is rebuilt are no longer read; the others all are. */
    @Test
    fun oldFilesAreReadWhileAPageDrawsThem() {
        val read = pageFiles.map { it.name }
        assertTrue("the old Language page is rebuilt (LanguageRegionPage.kt): $read", "OldLanguageRegion.kt" !in read)
        assertTrue("the old Lyrics pages are rebuilt (LyricsSingPage.kt): $read", "OldLyrics.kt" !in read)
        assertTrue("the old Playback & sound page is rebuilt (PlaybackPage.kt, SoundQualityPage.kt): $read", "OldSound.kt" !in read)
        assertTrue("the old Player page is rebuilt (PlayerPage.kt, PlayerParts.kt): $read", "OldPlayer.kt" !in read)
        assertTrue("the old Theme page is rebuilt (ThemeColorsPage.kt): $read", "OldTheme.kt" !in read)
        assertTrue("the old Looks page is rebuilt (LooksPage.kt): $read", "OldLooks.kt" !in read)
        assertTrue("the old Controls & gestures page is rebuilt (GesturesButtonsPage.kt): $read", "OldGestures.kt" !in read)
        assertTrue("the old Devices & connections page is rebuilt (CarPage.kt): $read", "OldCar.kt" !in read)
        // One per line, so pages rebuilt side by side each take their own line out.
        val stillRead = listOf(
            "OldTabs.kt",
            "GesturesButtonsPage.kt",
            "CarPage.kt",
            "OldAccounts.kt",
            "OldBackupAbout.kt",
            "LanguageRegionPage.kt",
            "PlayerPage.kt",
            "PlayerParts.kt",
            "LyricsSingPage.kt",
            "LyricsSingParts.kt",
            "PlaybackPage.kt",
            "SoundQualityPage.kt",
            "TextIconsPage.kt",
            "TextIconsParts.kt",
            "TabsMiniPlayerPage.kt",
            "ThemeColorsPage.kt",
            "LooksPage.kt",
        )
        for (name in stillRead) {
            assertTrue("$name is read: $read", name in read)
        }
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
            // By its group too: other pages have a "Layout". (1.8.4: the lock screen's rows are in Player's Modes group.)
            val needs = SettingsIndex.entries.first { it.title == com.prism.music.tr(id) && it.group == com.prism.music.tr(R.string.settings_player_modes) }.needs
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
        assertTrue(e.page == com.prism.music.ui.screens.settings.SettingsPageId.HOME_LIBRARY && e.group == com.prism.music.tr(R.string.home))
        // The page's own file (1.8.4: out of SettingsScreen.kt, into ui/screens/settings/), which the page still draws.
        assertTrue("HomeLibraryLook()" in File("src/main/java/com/prism/music/ui/screens/settings/HomeLibraryPage.kt").readText())
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
        // A page's file also holds its search lines (1.8.4): those name the row without drawing it.
        assertTrue("and on no settings page", sources.none { src -> src.lineSequence().any { row in it && "SettingEntry(" !in it } })
        val e = SettingsIndex.entries.single { it.title == com.prism.music.tr(R.string.speed_dial_your_plays_first) }
        org.junit.Assert.assertEquals(com.prism.music.ui.Routes.CATALOGUE, e.opens)
        org.junit.Assert.assertEquals(com.prism.music.tr(R.string.customize_home), e.pageTitle)
        // Still found by the page's name, and still says what to turn off first.
        assertTrue(SettingsIndex.search("customize home speed dial").contains(e))
        assertTrue(e.needs != null)
        // Every other result opens a settings page at its group, as before (1.8.4: the page of the map it's on now).
        val haptics = SettingsIndex.entries.first { it.title == "Haptics" }
        org.junit.Assert.assertEquals(com.prism.music.ui.Routes.settings("gestures_buttons", haptics.group), haptics.opens)
        org.junit.Assert.assertEquals(null, SettingsIndex.entries.first { it.page == null }.opens)
    }

    /**
     * 1.8.4: no setting is listed twice. A double is two entries that make the same result: the same page, the same
     * group and the same words shown ("Buttons in the row" was there twice, so a search showed two identical rows).
     * The same title in two groups of a page (Speakers' and Handoff's "Pairing code") is two settings, not a double.
     */
    @Test
    fun noSettingIsListedTwice() {
        val doubles = SettingsIndex.entries.groupBy { Triple(it.pageTitle, it.group, it.shown) }.filter { it.value.size > 1 }
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
