package com.prism.music

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.tv.TvRoot
import kotlinx.coroutines.launch

/**
 * Prism TV's screen (1.8.0, plan-1.8.0 §3): the TV launcher's entry (LEANBACK_LAUNCHER) with its own screens in
 * `ui/tv`, built for a remote. Everything below the screens is the phone's: [AppContainer], the playback service and
 * its media session, YouTube Music, lyrics, settings, translations. The phone's [MainActivity] sends itself here on a
 * TV, so the TV never shows phone screens.
 */
class TvActivity : ComponentActivity() {
    // Prism's language, when picked in Prism (below Android 13; AppLanguage).
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(AppLanguage.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = container
        c.player.connect()
        // As on the phone: the downloads' index comes off the disk in the background, never in the first frame's way.
        c.scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { c.downloads } }
        // Library: what YouTube Music keeps for the account, brought up to date as on the phone.
        if (c.settings.current.isLoggedIn) c.library.syncInBackground()
        setContent {
            CompositionLocalProvider(LocalContainer provides c) { TvRoot() }
        }
    }

    /** Any key: the room isn't quiet (the ambient screen waits, or goes). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        com.prism.music.ui.tv.Idle.touched()
    }

    /**
     * The remote's media keys, on every screen (plan §3): handled here before the screens see them, so a focused button
     * never swallows Play/Pause, and they don't depend on Android passing them on to the media session.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = TvMediaKeys.action(event.keyCode)
        if (action == null) {
            // The ambient screen: a key only brings back what was there.
            if (com.prism.music.ui.tv.Idle.ambient) { com.prism.music.ui.tv.Idle.touched(); return true }
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) TvMediaKeys.run(action, container.player)
        return true
    }
}

/** What each of the remote's media keys does. */
object TvMediaKeys {
    enum class Action { TOGGLE, PLAY, PAUSE, NEXT, PREVIOUS, FORWARD, BACKWARD, STOP }

    /** How far fast-forward and rewind move (the phone's double-tap seek). */
    const val SEEK_MS = 10_000L

    fun action(keyCode: Int): Action? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> Action.TOGGLE
        KeyEvent.KEYCODE_MEDIA_PLAY -> Action.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> Action.PAUSE
        KeyEvent.KEYCODE_MEDIA_NEXT -> Action.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> Action.PREVIOUS
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> Action.FORWARD
        KeyEvent.KEYCODE_MEDIA_REWIND -> Action.BACKWARD
        KeyEvent.KEYCODE_MEDIA_STOP -> Action.STOP
        else -> null
    }

    fun run(action: Action, player: com.prism.music.playback.PlayerConnection) {
        when (action) {
            Action.TOGGLE -> player.togglePlay()
            Action.PLAY -> player.setPlaying(true)
            Action.PAUSE -> player.setPlaying(false)
            Action.NEXT -> player.next()
            Action.PREVIOUS -> player.previous()
            Action.FORWARD -> player.seekBy(SEEK_MS)
            Action.BACKWARD -> player.seekBy(-SEEK_MS)
            // Stop pauses: the queue stays (a remote's Stop is pressed far more often by accident than meant).
            Action.STOP -> player.setPlaying(false)
        }
    }
}
