package com.prism.music

import android.os.Bundle
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
        setContent {
            CompositionLocalProvider(LocalContainer provides c) { TvRoot() }
        }
    }
}
