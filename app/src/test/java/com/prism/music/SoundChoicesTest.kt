package com.prism.music

import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.AutoplaySource
import com.prism.music.ui.screens.Crossfade
import com.prism.music.ui.screens.QueueEnd
import com.prism.music.ui.screens.SoundChoices
import com.prism.music.ui.screens.TrimSilence
import com.prism.music.ui.screens.VideoPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 1.8.0 (#210): Playback & sound's merged choices are faces over the settings Prism already keeps. Every old combination
 * of those settings shows as the right choice (nobody's setup changes on the update), and every choice written reads
 * back as itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SoundChoicesTest {
    private val base = AppSettings()

    @Test fun whenTheQueueEndsFollowsAutoplayAndItsSource() {
        assertEquals(QueueEnd.STOP, SoundChoices.queueEnd(base.copy(autoplay = false, autoplaySource = AutoplaySource.YOURS)))
        assertEquals(QueueEnd.YOURS, SoundChoices.queueEnd(base.copy(autoplay = true, autoplaySource = AutoplaySource.YOURS)))
        assertEquals(QueueEnd.MIXED, SoundChoices.queueEnd(base.copy(autoplay = true, autoplaySource = AutoplaySource.MIXED)))
        assertEquals(QueueEnd.YOUTUBE, SoundChoices.queueEnd(base.copy(autoplay = true, autoplaySource = AutoplaySource.YOUTUBE)))
        for (q in QueueEnd.entries) {
            val (on, source) = SoundChoices.queueEndValues(q)
            val after = base.copy(autoplay = on, autoplaySource = source ?: base.autoplaySource)
            assertEquals(q, SoundChoices.queueEnd(after))
        }
        // Stop keeps the source, so turning autoplay back on gives the old one.
        assertEquals(null, SoundChoices.queueEndValues(QueueEnd.STOP).second)
    }

    @Test fun musicVideosFollowSongsNotVideosAndEverywhere() {
        assertEquals(VideoPolicy.PLAY, SoundChoices.videoPolicy(base.copy(radioSongsOnly = false, songsEverywhere = false)))
        // An old "Everywhere" left on under a switched-off "Songs, not videos" did nothing: it still reads as Play them.
        assertEquals(VideoPolicy.PLAY, SoundChoices.videoPolicy(base.copy(radioSongsOnly = false, songsEverywhere = true)))
        assertEquals(VideoPolicy.RADIOS, SoundChoices.videoPolicy(base.copy(radioSongsOnly = true, songsEverywhere = false)))
        assertEquals(VideoPolicy.EVERYWHERE, SoundChoices.videoPolicy(base.copy(radioSongsOnly = true, songsEverywhere = true)))
        for (v in VideoPolicy.entries) {
            val (radios, everywhere) = SoundChoices.videoPolicyValues(v)
            assertEquals(v, SoundChoices.videoPolicy(base.copy(radioSongsOnly = radios, songsEverywhere = everywhere)))
        }
    }

    @Test fun crossfadeFollowsMixAndAutomaticLength() {
        assertEquals(Crossfade.OFF, SoundChoices.crossfade(base.copy(mix = false, mixAuto = true)))
        assertEquals(Crossfade.OFF, SoundChoices.crossfade(base.copy(mix = false, mixAuto = false)))
        assertEquals(Crossfade.AUTO, SoundChoices.crossfade(base.copy(mix = true, mixAuto = true)))
        assertEquals(Crossfade.FIXED, SoundChoices.crossfade(base.copy(mix = true, mixAuto = false)))
        for (x in Crossfade.entries) {
            val (on, auto) = SoundChoices.crossfadeValues(x)
            assertEquals(x, SoundChoices.crossfade(base.copy(mix = on, mixAuto = auto ?: base.mixAuto)))
        }
        // Off keeps automatic/fixed, so turning crossfade back on gives the old way.
        assertEquals(null, SoundChoices.crossfadeValues(Crossfade.OFF).second)
    }

    @Test fun trimSilenceFollowsSongsAndPodcasts() {
        fun s(songs: Boolean, episodes: Boolean) = base.copy(skipSilence = songs, sound = base.sound.copy(skipSilenceEpisodes = episodes))
        assertEquals(TrimSilence.OFF, SoundChoices.trimSilence(s(false, false)))
        assertEquals(TrimSilence.PODCASTS, SoundChoices.trimSilence(s(false, true)))
        assertEquals(TrimSilence.ALL, SoundChoices.trimSilence(s(true, true)))
        // Songs on with podcasts off (possible before 1.8.0) reads as both: what songs got stays the same.
        assertEquals(TrimSilence.ALL, SoundChoices.trimSilence(s(true, false)))
        for (t in TrimSilence.entries) {
            val (songs, episodes) = SoundChoices.trimSilenceValues(t)
            assertEquals(t, SoundChoices.trimSilence(s(songs, episodes)))
        }
    }
}
