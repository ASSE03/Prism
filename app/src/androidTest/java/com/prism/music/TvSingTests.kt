package com.prism.music

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prism.music.TvFixture.awaitFocus
import com.prism.music.TvFixture.awaitFocusOn
import com.prism.music.ui.tv.TAG_NAV
import com.prism.music.ui.tv.TAG_SETTINGS
import com.prism.music.ui.tv.TvSection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sing on more TVs (1.8.x): a TV whose check found it too slow for Sing's separation, or whose processor Sing's engine
 * doesn't run on, is offered the quick way, said plainly in Settings → Sing, rather than told it can't sing.
 */
@TvTest
@RunWith(AndroidJUnit4::class)
class TvSingTests {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()

    @Before fun setUp() { TvFixture.assumeTv() }

    private val inNav = androidx.compose.ui.test.SemanticsMatcher("in the side navigation") { n ->
        n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("$TAG_NAV/") == true
    }

    @Test fun aTvTooSlowForTheSeparationIsOfferedTheQuickWay() {
        Fixture.ready()
        val noEngine = com.prism.music.data.ModelStore.Model.singRuntime() == null
        // An earlier check's answer: too slow (on a TV the engine doesn't run on, that's the answer without a check).
        if (!noEngine) Fixture.context.getSharedPreferences("tv_sing", android.content.Context.MODE_PRIVATE).edit().putString("state", "SLOW").commit()
        TvFixture.launch()
        compose.awaitFocus(15_000)
        for (i in 0 until 6) {
            if (compose.onAllNodes(inNav and isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) break
            TvFixture.left(); Thread.sleep(300)
        }
        repeat(TvSection.SETTINGS.ordinal) { TvFixture.down(); Thread.sleep(200) }
        compose.awaitFocusOn(hasTestTag("$TAG_NAV/${TvSection.SETTINGS.name}"))
        TvFixture.ok()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/0"), 10_000)
        // Down to Sing: Account, Language, Sound, Lyrics, Sing.
        repeat(4) { TvFixture.down(); Thread.sleep(250) }
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/4"))
        val answer = tr(if (noEngine) R.string.tv_sing_no_cpu else R.string.tv_sing_too_slow)
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(answer, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // And Sing is offered: it's in the player's ⋯ for both answers.
        org.junit.Assert.assertTrue(com.prism.music.ui.tv.TvSing.state.value.sings)
    }
}
