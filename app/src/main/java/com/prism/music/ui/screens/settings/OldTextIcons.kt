package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.PrismSlider
import com.prism.music.ui.theme.PrismIcons
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.Corners
import com.prism.music.data.prefs.FontChoice
import com.prism.music.ui.theme.AppFonts
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.LocalUi
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.ChoiceCards
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.InstalledFontsSheet
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.Note
import com.prism.music.ui.screens.SliderSetting
import com.prism.music.ui.screens.Toggle
import com.prism.music.ui.screens.WholeWords

@Composable
internal fun TypeSettings() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    // A font file from the phone (#12), copied into Prism: for the whole app, or only for the lyrics.
    fun importer(slot: AppFonts.FontSlot, done: (String) -> Unit) = { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { AppFonts.import(context, uri, slot) } }
            r.onSuccess { error = null; done(it) }.onFailure { error = it.message ?: tr(R.string.that_font_couldn_t_be_used) }
        }
        Unit
    }
    val fontTypes = arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/vnd.ms-opentype", "application/octet-stream")
    val pickApp = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        importer(AppFonts.FontSlot.APP) { name -> prefs.setAppFont(name); prefs.setFontChoice(FontChoice.CUSTOM) })
    val pickLyrics = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        importer(AppFonts.FontSlot.LYRICS) { name -> prefs.setLyricsFontFile(name); prefs.setLyricsFont(FontChoice.CUSTOM.name) })
    val hasAppFont = remember(s.fontStamp) { AppFonts.customFile(AppFonts.FontSlot.APP) != null }
    val hasLyricsFont = remember(s.fontStamp) { AppFonts.customFile(AppFonts.FontSlot.LYRICS) != null }
    Group(tr(R.string.text)) {
        Label(tr(R.string.font))
        FontPicker(s.fontChoice, customName = s.appFontName.takeIf { hasAppFont }, stamp = s.fontStamp) { f ->
            if (f == FontChoice.CUSTOM && !hasAppFont) pickApp.launch(fontTypes) else prefs.setFontChoice(f)
        }
        Item(
            if (hasAppFont) tr(R.string.your_font, s.appFontName) else tr(R.string.use_a_font_from_your_phone),
            if (hasAppFont) tr(R.string.tap_to_pick_a_different_ttf) else tr(R.string.pick_a_ttf_or_otf_file),
            onClick = { pickApp.launch(fontTypes) },
        ) { Chevron() }
        // #318 (1.8.1): a font installed on the phone, without a file to pick (Android 10 and later list them).
        var installedFor by remember { mutableStateOf<AppFonts.FontSlot?>(null) }
        installedFor?.let { slot ->
            InstalledFontsSheet(slot, onPicked = { name ->
                if (slot == AppFonts.FontSlot.APP) { prefs.setAppFont(name); prefs.setFontChoice(FontChoice.CUSTOM) }
                else { prefs.setLyricsFontFile(name); prefs.setLyricsFont(FontChoice.CUSTOM.name) }
            }) { installedFor = null }
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) Item(
            tr(R.string.installed_fonts), tr(R.string.installed_fonts_detail), info = tr(R.string.installed_fonts_info),
            onClick = { installedFor = AppFonts.FontSlot.APP },
        ) { Chevron() }
        if (hasAppFont) Item(tr(R.string.remove_your_font), tr(R.string.back_to_a_built_in_font), onClick = {
            AppFonts.remove(AppFonts.FontSlot.APP)
            if (s.fontChoice == FontChoice.CUSTOM) prefs.setFontChoice(FontChoice.PRISM)
            prefs.setAppFont("")
        })
        Label(tr(R.string.lyrics_font))
        // "App font" keeps each lyric view's own face (the app's); the rest are the same fonts as above, or a file just for lyrics.
        val lyricOptions = listOf("") + FontChoice.entries.filter { it != FontChoice.CUSTOM && AppFonts.available(it) }.map { it.name } + FontChoice.CUSTOM.name
        Chips(lyricOptions, s.lyricsFont, { v ->
            when (v) {
                "" -> tr(R.string.app_font)
                FontChoice.CUSTOM.name -> if (hasLyricsFont) s.lyricsFontName.ifBlank { tr(R.string.your_font_2) } else tr(R.string.from_a_file)
                else -> FontChoice.valueOf(v).label
            }
        }) { v -> if (v == FontChoice.CUSTOM.name && !hasLyricsFont) pickLyrics.launch(fontTypes) else prefs.setLyricsFont(v) }
        if (android.os.Build.VERSION.SDK_INT >= 29) Item(
            tr(R.string.installed_fonts_for_lyrics), tr(R.string.installed_fonts_detail),
            onClick = { installedFor = AppFonts.FontSlot.LYRICS },
        ) { Chevron() }
        if (hasLyricsFont) {
            Item(tr(R.string.lyrics_font_file_2, s.lyricsFontName), tr(R.string.tap_to_pick_a_different_file), onClick = { pickLyrics.launch(fontTypes) }) { Chevron() }
            Item(tr(R.string.remove_the_lyrics_font_file), null, onClick = {
                AppFonts.remove(AppFonts.FontSlot.LYRICS)
                if (s.lyricsFont == FontChoice.CUSTOM.name) prefs.setLyricsFont("")
                prefs.setLyricsFontFile("")
            })
        }
        error?.let { Note(it) }
        SliderSetting(tr(R.string.text_size), s.textScale, 0.85f..1.25f, { "${(it * 100).roundToInt()}%" }, steps = 7, info = tr(R.string.on_top_of_your_phone_s)) { prefs.setTextScale(it) }
    }
    TypeFeelGroup()
}

/** Weight, letter spacing and the titles' own font (1.7.7), each shown on a page title and a line of text. */
@Composable
private fun TypeFeelGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val scheme = MaterialTheme.colorScheme
    @Composable
    fun Sample(t: com.prism.music.data.prefs.TypeLook) {
        val type = remember(s.fontChoice, s.fontStamp, t) { com.prism.music.ui.theme.appTypography(s.copy(type = t)) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(tr(R.string.songs), style = type.headlineSmall, color = scheme.onSurface, maxLines = 1)
            Text(tr(R.string.played_today), style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
    // Classic type (1.7.8, #155): Prism 1.6's bold headings, or today's light ones.
    Group(tr(R.string.type), info = tr(R.string.prism_1_7_writes_big_titles)) {
        ChoiceCards(listOf(false, true), s.type.classic, title = { if (it) tr(R.string.classic) else tr(R.string.app_name) }, detail = { if (it) tr(R.string.bold_headings_as_in_1_6) else tr(R.string.light_titles_as_now) }, onSelect = { prefs.setTypeClassic(it) }) {
            Sample(s.type.copy(classic = it))
        }
    }
    Group(tr(R.string.weight_and_spacing), info = tr(R.string.how_heavy_all_of_prism_s)) {
        ChoiceCards(com.prism.music.data.prefs.TypeWeight.entries, s.type.weight, title = { it.label }, detail = { null }, columns = 3, onSelect = { prefs.setTypeWeight(it) }) {
            Sample(s.type.copy(weight = it))
        }
        Label(tr(R.string.letter_spacing))
        ChoiceCards(com.prism.music.data.prefs.TypeSpacing.entries, s.type.spacing, title = { it.label }, detail = { null }, columns = 3, onSelect = { prefs.setTypeSpacing(it) }) {
            Sample(s.type.copy(spacing = it))
        }
    }
    Group(tr(R.string.titles), info = tr(R.string.page_section_and_album_titles_can)) {
        val options: List<FontChoice?> = listOf(null) + FontChoice.entries.filter { it != FontChoice.CUSTOM && it != s.fontChoice }
        ChoiceCards(options, s.type.titleFont, title = { it?.label ?: tr(R.string.same_as_the_text) }, detail = { it?.face }, columns = 2, onSelect = { prefs.setTitleFont(it) }) {
            Sample(s.type.copy(titleFont = it))
        }
    }
}

/**
 * Icon weight and ends (#19). Prism's icons are built as it starts, so a change is saved for the next start and a
 * Restart button appears; [com.prism.music.ui.theme.IconStyle] holds what's drawn now.
 */
@Composable
internal fun IconSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val drawn = com.prism.music.ui.theme.IconStyle.weight to com.prism.music.ui.theme.IconStyle.rounded
    var saved by remember { mutableStateOf(com.prism.music.ui.theme.IconStyle.saved(context)) }
    val looks = LocalContainer.current.looks
    fun save(v: Pair<com.prism.music.ui.theme.IconWeight, Boolean>) {
        saved = v
        com.prism.music.ui.theme.IconStyle.save(context, v.first, v.second)
        looks.iconsChanged()
    }
    var pack by remember { mutableStateOf(com.prism.music.ui.theme.IconStyle.savedPack(context)) }
    var likeStar by remember { mutableStateOf(com.prism.music.ui.theme.IconStyle.savedLikeStar(context)) }
    var fill by remember { mutableStateOf(com.prism.music.ui.theme.IconStyle.savedFill(context)) }
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Group(tr(R.string.icons)) {
        // Fill (1.7.7): each style drawn on four of Prism's icons, as they'd look.
        ChoiceCards(
            com.prism.music.ui.theme.IconFill.entries, fill, title = { it.label }, detail = { it.detail }, columns = 3,
            onSelect = {
                fill = it
                com.prism.music.ui.theme.IconStyle.saveFill(context, it)
                looks.iconsChanged()
            },
        ) { f ->
            // Three: a fourth was squeezed to a dot in a third of a phone's width.
            // The drawings' own names (design/icons), never words on the screen: translated, "Inicio" had no drawing and
            // the page closed Prism in every language but English (#255).
            val icons = remember(f) { listOf("Home", "Library", "Heart").map { com.prism.music.ui.theme.PrismIcons.preview(it, f) } }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                icons.forEach { Icon(it, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
        // "The tab you're on" is with the tab bar now (1.8.0: NavBarGroup).
        // Packs (1.7.7 adds Facet and Dots): each card draws the same four icons in its pack.
        Label(tr(R.string.pack))
        ChoiceCards(
            com.prism.music.ui.theme.IconPack.entries, pack, title = { it.label }, detail = { it.detail },
            onSelect = {
                pack = it
                com.prism.music.ui.theme.IconStyle.savePack(context, it)
                looks.iconsChanged()
            },
        ) { p ->
            // Cupertino is drawn by hand for its own icons; the others come from the drawings.
            val icons = remember(p) {
                if (p == com.prism.music.ui.theme.IconPack.CUPERTINO) com.prism.music.ui.theme.PrismIcons.cupertinoPreview()
                // The drawings' own names, never translated words (#255).
                else listOf("Home", "Search", "Heart", "Play").map { com.prism.music.ui.theme.PrismIcons.preview(it, com.prism.music.ui.theme.IconFill.OUTLINE, p) }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                icons.forEach { Icon(it, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
        Label(tr(R.string.weight))
        Chips(com.prism.music.ui.theme.IconWeight.entries, saved.first, { it.label }) { save(it to saved.second) }
        Toggle(tr(R.string.rounded), tr(R.string.round_ends_and_softer_corners), saved.second) { save(saved.first to it) }
        Toggle(tr(R.string.likes_as_stars), tr(R.string.a_star_instead_of_a_heart), likeStar) {
            likeStar = it
            com.prism.music.ui.theme.IconStyle.saveLikeStar(context, it)
            looks.iconsChanged()
        }
        if (saved != drawn || pack != com.prism.music.ui.theme.IconStyle.pack || likeStar != com.prism.music.ui.theme.IconStyle.likeStar ||
            fill != com.prism.music.ui.theme.IconStyle.fill) {
            Item(tr(R.string.restart_prism_to_see_them), tr(R.string.playback_stops_for_a_moment), onClick = {
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launch != null) {
                    context.startActivity(android.content.Intent.makeRestartActivityTask(launch.component).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    Runtime.getRuntime().exit(0)
                }
            }) { Chevron() }
        }
    }
}

/** The launcher icon (#20), each shown as its own two layers in a rounded square. */
@Composable
internal fun AppIconSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var current by remember { mutableStateOf(com.prism.music.ui.theme.AppIcons.current(context)) }
    Group(tr(R.string.app_icon), info = tr(R.string.your_launcher_may_take_a_moment)) {
        // An even grid across the page (#194: fixed 72 dp cells left an empty column at the side with larger text, and
        // cut "Spectrum" short): as many columns as fit, names made smaller rather than cut.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            val cell = 76.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns = ((maxWidth + 8.dp) / (cell + 8.dp)).toInt().coerceIn(3, 6)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.prism.music.ui.theme.AppIcon.entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { icon ->
                            val on = icon == current
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable {
                                    if (!on) { com.prism.music.ui.theme.AppIcons.set(context, icon); current = icon }
                                }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(60.dp).then(if (on) Modifier.border(2.dp, com.prism.music.ui.theme.PrismTokens.spectrum, RoundedCornerShape(18.dp)) else Modifier)
                                        .padding(if (on) 4.dp else 0.dp).clip(RoundedCornerShape(15.dp)),
                                ) {
                                    // An adaptive icon's layers are 108 units with the visible 72 in the middle: draw them 1.5× and crop.
                                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon.background), null, Modifier.fillMaxSize().scale(1.5f))
                                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon.foreground), null, Modifier.fillMaxSize().scale(1.5f))
                                }
                                Spacer(Modifier.height(4.dp))
                                WholeWords(icon.label, MaterialTheme.typography.labelMedium, if (on) scheme.onSurface else scheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        // The last row keeps the others' column widths.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontPicker(current: FontChoice, customName: String?, stamp: Long, onPick: (FontChoice) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ui = LocalUi.current
    // "Your font" is always pickable: without a file yet, picking it asks for one.
    val missing = remember(stamp) { FontChoice.entries.filterNot { it == FontChoice.CUSTOM || AppFonts.available(it) } }
    // An even grid across the page (#117: fixed-width cards left a gap at the side and came out at different heights
    // with larger text): as many columns as fit at ~100 dp, every card in a row as tall as the tallest.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        // Wider with larger text, so names like "Geometric" still fit.
        val card = 100.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columns = ((maxWidth + 8.dp) / (card + 8.dp)).toInt().coerceIn(2, 5)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FontChoice.entries.chunked(columns).forEach { rowFonts ->
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowFonts.forEach { f ->
                        val on = f == current
                        val usable = f !in missing
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clip(ui.tile)
                                .background(com.prism.music.ui.components.glassFill(if (on) 2.6f else 1f))
                                .then(if (on) Modifier.border(1.5.dp, com.prism.music.ui.theme.PrismTokens.spectrum, ui.tile) else Modifier)
                                .clickable(enabled = usable) { onPick(f) }
                                .then(if (usable) Modifier else Modifier.alpha(0.38f))
                                .padding(10.dp),
                        ) {
                            Text(tr(R.string.aa), style = TextStyle(fontFamily = AppFonts.family(f), fontWeight = FontWeight.Medium, fontSize = MaterialTheme.typography.headlineSmall.fontSize), color = scheme.onSurface)
                            // "Dyslexia-friendly" in OpenDyslexic's wide letters: "friendly" alone is wider than the card,
                            // so Android split the word. The name gets smaller until no word is split.
                            WholeWords(
                                if (f == FontChoice.CUSTOM) customName ?: tr(R.string.your_font_3) else f.label,
                                MaterialTheme.typography.labelMedium.copy(fontFamily = AppFonts.family(f)),
                                if (on) scheme.onSurface else scheme.onSurfaceVariant,
                            )
                            // The typeface's own name, quietly, so it can be looked up (and matched to a file for Your font).
                            f.face?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant.copy(alpha = 0.75f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                    repeat(columns - rowFonts.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun ShapeSettings() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val scheme = MaterialTheme.colorScheme
    Group(tr(R.string.shape)) {
        // Corners as a slider (1.7.7): the old four steps are its marks, and a Look that names a step still works.
        val corner = s.shapes.cornerScale ?: s.corners.scale
        SliderSetting(
            tr(R.string.corners), corner, 0f..2f,
            { v -> Corners.entries.minByOrNull { kotlin.math.abs(it.scale - v) }!!.let { if (kotlin.math.abs(it.scale - v) < 0.08f) it.label else "${(v * 100).roundToInt()}%" } },
        ) { v ->
            // Close to a step: that step, so it reads by name.
            Corners.entries.firstOrNull { kotlin.math.abs(it.scale - v) < 0.06f }?.let { prefs.setCorners(it) } ?: prefs.setCornerScale(v)
        }
        // A peek at what the corners look like.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ui = LocalUi.current
            listOf(scheme.primary, scheme.tertiary, scheme.secondary).forEach { col ->
                Box(Modifier.weight(1f).aspectRatio(1.3f).clip(ui.tile).background(Brush.linearGradient(listOf(col, col.copy(alpha = 0.55f)))))
            }
        }
    }
    CoverShapeGroup()
    ButtonShapeGroup()
}

/** Covers' shapes (1.7.7): which kind of cover, then each shape drawn as a cover would be. */
@Composable
private fun CoverShapeGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    var which by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("songs") }
    val kinds = listOf("songs" to tr(R.string.songs), "albums" to tr(R.string.albums), "artists" to tr(R.string.artists), "player" to tr(R.string.player))
    val now = when (which) { "albums" -> s.shapes.albums; "artists" -> s.shapes.artists; "player" -> s.shapes.player; else -> s.shapes.songs }
    Group(tr(R.string.covers_shapes), info = tr(R.string.each_kind_of_picture_can_have)) {
        com.prism.music.ui.components.Segmented(
            kinds.map { it.first }, which, { k -> kinds.first { it.first == k }.second }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) { which = it }
        ChoiceCards(
            com.prism.music.data.prefs.CoverShape.entries, now, title = { it.label }, detail = { null }, columns = 4,
            onSelect = { prefs.setCoverShape(which, it) },
        ) { shape -> CoverShapeSample(shape, round = which == "artists") }
        // 1.8.0 (from Player → Cover): it squares video pictures everywhere, not only on the player (#92).
        Toggle(
            tr(R.string.square_video_covers), tr(R.string.no_black_bars_when_a_video), s.squareVideoCovers,
            info = tr(R.string.youtube_s_video_pictures_are_wide),
        ) { prefs.setSquareVideoCovers(it) }
    }
}

@Composable
private fun CoverShapeSample(shape: com.prism.music.data.prefs.CoverShape, round: Boolean) {
    val ui = LocalUi.current
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth(0.82f).aspectRatio(1f)
                .clip(com.prism.music.ui.theme.CoverShapes.of(shape, ui.r(14.dp)))
                .background(Brush.linearGradient(listOf(scheme.primary, com.prism.music.ui.theme.PrismTokens.spectrumAt(if (round) 0.75f else 0.3f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(if (round) PrismIcons.Person else PrismIcons.Song, null, tint = Color.White.copy(alpha = 0.85f)) }
    }
}

/** Buttons' shape and the Play button (1.7.7). */
@Composable
private fun ButtonShapeGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val scheme = MaterialTheme.colorScheme
    Group(tr(R.string.buttons)) {
        ChoiceCards(
            com.prism.music.data.prefs.ButtonShape.entries, s.shapes.button, title = { it.label }, detail = { null }, columns = 3,
            onSelect = { prefs.setButtonShape(it) },
        ) { b ->
            val shape = LocalUi.current.copy(buttonShape = b).button
            Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(30.dp).clip(shape).background(scheme.onSurface), contentAlignment = Alignment.Center) {
                    Text(tr(R.string.widget_play), style = MaterialTheme.typography.labelMedium, color = scheme.surface)
                }
            }
        }
        Label(tr(R.string.play_on_album_and_playlist_pages))
        ChoiceCards(
            listOf(false, true), s.shapes.playOutlined, title = { if (it) tr(R.string.outlined) else tr(R.string.filled) },
            detail = { if (it) tr(R.string.just_its_outline) else tr(R.string.solid_with_a_glow) },
            onSelect = { prefs.setPlayOutlined(it) },
        ) { outlined ->
            val shape = LocalUi.current.button
            Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.fillMaxWidth().height(34.dp).clip(shape)
                        .then(if (outlined) Modifier.border(1.5.dp, scheme.onSurface, shape) else Modifier.background(scheme.onSurface)),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PrismIcons.Play, null, Modifier.size(16.dp), tint = if (outlined) scheme.onSurface else scheme.surface)
                    Spacer(Modifier.width(4.dp))
                    Text(tr(R.string.widget_play), style = MaterialTheme.typography.labelMedium, color = if (outlined) scheme.onSurface else scheme.surface)
                }
            }
        }
        // Classic controls (1.7.8, #155): each card draws a switch and a slider its way.
        Label(tr(R.string.switches_sliders_and_buttons), info = tr(R.string.prism_s_own_lit_with_your))
        ChoiceCards(listOf(false, true), s.shapes.classicControls, title = { if (it) tr(R.string.classic) else tr(R.string.app_name) }, detail = { if (it) tr(R.string.as_in_1_6) else tr(R.string.lit_with_your_light_as_now) }, onSelect = { prefs.setControlsClassic(it) }) { classic ->
            CompositionLocalProvider(LocalUi provides LocalUi.current.copy(classicControls = classic)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.prism.music.ui.components.PrismSlider(0.6f, {}, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    com.prism.music.ui.components.PrismSwitch(true, null)
                }
            }
        }
    }
}
