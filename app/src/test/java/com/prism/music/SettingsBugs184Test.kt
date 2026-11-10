package com.prism.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.prism.music.data.HistoryPause
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.BleedFit
import com.prism.music.data.prefs.LyricsExtraPlace
import com.prism.music.ui.screens.settings.CoverFitChoice
import com.prism.music.ui.screens.CrossfadeOnBarChoice
import com.prism.music.ui.screens.SettingsSection
import com.prism.music.ui.screens.TranslationPlaceChoice
import com.prism.music.ui.screens.lookStepLine
import com.prism.music.ui.theme.PrismTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 1.8.4's settings bug round (plan §1), the rows on the pages themselves; the two about Settings' search are in
 * [SettingsIndexTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h3000dp-xxhdpi", application = android.app.Application::class)
class SettingsBugs184Test {
    @get:Rule val compose = createComposeRule()

    private fun page(name: String) = File("src/main/java/com/prism/music/ui/screens/$name").readText()

    /** SettingsScreen.kt and every page's file under `ui/screens/settings/` (1.8.4: one file a page). */
    private fun settingsPages() = listOf(page("SettingsScreen.kt")) +
        File("src/main/java/com/prism/music/ui/screens/settings").listFiles().orEmpty().filter { it.extension == "kt" }.sortedBy { it.name }.map { it.readText() }

    /** The one settings file that holds [marker]. */
    private fun pageWith(marker: String) = settingsPages().single { marker in it }

    private fun show(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
        compose.setContent { PrismTheme(settings, artworkColor = null) { Column(Modifier.width(412.dp)) { content() } } }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** Looks → History: a step's line was English in every language ("… · tap to undo" written in the code). */
    @Test fun aLookStepsLineComesFromTheStrings() {
        assertFalse("LookSettings.kt still writes the line itself", Regex("\"[^\"\\n]*tap to [^\"\\n]*\"").containsMatchIn(page("LookSettings.kt")))
        assertEquals(tr(R.string.look_step_tap_to_undo, "5 minutes ago"), lookStepLine("5 minutes ago", newest = true))
        assertEquals(tr(R.string.look_step_tap_to_go_back, "5 minutes ago"), lookStepLine("5 minutes ago", newest = false))
        assertTrue(lookStepLine("5 minutes ago", newest = true).startsWith("5 minutes ago"))
        assertTrue(lookStepLine("5 minutes ago", true) != lookStepLine("5 minutes ago", false))
    }

    /** Player → Progress bar: "Show the crossfade on the progress bar" only does anything with Crossfade on. */
    @Test fun theCrossfadeOnTheBarChoiceShowsOnlyWithCrossfadeOn() {
        val off = AppSettings(mix = false)
        val on = AppSettings(mix = true)
        var s by androidx.compose.runtime.mutableStateOf(off)
        show { CrossfadeOnBarChoice(s, cover = null) {} }
        assertEquals("with Crossfade off", 0, shown(tr(R.string.crossfade_on_the_progress_bar)))
        assertEquals("its cards with Crossfade off", 0, shown(tr(R.string.shaped_by_the_crossfade)))
        s = on
        compose.waitForIdle()
        assertEquals("with Crossfade on", 1, shown(tr(R.string.crossfade_on_the_progress_bar)))
        assertEquals("its cards with Crossfade on", 1, shown(tr(R.string.shaped_by_the_crossfade)))
    }

    /** Player → Cover: the "Fill the screen / Whole cover" chips have a label. */
    @Test fun theCoverFitChipsAreLabelled() {
        show { CoverFitChoice(BleedFit.FILL) {} }
        assertEquals(1, shown(tr(R.string.how_the_cover_fits)))
        BleedFit.entries.forEach { assertEquals(it.label, 1, shown(it.label)) }
        // And the page's chips are these: none of BleedFit's left bare.
        val chips = "Chips(com.prism.music.data.prefs.BleedFit.entries"
        assertEquals(1, settingsPages().sumOf { it.split(chips).size - 1 })
        val src = pageWith("fun CoverFitChoice(")
        assertTrue(chips in src.substringAfter("fun CoverFitChoice(").substringBefore("\n}"))
    }

    /** Lyrics → Translations & romanized lines: the "Under the line / Over the line" chips have a label. */
    @Test fun theTranslationPlaceChipsAreLabelled() {
        show { TranslationPlaceChoice(LyricsExtraPlace.BELOW) {} }
        assertEquals(1, shown(tr(R.string.translation_place)))
        LyricsExtraPlace.entries.forEach { assertEquals(it.label, 1, shown(it.label)) }
        assertTrue("the group shows them through the labelled choice", "TranslationPlaceChoice(s.lyricsExtraPlace)" in page("LyricsYourWay.kt"))
    }

    /** The hub's line for Recommendations & Replay: nothing from another page (the content country is on Language now). */
    @Test fun recommendationsLineIsFromItsOwnPage() {
        val plain = SettingsSection.LIBRARY.summary(AppSettings())
        val german = SettingsSection.LIBRARY.summary(AppSettings(contentCountry = "DE"))
        assertFalse("the content country is on the Language page: $german", "Germany" in german || "DE" in german)
        assertEquals(plain, german)
        // What it does say is the page's own: history paused shows.
        val paused = AppSettings().let { it.copy(behavior = it.behavior.copy(historyPausedUntil = HistoryPause.FOREVER)) }
        assertFalse(tr(R.string.hist_paused_title) in plain)
        assertTrue(tr(R.string.hist_paused_title) in SettingsSection.LIBRARY.summary(paused))
    }

    /** Downloads → Mobile data → Load ahead on mobile data: its ⓘ sent people to Playback & sound; Load ahead is on this page. */
    @Test fun loadAheadOnMobileDatasInfoNamesTheRightPlace() {
        val name = Regex("""tr\(R\.string\.load_ahead_on_mobile_data\),.*?info = tr\(R\.string\.(\w+)\)""", RegexOption.DOT_MATCHES_ALL)
            .find(pageWith("tr(R.string.load_ahead_on_mobile_data),"))!!.groupValues[1]
        val info = tr(R.string::class.java.getField(name).getInt(null))
        assertTrue("the ⓘ's text was read ($name)", info.length > 20 && !info.startsWith("#"))
        assertFalse("it names Playback & sound: $info", tr(R.string.playback_sound) in info)
        // Load ahead's own group is on the Downloads page, right above the Mobile data group.
        val downloads = pageWith("tr(R.string.load_ahead_on_mobile_data),").substringBefore("tr(R.string.load_ahead_on_mobile_data),")
        assertTrue(downloads.lastIndexOf("Group(tr(R.string.load_ahead)") in 0 until downloads.lastIndexOf("Group(tr(R.string.mobile_data)"))
    }
}
