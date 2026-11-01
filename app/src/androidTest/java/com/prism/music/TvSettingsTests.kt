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
        // The navigation opens on the place shown: up to its top (Home), then down to [section].
        repeat(TvSection.entries.size) { TvFixture.up(); Thread.sleep(150) }
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
        TvFixture.down()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/1"))
        TvFixture.down()
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

    /**
     * The real TV's feedback (1.8.0): a TV that had Prism's phone app before (onboarded there) still gets the TV's
     * welcome once, signed out; and Settings' "Not signed in" signs in with the phone.
     */
    @Test fun aTvOnboardedOnThePhoneAppIsWelcomedOnceAndNotSignedInSignsIn() {
        Fixture.ready()
        kotlinx.coroutines.runBlocking {
            Fixture.container.settings.editNow { it[androidx.datastore.preferences.core.booleanPreferencesKey("tv_welcomed")] = false }
            kotlinx.coroutines.withTimeout(10_000) { while (Fixture.container.settings.current.tv.welcomed) kotlinx.coroutines.delay(20) }
        }
        assertTrue(Fixture.container.settings.current.onboarded)
        TvFixture.launch()
        compose.awaitFocusOn(hasTestTag("$TAG_WELCOME/phone"), 20_000)
        TvFixture.right()
        compose.awaitFocusOn(hasTestTag("$TAG_WELCOME/skip"))
        TvFixture.ok()
        compose.waitUntil(10_000) { Fixture.container.settings.current.tv.welcomed }
        // Settings → Account: "Not signed in" opens the sign-in with the phone.
        goTo(TvSection.SETTINGS)
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/0"), 10_000)
        TvFixture.right()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/setting/0"))
        TvFixture.ok()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(com.prism.music.ui.tv.TAG_PAIR), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * The owner's second round (1.8.0): Home's rows are customized from the TV's Settings (Customize Home), in the
     * phone's own Home layout (so the phone agrees): a row hidden there is gone from Home.
     */
    @Test fun aHomeRowHiddenInSettingsIsGoneFromHome() {
        Fixture.ready()
        val c = Fixture.container
        val before = c.settings.current.homeLayout
        try {
            // A shelf on Home (fresh for this run, so nothing is asked of the network).
            val title = "Prism settings row"
            val key = com.prism.music.data.prefs.HomeSections.shelfKey(title)
            com.prism.music.ui.tv.TvHomeShelves.keep(null, listOf(com.prism.music.data.model.Shelf(title, items = Fixture.songs(2).map { com.prism.music.data.model.SongItem(it) })), System.currentTimeMillis())
            TvFixture.launch()
            compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(title), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            goTo(TvSection.SETTINGS)
            compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/0"), 10_000)
            // Home: the seventh group; its Customize Home opens the editor.
            for (g in 1..6) { TvFixture.down(); compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/$g")) }
            TvFixture.right()
            compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/setting/0"))
            TvFixture.ok()
            // The hero, Quick picks, Speed dial, Recently played, then the shelf: OK hides it.
            compose.awaitFocusOn(hasTestTag("${com.prism.music.ui.tv.TAG_HOME_EDITOR}/row/${com.prism.music.ui.tv.TvHomeLayout.HERO}"), 10_000)
            repeat(4) { TvFixture.down(); Thread.sleep(250) }
            compose.awaitFocusOn(hasTestTag("${com.prism.music.ui.tv.TAG_HOME_EDITOR}/row/$key"))
            TvFixture.ok()
            compose.waitUntil(10_000) { com.prism.music.ui.tv.hiddenIn(c.settings.current.homeLayout, key) }
            TvFixture.back()
            compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/setting/0"), 10_000)
            goTo(TvSection.HOME)
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(TAG_HOME), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(1_000)
            assertTrue("\"$title\" still on Home", compose.onAllNodes(androidx.compose.ui.test.hasText(title), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        } finally {
            c.settings.setHomeLayout(before)
        }
    }

    /** The review (1.8.0): signing out on a (shared) TV takes the account's library with it, as on the phone. */
    @Test fun signingOutTakesTheAccountsLibraryWithIt() {
        Fixture.ready()
        val c = Fixture.container
        kotlinx.coroutines.runBlocking {
            c.settings.fromBackup(mapOf("cookie" to "s:SAPISID=tv-test; SID=tv-test", "account_name" to "s:TV Listener"), settings = false, accounts = true)
        }
        TvFixture.launch()
        goTo(TvSection.SETTINGS)
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/0"), 10_000)
        // The account's library, as a sync would have kept it.
        c.library.playlists.value = listOf(com.prism.music.data.model.PlaylistItem("PLtv", "The listener's own list", "", null))
        // Account: signed in as, sign in with phone, phone as remote, sign out.
        TvFixture.right()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/setting/0"))
        repeat(3) { TvFixture.down(); Thread.sleep(250) }
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/setting/3"))
        TvFixture.ok()
        compose.awaitFocusOn(hasTestTag("${com.prism.music.ui.tv.TAG_ACTIONS}/0"), 10_000)
        TvFixture.ok()
        compose.waitUntil(10_000) { !c.settings.current.isLoggedIn }
        assertTrue(c.library.playlists.value.isEmpty())
    }

    @Test fun anUpdateShowsOnHomeAndTheRemoteReachesIt() {
        RecordedNetwork.answers["https://api.github.com/repos/Yigder/Prism/releases/latest"] =
            """{"tag_name":"v9.9.9","html_url":"https://github.com/Yigder/Prism/releases/tag/v9.9.9",
               "assets":[{"name":"Prism.apk","browser_download_url":"https://github.com/Yigder/Prism/releases/download/v9.9.9/Prism.apk"}]}"""
        Fixture.ready()
        kotlinx.coroutines.runBlocking { Fixture.container.updates.checkNow() }
        TvFixture.launch()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("$TAG_HOME/update"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Home's first item: Search while it's empty, the first card once its rows come (focus follows when Search goes,
        // never left on the side navigation), the hero's "Now playing" if a song is left from another test. Up: Update.
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/search") or hasTestTag("$TAG_HOME/row0/0") or hasTestTag("$TAG_HOME/player"), 15_000)
        if (compose.onAllNodes(hasTestTag("$TAG_HOME/search") and androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            Thread.sleep(3_000) // the rows may still be on their way: let them come, so Up starts from where focus settles
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/search") or hasTestTag("$TAG_HOME/row0/0") or hasTestTag("$TAG_HOME/player"), 10_000)
        compose.waitForIdle()
        TvFixture.up()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/update"))
        assertTrue(Fixture.container.updates.available.value?.version == "9.9.9")
    }
}
