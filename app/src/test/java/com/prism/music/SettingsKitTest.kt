package com.prism.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.ThemeMode
import com.prism.music.ui.components.LocalFrosted
import com.prism.music.ui.screens.ShowOption
import com.prism.music.ui.screens.settings.ChoiceRow
import com.prism.music.ui.screens.settings.LanguageRegionContent
import com.prism.music.ui.screens.settings.LinkRow
import com.prism.music.ui.screens.settings.PageReset
import com.prism.music.ui.screens.settings.PickListSheet
import com.prism.music.ui.screens.settings.PickRow
import com.prism.music.ui.screens.settings.PickSheet
import com.prism.music.ui.screens.settings.SettingsCardGlass
import com.prism.music.ui.screens.settings.SettingsGroup
import com.prism.music.ui.screens.settings.SettingsPageScaffold
import com.prism.music.ui.screens.settings.SettingsRowChanged
import com.prism.music.ui.screens.settings.SettingsRowValue
import com.prism.music.ui.screens.settings.SettingsTags
import com.prism.music.ui.screens.settings.ShowChipsRow
import com.prism.music.ui.screens.settings.SliderRow
import com.prism.music.ui.screens.settings.SwitchRow
import com.prism.music.ui.screens.settings.resetLine
import com.prism.music.ui.theme.PrismTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.8.4, the settings kit (plan §2.2, §2.4, §2.9): the five row types with their tags, values and heights, the grouped
 * card (plain in Performance mode), the page scaffold (Reset this page, jump chips, the pinned preview) and the pick
 * sheets, on a 360 dp phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class SettingsKitTest {
    @get:Rule val compose = createComposeRule()

    private fun show(settings: AppSettings = AppSettings(themeMode = ThemeMode.DARK), fontScale: Float = 1f, frosted: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                PrismTheme(settings, artworkColor = null) {
                    CompositionLocalProvider(LocalFrosted provides frosted) {
                        Box(Modifier.width(360.dp).background(MaterialTheme.colorScheme.background)) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tagged(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()

    @Composable
    private fun EveryRow() {
        SettingsGroup("Rows") {
            SwitchRow("Gapless albums", true, value = "On for albums") {}
            PickRow("Layout", "Classic") {}
            SliderRow("Crossfade", 6f, 0f..12f, { "${it.toInt()} s" }) {}
            ChoiceRow("Theme", listOf("Light", "Dark", "Auto"), "Dark", { it }) {}
            ShowChipsRow("In the queue", listOf(ShowOption("Covers", true) {}, ShowOption("Durations", false) {}, ShowOption("Numbers", true) {}))
            LinkRow("Equalizer", value = "Flat") {}
        }
    }

    @Test fun everyRowCarriesItsTypeAndItsValue() {
        show { EveryRow() }
        fun values(tag: String) = tagged(tag).map { it.config[SettingsRowValue] }
        assertEquals(listOf("On for albums"), values(SettingsTags.ROW_SWITCH))
        assertEquals(listOf("Classic"), values(SettingsTags.ROW_PICK))
        assertEquals(listOf("6 s"), values(SettingsTags.ROW_SLIDER))
        assertEquals(listOf("Dark", "Covers · Numbers"), values(SettingsTags.ROW_CHOICE))
        assertEquals(listOf("Flat"), values(SettingsTags.ROW_LINK))
        // Five types and nothing else: every row in the card is one of them.
        assertEquals(6, SettingsTags.rowTypes.sumOf { tagged(it).size })
        assertEquals(5, SettingsTags.rowTypes.size)
        // The value line is on screen, not only in the tag.
        compose.onNodeWithText("On for albums", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Classic", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("6 s", useUnmergedTree = true).assertExists()
    }

    @Test fun aRowIs64WithAValueLineAnd56Without() {
        show {
            SettingsGroup(null) {
                SwitchRow("Skip silence", false) {}
                SwitchRow("Normalize", true, value = "Quieter songs come up") {}
                PickRow("Layout", "Classic") {}
                LinkRow("Licenses") {}
                LinkRow("Equalizer", value = "Flat") {}
            }
        }
        val switches = compose.onAllNodes(hasTestTag(SettingsTags.ROW_SWITCH))
        switches[0].assertHeightIsEqualTo(56.dp)
        switches[1].assertHeightIsEqualTo(64.dp)
        compose.onNodeWithTag(SettingsTags.ROW_PICK).assertHeightIsEqualTo(64.dp)
        val links = compose.onAllNodes(hasTestTag(SettingsTags.ROW_LINK))
        links[0].assertHeightIsEqualTo(56.dp)
        links[1].assertHeightIsEqualTo(64.dp)
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val out = ArrayList<TextLayoutResult>()
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.first()
    }

    @Test fun atLargeTextARowGrowsAndNothingIsCutOff() {
        val title = "Keep the screen on while the lyrics are showing on the player"
        val value = "Only while a song is playing and Prism is the app in front"
        show(fontScale = 1.5f) { SettingsGroup(null) { PickRow(title, value) {} } }
        val row = compose.onNodeWithTag(SettingsTags.ROW_PICK).fetchSemanticsNode()
        val tall = with(compose.density) { row.size.height.toDp() }
        assertTrue("the row grew past 64 dp: $tall", tall > 64.dp)
        for (text in listOf(title, value)) {
            val l = layoutOf(text)
            assertTrue("\"$text\" wraps", l.lineCount >= 2)
            assertFalse("\"$text\" is cut off", l.hasVisualOverflow)
            assertFalse(l.isLineEllipsized(l.lineCount - 1))
        }
    }

    @Test fun theChangedDotFollowsItsFlag() {
        show {
            SettingsGroup(null) {
                PickRow("Background", "Blurred cover", changed = true) {}
                PickRow("Layout", "Classic") {}
            }
        }
        assertEquals(1, compose.onAllNodes(hasTestTag(SettingsTags.CHANGED_DOT), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(listOf(true, false), tagged(SettingsTags.ROW_PICK).map { it.config[SettingsRowChanged] })
    }

    // ---------------------------------------------------------------- the card

    /** The card's own color in the middle, drawn over red: glass lets the red through, the plain card doesn't. */
    private fun cardOverRed(settings: AppSettings): Triple<Boolean, Int, Int> {
        var plain = Color.Unspecified
        show(settings, frosted = true) {
            plain = MaterialTheme.colorScheme.surfaceContainerHigh
            Box(Modifier.fillMaxWidth().background(Color.Red)) { SettingsGroup(null) { Spacer(Modifier.fillMaxWidth().height(48.dp)) } }
        }
        val card = compose.onNodeWithTag(SettingsTags.GROUP_CARD)
        val shot = card.captureToImage().asAndroidBitmap()
        return Triple(card.fetchSemanticsNode().config[SettingsCardGlass], shot.getPixel(shot.width / 2, shot.height / 2), plain.toArgb())
    }

    @Test fun performanceModeGivesThePlainCard() {
        val (glass, pixel, plain) = cardOverRed(AppSettings(themeMode = ThemeMode.DARK, performanceMode = true))
        assertFalse("no glass in Performance mode", glass)
        assertEquals("a solid surface, nothing of the page shows through", plain, pixel)
    }

    @Test fun onAFrostedPageTheCardIsGlass() {
        val (glass, pixel, plain) = cardOverRed(AppSettings(themeMode = ThemeMode.DARK))
        assertTrue(glass)
        assertNotEquals("the page shows through the glass", plain, pixel)
    }

    // ---------------------------------------------------------------- the page

    private val groups = listOf("Layout", "Progress bar", "Cover", "On it", "Lock screen")

    @Composable
    private fun Page(
        count: Int, state: LazyListState, reset: PageReset? = null, strips: MutableList<Boolean>? = null, preview: Boolean = false,
    ) {
        SettingsPageScaffold(
            "Player", bottomPadding = 800.dp, showBack = false, listState = state, reset = reset, onSearch = {},
            preview = if (preview) ({ strip -> strips?.add(strip); Box(Modifier.fillMaxSize()) }) else null,
        ) {
            groups.take(count).forEach { g ->
                group(g) { repeat(5) { i -> SwitchRow("$g option ${i + 1}", i % 2 == 0) {} } }
            }
        }
    }

    @Test fun resetThisPageListsWhatChangesBeforeItResets() {
        var reset = 0
        val changes = listOf(resetLine("Background", "Blurred cover", "Cover colors"), resetLine("Ring on the play button", "Off", "On"))
        show { Page(2, LazyListState(), PageReset(changes) { reset++ }) }
        compose.onNodeWithTag(SettingsTags.PAGE_MORE).performClick()
        compose.onNodeWithTag(SettingsTags.RESET_ITEM).performClick()
        val listed = compose.onAllNodes(hasTestTag(SettingsTags.RESET_CHANGE)).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString { t -> t.text } }
        assertEquals(listOf("Background: Blurred cover → Cover colors", "Ring on the play button: Off → On"), listed)
        compose.onNodeWithText("Reset Player?").assertExists()
        assertEquals("nothing is reset before the confirm", 0, reset)
        compose.onNodeWithTag(SettingsTags.RESET_CONFIRM).performClick()
        compose.waitForIdle()
        assertEquals(1, reset)
        assertEquals("the confirm is gone", 0, compose.onAllNodes(hasTestTag(SettingsTags.RESET_CHANGE)).fetchSemanticsNodes().size)
    }

    @Test fun cancellingTheResetChangesNothing() {
        var reset = 0
        show { Page(2, LazyListState(), PageReset(listOf(resetLine("Layout", "Compact", "Classic"))) { reset++ }) }
        compose.onNodeWithTag(SettingsTags.PAGE_MORE).performClick()
        compose.onNodeWithTag(SettingsTags.RESET_ITEM).performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(0, reset)
    }

    @Test fun withNothingChangedResetCantBeTapped() {
        show { Page(2, LazyListState(), PageReset(emptyList()) {}) }
        compose.onNodeWithTag(SettingsTags.PAGE_MORE).performClick()
        compose.onNodeWithTag(SettingsTags.RESET_ITEM).assertIsNotEnabled()
    }

    @Test fun aJumpChipScrollsToItsGroup() {
        val state = LazyListState()
        show { Page(5, state) }
        assertEquals(0, state.firstVisibleItemIndex)
        compose.onNode(hasText("On it") and hasAnyAncestor(hasTestTag(SettingsTags.JUMP_CHIPS))).performClick()
        compose.waitForIdle()
        // The list: the title, the chips, then the groups; "On it" is the fourth group.
        assertEquals(2 + 3, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
        compose.onNodeWithText("On it option 1", useUnmergedTree = true).assertExists()
    }

    @Test fun fewerThanFourGroupsGetNoJumpChips() {
        show { Page(3, LazyListState()) }
        assertEquals(0, tagged(SettingsTags.JUMP_CHIPS).size)
    }

    @Test fun thePinnedPreviewShrinksToAStripAndATapOpensIt() {
        val strips = ArrayList<Boolean>()
        show { Page(3, LazyListState(), strips = strips, preview = true) }
        compose.onNodeWithTag(SettingsTags.PREVIEW).assertHeightIsEqualTo(200.dp)
        assertEquals(false, strips.last())
        compose.onNodeWithTag(SettingsTags.PAGE_LIST).performScrollToIndex(3)
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.PREVIEW).assertHeightIsEqualTo(72.dp)
        assertEquals("the preview is told it's a strip", true, strips.last())
        // Still there, under the bar.
        compose.onNodeWithTag(SettingsTags.PREVIEW).assertExists()
        compose.onNodeWithTag(SettingsTags.PREVIEW).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.PREVIEW).assertHeightIsEqualTo(200.dp)
        assertEquals(false, strips.last())
    }

    // ---------------------------------------------------------------- pick sheets

    private val styles = listOf("Line", "Wave", "Thick", "Steps")

    @Test fun aTapInThePickSheetAppliesAndCloses() {
        var style by mutableStateOf("Line")
        var open by mutableStateOf(true)
        show {
            if (open) PickSheet("Progress bar style", styles, style, { it }, { style = it }, { open = false }) { Box(Modifier.fillMaxWidth().height(34.dp)) }
        }
        val options = compose.onAllNodes(hasTestTag(SettingsTags.SHEET_OPTION))
        assertEquals(4, options.fetchSemanticsNodes().size)
        assertEquals("the current pick is the marked one", listOf(true, false, false, false), options.fetchSemanticsNodes().map { it.config[SemanticsProperties.Selected] })
        compose.onNode(hasTestTag(SettingsTags.SHEET_OPTION) and hasText("Wave")).performClick()
        compose.waitUntil(5_000) { !open }
        assertEquals("Wave", style)
    }

    @Test fun swipingThePickSheetAwayChangesNothing() {
        var style by mutableStateOf("Line")
        var picks = 0
        var open by mutableStateOf(true)
        show {
            if (open) PickSheet("Progress bar style", styles, style, { it }, { style = it; picks++ }, { open = false }) { Box(Modifier.fillMaxWidth().height(34.dp)) }
        }
        compose.onNodeWithTag(SettingsTags.SHEET).performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { !open }
        assertEquals("Line", style)
        assertEquals(0, picks)
    }

    @Test fun theListSheetSearchesAndPicks() {
        val countries = listOf("Austria", "Belgium", "Germany", "Japan", "Norway")
        var country by mutableStateOf("Japan")
        var open by mutableStateOf(true)
        show {
            if (open) PickListSheet("Content country", countries, country, { it }, { country = it }, { open = false }, searchable = true)
        }
        assertEquals(5, compose.onAllNodes(hasTestTag(SettingsTags.SHEET_OPTION)).fetchSemanticsNodes().size)
        compose.onNode(hasSetTextAction()).performTextInput("ger")
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodes(hasTestTag(SettingsTags.SHEET_OPTION)).fetchSemanticsNodes().size)
        compose.onNode(hasTestTag(SettingsTags.SHEET_OPTION) and hasText("Germany")).performClick()
        compose.waitUntil(5_000) { !open }
        assertEquals("Germany", country)
    }

    // ---------------------------------------------------------------- the sample page

    @Test fun languageAndRegionShowsBothValuesAndPicksACountry() {
        var country by mutableStateOf("DE")
        var language = "unset"
        show { LanguageRegionContent(0.dp, AppLanguage.PHONE, country, { language = it }, { country = it }, showBack = false) }
        val rows = tagged(SettingsTags.ROW_PICK)
        assertEquals(2, rows.size)
        assertTrue("the language row says it follows the phone: ${rows[0].config[SettingsRowValue]}", rows[0].config[SettingsRowValue].startsWith("Automatic ("))
        assertEquals("Germany", rows[1].config[SettingsRowValue])
        assertEquals(listOf(false, true), rows.map { it.config[SettingsRowChanged] })
        // One group, no header that repeats the page.
        assertEquals(1, tagged(SettingsTags.GROUP).size)

        // (The row's own tap: a click at its middle would land on the ⓘ, which sits right after this short title.)
        compose.onAllNodes(hasTestTag(SettingsTags.ROW_PICK))[1].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("norw")
        compose.waitForIdle()
        compose.onNode(hasTestTag(SettingsTags.SHEET_OPTION) and hasText("Norway")).performClick()
        compose.waitUntil(5_000) { country == "NO" }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(SettingsTags.SHEET)).fetchSemanticsNodes().isEmpty() }
        assertEquals("Norway", tagged(SettingsTags.ROW_PICK)[1].config[SettingsRowValue])
        assertEquals("the language was left alone", "unset", language)

        // Reset this page lists the country (and only it) and puts it back to Automatic.
        compose.onNodeWithTag(SettingsTags.PAGE_MORE).performClick()
        compose.onNodeWithTag(SettingsTags.RESET_ITEM).performClick()
        val listed = compose.onAllNodes(hasTestTag(SettingsTags.RESET_CHANGE)).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString { t -> t.text } }
        assertEquals(1, listed.size)
        assertTrue(listed[0], listed[0].startsWith("Content country: Norway → Automatic ("))
        compose.onNodeWithTag(SettingsTags.RESET_CONFIRM).performClick()
        compose.waitForIdle()
        assertEquals("", country)
        assertEquals(listOf(false, false), tagged(SettingsTags.ROW_PICK).map { it.config[SettingsRowChanged] })
    }
}
