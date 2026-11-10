package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.theme.PrismIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.LyricsAnimationChoices
import com.prism.music.ui.screens.LyricsAnimationGroup
import com.prism.music.ui.screens.LyricsBackgroundGroup
import com.prism.music.ui.screens.LyricsEffectsGroup
import com.prism.music.ui.screens.LyricsExtrasGroup
import com.prism.music.ui.screens.LyricsOnThePlayerGroup
import com.prism.music.ui.screens.LyricsStyleGroup
import com.prism.music.ui.screens.LyricsTextGroup
import com.prism.music.ui.screens.MoreOptions
import com.prism.music.ui.screens.Note
import com.prism.music.ui.screens.SliderSetting
import com.prism.music.ui.screens.Toggle

// ---------------------------------------------------------------------------- Lyrics

/** Languages lyrics can be translated into (Google Translate codes); "" is the phone's own. */
private val TRANSLATE_LANGUAGES = listOf(
    "" to tr(R.string.phone_s_language), "en" to tr(R.string.english), "es" to tr(R.string.spanish), "pt" to tr(R.string.portuguese), "fr" to tr(R.string.french),
    "de" to tr(R.string.german), "it" to tr(R.string.italian), "nl" to tr(R.string.dutch), "pl" to tr(R.string.polish), "tr" to tr(R.string.turkish), "ru" to tr(R.string.russian),
    "uk" to tr(R.string.ukrainian), "ar" to tr(R.string.arabic), "hi" to tr(R.string.hindi), "id" to tr(R.string.indonesian), "vi" to tr(R.string.vietnamese),
    "tl" to tr(R.string.filipino), "ja" to tr(R.string.japanese), "ko" to tr(R.string.korean), tr(R.string.zh_cn) to tr(R.string.chinese_simplified), tr(R.string.zh_tw) to tr(R.string.chinese_traditional),
)

/** Kuromoji's dictionary, for Japanese lyrics' kanji: download, progress, or remove. */
@Composable
private fun JapaneseReadingData() {
    val c = LocalContainer.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val states by c.models.states.collectAsState()
    val m = com.prism.music.data.ModelStore.Model.JAPANESE
    val state = states[m]
    val subtitle = when (state) {
        com.prism.music.data.ModelStore.State.Ready -> tr(R.string.on_this_phone_tap_to_remove, formatMb(c.models.sizeOnPhone(m)))
        is com.prism.music.data.ModelStore.State.Downloading -> tr(R.string.downloading_5, (state.fraction * 100).toInt())
        is com.prism.music.data.ModelStore.State.Failed -> tr(R.string.couldn_t_download_it_tap_to)
        else -> tr(R.string.only_for_japanese_songs, com.prism.music.data.ModelStore.Model.sizeText(m))
    }
    Item(tr(R.string.japanese_kanji_readings), subtitle, info = tr(R.string.only_japanese_needs_this_its_kanji), onClick = {
        when (state) {
            com.prism.music.data.ModelStore.State.Ready -> c.models.delete(m)
            is com.prism.music.data.ModelStore.State.Downloading -> {}
            else -> scope.launch { c.models.download(m) }
        }
    })
}

// In the same (decimal) megabytes as the download sizes, so "74 MB on the phone" and the installed size agree.
private fun formatMb(bytes: Long) = "${bytes / 1_000_000} MB"

/** Settings → Lyrics → Sing: its download, mobile data, and the room its songs' separated vocals take. */
@Composable
private fun SingSettings() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val states by c.models.states.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { c.models.refresh() }
    val runtime = com.prism.music.data.ModelStore.Model.singRuntime()
    val model = com.prism.music.data.ModelStore.Model.SING_MODEL
    var used by remember { mutableStateOf(c.sing.store.bytes()) }
    Group(tr(R.string.sing)) {
        if (runtime == null) {
            Note(tr(R.string.sing_doesn_t_run_on_this))
            return@Group
        }
        val ready = states[runtime] == com.prism.music.data.ModelStore.State.Ready && states[model] == com.prism.music.data.ModelStore.State.Ready
        val downloading = listOf(runtime, model).mapNotNull { states[it] as? com.prism.music.data.ModelStore.State.Downloading }.firstOrNull()
        Item(
            tr(R.string.sing_s_voice_separation),
            when {
                ready -> tr(R.string.on_this_phone_tap_to_remove, formatMb(c.models.sizeOnPhone(runtime) + c.models.sizeOnPhone(model)))
                downloading != null -> tr(R.string.downloading_5, (downloading.fraction * 100).toInt())
                else -> tr(R.string.needed_for_sing, com.prism.music.data.ModelStore.Model.sizeText(runtime, model))
            },
            info = tr(R.string.takes_the_singer_s_voice_out),
            onClick = {
                if (ready) { c.sing.setOn(false); c.models.delete(runtime); c.models.delete(model) }
                else if (downloading == null) scope.launch { c.sing.downloadEngine() }
            },
        )
        Toggle(
            tr(R.string.get_songs_ready_while_sing_is), tr(R.string.so_sing_starts_at_once_when), s.singAlwaysReady,
            info = tr(R.string.the_playing_song_and_the_next),
        ) { c.settings.setSingAlwaysReady(it) }
        // One step past 4 GB is "Unlimited" (#238).
        val top = 4096f + 256f
        SliderSetting(
            tr(R.string.room_for_sung_songs), s.singCacheMb.toFloat().coerceAtMost(top), 256f..top,
            { if (it >= top - 1f) tr(R.string.unlimited) else "${(it / 1024f).let { gb -> if (gb >= 1) "%.1f GB".format(gb) else "${it.roundToInt()} MB" }}" }, steps = 15,
        ) {
            c.settings.setSingCacheMb(if (it >= top - 1f) com.prism.music.data.prefs.SING_ROOM_UNLIMITED else it.roundToInt())
        }
        Item(tr(R.string.clear_sung_songs), tr(R.string.using, formatMb(used)), info = tr(R.string.songs_are_worked_out_again_the), onClick = { c.sing.store.clear(); used = 0 })
    }
}

/** The lyrics' animation; [title] names the group where "Animation" alone wouldn't say whose (the player's Customize sheet). */
@Composable
internal fun LyricsLookGroup(title: String = tr(R.string.animation)) {
    // Lyrics in parts (1.8.0, #253): LyricsYourWay.kt.
    Group(title, info = tr(R.string.animation_info)) { LyricsAnimationChoices() }
}

/** Customize Prism → Lyrics (1.8.0, #253): where you start from, then the four parts; the line on the player folded away. */
@Composable
internal fun LyricsLookPage() {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        LyricsStyleGroup()
        LyricsAnimationGroup()
        LyricsTextGroup()
        LyricsBackgroundGroup()
        LyricsEffectsGroup()
        MoreOptions(listOf(tr(R.string.translations_romanized_lines), tr(R.string.on_the_player))) {
            LyricsExtrasGroup()
            LyricsOnThePlayerGroup()
        }
    }
}

/** Settings → Lyrics & Sing: where lyrics come from, their language, Sing, and your files (their look is in Customize). */
@Composable
internal fun LyricsSettings() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group(tr(R.string.sources)) {
            Toggle(tr(R.string.prefer_karaoke_synced_lyrics), tr(R.string.word_by_word_first_then_by), s.preferSynced, info = tr(R.string.karaoke_lyrics_lit_up_word_by)) { prefs.setPreferSynced(it) }
            Toggle(tr(R.string.uncensor_lyrics), tr(R.string.fills_in_starred_out_words), s.skipCensored, info = tr(R.string.swear_words_starred_out_f_k)) { prefs.setSkipCensored(it) }
            LyricSourcesOrder()
            // 1.8.0: from the lyrics look page (it's about sources, not looks).
            Toggle(tr(R.string.lyrics_source_button), tr(R.string.lyrics_source_button_info), s.lyricsSourceButton) { prefs.setLyricsSourceButton(it) }
        }
        Group(tr(R.string.language)) {
            // 1.8.0: the switch is the first choice, Off (same stored settings: lyricsTranslate + lyricsTranslateTo).
            Label(tr(R.string.translate_into), info = tr(R.string.for_songs_in_another_language_machine))
            val off = "\u0000off"
            Chips(
                listOf(off) + TRANSLATE_LANGUAGES.map { it.first },
                if (!s.lyricsTranslate) off else s.lyricsTranslateTo.takeIf { code -> TRANSLATE_LANGUAGES.any { it.first == code } } ?: "",
                { code -> if (code == off) tr(R.string.off) else TRANSLATE_LANGUAGES.first { it.first == code }.second },
            ) { code -> if (code == off) prefs.setLyricsTranslate(false) else { prefs.setLyricsTranslateTo(code); prefs.setLyricsTranslate(true) } }
            Label(tr(R.string.romanize_lyrics), info = tr(R.string.other_scripts_in_latin_letters_to))
            Chips(com.prism.music.data.prefs.RomanizeMode.entries, s.romanize, { it.label }) { prefs.setRomanize(it) }
            if (s.romanize != com.prism.music.data.prefs.RomanizeMode.OFF) Toggle(
                tr(R.string.romanize_my_own_language_too), tr(R.string.lyrics_too, java.util.Locale.getDefault().displayLanguage), s.romanizeAll,
                info = tr(R.string.korean_japanese_russian_and_greek_are),
            ) { prefs.setRomanizeAll(it) }
            JapaneseReadingData()
        }
        SingSettings()
        // 1.8.0: rarely used, so folded away.
        MoreOptions(listOf(tr(R.string.your_music_files))) {
            Group(tr(R.string.your_music_files)) {
                val c = LocalContainer.current
                // #344 (1.8.2): the same switch as its row in Order above.
                Toggle(
                    tr(R.string.read_lyrics_from_your_music_files), tr(R.string.synced_or_plain_with_no_internet), s.behavior.embeddedLyrics,
                    info = tr(R.string.flac_ogg_opus_mp3_and_m4a_their),
                    // And its guard: one source stays on.
                    enabled = !s.behavior.embeddedLyrics || s.lyricsOrder.count(s::lyricsSourceOn) > 1,
                ) { prefs.setEmbeddedLyrics(it) }
                Item(
                    tr(R.string.save_lyrics_into_your_music_files), tr(R.string.so_other_players_show_them_too),
                    info = tr(R.string.synced_lyrics_go_into_your_flac),
                    onClick = { c.lyricsToFiles.request(null) },
                )
                // Reddit, 1.7.9.2: what goes in with them.
                Toggle(
                    tr(R.string.keep_word_timings), tr(R.string.karaoke_lyrics_light_word_by_word), s.lyricsFileWords,
                    info = tr(R.string.saved_as_enhanced_lrc_players),
                ) { prefs.setLyricsFileWords(it) }
                Toggle(
                    tr(R.string.include_the_translation), tr(R.string.under_each_line_in_your_translation_language), s.lyricsFileTranslation,
                ) { prefs.setLyricsFileTranslation(it) }
            }
        }
    }
}

/** The lyric sources in the order they're asked, each with its switch (#51); hold one to move it. */
@Composable
private fun LyricSourcesOrder() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Label(tr(R.string.order), info = tr(R.string.tried_top_to_bottom_hold_one))
    com.prism.music.ui.components.ReorderList(s.lyricsOrder, { it }, { prefs.setLyricsOrder(it) }) { src, i, held, mod ->
        val on = s.lyricsSourceOn(src)
        Row(mod.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (i == 0 && on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", color = if (i == 0 && on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).alpha(if (on) 1f else 0.45f)) {
                Text(src.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(src.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Turned off, a source is never asked (#51); at least one stays on.
            com.prism.music.ui.components.PrismSwitch(
                on, { v -> if (src == com.prism.music.data.prefs.LyricsSource.EMBEDDED) prefs.setEmbeddedLyrics(v) else prefs.setLyricsOff(if (v) s.lyricsOff - src else s.lyricsOff + src) },
                enabled = !on || s.lyricsOrder.count(s::lyricsSourceOn) > 1,
                modifier = Modifier.scale(0.8f),
            )
            if (!held) {
                IconButton(onClick = { prefs.setLyricsOrder(s.lyricsOrder.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                    Icon(PrismIcons.ChevronUp, tr(R.string.move_up_2, src.label))
                }
                IconButton(onClick = { prefs.setLyricsOrder(s.lyricsOrder.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < s.lyricsOrder.lastIndex) {
                    Icon(PrismIcons.ChevronDown, tr(R.string.move_down_2, src.label))
                }
            } else Spacer(Modifier.width(96.dp))
            com.prism.music.ui.components.DragGrip(Modifier.dragHandle().padding(start = 2.dp), tr(R.string.drag, src.label))
        }
    }
}
