package com.prism.music

import android.app.UiModeManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.prism.music.ui.PortraitLockEffect
import com.prism.music.ui.portraitLockOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/**
 * #582: Android Auto (a Kia EV5, a Galaxy S24+ on Android 17) dropped after about five minutes while "Lock to portrait"
 * was on, and stayed up with it off. The phone activity asked for portrait whatever the phone was doing; now nothing is
 * forced while the phone is in a car (Android Auto projecting, car mode), and the request is never made again unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi", application = android.app.Application::class)
class PortraitLockTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val portrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private val free = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val video = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    // ------------------------------------------------------------------ The choice

    @Test fun aPhoneOutOfTheCarIsHeldUpright() {
        assertEquals(portrait, portraitLockOrientation(lockPortrait = true, tablet = false, inCar = false, current = free))
        assertEquals("switched off: free again", free, portraitLockOrientation(false, tablet = false, inCar = false, current = portrait))
        assertEquals("a tablet keeps its own layouts", free, portraitLockOrientation(true, tablet = true, inCar = false, current = portrait))
    }

    @Test fun inACarNothingIsForced() {
        assertNull("never forced in the first place", portraitLockOrientation(true, tablet = false, inCar = true, current = free))
        assertEquals("a car came while upright: let go", free, portraitLockOrientation(true, tablet = false, inCar = true, current = portrait))
    }

    @Test fun theSameRequestIsNeverMadeTwice() {
        assertNull(portraitLockOrientation(true, tablet = false, inCar = false, current = portrait))
        assertNull(portraitLockOrientation(false, tablet = false, inCar = false, current = free))
        // Android's "no request at all" is as free as "unspecified".
        assertNull(portraitLockOrientation(false, tablet = false, inCar = false, current = -2))
    }

    @Test fun aFullScreenVideosLandscapeIsLeftAlone() {
        assertNull(portraitLockOrientation(true, tablet = false, inCar = false, current = video))
        assertNull(portraitLockOrientation(true, tablet = false, inCar = true, current = video))
    }

    // ------------------------------------------------------------------ The activity's request

    private fun requested() = compose.activity.requestedOrientation
    private val app get() = RuntimeEnvironment.getApplication()

    private fun settle(until: () -> Boolean) {
        compose.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); until() }
    }

    @Test fun carModeLetsGoAndLeavingItHoldsUprightAgain() {
        val on = mutableStateOf(false)
        compose.setContent { PortraitLockEffect(on.value) }
        compose.waitForIdle()
        assertEquals(free, requested())

        on.value = true
        settle { requested() == portrait }

        val ui = shadowOf(app.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
        ui.setCurrentModeType(Configuration.UI_MODE_TYPE_CAR)
        app.sendBroadcast(Intent(UiModeManager.ACTION_ENTER_CAR_MODE))
        settle { requested() == free }

        ui.setCurrentModeType(Configuration.UI_MODE_TYPE_NORMAL)
        app.sendBroadcast(Intent(UiModeManager.ACTION_EXIT_CAR_MODE))
        settle { requested() == portrait }
    }

    /** Android Auto's own connection provider, as androidx.car.app's CarConnection reads it (2: projecting). */
    class Connection : ContentProvider() {
        @Volatile var state = 0
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
            MatrixCursor(arrayOf("CarConnectionState")).apply { addRow(arrayOf(state)) }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }

    @Test fun androidAutoProjectingLetsGo() {
        val connection = Connection()
        ShadowContentResolver.registerProviderInternal("androidx.car.app.connection", connection)
        val on = mutableStateOf(true)
        compose.setContent { PortraitLockEffect(on.value) }
        settle { requested() == portrait }

        connection.state = 2
        app.sendBroadcast(Intent("androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"))
        settle { requested() == free }

        connection.state = 0
        app.sendBroadcast(Intent("androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"))
        settle { requested() == portrait }

        // Switched off in the car and out of it: free.
        on.value = false
        settle { requested() == free }
    }
}
