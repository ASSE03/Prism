package com.prism.music

import com.prism.music.ui.tour.FirstSteps
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.8.8 (plan-tour.md §3 B, D11): a new install's first visit is one thing at a time. The two Home offer cards
 * (Performance mode, Smart Downloads) wait while First steps is pending; the Holiday Mode question never comes while
 * First steps is pending or its closing card is up, and not at all in a new install's first session. The rules, and
 * that Home and the activity ask them where those things are shown.
 */
class FirstRunOrderTest {
    @Test fun theOfferCardsWaitWhileFirstStepsIsPending() {
        assertTrue(FirstSteps.offersWait(pending = true))
        assertFalse(FirstSteps.offersWait(pending = false))
    }

    @Test fun theHolidayQuestionWaits() {
        assertTrue("First steps pending", FirstSteps.holidayWaits(pending = true, closing = false, newInstallSession = false))
        assertTrue("its closing card up", FirstSteps.holidayWaits(pending = false, closing = true, newInstallSession = false))
        assertTrue("a new install's first session, even with First steps done", FirstSteps.holidayWaits(pending = false, closing = false, newInstallSession = true))
        assertFalse("another day", FirstSteps.holidayWaits(pending = false, closing = false, newInstallSession = false))
    }

    private fun source(path: String) = File("src/main/java/com/prism/music/$path").readText()

    @Test fun homeAsksBeforeEitherOfferCard() {
        val home = source("ui/screens/HomeScreen.kt")
        for (offer in listOf("PerformanceOffer(", "SmartDownloadsOffer(smartAnswered")) {
            val line = home.lines().single { offer in it && "item(key" in it }
            assertTrue("$offer waits for First steps: $line", "FirstSteps.offersWait(" in line && line.indexOf("FirstSteps.offersWait(") < line.indexOf(offer))
        }
    }

    @Test fun theHolidayQuestionAsksFirst() {
        val main = source("MainActivity.kt")
        val clear = main.lines().single { "val clear = showChrome" in it }
        assertTrue("Holiday Mode's moment asks First steps: $clear", "FirstSteps.holidayWaits(" in clear)
        assertTrue("and a new install's first session counts", "c.returningUser == false" in clear)
        assertTrue("and no coach tour is running", "Coach.run == null" in clear)
    }
}
