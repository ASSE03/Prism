package com.prism.music

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prism.music.TvFixture.awaitFocus
import com.prism.music.TvFixture.awaitFocusOn
import com.prism.music.ui.tv.TAG_NAV
import com.prism.music.ui.tv.TAG_PLACE
import com.prism.music.ui.tv.TvSection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prism TV's shell (1.8.0 slice 1): it opens from the launcher with focus on Home, the remote reaches every
 * top-level place, Back goes Home and then leaves Prism, and the phone's screen hands itself over to the TV's.
 */
@TvTest
@RunWith(AndroidJUnit4::class)
class TvShellTests {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()

    @Before fun setUp() {
        TvFixture.assumeTv()
        Fixture.ready()
    }

    private fun placeShown(s: TvSection) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag("$TAG_PLACE/${s.name}"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun opensOnHomeWithFocus() {
        TvFixture.launch()
        placeShown(TvSection.HOME)
        compose.awaitFocusOn(hasTestTag("$TAG_NAV/${TvSection.HOME.name}"), 10_000)
    }

    @Test fun theRemoteReachesEveryPlace() {
        TvFixture.launch()
        compose.awaitFocusOn(hasTestTag("$TAG_NAV/${TvSection.HOME.name}"), 10_000)
        for (s in TvSection.entries.drop(1)) {
            TvFixture.down()
            compose.awaitFocusOn(hasTestTag("$TAG_NAV/${s.name}"))
            TvFixture.ok()
            placeShown(s)
            compose.awaitFocus()
        }
    }

    @Test fun backGoesHomeThenLeaves() {
        val scenario = TvFixture.launch()
        compose.awaitFocusOn(hasTestTag("$TAG_NAV/${TvSection.HOME.name}"), 10_000)
        repeat(3) { TvFixture.down() }
        compose.awaitFocusOn(hasTestTag("$TAG_NAV/${TvSection.SETTINGS.name}"))
        TvFixture.ok()
        placeShown(TvSection.SETTINGS)
        TvFixture.back()
        placeShown(TvSection.HOME)
        compose.awaitFocus()
        TvFixture.back()
        val left = (1..50).any { Thread.sleep(100); scenario.state == Lifecycle.State.DESTROYED }
        assertEquals("Back on Home leaves Prism", true, left)
    }

    @Test fun thePhoneScreenOpensTheTvs() {
        val phone = ActivityScenario.launch(PrismActivity::class.java)
        placeShown(TvSection.HOME)
        compose.awaitFocus(10_000)
        assertEquals(Lifecycle.State.DESTROYED, phone.state)
    }
}
