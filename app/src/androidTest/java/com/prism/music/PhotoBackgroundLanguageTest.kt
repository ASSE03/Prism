package com.prism.music

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.data.prefs.ThemeMode
import com.prism.music.ui.theme.BackdropScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * #378: a photo as Home's background showed black, with Ambient ticked, on a phone in German: the choice was saved under
 * Android's translated word ("FOTO|…"), which Prism didn't read back as a photo. A picture saved that way is drawn, with
 * Pure black on and off.
 */
@RunWith(AndroidJUnit4::class)
class PhotoBackgroundLanguageTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()
    private val c get() = Fixture.container

    /** How much of the screen is the test photo's green (under the theme's wash). */
    private fun greenShare(): Float {
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return 0f
        var green = 0
        var all = 0
        for (y in 0 until shot.height step 16) for (x in 0 until shot.width step 16) {
            val p = shot.getPixel(x, y)
            val r = android.graphics.Color.red(p); val g = android.graphics.Color.green(p); val b = android.graphics.Color.blue(p)
            all++
            if (g > r + 16 && g > b + 16) green++
        }
        shot.recycle()
        return green.toFloat() / all
    }

    private fun photoShows(value: String, pureBlack: Boolean, shot: String) {
        Fixture.ready()
        val file = File(Fixture.context.filesDir, "test-photo.jpg")
        val picture = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(30, 220, 60)) }
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        c.settings.setThemeMode(ThemeMode.DARK)
        c.settings.setPureBlack(pureBlack)
        c.settings.setBackdrops(mapOf(BackdropScreen.HOME.name to "$value|${file.path}"))
        Fixture.launch()
        compose.waitUntil(20_000) { greenShare() > 0.25f }
        Fixture.screenshot(shot)
        assertTrue(greenShare() > 0.25f)
    }

    @Test fun aPhotoSavedInGermanShows() = photoShows("FOTO", pureBlack = false, shot = "photo-foto")
    @Test fun aPhotoSavedInGermanShowsWithPureBlack() = photoShows("FOTO", pureBlack = true, shot = "photo-foto-black")
    @Test fun aPhotoSavedInRussianShows() = photoShows("ФОТО", pureBlack = true, shot = "photo-russian-black")
    @Test fun aPhotoSavedAsItIsNowShows() = photoShows("PHOTO", pureBlack = true, shot = "photo-now-black")
}
