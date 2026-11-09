package com.prism.music

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.ThemeMode
import com.prism.music.ui.components.LocalFrosted
import com.prism.music.ui.screens.ShowOption
import com.prism.music.ui.screens.settings.ChoiceRow
import com.prism.music.ui.screens.settings.LanguageRegionContent
import com.prism.music.ui.screens.settings.LinkRow
import com.prism.music.ui.screens.settings.PageReset
import com.prism.music.ui.screens.settings.PickCards
import com.prism.music.ui.screens.settings.PickList
import com.prism.music.ui.screens.settings.PickRow
import com.prism.music.ui.screens.settings.SettingsPageScaffold
import com.prism.music.ui.screens.settings.SettingsTags
import com.prism.music.ui.screens.settings.ShowChipsRow
import com.prism.music.ui.screens.settings.SliderRow
import com.prism.music.ui.screens.settings.SwitchRow
import com.prism.music.ui.theme.Backdrop
import com.prism.music.ui.theme.BackdropLayer
import com.prism.music.ui.theme.PrismTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 1.8.4, the settings kit drawn for real, for the owner's look before the pages are built (plan §2.10 step 1): the
 * sample page (Language & region) in light, dark and Performance mode at 360 dp, and a page that shows every part of
 * the kit (the pinned preview, jump chips, the five rows, the two sheets). PNGs go to app/build/settings-kit/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h760dp-xxhdpi")
class SettingsKitRenderTest {
    @get:Rule val compose = createComposeRule()

    /** [glass]: on a background, as the app's frosted pages are (the Spectrum field stands in for the ambient frost). */
    private fun show(settings: AppSettings, glass: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            PrismTheme(settings, artworkColor = null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (glass) BackdropLayer(Backdrop.SPECTRUM, null, 0.35f, motion = false, modifier = Modifier.fillMaxSize())
                    CompositionLocalProvider(LocalFrosted provides glass) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun save(name: String): Bitmap {
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/settings-kit").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return shot
    }

    private fun languageRegion(name: String, settings: AppSettings, glass: Boolean) {
        show(settings, glass) { LanguageRegionContent(0.dp, AppLanguage.PHONE, "DE", {}, {}, onBack = {}, onSearch = {}) }
        val shot = save(name)
        assertEquals("360 dp wide at xxhdpi", 1080, shot.width)
        assertEquals(2, compose.onAllNodes(hasTestTag(SettingsTags.ROW_PICK)).fetchSemanticsNodes().size)
    }

    @Test fun languageRegionLight() = languageRegion("language-region-light", AppSettings(themeMode = ThemeMode.LIGHT), glass = true)
    @Test fun languageRegionDark() = languageRegion("language-region-dark", AppSettings(themeMode = ThemeMode.DARK), glass = true)

    /** Performance mode: no frosted page behind it, and the card a plain surface. */
    @Test fun languageRegionPerformance() = languageRegion("language-region-performance", AppSettings(themeMode = ThemeMode.DARK, performanceMode = true), glass = false)

    /** A stand-in for a page's live preview: a cover, two lines and a progress bar (just the cover and lines as a strip). */
    @Composable
    private fun SamplePreview(strip: Boolean) {
        val cover = Brush.linearGradient(listOf(Color(0xFFFF9A62), Color(0xFFC94B7C), Color(0xFF3A3A8C)))
        if (strip) androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(cover))
            Column(Modifier.padding(start = 12.dp)) {
                Text("Test title", style = MaterialTheme.typography.titleSmall)
                Text("Test artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp).clip(RoundedCornerShape(14.dp)).background(cover))
            Text("Test title", Modifier.fillMaxWidth().padding(top = 10.dp), style = MaterialTheme.typography.titleSmall)
            Text("Test artist", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)))
        }
    }

    @Composable
    private fun EveryPart() {
        SettingsPageScaffold(
            "Player", bottomPadding = 0.dp, onBack = {}, onSearch = {}, reset = PageReset(listOf("Background: Blurred cover → Cover colors")) {},
            preview = { strip -> SamplePreview(strip) },
        ) {
            group("Layout") {
                PickRow("Layout", "Classic", thumbnail = { Box(Modifier.fillMaxSize().background(Color(0xFF1D1D26))) }) {}
                PickRow("Background", "Blurred cover", changed = true, thumbnail = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF6D4A8F), Color(0xFF23233A))))) }) {}
                SwitchRow("Ring on the play button", true, info = "A ring of your light round the play button.") {}
            }
            group("Progress bar") {
                PickRow("Style", "Wave") {}
                ChoiceRow("Color", listOf("Your light", "Accent", "White"), "Your light", { it }) {}
                SliderRow("Thickness", 4f, 2f..8f, { "${it.toInt()} dp" }, changed = true) {}
            }
            group("Cover") {
                ChoiceRow("Edge to edge", listOf("Off", "Cover", "Video", "Both"), "Off", { it }) {}
                SwitchRow("Cover flies into place", false, value = "Opening the player") {}
            }
            group("On it") {
                ShowChipsRow("Show", listOf(ShowOption("Like", true) {}, ShowOption("Lyrics", true) {}, ShowOption("Queue", true) {}, ShowOption("Sleep timer", false) {}, ShowOption("Speed", false) {}))
                LinkRow("Lock screen and desk mode", value = "Cover layout", icon = com.prism.music.ui.theme.PrismIcons.Settings) {}
            }
        }
    }

    @Test fun everyPartDark() {
        show(AppSettings(themeMode = ThemeMode.DARK), glass = true) { EveryPart() }
        save("kit-dark")
        assertEquals(1, compose.onAllNodes(hasTestTag(SettingsTags.JUMP_CHIPS)).fetchSemanticsNodes().size)
        // Scrolled: the preview is a strip under the bar, the title sits in the bar.
        compose.onNodeWithTag(SettingsTags.PAGE_LIST).performScrollToIndex(4)
        compose.waitForIdle()
        save("kit-dark-scrolled")
    }

    @Test fun everyPartLight() {
        show(AppSettings(themeMode = ThemeMode.LIGHT), glass = true) { EveryPart() }
        save("kit-light")
    }

    @Test fun everyPartPerformance() {
        show(AppSettings(themeMode = ThemeMode.DARK, performanceMode = true), glass = false) { EveryPart() }
        save("kit-performance")
    }

    @Test fun everyPartAtLargeText() {
        show(AppSettings(themeMode = ThemeMode.DARK, textScale = 1.5f), glass = true) { EveryPart() }
        save("kit-dark-large-text")
    }

    /** The two sheets' contents, as they sit in the sheet (drawn here without the sheet's window, which a capture of the page leaves out). */
    @Test fun sheets() {
        val styles = listOf("Line", "Wave", "Thick", "Steps", "Thin", "Dotted")
        show(AppSettings(themeMode = ThemeMode.DARK), glass = false) {
            Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Column(Modifier.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(top = 22.dp)) {
                    PickCards("Progress bar style", styles, "Wave", { it }, "How the line under the song is drawn.", 2, 0.8f, { style ->
                        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxWidth().height(if (style == "Thick") 8.dp else 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)))
                        }
                    }) {}
                }
                Column(Modifier.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(top = 22.dp)) {
                    PickList("Content country", listOf("Automatic (follow the phone)", "United States", "Austria", "Belgium", "Germany"), "Germany", { it }, null, { null }, true) {}
                }
            }
        }
        val shot = save("sheets-dark")
        assertTrue(shot.height > 0)
    }
}
