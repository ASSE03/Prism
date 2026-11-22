package com.prism.music

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prism.music.ui.tour.COACH_CAPTION_TAG
import com.prism.music.ui.tour.Coach
import com.prism.music.ui.tour.Tour
import com.prism.music.ui.tour.TourAnchors
import com.prism.music.ui.tour.WHATS_NEW_BACK_TAG
import com.prism.music.ui.tour.WHATS_NEW_MORE_TAG
import com.prism.music.ui.tour.WHATS_NEW_SHOW_ME_TAG
import com.prism.music.ui.tour.whatsNewPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What's new's "Show me" (1.8.8, plan-tour.md §3 C, §5): someone updating by one release sees that release's cards; the
 * headline's Show me opens its place with the coach's hole on the exact control (from 1.8.8's cards on; an older card
 * without a coach just opens the place), and the chip "Back to what's new" puts back the page they were on and opens
 * the next card. Someone who skipped several releases gets the "more things" line, which opens the Guide.
 */
@RunWith(AndroidJUnit4::class)
class WhatsNewShowMeAppTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()

    /** The tour last seen at [seen] (Fixture.ready marks it seen at this build). */
    private fun lastSeen(seen: Int) =
        Fixture.context.getSharedPreferences("tour", Context.MODE_PRIVATE).edit().putInt("seen_version", seen).commit()

    private val newest get() = Tour.steps.maxOf { it.since }
    private val releaseBefore get() = Tour.steps.map { it.since }.filter { it < newest }.max()

    private fun waitFor(tag: String, ms: Long = 20_000) =
        compose.waitUntil(ms) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String, ms: Long = 20_000) =
        compose.waitUntil(ms) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun tap(text: String) = compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()

    @Test fun showMeOpensThePlaceAndTheChipReturnsToTheNextCard() {
        Fixture.ready()
        lastSeen(releaseBefore)
        val plan = whatsNewPlan(releaseBefore, returning = true)!!
        val head = plan.steps.first()
        assertNotNull("the newest release's headline has a Show me", head.showMe)
        Fixture.launch()

        // The opening page, then the headline.
        waitForText(tr(R.string.what_s_new_in_prism, BuildConfig.VERSION_NAME))
        tap(tr(R.string.start))
        waitForText(head.title)
        compose.onAllNodes(hasTestTag(WHATS_NEW_SHOW_ME_TAG)).onFirst().performClick()

        // The pager stepped aside; the place is open, the coach's hole on the control when the card names one.
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(head.body)).fetchSemanticsNodes().isEmpty() }
        val coach = head.coach
        if (coach != null) {
            waitFor(COACH_CAPTION_TAG)
            waitForText(coach.caption)
            compose.waitUntil(10_000) { TourAnchors.boundsOf(coach.anchor) != null }
            Fixture.screenshot("WhatsNewShowMe-hole")
        }
        waitFor(WHATS_NEW_BACK_TAG)

        // Back to what's new: the next card, the coach gone.
        compose.onAllNodes(hasTestTag(WHATS_NEW_BACK_TAG)).onFirst().performClick()
        val next = plan.steps.getOrNull(1) ?: plan.steps.first()
        waitForText(next.body)
        assertTrue("the coach ended", Coach.run == null)
        assertEquals(0, compose.onAllNodes(hasTestTag(WHATS_NEW_BACK_TAG)).fetchSemanticsNodes().size)
        // Done: nothing more is pending at this build.
        tap(tr(R.string.done))
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(next.body)).fetchSemanticsNodes().isEmpty() }
        assertEquals(BuildConfig.VERSION_CODE, Tour.seenVersion(Fixture.context))
    }

    @Test fun severalReleasesSkippedFoldIntoALineThatOpensTheGuide() {
        Fixture.ready()
        lastSeen(54) // 1.8.0
        val plan = whatsNewPlan(54, returning = true)!!
        assertTrue(plan.more > 0)
        Fixture.launch()
        waitForText(tr(R.string.what_s_new_in_prism, BuildConfig.VERSION_NAME))
        tap(tr(R.string.start))
        // To the last card, where the line is.
        repeat(plan.steps.size - 1) { tap(tr(R.string.widget_next)) }
        waitFor(WHATS_NEW_MORE_TAG)
        waitForText(trp(R.plurals.n_more_things_since, plan.more))
        compose.onAllNodes(hasTestTag(WHATS_NEW_MORE_TAG)).onFirst().performClick()
        waitForText(tr(R.string.guide_search_hint))
        waitForText(tr(R.string.guide_topic_playing))
        assertEquals(BuildConfig.VERSION_CODE, Tour.seenVersion(Fixture.context))
    }
}
