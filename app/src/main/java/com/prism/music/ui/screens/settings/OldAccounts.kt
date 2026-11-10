package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.ChoiceCards
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.Note
import com.prism.music.ui.screens.Toggle

/**
 * Discord presence: "Listening to Prism" with the song on the listener's Discord profile. Turning it on the first time
 * signs in to Discord (on Discord's own page); the session stays on this phone.
 */
@Composable
internal fun DiscordGroup() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val nav = LocalNavigator.current
    val state by c.discord.state.collectAsState()
    val user by c.discord.userName.collectAsState()
    Group(tr(R.string.discord)) {
        Toggle(
            tr(R.string.show_what_you_re_playing),
            when {
                !c.discord.signedIn -> tr(R.string.listening_to_prism_on_your_profile)
                state == com.prism.music.playback.DiscordPresence.State.CONNECTED -> (user?.let { tr(R.string.on_as, it) } ?: tr(R.string.on_2))
                state == com.prism.music.playback.DiscordPresence.State.CONNECTING -> tr(R.string.connecting_to_discord)
                else -> (user?.let { tr(R.string.on_discord_as, it) } ?: tr(R.string.on_discord))
            },
            s.discordPresence && c.discord.signedIn,
            info = tr(R.string.your_profile_shows_the_song_and),
        ) { on ->
            if (on && !c.discord.signedIn) nav.go(Routes.DISCORD_LOGIN) else c.settings.setDiscordPresence(on)
        }
        if (c.discord.signedIn) Item(tr(R.string.sign_out_of_discord), tr(R.string.forgets_the_sign_in_on_this), onClick = {
            c.settings.setDiscordPresence(false)
            c.discord.signOut()
        })
        Item(
            tr(R.string.about_this), tr(R.string.a_small_risk_to_your_discord),
            info = tr(R.string.discord_has_no_way_for_android),
            onClick = {},
        )
    }
}

/** What links Prism sends (Reddit R9), and YouTube Music links opening in Prism (R10). */
@Composable
internal fun SharingGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val context = LocalContext.current
    // Android 12+ asks the listener whether Prism opens music.youtube.com links (an app that isn't YouTube Music can't
    // claim them), so the state is read again whenever Prism comes back from that page.
    val resumed = com.prism.music.ui.components.rememberResumeCount()
    val opensLinks = remember(resumed) { com.prism.music.data.LinkHandling.opensYouTubeMusicLinks(context) }
    Group(tr(R.string.sharing_and_links)) {
        Label(tr(R.string.links_you_share), info = tr(R.string.albums_have_no_page_on_youtube))
        ChoiceCards(
            listOf(false, true), s.shareYoutubeLinks, title = { if (it) tr(R.string.youtube) else tr(R.string.youtube_music_2) },
            detail = { if (it) tr(R.string.opens_in_youtube) else tr(R.string.opens_in_youtube_music) }, onSelect = { prefs.setShareYoutubeLinks(it) },
        ) { youtube ->
            Text(
                if (youtube) tr(R.string.youtu_be_fj9ruzimczq) else tr(R.string.music_youtube_com_watch_v_fj9ruzimczq),
                Modifier.fillMaxWidth().padding(vertical = 10.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Item(
            tr(R.string.open_youtube_music_links_in_prism),
            when (opensLinks) {
                true -> tr(R.string.on_links_you_tap_open_here)
                false -> tr(R.string.off_turn_on_open_supported_links)
                null -> tr(R.string.choose_prism_when_a_link_asks)
            },
            info = tr(R.string.android_only_lets_an_app_open),
            onClick = { com.prism.music.data.LinkHandling.openSettings(context) },
        ) { Chevron() }
    }
}

/** Settings → Recommendations & Replay (1.7.6): what Prism suggests and from where, and your listening recap. */
/** Last.fm scrobbling (#133, #160): connect your account in the browser, then what's sent. */
@Composable
internal fun LastFmGroup() {
    val c = LocalContainer.current
    val context = LocalContext.current
    val lastFm = c.lastFm
    val user by lastFm.user.collectAsState()
    val o by lastFm.options.collectAsState()
    val waiting by lastFm.waiting.collectAsState()
    Group(
        "Last.fm",
        info = tr(R.string.sends_what_you_listen_to_scrobbles_all),
    ) {
        if (!lastFm.available) {
            Note(tr(R.string.this_copy_of_prism_was_built))
            return@Group
        }
        if (user == null) {
            Item(tr(R.string.connect_last_fm), tr(R.string.opens_last_fm_to_let_prism), onClick = {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(lastFm.authUrl()))) }
            }) { Chevron() }
            return@Group
        }
        Toggle(tr(R.string.scrobble), tr(R.string.connected_as, user) + if (waiting > 0) tr(R.string.waiting_to_send, waiting) else "", o.scrobble) { lastFm.setOptions(o.copy(scrobble = it)) }
        Toggle(
            tr(R.string.first_artist_only), tr(R.string.a_b_c_goes_to_last), o.firstArtistOnly,
            info = tr(R.string.last_fm_matches_one_artist_per),
        ) { lastFm.setOptions(o.copy(firstArtistOnly = it)) }
        Toggle(tr(R.string.love_when_i_like_a_song), tr(R.string.liking_here_loves_it_on_last), o.loveOnLike) { lastFm.setOptions(o.copy(loveOnLike = it)) }
        Item(tr(R.string.disconnect), tr(R.string.stops_scrobbling_and_forgets_the_connection), onClick = { lastFm.signOut() })
    }
}
