package com.prism.music.ui.screens

import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.AutoplaySource
import com.prism.music.data.prefs.SettingsRepository
import com.prism.music.tr

/** When the queue ends (1.8.0, #210): autoplay's switch and its source as one choice. */
enum class QueueEnd(val label: String) { STOP(tr(R.string.stop)), YOURS(tr(R.string.your_music)), MIXED(tr(R.string.queue_end_mixed)), YOUTUBE(tr(R.string.prism_music)) }

/** Music videos (1.8.0): "Songs, not videos" and its "Everywhere" as one choice. */
enum class VideoPolicy(val label: String) { PLAY(tr(R.string.play_them)), RADIOS(tr(R.string.songs_in_radios)), EVERYWHERE(tr(R.string.songs_everywhere)) }

/**
 * Crossfade (1.8.0, #210): its switch and "Automatic length" as one choice; Fixed shows the length slider. Automix (1.8.8,
 * plan-mix §3.2): automatic length with a DJ-style move picked for each pair of songs.
 */
enum class Crossfade(val label: String) {
    OFF(tr(R.string.off)), AUTO(tr(R.string.automatic_plain)), FIXED(tr(R.string.fixed_length)), AUTOMIX(tr(R.string.automix)),
}

/** What a [Crossfade] choice stores: Crossfade on, automatic length, Automix (null = leave it as it was). */
data class CrossfadeValues(val on: Boolean, val auto: Boolean?, val automix: Boolean?)

/** Skip silence (1.8.0, #210; "Songs only" #325): skip silence for songs and for podcast episodes as one choice. */
enum class TrimSilence(val label: String) { OFF(tr(R.string.off)), SONGS(tr(R.string.songs_only)), PODCASTS(tr(R.string.podcasts_only)), ALL(tr(R.string.songs_and_podcasts)) }

/**
 * The merged choices on Playback & sound, each read from and written to the settings Prism already keeps, so nobody's
 * choices change on the update and Looks, backups and older builds read them as before. The mapping is pure ([read]/
 * [write] pairs, SoundChoicesTest); the `set` functions only hand the values to [SettingsRepository].
 */
object SoundChoices {
    fun queueEnd(s: AppSettings): QueueEnd = if (!s.autoplay) QueueEnd.STOP else when (s.autoplaySource) {
        AutoplaySource.YOURS -> QueueEnd.YOURS
        AutoplaySource.MIXED -> QueueEnd.MIXED
        AutoplaySource.YOUTUBE -> QueueEnd.YOUTUBE
    }

    /** Autoplay on, and its source (null = leave the source as it was, so Stop then back keeps the old source). */
    fun queueEndValues(q: QueueEnd): Pair<Boolean, AutoplaySource?> = when (q) {
        QueueEnd.STOP -> false to null
        QueueEnd.YOURS -> true to AutoplaySource.YOURS
        QueueEnd.MIXED -> true to AutoplaySource.MIXED
        QueueEnd.YOUTUBE -> true to AutoplaySource.YOUTUBE
    }

    fun setQueueEnd(prefs: SettingsRepository, q: QueueEnd) {
        val (on, source) = queueEndValues(q)
        source?.let { prefs.setAutoplaySource(it) }
        prefs.setAutoplay(on)
    }

    fun videoPolicy(s: AppSettings): VideoPolicy = when {
        !s.radioSongsOnly -> VideoPolicy.PLAY
        s.songsEverywhere -> VideoPolicy.EVERYWHERE
        else -> VideoPolicy.RADIOS
    }

    /** radioSongsOnly, songsEverywhere. */
    fun videoPolicyValues(v: VideoPolicy): Pair<Boolean, Boolean> = when (v) {
        VideoPolicy.PLAY -> false to false
        VideoPolicy.RADIOS -> true to false
        VideoPolicy.EVERYWHERE -> true to true
    }

    fun setVideoPolicy(prefs: SettingsRepository, v: VideoPolicy) {
        val (radios, everywhere) = videoPolicyValues(v)
        prefs.setRadioSongsOnly(radios)
        prefs.setSongsEverywhere(everywhere)
    }

    /** Automix counts first: it implies an automatic length, whatever `mix_auto` says. */
    fun crossfade(s: AppSettings): Crossfade = when {
        !s.mix.on -> Crossfade.OFF
        s.mix.automix -> Crossfade.AUTOMIX
        s.mix.auto -> Crossfade.AUTO
        else -> Crossfade.FIXED
    }

    /**
     * mix, mixAuto, mixAutomix (null = leave as it was: Off keeps whether it was automatic and whether it was Automix, for
     * when it's turned back on). Any other choice turns Automix off.
     */
    fun crossfadeValues(x: Crossfade): CrossfadeValues = when (x) {
        Crossfade.OFF -> CrossfadeValues(false, null, null)
        Crossfade.AUTO -> CrossfadeValues(true, true, false)
        Crossfade.FIXED -> CrossfadeValues(true, false, false)
        Crossfade.AUTOMIX -> CrossfadeValues(true, true, true)
    }

    fun setCrossfade(prefs: SettingsRepository, x: Crossfade) {
        val v = crossfadeValues(x)
        v.auto?.let { prefs.setMixAuto(it) }
        v.automix?.let { prefs.setMixAutomix(it) }
        prefs.setMix(v.on)
    }

    /** "Hear it on the next song" seeks this far before the coming transition. */
    const val HEAR_IT_LEAD_MS = 12_000L

    /**
     * Where "Hear it on the next song" seeks to: [HEAR_IT_LEAD_MS] before the coming transition [mark] (the engine's
     * [com.prism.music.playback.MixState.mark]), when it belongs to the song playing ([songId]) and Crossfade is on; else
     * null (nothing is coming, and the button is greyed). Never before the song's start.
     */
    fun hearItAt(s: AppSettings, mark: com.prism.music.playback.BlendMark?, songId: String?): Long? {
        if (!s.mix.on || mark == null || songId == null || mark.songId != songId || mark.startMs <= 0) return null
        return (mark.startMs - HEAR_IT_LEAD_MS).coerceAtLeast(0)
    }

    /**
     * #325: the two settings say it as they're stored. Songs on with podcasts off is "Songs only" again, as it was picked
     * with 1.7.8's two switches (1.8.0's one choice had no such place, and read and played it as both).
     */
    fun trimSilence(s: AppSettings): TrimSilence = when {
        s.skipSilence && s.sound.skipSilenceEpisodes -> TrimSilence.ALL
        s.skipSilence -> TrimSilence.SONGS
        s.sound.skipSilenceEpisodes -> TrimSilence.PODCASTS
        else -> TrimSilence.OFF
    }

    /** skipSilence (songs), skipSilenceEpisodes. */
    fun trimSilenceValues(t: TrimSilence): Pair<Boolean, Boolean> = when (t) {
        TrimSilence.OFF -> false to false
        TrimSilence.SONGS -> true to false
        TrimSilence.PODCASTS -> false to true
        TrimSilence.ALL -> true to true
    }

    fun setTrimSilence(prefs: SettingsRepository, t: TrimSilence) {
        val (songs, episodes) = trimSilenceValues(t)
        prefs.setSkipSilence(songs)
        prefs.setSkipSilenceEpisodes(episodes)
    }
}
