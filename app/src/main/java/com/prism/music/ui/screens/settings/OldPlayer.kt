package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.CoverSize
import com.prism.music.data.prefs.PlayerBackground
import com.prism.music.data.prefs.TransportStyle
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlin.math.roundToInt
import com.prism.music.ui.screens.ButtonsPillCards
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.ChoiceCards
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.MoreOptions
import com.prism.music.ui.screens.Note
import com.prism.music.ui.screens.PlayerBackgroundCards
import com.prism.music.ui.screens.PlayerBackgroundTuning
import com.prism.music.ui.screens.PlayerLayoutGroup
import com.prism.music.ui.screens.PlayerPartsGroup
import com.prism.music.ui.screens.PlayerProgressGroup
import com.prism.music.ui.screens.PlayerTitleGroup
import com.prism.music.ui.screens.PlayerVisualizerGroup
import com.prism.music.ui.screens.ShowChips
import com.prism.music.ui.screens.ShowOption
import com.prism.music.ui.screens.SliderSetting
import com.prism.music.ui.screens.Toggle
import com.prism.music.ui.screens.worded

// ---------------------------------------------------------------------------- Player

@Composable
private fun LockScreenGroup() {
    // 1.8.2: Prism's own lock screen ([com.prism.music.ui.lockscreen.LockScreenActivity]); off unless switched on.
    val s = LocalAppSettings.current
    val container = LocalContainer.current
    val prefs = container.settings
    val context = androidx.compose.ui.platform.LocalContext.current
    val b = s.behavior
    val gate = com.prism.music.ui.lockscreen.LockScreenGate
    val resumed = com.prism.music.ui.components.rememberResumeCount()
    // Read again on coming back from Android's page.
    val canStart = remember(resumed, b.lockScreen) { gate.canStartDirectly(context) }
    val maker = remember { com.prism.music.ui.lockscreen.LockScreenMaker.of() }
    val refused = remember(resumed, b.lockScreen) { com.prism.music.ui.lockscreen.LockScreenRefusals.lately(context) }
    val song by container.player.currentSong.collectAsState()
    Group(tr(R.string.lock_screen_title)) {
        Toggle(tr(R.string.lock_screen_title), tr(R.string.lock_screen_detail), b.lockScreen, info = tr(R.string.lock_screen_info)) { on ->
            prefs.setLockScreen(on)
            // From Android 13 an app installed from a file only gets "App was denied access" on that page: the steps come first.
            if (on && !gate.canStartDirectly(context) && !gate.mayBeRestricted) gate.openPermissionPage(context)
        }
        if (b.lockScreen) {
            if (!canStart) {
                Note(tr(R.string.lock_screen_needs_permission))
                if (gate.mayBeRestricted) {
                    Item(tr(R.string.lock_screen_app_info), tr(R.string.lock_screen_app_info_detail), onClick = { gate.openAppInfo(context) }, info = tr(R.string.lock_screen_app_info_info))
                    Item(tr(R.string.lock_screen_allow_step), tr(R.string.lock_screen_allow_detail), onClick = { gate.openPermissionPage(context) }, info = tr(R.string.lock_screen_allow_info))
                } else {
                    Item(tr(R.string.lock_screen_allow), tr(R.string.lock_screen_allow_detail), onClick = { gate.openPermissionPage(context) }, info = tr(R.string.lock_screen_allow_info))
                }
            }
            // D43 (1.8.3): vivo and Xiaomi add permissions of their own that Android can't read: one more step there,
            // and a line when the phone refused the player last time.
            maker?.let { m ->
                val detail = if (m == com.prism.music.ui.lockscreen.LockScreenMaker.XIAOMI) R.string.lock_screen_maker_xiaomi_detail else R.string.lock_screen_maker_vivo_detail
                Item(tr(R.string.lock_screen_maker_step), tr(detail), onClick = { com.prism.music.ui.lockscreen.LockScreenMaker.openPage(context, m) }, info = tr(R.string.lock_screen_maker_info))
            }
            if (canStart && refused) Note(tr(R.string.lock_screen_refused))
            Label(tr(R.string.lock_screen_layout_label))
            if (s.performanceMode) Note(tr(R.string.lock_screen_performance))
            ChoiceCards(
                com.prism.music.data.prefs.LockLayout.entries, b.lockScreenLayout, title = { it.label }, detail = { it.detail }, columns = 2,
                onSelect = { prefs.setLockScreenLayout(it) },
            ) { com.prism.music.ui.lockscreen.LockScreenSample(it, song) }
            Label(tr(R.string.lock_screen_show), info = tr(R.string.lock_screen_show_info))
            ShowChips(
                listOf(
                    ShowOption(tr(R.string.volume), b.lockScreenVolume) { prefs.setLockScreenVolume(it) },
                    ShowOption(tr(R.string.lock_screen_show_like), b.lockScreenLike) { prefs.setLockScreenLike(it) },
                    ShowOption(tr(R.string.lock_screen_show_output), b.lockScreenOutput) { prefs.setLockScreenOutput(it) },
                    // 1.8.3 (D47): off by default; the cards above show them once on.
                    ShowOption(tr(R.string.repeat), b.lockScreenRepeat) { prefs.setLockScreenRepeat(it) },
                    ShowOption(tr(R.string.up_next), b.lockScreenUpNext) { prefs.setLockScreenUpNext(it) },
                ),
            )
            Label(tr(R.string.lock_screen_paused_label), info = tr(R.string.lock_screen_paused_info))
            Chips(com.prism.music.data.prefs.LockPaused.entries, b.lockScreenPaused, { it.label }) { prefs.setLockScreenPaused(it) }
            Toggle(tr(R.string.lock_screen_hide), tr(R.string.lock_screen_hide_detail), b.lockScreenHideSong, info = tr(R.string.lock_screen_hide_info)) { prefs.setLockScreenHideSong(it) }
        }
    }
}

@Composable
internal fun PlayerSettings() {
    val s = LocalAppSettings.current
    val container = LocalContainer.current
    val prefs = container.settings
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        // How the player looks (1.7.6: what it does on a touch is in Controls & gestures, what it keeps on the phone in
        // Downloads, storage & data). 1.8.0: its layout, then what's on it (one editor with the player drawn, replacing
        // "Show on the player", "Under the title" and the buttons; PlayerYourWay.kt), its look and its cover.
        // "Song length" went (tapping the time switches it, and it's remembered); the cast mark is no longer a choice.
        PlayerLayoutGroup()
        PlayerPartsGroup()
        PlayerLookGroup()
        PlayerCoverGroup()
        Group(tr(R.string.lyrics_and_the_queue), info = tr(R.string.the_player_s_controls_while_the)) {
            ChoiceCards(
                com.prism.music.data.prefs.PanelPlayer.entries, s.view.panelPlayer, title = { it.label }, detail = { it.detail }, columns = 2,
                onSelect = { prefs.setPanelPlayer(it) },
            ) { PanelPlayerSample(it) }
            if (s.view.panelPlayer == com.prism.music.data.prefs.PanelPlayer.AUTO_HIDE) Toggle(
                tr(R.string.scrolling_up_shows_the_controls), tr(R.string.back_up_the_lyrics_or_the), s.behavior.scrollShowsControls,
                info = tr(R.string.off_scrolling_back_up_to_an),
            ) { prefs.setScrollShowsControls(it) }
            Toggle(
                tr(R.string.queue_headings), tr(R.string.played_now_playing_and_up_next), s.view.queueHeadings,
                info = tr(R.string.off_the_queue_is_one_list),
            ) { prefs.setQueueHeadings(it) }
            // #275: room in the queue's header; DJ can be one of the player's buttons instead.
            Toggle(
                tr(R.string.dj_in_the_queue), tr(R.string.dj_in_the_queue_detail), s.view.queueDjChip,
                info = tr(R.string.dj_in_the_queue_info),
            ) { prefs.setQueueDjChip(it) }
            // #331, #294: the queue's rows.
            Toggle(tr(R.string.song_lengths_in_the_queue), null, s.view.queueLengths) { prefs.setQueueLengths(it) }
            Toggle(
                tr(R.string.move_songs_you_have_played), null, s.view.queuePlayedMoves,
                info = tr(R.string.move_songs_you_have_played_info),
            ) { prefs.setQueuePlayedMoves(it) }
            Toggle(
                tr(R.string.tidier_queue), null, s.view.queueTidy,
                info = tr(R.string.tidier_queue_info),
            ) { prefs.setQueueTidy(it) }
            // #280: which page the player opens on, and how readily the lyrics' controls move.
            Toggle(
                tr(R.string.reopen_on_the_last_page), null, s.behavior.playerRemembersPage,
                info = tr(R.string.reopen_on_the_last_page_info),
            ) { prefs.setPlayerRemembersPage(it) }
            Toggle(
                tr(R.string.steadier_lyrics), null, s.behavior.lyricsSteady,
                info = tr(R.string.steadier_lyrics_info),
            ) { prefs.setLyricsSteady(it) }
        }
        Group(tr(R.string.animated_covers)) {
            Toggle(
                tr(R.string.animated_covers), tr(R.string.moving_artwork_when_a_song_has), s.canvasEnabled,
                info = tr(R.string.motion_artwork_and_video_covers_turn),
            ) { prefs.setCanvasEnabled(it) }
            if (s.canvasEnabled) Toggle(
                tr(R.string.fill_the_player), tr(R.string.the_animated_cover_plays_behind_everything), s.canvasFillsBackground,
                info = tr(R.string.the_moving_artwork_fills_the_whole),
            ) { prefs.setCanvasFillsBackground(it) }
        }
        // #265 (1.8.0): the TV's ambient screen on a phone at its stand ([com.prism.music.ui.tv.AmbientHost]); off unless switched on.
        Group(tr(R.string.desk_mode)) {
            Toggle(tr(R.string.desk_mode), tr(R.string.desk_mode_detail), s.tv.deskMode, info = tr(R.string.desk_mode_info)) { prefs.setDeskMode(it) }
        }
        LockScreenGroup()
        MoreOptions(listOf(tr(R.string.title_and_times), tr(R.string.visualizer), tr(R.string.immersive_mode))) {
        PlayerTitleGroup()
        PlayerVisualizerGroup()
        Group(tr(R.string.immersive_mode)) {
            Toggle(
                tr(R.string.immersive_mode), tr(R.string.the_cover_fills_the_screen_lyrics), s.immersivePlayer,
                info = tr(R.string.like_a_lyric_video_tap_the),
            ) { prefs.setImmersivePlayer(it) }
            if (s.immersivePlayer) {
                Label(tr(R.string.artwork))
                Chips(com.prism.music.data.prefs.ImmersiveArt.entries, s.immersiveArt, { it.label }) { prefs.setImmersiveArt(it) }
                Label(tr(R.string.lyrics))
                Chips(com.prism.music.data.prefs.ImmersiveLyrics.entries, s.immersiveLyrics, { it.label }) { prefs.setImmersiveLyrics(it) }
                Toggle(tr(R.string.keep_controls_on_screen), tr(R.string.previous_play_and_next_stay_up), s.immersiveControls) { prefs.setImmersiveControls(it) }
                Toggle(
                    tr(R.string.show_the_video), tr(R.string.a_playing_music_video_behind_the), s.view.immersiveVideo,
                    info = tr(R.string.when_a_music_video_is_playing),
                ) { prefs.setImmersiveVideo(it) }
                Spacer(Modifier.height(6.dp))
            }
        }
        }
    }
}

@Composable
internal fun PlayerLookGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Group(tr(R.string.look)) {
        // #320 (1.8.1): White is a white player with dark type, for the light theme.
        Label(tr(R.string.background), info = tr(R.string.player_background_white_info) + " " + tr(R.string.cover_gradient_info))
        // #232, #338 (1.8.2): each as a small player on that background, with the song playing.
        PlayerBackgroundCards()
        if (s.playerBackground == PlayerBackground.APPEARANCE) {
            val nav = LocalNavigator.current
            val own = s.backdrops[com.prism.music.ui.theme.PLAYER_BACKDROP]
            Item(
                tr(R.string.which_background),
                if (own == null) tr(R.string.same_as_home_darkened, com.prism.music.ui.theme.Backdrop.parse(s.backdrops[com.prism.music.ui.theme.BackdropScreen.HOME.name]).first.label)
                else com.prism.music.ui.theme.Backdrop.parse(own).first.label + tr(R.string.darkened_2),
                onClick = { nav.go(Routes.BACKGROUNDS + "?player=true") },
            ) { Chevron() }
        }
        // #309 (1.8.1): the slider runs on past what it could before, to a fully dark player at 0% (stored values from
        // before draw as they did; only the percent shown for them moved).
        if (s.playerBackground == PlayerBackground.BLUR || s.playerBackground == PlayerBackground.APPEARANCE) SliderSetting(
            tr(R.string.show_background), com.prism.music.ui.player.backdropShowOnSlider(s.playerBackdropShow), 0f..1f,
            {
                val show = com.prism.music.ui.player.backdropShowFromSlider(it)
                worded(if (it < 0.01f) tr(R.string.dark) else if (show < 0.2f) tr(R.string.subtle) else if (show < 0.7f) tr(R.string.balanced) else tr(R.string.vivid), it)
            },
            info = tr(R.string.show_background_info),
        ) { prefs.setPlayerBackdropShow(com.prism.music.ui.player.backdropShowFromSlider(it)) }
        // The mesh's movement, the blur's softness (1.7.4; one movement slider since 1.8.0).
        PlayerBackgroundTuning()
        Label(tr(R.string.controls))
        Chips(TransportStyle.entries, s.transportStyle, { it.label }) { prefs.setTransportStyle(it) }
        // #231 (1.7.9.2): the split-light ring around play, which some find too much.
        Toggle(tr(R.string.ring_on_the_play_button), tr(R.string.the_split_light_around_play_and_pause), s.view.playRing) { prefs.setPlayRing(it) }
        // The row in a capsule or apart (1.8.0: a look choice here; which buttons are in What's on the player).
        ButtonsPillCards()
    }
    // 1.8.0: the progress bar's style, color, thickness and knob in one group (they were split between here and More options).
    PlayerProgressGroup()
}

/** The player with its lyrics open, drawn small: lines of lyrics, and the controls under them as [mode] leaves them. */
@Composable
private fun PanelPlayerSample(mode: com.prism.music.data.prefs.PanelPlayer) {
    val ink = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 18.dp, vertical = 6.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(ink.copy(alpha = 0.06f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
        val controls = mode != com.prism.music.data.prefs.PanelPlayer.HIDDEN
        // Small (#230): the controls take a slimmer band at the bottom, the list most of the room.
        val small = mode == com.prism.music.data.prefs.PanelPlayer.SMALL
        val linesEnd = if (small) h * 0.76f else if (controls) h * 0.62f else h * 0.92f
        var y = h * 0.12f
        var i = 0
        while (y < linesEnd) {
            drawRoundRect(
                ink.copy(alpha = if (i == 1) 0.85f else 0.3f), androidx.compose.ui.geometry.Offset(w * 0.12f, y),
                androidx.compose.ui.geometry.Size(w * (if (i % 2 == 0) 0.62f else 0.74f), h * 0.07f), androidx.compose.ui.geometry.CornerRadius(h * 0.035f),
            )
            y += h * 0.14f; i++
        }
        if (small) {
            drawRect(ink.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.83f), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.02f))
            listOf(0.4f, 0.5f, 0.6f).forEachIndexed { k, x ->
                drawCircle(ink.copy(alpha = 0.85f), if (k == 1) h * 0.05f else h * 0.03f, androidx.compose.ui.geometry.Offset(w * x, h * 0.92f))
            }
        } else if (controls) {
            val a = if (mode == com.prism.music.data.prefs.PanelPlayer.AUTO_HIDE) 0.3f else 0.85f
            drawRect(ink.copy(alpha = a * 0.6f), androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.72f), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.025f))
            listOf(0.32f, 0.5f, 0.68f).forEachIndexed { k, x ->
                drawCircle(ink.copy(alpha = a), if (k == 1) h * 0.08f else h * 0.05f, androidx.compose.ui.geometry.Offset(w * x, h * 0.86f))
            }
        }
    }
}

/** How an edge-to-edge cover fits: filling the screen or whole. Under its own label since 1.8.4 (the chips stood alone). */
@Composable
internal fun CoverFitChoice(fit: com.prism.music.data.prefs.BleedFit, onSelect: (com.prism.music.data.prefs.BleedFit) -> Unit) {
    Label(tr(R.string.how_the_cover_fits))
    Chips(com.prism.music.data.prefs.BleedFit.entries, fit, { it.label }, onSelect)
}

@Composable
internal fun PlayerCoverGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Group(tr(R.string.cover)) {
        Label(tr(R.string.cover_size))
        Chips(CoverSize.entries, s.coverSize, { it.label }) { prefs.setCoverSize(it) }
        SliderSetting(tr(R.string.corners), s.playerArtCorners.toFloat(), 0f..32f, { tr(R.string.dp, it.roundToInt()) }, steps = 7) { prefs.setPlayerArtCorners(it.roundToInt()) }
        // Two switches again (1.8.1): one for the cover, one for a music video; the video's own button changes only the video.
        Toggle(tr(R.string.edge_to_edge_cover), null, s.fullBleedArtwork, info = tr(R.string.the_cover_runs_to_the_screen)) { prefs.setFullBleedArtwork(it) }
        Toggle(tr(R.string.edge_to_edge_video), null, s.videoFullBleed, info = tr(R.string.a_music_video_runs_to_the)) { prefs.setVideoFullBleed(it) }
        if (s.fullBleedArtwork) CoverFitChoice(s.view.bleedFit) { prefs.setBleedFit(it) }
        // #293: over a full-bleed picture, smaller controls and a later fade.
        if (s.fullBleedArtwork || s.videoFullBleed) Toggle(
            tr(R.string.more_room_for_the_picture), null, s.view.bleedRoomy,
            info = tr(R.string.more_room_for_the_picture_info),
        ) { prefs.setBleedRoomy(it) }
        // With full-bleed artwork the banner covers the cover that shrinks (Reddit, 1.7.9.3): it says so rather than seem broken.
        Toggle(
            tr(R.string.shrink_when_paused),
            if (s.fullBleedArtwork) tr(R.string.not_with_full_bleed_artwork_the_banner_covers_it) else tr(R.string.the_cover_eases_back_while_paused),
            s.artShrinkOnPause,
        ) { prefs.setArtShrinkOnPause(it) }
        // Square video covers moved to Icons & shapes → Covers' shapes (1.8.0: it changes covers everywhere).
        Toggle(
            tr(R.string.cover_flies_into_place), tr(R.string.opening_the_player_the_mini_player), s.view.coverFlight,
            info = tr(R.string.instead_of_the_whole_player_sliding),
        ) { prefs.setCoverFlight(it) }
        // #223: and back the same way.
        if (s.view.coverFlight) Toggle(
            tr(R.string.cover_flies_back_too), null, s.view.coverFlightBack,
            info = tr(R.string.cover_flies_back_too_info),
        ) { prefs.setCoverFlightBack(it) }
    }
}
