package com.prism.music

import com.prism.music.ui.screens.settings.SettingsMap
import com.prism.music.ui.screens.settings.SettingsPageId
import com.prism.music.ui.screens.settings.SettingsTarget
import com.prism.music.ui.screens.settings.index
import com.prism.music.ui.tour.Tour
import com.prism.music.ui.tour.TourStep
import com.prism.music.ui.tour.TourTopic
import com.prism.music.ui.tour.whatsNewPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tour's rules: someone who finished the tour at a version sees only steps added since; no step is dated after the
 * build it ships in (it could never be marked seen, and would come back at every start). Since 1.8.8 (plan-tour.md §3
 * C, D, §5): a new install gets First steps, not the history; What's new shows the newest release's cards with the
 * older ones folded into one "more things" count; every card has a Guide topic and every topic a card; and every
 * release from 1.8.8 on keeps to two cards of at most four points, its headline's Show me using the coach.
 */
// Robolectric: the tour's words come from strings.xml (1.7.9).
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class TourTest {
    private val newest get() = Tour.steps.maxOf { it.since }
    private fun cardsOf(release: Int) = Tour.steps.filter { it.since == release }

    /** A new install is not shown the history (it gets First steps); every step is still there for the Guide. */
    @Test
    fun aNewInstallIsNotShownTheHistory() {
        assertNull(whatsNewPlan(0, returning = false))
        assertEquals(Tour.steps, Tour.stepsAfter(0))
    }

    /** Updating by one release gives that release's cards, and no "more things" line. */
    @Test
    fun anUpdateByOneReleaseGivesThatReleasesCards() {
        val before = Tour.steps.map { it.since }.filter { it < newest }.max()
        val plan = whatsNewPlan(before, returning = true)!!
        assertFalse(plan.welcome)
        assertEquals(cardsOf(newest), plan.steps)
        assertEquals(0, plan.more)
        assertEquals(0, plan.start)
        // The bar (plan §6.7): at most 3 pages, the opening and two cards.
        assertTrue(plan.pages <= 3)
    }

    /** Updating across several releases gives the newest release's cards, and the rest as one count. */
    @Test
    fun anUpdateAcrossSeveralGivesTheNewestAndTheCount() {
        for (seen in listOf(54, 60, 63)) {
            val plan = whatsNewPlan(seen, returning = true)!!
            assertEquals(cardsOf(newest), plan.steps)
            assertEquals(Tour.stepsAfter(seen).size - cardsOf(newest).size, plan.more)
            assertTrue("$seen: older releases were skipped", plan.more > 0)
        }
        // The bar (plan §6.7): someone updating from 1.8.0 (build 54) sees at most 4 pages.
        assertTrue(whatsNewPlan(54, returning = true)!!.pages <= 4)
        // At this version, nothing is pending.
        assertNull(whatsNewPlan(BuildConfig.VERSION_CODE, returning = true))
    }

    /** Someone from before tours ("Welcome to the new Prism") gets the newest release's cards and the Guide line, not every page. */
    @Test
    fun aListenerFromBeforeToursGetsTheNewestAndTheGuideLine() {
        val plan = whatsNewPlan(0, returning = true)!!
        assertTrue(plan.welcome)
        assertEquals(cardsOf(newest), plan.steps)
        assertEquals(Tour.steps.size - plan.steps.size, plan.more)
        assertTrue(plan.pages <= 3)
    }

    /** A plan opened again after "Show me" starts at the card after, never past the last. */
    @Test
    fun showMeComesBackToTheNextCard() {
        val plan = whatsNewPlan(0, returning = true)!!
        assertEquals(2, plan.at(2).start)
        assertEquals(plan.pages - 1, plan.at(99).start)
        assertEquals(plan.steps, plan.at(1).steps)
        assertEquals(plan.more, plan.at(1).more)
    }

    /** Every card has a Guide topic, and every topic has a card (in this build, and with the release being built). */
    @Test
    fun everyCardHasATopicAndEveryTopicACard() {
        for (list in listOf(Tour.steps, Tour.allSteps)) {
            val none = list.filter { it.topic == null }.map { "${it.since}: ${it.title}" }
            assertEquals("cards without a topic", emptyList<String>(), none)
            for (t in TourTopic.entries) assertTrue("$t has no card", list.any { it.topic == t })
        }
        // Within a topic, the newest come first.
        for (t in TourTopic.entries) {
            val since = t.cards.map { it.since }
            assertEquals(since.sortedDescending(), since)
            assertEquals(Tour.steps.count { it.topic == t }, t.cards.size)
        }
    }

    /** The two-card rule's breaks in [steps]: a release from 1.8.8 on with more than two cards, more than four points, or no coach on its headline. */
    private fun twoCardRuleBreaks(steps: List<TourStep>): List<String> = steps.filter { it.since >= Tour.V1_8_8 }.groupBy { it.since }.flatMap { (v, cards) ->
        listOfNotNull(
            "$v: ${cards.size} cards".takeIf { cards.size > 2 },
            "$v: a card of more than four points".takeIf { cards.any { it.points.size > 4 } },
            "$v: the headline's Show me has no coach".takeIf { cards.first().coach == null || cards.first().showMe == null },
            "$v: the second card isn't \"Also new\"".takeIf { cards.size == 2 && cards[1].title != tr(R.string.also_new) },
        )
    }

    /** From 1.8.8 on (plan D8): at most two cards, headline and "Also new", four points each, the headline's Show me on the coach. */
    @Test
    fun theTwoCardRuleFrom188() {
        assertEquals(emptyList<String>(), twoCardRuleBreaks(Tour.allSteps))
        // The rule catches a release that breaks it.
        val icon = com.prism.music.ui.theme.PrismIcons.Heart
        val coach = com.prism.music.ui.tour.ShowMeCoach(com.prism.music.ui.tour.TourAnchor.CROSSFADE, "Here.")
        val good = TourStep(80, icon, "a", "b", listOf("1", "2", "3", "4"), showMe = "settings/playback", coach = coach)
        assertEquals(emptyList<String>(), twoCardRuleBreaks(listOf(good, TourStep(80, icon, tr(R.string.also_new), "d"))))
        assertEquals(1, twoCardRuleBreaks(listOf(good, good.copy(title = tr(R.string.also_new)), good.copy(title = tr(R.string.also_new)))).size)
        assertEquals(1, twoCardRuleBreaks(listOf(good.copy(points = listOf("1", "2", "3", "4", "5")))).size)
        assertEquals(1, twoCardRuleBreaks(listOf(good.copy(coach = null))).size)
        // Releases before 1.8.8 aren't held to it.
        assertEquals(emptyList<String>(), twoCardRuleBreaks(listOf(good.copy(since = 67, coach = null), good.copy(since = 67), good.copy(since = 67))))
    }

    /**
     * 1.8.8 (build 68): its two cards, written ahead of the build's versionCode (they show once it is 68). The
     * headline opens Playback at "From one song to the next" with the coach's hole on the Crossfade row.
     */
    @Test
    fun the188Cards() {
        val cards = Tour.allSteps.filter { it.since == Tour.V1_8_8 }
        assertEquals(listOf(tr(R.string.tour_188_mix_title), tr(R.string.also_new)), cards.map { it.title })
        assertTrue(cards.all { it.points.size in 1..4 && it.points.all { p -> p.isNotBlank() } && "%" !in (it.points + it.body).joinToString() })
        if (BuildConfig.VERSION_CODE < Tour.V1_8_8) assertTrue("not shown before the build is 1.8.8", Tour.steps.none { it.since == Tour.V1_8_8 })
        val head = cards.first()
        assertEquals(com.prism.music.ui.tour.TourAnchor.CROSSFADE, head.coach!!.anchor)
        val (key, find) = parse(head.showMe!!)
        val page = SettingsPageId.PLAYBACK
        assertEquals(SettingsTarget(page, tr(R.string.playback_song_to_song)), SettingsMap.resolve(key, find))
        assertTrue(page.index.any { it.group == tr(R.string.playback_song_to_song) && it.title == tr(R.string.crossfade) })
        // Only the last card asks for support, once for the release.
        assertEquals(listOf(false, true), cards.map { it.support })
        assertTrue(cards.last().points[1].contains(tr(R.string.ls_sing_button)))
        assertTrue(cards.last().points[2].contains(tr(R.string.radio_started)))
        assertTrue(cards.last().points[3].contains(tr(R.string.clear_cache)))
    }

    /** The Mix headline is sample wording until Mix is built: the 1.8.8 build can't ship with it. */
    @Test
    fun theMixHeadlineIsFilledBefore188Ships() {
        assertFalse(
            "1.8.8's Mix card is still the placeholder (TourSteps.kt, MIX_HEADLINE_PLACEHOLDER)",
            Tour.MIX_HEADLINE_PLACEHOLDER && BuildConfig.VERSION_CODE >= Tour.V1_8_8,
        )
    }

    /** Every release with cards has its name for the Guide's "Since …". */
    @Test
    fun everyReleaseHasItsName() {
        for (v in Tour.allSteps.map { it.since }.toSet()) assertNotNull("$v", Tour.versionNames[v])
        assertTrue("written at most one release ahead", Tour.allSteps.all { it.since <= BuildConfig.VERSION_CODE + 1 })
    }

    private val context get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun freshInstall() = context.getSharedPreferences("tour", android.content.Context.MODE_PRIVATE).edit().clear().commit()

    /**
     * 1.8.8 (plan-tour.md §3 B, §5): a new install is given First steps (five steps), not the history: no pager of every
     * release's pages, then or at a later start; at this version nothing else is pending.
     */
    @Test
    fun aNewInstallGetsFirstStepsAndNotTheHistory() {
        freshInstall()
        com.prism.music.ui.tour.FirstSteps.load(context, returning = false)
        assertTrue(com.prism.music.ui.tour.FirstSteps.pending)
        assertEquals(5, com.prism.music.ui.tour.FirstSteps.steps {}.size)
        assertNull("no history pager for a new install", com.prism.music.ui.tour.pendingTour(context, returning = false))
        // Prism closed before First steps came up: the next start (set up by then) still gives First steps, no pager.
        com.prism.music.ui.tour.FirstSteps.load(context, returning = true)
        assertTrue(com.prism.music.ui.tour.FirstSteps.pending)
        assertNull(com.prism.music.ui.tour.pendingTour(context, returning = true))
        // Done (finished or skipped): never again, and still no history.
        com.prism.music.ui.tour.FirstSteps.markDone(context)
        com.prism.music.ui.tour.FirstSteps.load(context, returning = false)
        assertTrue(!com.prism.music.ui.tour.FirstSteps.pending)
        assertNull(com.prism.music.ui.tour.pendingTour(context, returning = true))
    }

    /** D9: nobody who has seen the tour, and nobody returning (set up before this start), is given First steps. */
    @Test
    fun peopleAlreadyOnPrismNeverGetFirstSteps() {
        freshInstall()
        com.prism.music.ui.tour.FirstSteps.load(context, returning = true)
        assertTrue("set up before tours existed", !com.prism.music.ui.tour.FirstSteps.pending)
        freshInstall()
        Tour.markSeen(context)
        com.prism.music.ui.tour.FirstSteps.load(context, returning = false)
        assertTrue("saw the old tour", !com.prism.music.ui.tour.FirstSteps.pending)
        freshInstall()
        context.getSharedPreferences("tour", android.content.Context.MODE_PRIVATE).edit().putInt("seen_version", 61).commit()
        com.prism.music.ui.tour.FirstSteps.load(context, returning = true)
        assertTrue(!com.prism.music.ui.tour.FirstSteps.pending)
        assertNotNull("someone updating still gets What's new", com.prism.music.ui.tour.pendingTour(context, returning = true))
    }

    @Test
    fun aTourSeenAtThisVersionShowsNothingMore() {
        assertTrue(Tour.stepsAfter(BuildConfig.VERSION_CODE).isEmpty())
    }

    @Test
    fun updatesSeeOnlyNewerSteps() {
        for (seen in listOf(1, 38, 39)) {
            assertTrue(Tour.stepsAfter(seen).all { it.since > seen })
            assertEquals(Tour.steps.count { it.since > seen }, Tour.stepsAfter(seen).size)
        }
    }

    /** The cards with the support line (1.8.7.6) that are not the last of their release's cards. */
    private fun supportMisplaced(steps: List<TourStep>): List<String> = steps.withIndex()
        .filter { (i, s) -> s.support && steps.drop(i + 1).any { it.since == s.since } }
        .map { (_, s) -> "${s.since}: ${s.title}" }

    /**
     * 1.8.7.6 (plan-1.8.7.6 §4.1 S6, D5): the line asking to support Prism sits only on the last card of a release's
     * cards (so a release asks once, at its end).
     */
    @Test
    fun theSupportLineOnlyEndsARelease() {
        assertEquals(emptyList<String>(), supportMisplaced(Tour.steps))
        // The rule catches a line put on an earlier card of a release.
        val icon = com.prism.music.ui.theme.PrismIcons.Heart
        val wrong = listOf(TourStep(70, icon, "a", "b", support = true), TourStep(70, icon, "c", "d"))
        assertEquals(listOf("70: a"), supportMisplaced(wrong))
        assertEquals(emptyList<String>(), supportMisplaced(listOf(TourStep(70, icon, "a", "b"), TourStep(70, icon, "c", "d", support = true), TourStep(71, icon, "e", "f", support = true))))
    }

    @Test
    fun stepsAreDatedNoLaterThanThisBuild() {
        assertTrue(Tour.steps.all { it.since in 1..BuildConfig.VERSION_CODE })
        assertTrue("every step says something", Tour.steps.all { it.title.isNotBlank() && it.body.isNotBlank() })
    }

    /** 1.8.1 (build 55): someone updating from 1.8.0 (build 54) sees its five pages, in order, and nothing older. */
    @Test
    fun anUpdateFrom180ShowsThe181Pages() {
        val new = Tour.stepsAfter(54).filter { it.since <= 55 }
        assertTrue(new.all { it.since == 55 && it.kind == TourStep.Kind.PLAIN })
        assertEquals(
            listOf(R.string.tour_181_history, R.string.tour_181_spotify, R.string.tour_181_replay, R.string.tour_181_yours, R.string.also_new)
                .map { tr(it) },
            new.map { it.title },
        )
        assertEquals(com.prism.music.ui.Routes.BRING_HISTORY, new[0].showMe)
        assertEquals(com.prism.music.ui.Routes.settings("spotify"), new[1].showMe)
        assertEquals("settings/library?find=History", new[2].showMe)
        // Short pages: a few points each.
        assertTrue(new.all { it.points.size in 3..5 })
    }

    /** 1.8.2 (build 57): the lock screen player has its page, which opens where it's switched on. */
    @Test
    fun anUpdateFrom181ShowsTheLockScreenPage() {
        val page = Tour.stepsAfter(56).first { it.title == tr(R.string.tour_182_lock_title) }
        assertEquals(57, page.since)
        assertTrue(page.showMe!!.startsWith("settings/player"))
    }

    /** 1.8.2: the new Home sections are only offered in Customize Home, so this page is how listeners hear of them. */
    @Test
    fun anUpdateFrom181HearsOfTheNewHomeSections() {
        val page = Tour.stepsAfter(56).first { it.title == tr(R.string.tour_182_discover_title) }
        assertEquals(57, page.since)
        assertTrue(page.showMe!!.startsWith("settings/library"))
    }

    /** 1.8.3: an update from 1.8.2.1 (58) shows smart playlists, tags, select many, library search and the rest. */
    @Test
    fun anUpdateFrom182ShowsThe183Pages() {
        val pages = Tour.stepsAfter(58).filter { it.since <= 59 }
        assertEquals(5, pages.size)
        assertTrue(pages.all { it.since == 59 })
        assertEquals(tr(R.string.tour_183_smart_title), pages.first().title)
        assertEquals(com.prism.music.ui.Routes.librarySearch(), pages.first { it.title == tr(R.string.tour_183_search_title) }.showMe)
    }

    /**
     * 1.8.4 (build 61): an update from 1.8.3.1 (60) shows Settings made simple, lyrics timing, the lock screen in the
     * player's look, finding the playing song and the rest, in that order, and nothing older.
     */
    @Test
    fun anUpdateFrom1831ShowsThe184Pages() {
        val pages = Tour.stepsAfter(60).filter { it.since <= 61 }
        assertTrue(pages.all { it.since == 61 && it.kind == TourStep.Kind.PLAIN })
        assertEquals(
            listOf(R.string.tour_184_settings_title, R.string.tour_184_lyrics_title, R.string.tour_184_lock_title, R.string.tour_184_lists_title, R.string.also_new)
                .map { tr(it) },
            pages.map { it.title },
        )
        // As many pages as the releases before it, a few points each.
        assertTrue(pages.all { it.points.size in 3..5 && it.points.all { p -> p.isNotBlank() } })
        // The settings page opens Settings' first page (plan §5); "Also new" opens nothing, as before.
        assertEquals(com.prism.music.ui.Routes.SETTINGS, pages.first().showMe)
        assertNull(pages.last().showMe)
        // A point that names a row says the row's own name (filled in, not a "%1$s").
        assertTrue(pages.flatMap { it.points }.none { "%" in it })
        assertTrue(pages[2].points.first().contains(tr(R.string.settings_player_same_look)))
        assertTrue(pages[3].points.first().contains(tr(R.string.go_to_the_playing_song)))
    }

    /**
     * 1.8.4: each step about a setting opens the page that holds it, at the group that draws it. Checked through
     * [SettingsMap.resolve] (the route's page and group) and the page's search lines (each names the group its row is
     * drawn in; SettingsMapTest holds those lines to the drawn page).
     */
    @Test
    fun the184StepsOpenThePlaceTheyDescribe() {
        val pages = Tour.stepsAfter(60).filter { it.since <= 61 }
        // Step title → (page, group, a row the step talks of that is drawn in that group).
        val places = mapOf(
            tr(R.string.tour_184_lyrics_title) to Triple(SettingsPageId.LYRICS_SING, tr(R.string.ls_sources_and_timing), tr(R.string.ls_offset)),
            tr(R.string.tour_184_lock_title) to Triple(SettingsPageId.PLAYER, tr(R.string.settings_player_modes), tr(R.string.settings_player_same_look)),
            tr(R.string.tour_184_lists_title) to Triple(SettingsPageId.HOME_LIBRARY, tr(R.string.hl_lists), tr(R.string.hl_locate_button)),
        )
        for ((title, place) in places) {
            val (page, group, row) = place
            val step = pages.firstOrNull { it.title == title }
            assertNotNull("no 1.8.4 step \"$title\"", step)
            val route = step!!.showMe
            assertNotNull("\"$title\" has no Show me", route)
            val (key, find) = parse(route!!)
            assertEquals("$route: its own page's key", page.key, key)
            assertEquals(route, SettingsTarget(page, group), SettingsMap.resolve(key, find))
            assertTrue("$route: ${page.title} draws no group \"$group\"", page.index.any { it.group == group })
            assertTrue("$route: \"$row\" isn't in ${page.title} › $group", page.index.any { it.group == group && it.title == row })
        }
    }

    /** 1.8.7.5 (build 66): an update from 1.8.7 shows Your Spotify, opening Accounts & sharing at Spotify, then the fixes. */
    /**
     * 1.8.7.6 (build 67): an update from 1.8.7.5 (66) shows Spotify's canvas, then the fixes, and nothing older. The first
     * page opens the Player page at Canvas; only the last carries the line about supporting Prism.
     */
    @Test
    fun anUpdateFrom1875ShowsSpotifysCanvas() {
        // (Up to 1.8.7.6's own: from 1.8.8's build on, its cards come after these.)
        val pages = Tour.stepsAfter(66).filter { it.since <= 67 }
        assertEquals(listOf(R.string.tour_1876_canvas_title, R.string.also_new).map { tr(it) }, pages.map { it.title })
        assertTrue(pages.all { it.since == 67 && it.kind == TourStep.Kind.PLAIN })
        assertTrue(pages.all { it.points.size in 3..5 && it.points.all { p -> p.isNotBlank() } })
        assertTrue(pages.flatMap { it.points + it.body }.none { "%" in it })
        assertEquals(listOf(false, true), pages.map { it.support })
        val (key, find) = parse(pages.first().showMe!!)
        assertEquals(SettingsPageId.PLAYER, SettingsMap.resolve(key, find)?.page)
        // An update from 1.8.7 sees both releases' pages, in order.
        assertEquals(listOf(66, 66, 67, 67), Tour.stepsAfter(65).map { it.since }.filter { it <= 67 })
    }

    @Test
    fun anUpdateFrom187ShowsYourSpotify() {
        val pages = Tour.stepsAfter(65).filter { it.since == 66 }
        assertEquals(listOf(R.string.tour_1875_spotify_title, R.string.also_new).map { tr(it) }, pages.map { it.title })
        assertTrue(pages.all { it.since == 66 && it.kind == TourStep.Kind.PLAIN })
        assertTrue(pages.all { it.points.size in 3..5 && it.points.all { p -> p.isNotBlank() } })
        assertTrue(pages.flatMap { it.points + it.body }.none { "%" in it })
        val (key, find) = parse(pages.first().showMe!!)
        assertEquals(SettingsTarget(SettingsPageId.ACCOUNTS_SHARING, tr(R.string.spotify)), SettingsMap.resolve(key, find))
    }

    /**
     * 1.8.7 (build 65): an update from 1.8.6 (64) shows It just plays, then the rest, and nothing older. The first page
     * names the check as its row is written and opens the group it is in; the second names the Canvas switch and
     * Crossfade, and opens the Player page at Cover.
     */
    @Test
    fun anUpdateFrom186ShowsItJustPlays() {
        val pages = Tour.stepsAfter(64).filter { it.since == 65 }
        assertEquals(listOf(R.string.tour_187_plays_title, R.string.also_new).map { tr(it) }, pages.map { it.title })
        assertTrue(pages.all { it.since == 65 && it.kind == TourStep.Kind.PLAIN })
        assertTrue(pages.all { it.points.size in 3..5 && it.points.all { p -> p.isNotBlank() } })
        assertTrue(pages.flatMap { it.points + it.body }.none { "%" in it })
        val plays = pages.first()
        assertTrue(plays.points.last().contains(tr(R.string.selftest_title)) && plays.points.last().contains(tr(R.string.diagnostics)))
        val (key, find) = parse(plays.showMe!!)
        val page = SettingsPageId.BACKUP_ABOUT
        assertEquals(SettingsTarget(page, tr(R.string.diagnostics)), SettingsMap.resolve(key, find))
        assertTrue(page.index.any { it.group == tr(R.string.diagnostics) && it.title == tr(R.string.selftest_title) })
        val also = pages.last()
        assertTrue(also.points[0].contains(tr(R.string.canvas_lyrics)))
        assertTrue(also.points[2].contains(tr(R.string.crossfade)))
        val (alsoKey, alsoFind) = parse(also.showMe!!)
        assertEquals(SettingsPageId.PLAYER.key, alsoKey)
        assertEquals(tr(R.string.cover), alsoFind)
        assertTrue(SettingsPageId.PLAYER.index.any { it.group == tr(R.string.cover) && it.title == tr(R.string.canvas_lyrics) })
    }

    /**
     * 1.8.6 (build 64): an update from 1.8.5 (63) shows Canvas, then the hours on Replay's chart, and nothing older.
     * Canvas's page names the switch and the menu's rows as they are written, and opens the Player page at Cover.
     */
    @Test
    fun anUpdateFrom185ShowsCanvas() {
        val pages = Tour.stepsAfter(63).filter { it.since == 64 }
        assertEquals(listOf(R.string.tour_186_canvas_title, R.string.also_new).map { tr(it) }, pages.map { it.title })
        assertTrue(pages.all { it.since == 64 && it.kind == TourStep.Kind.PLAIN })
        assertTrue(pages.flatMap { it.points + it.body }.none { "%" in it })
        val canvas = pages.first()
        assertEquals(4, canvas.points.size)
        // Owner, 2026-11-13: Canvas and Animated covers are two settings; the page names both switches.
        assertTrue(canvas.points[1].contains(tr(R.string.canvas)) && canvas.points[1].contains(tr(R.string.animated_covers)))
        assertTrue(canvas.points[2].contains(tr(R.string.canvas_pick_video)) && canvas.points[2].contains(tr(R.string.canvas_pick_part)))
        val (key, find) = parse(canvas.showMe!!)
        assertEquals(com.prism.music.ui.screens.settings.SettingsPageId.PLAYER.key, key)
        assertEquals(tr(R.string.cover), find)
        assertTrue(pages.last().points.single().contains(tr(R.string.hours_by_month)))
        assertEquals(com.prism.music.ui.Routes.REPLAY, pages.last().showMe)
    }

    /**
     * 1.8.5 (build 63): an update from 1.8.4.1 (62) shows Replay's new sections, Rewind 2026, the widgets and notices,
     * and the rest, in that order, and nothing older.
     */
    @Test
    fun anUpdateFrom1841ShowsThe185Pages() {
        val pages = Tour.stepsAfter(62).filter { it.since == 63 }
        assertTrue(pages.all { it.kind == TourStep.Kind.PLAIN })
        assertEquals(
            listOf(R.string.tour_185_replay_title, R.string.tour_185_rewind_title, R.string.tour_185_more_title, R.string.also_new).map { tr(it) },
            pages.map { it.title },
        )
        assertTrue(pages.all { it.points.size in 3..5 && it.points.all { p -> p.isNotBlank() } })
        assertTrue(pages.flatMap { it.points }.none { "%" in it })
        assertEquals(com.prism.music.ui.Routes.REPLAY, pages.first().showMe)
        assertNull(pages.last().showMe)
        // The notices and Finish the lyric are named as their rows are, and the step opens the group that draws them.
        val more = pages[2]
        assertTrue(more.points[1].contains(tr(R.string.replay_notices)))
        assertTrue(more.points[2].contains(tr(R.string.finish_lyric_title)))
        val (key, find) = parse(more.showMe!!)
        val page = SettingsPageId.RECOMMENDATIONS_HISTORY
        assertEquals(SettingsTarget(page, tr(R.string.replay_2)), SettingsMap.resolve(key, find))
        for (row in listOf(R.string.replay_notices, R.string.finish_lyric_title)) {
            assertTrue(page.index.any { it.group == tr(R.string.replay_2) && it.title == tr(row) })
        }
        assertTrue(pages[3].points[2].contains(tr(R.string.playback_face_down_pause)))
    }
    /** `settings/<key>?find=<group>` → its key and group, as the app's route reads it. */
    private fun parse(route: String): Pair<String, String?> {
        val key = route.removePrefix("settings/").substringBefore('?')
        val find = route.substringAfter("?find=", "").takeIf { it.isNotBlank() }?.let { android.net.Uri.decode(it) }
        return key to find
    }

    /**
     * Every "Show me" opens a page that exists: a settings page by a key Settings resolves (1.8.4's page keys and the
     * old ones), or one of the app's routes.
     */
    @Test
    fun everyShowMeOpensARealPage() {
        val routes = setOf(
            com.prism.music.ui.Routes.SETTINGS, com.prism.music.ui.Routes.SEARCH, com.prism.music.ui.Routes.CATALOGUE,
            com.prism.music.ui.Routes.BRING_HISTORY, com.prism.music.ui.Routes.LIBRARY, com.prism.music.ui.Routes.librarySearch(),
            com.prism.music.ui.Routes.REPLAY,
        )
        for (route in Tour.steps.mapNotNull { it.showMe }) {
            if (route.startsWith("settings/")) {
                val (key, find) = parse(route)
                assertNotNull("$route: no settings page \"$key\"", SettingsMap.resolve(key, find))
            } else assertTrue("$route isn't a page the tour knows", route in routes)
        }
    }
}
