package com.prism.music

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prism.music.TvFixture.awaitFocus
import com.prism.music.TvFixture.awaitFocusOn
import com.prism.music.ui.tv.TAG_HOME
import com.prism.music.ui.tv.TAG_NAV
import com.prism.music.ui.tv.TAG_SETTINGS
import com.prism.music.ui.tv.TAG_WELCOME
import com.prism.music.ui.tv.TvSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prism TV, slice 8: the TV's settings with the remote (a switch flips, a setting's ⓘ opens its explanation), the first
 * run's welcome, and the update banner on Home.
 */
@TvTest
@RunWith(AndroidJUnit4::class)
class TvSettingsTests {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()

    @Before fun setUp() { TvFixture.assumeTv() }

    @After fun cleanUp() {
        RecordedNetwork.answers.remove("https://api.github.com/repos/Yigder/Prism/releases/latest")
        Fixture.container.settings.setNormalize(true)
    }

    private val inNav = androidx.compose.ui.test.SemanticsMatcher("in the side navigation") { n ->
        n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("$TAG_NAV/") == true
    }

    private fun goTo(section: TvSection) {
        compose.awaitFocus(15_000)
        for (i in 0 until 6) {
            if (compose.onAllNodes(inNav and isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) break
            TvFixture.left(); Thread.sleep(300)
        }
        repeat(section.ordinal) { TvFixture.down(); Thread.sleep(200) }
        compose.awaitFocusOn(hasTestTag("$TAG_NAV/${section.name}"))
        TvFixture.ok()
    }

    @Test fun aSwitchFlipsAndItsExplanationOpensWithTheRemote() {
        Fixture.ready()
        TvFixture.launch()
        goTo(TvSection.SETTINGS)
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/0"), 10_000)
        // Down to Sound (moving along the groups shows each), Right into it, down to "Same loudness".
        TvFixture.down(); TvFixture.down()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/2"))
        TvFixture.right()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/setting/0"))
        TvFixture.down()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/setting/1"))
        val before = Fixture.container.settings.current.normalize
        TvFixture.ok()
        compose.waitUntil(5_000) { Fixture.container.settings.current.normalize != before }
        // Its ⓘ, one step Right: the explanation, closed by Back, focus back on the ⓘ.
        TvFixture.right()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/info/1"))
        TvFixture.ok()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/infoclose"), 10_000)
        TvFixture.back()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("$TAG_SETTINGS/infoclose"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        compose.awaitFocus()
    }

    @Test fun theFirstRunWelcomesAndCarriesOnWithoutAnAccount() {
        // A fresh install: no Fixture.ready().
        TvFixture.launch()
        compose.awaitFocusOn(hasTestTag("$TAG_WELCOME/phone"), 20_000)
        TvFixture.right()
        compose.awaitFocusOn(hasTestTag("$TAG_WELCOME/skip"))
        TvFixture.ok()
        compose.waitUntil(10_000) { Fixture.container.settings.current.onboarded }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(TAG_HOME), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.awaitFocus()
        assertEquals(false, Fixture.container.settings.current.isLoggedIn)
    }

    @Test fun anUpdateShowsOnHomeAndTheRemoteReachesIt() {
        RecordedNetwork.answers["https://api.github.com/repos/Yigder/Prism/releases/latest"] =
            """{"tag_name":"v9.9.9","html_url":"https://github.com/Yigder/Prism/releases/tag/v9.9.9",
               "assets":[{"name":"Prism.apk","browser_download_url":"https://github.com/Yigder/Prism/releases/download/v9.9.9/Prism.apk"}]}"""
        Fixture.ready()
        kotlinx.coroutines.runBlocking { Fixture.container.updates.checkNow() }
        TvFixture.launch()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("$TAG_HOME/update"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Empty Home (no network): its Search button first; Up reaches Update.
        compose.awaitFocus(10_000)
        TvFixture.up()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/update"))
        assertTrue(Fixture.container.updates.available.value?.version == "9.9.9")
    }
}
