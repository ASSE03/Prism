package com.prism.music

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prism.music.ui.tour.Coach
import com.prism.music.ui.tour.CoachStep
import com.prism.music.ui.tour.TourAnchor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 1.8.8: First steps on a new install. Liking the song (step 4) downloads it (the fresh-install defaults download
 * likes), and the first download asked for notifications: Android's prompt came up over step 5. While a coach tour runs
 * nothing is asked and nothing is marked asked, so a later download asks instead.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class NotificationAskWaitsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val c by lazy { AppContainer(app) }
    private val asked get() = app.getSharedPreferences("prompts", Context.MODE_PRIVATE).getBoolean("notifications_asked", false)

    @After fun tearDown() { Coach.run?.let { Coach.skip() } }

    @Test fun notAskedWhileACoachTourRunsAndAskedAfter() {
        var prompts = 0
        c.notificationPrompt = { prompts++ }
        Coach.start(listOf(CoachStep(TourAnchor.QUICK_PICK, "Tap a song to play it.", done = { false })))
        c.askNotifications()
        assertEquals("no prompt over the tour", 0, prompts)
        assertFalse("not marked asked: a later download asks", asked)

        Coach.skip()
        c.askNotifications()
        assertEquals(1, prompts)
        assertTrue(asked)
    }
}
