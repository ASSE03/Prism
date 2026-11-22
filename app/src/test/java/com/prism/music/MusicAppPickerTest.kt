package com.prism.music

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #548: Samsung's "Play music" and other default-music-app pickers list the apps that answer a MAIN intent with
 * CATEGORY_APP_MUSIC, or the older MUSIC_PLAYER action. Prism answered neither, so the picker said no app could do it.
 * Both open Prism's one screen; the launcher still shows one entry (the enabled icon alias), not a second icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MusicAppPickerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val pm = context.packageManager

    private fun resolvesToPrism(intent: Intent) {
        val found = pm.queryIntentActivities(intent.setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue("$intent should open Prism", found.any { it.activityInfo.name == PrismActivity::class.java.name })
    }

    @Test fun theMusicAppCategoryOpensPrism() {
        // What makeMainSelectorActivity puts in its selector: the picker queries with this.
        resolvesToPrism(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC))
        val selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).selector!!
        resolvesToPrism(Intent(selector))
    }

    @Test fun theMusicPlayerActionOpensPrism() {
        resolvesToPrism(Intent(MediaStoreMusicPlayer))
    }

    @Test fun theLauncherStillShowsOneEntry() {
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0,
        )
        assertEquals("one launcher entry, the enabled icon alias: ${launcher.map { it.activityInfo.name }}", 1, launcher.size)
        assertEquals("com.prism.music.MainActivity", launcher.single().activityInfo.name)
    }

    private companion object {
        const val MediaStoreMusicPlayer = "android.intent.action.MUSIC_PLAYER"
    }
}
