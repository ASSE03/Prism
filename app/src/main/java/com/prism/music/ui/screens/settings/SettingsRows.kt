package com.prism.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.ChoiceFlow
import com.prism.music.ui.components.PrismSlider
import com.prism.music.ui.components.PrismSwitch
import com.prism.music.ui.components.Segmented
import com.prism.music.ui.screens.InfoMark
import com.prism.music.ui.screens.InfoText
import com.prism.music.ui.screens.ShowChips
import com.prism.music.ui.screens.ShowOption
import com.prism.music.ui.theme.LocalUi
import com.prism.music.ui.theme.PrismIcons
import com.prism.music.ui.theme.PrismTokens
import com.prism.music.ui.theme.hairline

/**
 * The settings kit of 1.8.4 ("Settings, made simple"). A page is a [SettingsPageScaffold] of [SettingsGroup] cards, and a
 * card holds only the five row types: [SwitchRow], [PickRow], [SliderRow], [ChoiceRow] (and its many-at-once form,
 * [ShowChipsRow]) and [LinkRow].
 *
 * What every row does by itself: 64 dp tall with a value line and 56 dp without (it grows, never cuts text off, when
 * the text is large), 16 dp gutters, the title in titleMedium and the value in bodyMedium at 70%, an optional ⓘ
 * (`info`) that opens the explanation under the row, and a dot after the value when `changed` is true. Rows draw no
 * icon (calmer), except a [LinkRow] to another screen. Nothing in a row moves or blurs while it's at rest.
 *
 * For tests: each row carries one of [SettingsTags.rowTypes] as its test tag, its value in words as
 * [SettingsRowValue] and its `changed` flag as [SettingsRowChanged].
 */
object SettingsTags {
    const val ROW_SWITCH = "settings:row:switch"
    const val ROW_PICK = "settings:row:pick"
    const val ROW_SLIDER = "settings:row:slider"
    const val ROW_CHOICE = "settings:row:choice"
    const val ROW_LINK = "settings:row:link"

    /** The five row types, and nothing else. */
    val rowTypes = setOf(ROW_SWITCH, ROW_PICK, ROW_SLIDER, ROW_CHOICE, ROW_LINK)

    /** A [SettingsGroup] (its header and card), and the card alone (it carries [SettingsCardGlass]). */
    const val GROUP = "settings:group"
    const val GROUP_CARD = "settings:group:card"

    /** The dot after a changed row's value. */
    const val CHANGED_DOT = "settings:changed"

    const val PAGE_LIST = "settings:page:list"
    const val PAGE_TITLE = "settings:page:title"
    const val PAGE_SEARCH = "settings:page:search"
    const val PAGE_MORE = "settings:page:more"
    const val JUMP_CHIPS = "settings:jump"
    const val PREVIEW = "settings:preview"

    const val RESET_ITEM = "settings:reset"
    const val RESET_CHANGE = "settings:reset:change"
    const val RESET_CONFIRM = "settings:reset:confirm"

    const val SHEET = "settings:sheet"
    const val SHEET_OPTION = "settings:sheet:option"
    const val SHEET_SEARCH = "settings:sheet:search"
}

/** A row's value in words, as it shows it ("" for a switch or link without a value line). */
val SettingsRowValue = SemanticsPropertyKey<String>("settings_row_value")
var SemanticsPropertyReceiver.settingsRowValue by SettingsRowValue

/** Whether a row was given `changed = true` (it shows the dot). */
val SettingsRowChanged = SemanticsPropertyKey<Boolean>("settings_row_changed")
var SemanticsPropertyReceiver.settingsRowChanged by SettingsRowChanged

/** How far in from the card's start edge the hairline above a row begins ([SettingsGroup] reads it). */
internal data class HairlineInset(val start: Dp) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@HairlineInset
}

/** The small dot that marks a value that differs from how Prism comes. */
@Composable
fun ChangedDot(modifier: Modifier = Modifier) {
    Box(
        modifier.padding(start = 6.dp).size(6.dp).clip(CircleShape).background(PrismTokens.spectrumRing())
            .testTag(SettingsTags.CHANGED_DOT).semantics { contentDescription = tr(R.string.changed) },
    )
}

/** What the five rows share: the title and value lines, ⓘ, the dot, the heights, the tag. */
@Composable
private fun RowFrame(
    type: String,
    title: String,
    /** The line under the title, or null for none (the row is then 56 dp). */
    value: String?,
    /** The value for [SettingsRowValue] (what a chip or a slider shows isn't on a value line). */
    spoken: String,
    info: String?,
    changed: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    /** What a tap on the whole row does (clickable, toggleable), or nothing for rows with their own controls. */
    interaction: Modifier = Modifier,
    icon: ImageVector? = null,
    /** False where the row places the dot itself (a slider's, beside its value). */
    dot: Boolean = changed,
    /** A row with controls under its title line is as tall as they make it. */
    stacked: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val ui = LocalUi.current
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable(title, type) { mutableStateOf(false) }
    // The page-spacing setting still scales rows; never under a finger's reach.
    val min = if (stacked) 44.dp else ui.gap(if (value != null) 64.dp else 56.dp).coerceAtLeast(48.dp)
    Column(
        modifier.fillMaxWidth().then(HairlineInset(if (icon != null) 56.dp else 16.dp))
            .testTag(type).semantics { settingsRowValue = spoken; settingsRowChanged = changed }
            .then(interaction).then(if (enabled) Modifier else Modifier.alpha(0.38f)),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = min).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(24.dp), tint = scheme.onSurfaceVariant); Spacer(Modifier.width(16.dp)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // No line limit: at large text the title wraps and the row grows.
                    Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    if (dot && value == null) ChangedDot()
                    if (info != null) InfoMark(open, title) { open = !open }
                }
                if (value != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface.copy(alpha = 0.7f))
                    if (dot) ChangedDot()
                }
            }
            if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        }
        InfoText(open && info != null, info, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
        below?.invoke(this)
    }
}

/**
 * Row type 1, **Switch**: on or off; the whole row is the switch. [value] is an optional line under the title (the
 * state in words where on/off alone doesn't say it).
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    value: String? = null,
    info: String? = null,
    changed: Boolean = false,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    RowFrame(
        SettingsTags.ROW_SWITCH, title, value, value.orEmpty(), info, changed, enabled, modifier,
        interaction = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        trailing = { PrismSwitch(checked, null, enabled = enabled) },
    )
}

/**
 * Row type 2, **Pick**: the current [value] in words and a chevron; a tap opens the choices ([onClick]), normally a
 * [PickSheet] or [PickListSheet] ([PickCardsRow] and [PickListRow] do both parts). [thumbnail]: a small picture of the
 * current pick for looks, drawn in a 44 × 30 dp frame before the chevron.
 */
@Composable
fun PickRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    changed: Boolean = false,
    enabled: Boolean = true,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    RowFrame(
        SettingsTags.ROW_PICK, title, value, value, info, changed, enabled, modifier,
        interaction = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        trailing = {
            if (thumbnail != null) {
                val frame = RoundedCornerShape(8.dp)
                Box(Modifier.size(44.dp, 30.dp).clip(frame).border(1.dp, scheme.hairline, frame), contentAlignment = Alignment.Center) { thumbnail() }
                Spacer(Modifier.width(8.dp))
            }
            Icon(PrismIcons.ChevronRight, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        },
    )
}

/**
 * Row type 3, **Slider**: the title, the value as [valueText] writes it, and the slider under them. It follows the
 * finger and saves ([onSave]) when let go.
 */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    info: String? = null,
    changed: Boolean = false,
    enabled: Boolean = true,
    onSave: (Float) -> Unit,
) {
    var v by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { v = value }
    val shown = valueText(v)
    RowFrame(
        SettingsTags.ROW_SLIDER, title, null, shown, info, changed, enabled, modifier, dot = false, stacked = true,
        trailing = {
            Text(shown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            if (changed) ChangedDot()
        },
        below = {
            PrismSlider(
                v, { v = it }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp), enabled = enabled,
                valueRange = range, steps = steps, onValueChangeFinished = { onSave(v) },
            )
        },
    )
}

/**
 * Row type 4, **Choice chips**: one of two to four short words, picked in place under the title. For more or longer
 * choices, or ones that need an example to tell apart, use a Pick row.
 */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    info: String? = null,
    changed: Boolean = false,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    // One capsule while the words fit in it; at large text (or long words) pills that wrap, so no word is cut off.
    val large = LocalDensity.current.fontScale >= 1.3f
    val capsule = !large && options.size in 2..4 && options.all { label(it).length <= 12 } && options.sumOf { label(it).length } <= 30
    val pick: (T) -> Unit = { if (enabled) onSelect(it) }
    RowFrame(
        SettingsTags.ROW_CHOICE, title, null, label(selected), info, changed, enabled, modifier, stacked = true,
        below = {
            val m = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            if (capsule) Segmented(options, selected, label, m, pick) else ChoiceFlow(options, { it == selected }, label, m, pick)
        },
    )
}

/**
 * Row type 4, many at once (**Show chips**): several things to show or hide as chips that tick on and off, in place of
 * a run of switches ("In the queue: Show"). Each [ShowOption] carries its own state and setter, so the stored values
 * stay as they are.
 */
@Composable
fun ShowChipsRow(
    title: String,
    options: List<ShowOption>,
    modifier: Modifier = Modifier,
    info: String? = null,
    changed: Boolean = false,
) {
    RowFrame(
        SettingsTags.ROW_CHOICE, title, null, options.filter { it.on }.joinToString(" · ") { it.label }, info, changed, true, modifier, stacked = true,
        below = { ShowChips(options); Spacer(Modifier.heightIn(min = 4.dp)) },
    )
}

/**
 * Row type 5, **Link**: opens another screen (or, with [external], something outside Prism). The only row that may
 * carry an [icon]. [value]: an optional line under the title (the screen's state in words).
 */
@Composable
fun LinkRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    info: String? = null,
    changed: Boolean = false,
    enabled: Boolean = true,
    external: Boolean = false,
    onClick: () -> Unit,
) {
    RowFrame(
        SettingsTags.ROW_LINK, title, value, value.orEmpty(), info, changed, enabled, modifier,
        interaction = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick), icon = icon,
        trailing = {
            Icon(if (external) PrismIcons.OpenInNew else PrismIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}
