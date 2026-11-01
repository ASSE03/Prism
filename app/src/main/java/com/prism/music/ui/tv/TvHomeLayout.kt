package com.prism.music.ui.tv

import com.prism.music.data.prefs.HomeSectionConfig
import com.prism.music.data.prefs.HomeSections

/*
 * Customizing the TV's Home (1.8.0; the owner: "You should be able to customize the homescreen"): showing, hiding and
 * moving its rows (Settings → Home → Customize Home, or the button at the end of Home). Kept in two places, so the
 * phone and the TV agree where they can:
 * - Which rows show: the rows both have (Quick picks, Speed dial, Recently played, YouTube's shelves, named by the
 *   phone's keys) in the phone's own Home layout ([com.prism.music.data.prefs.AppSettings.homeLayout]): a row hidden on
 *   one is hidden on the other. The TV's own now-playing hero in the TV's layout
 *   ([com.prism.music.data.prefs.TvPrefs.homeLayout]).
 * - Their order: the TV's layout (it has the hero, and the phone lays out sections the TV doesn't have). None kept, the
 *   rows both have follow the phone's order where it names them and stay where Home puts them otherwise, so an
 *   untouched phone layout is the TV's Home as it was.
 * Back to default: no TV layout, and every TV row shown again.
 */

/** A TV row's key in the phone's Home layout (the TV's rows use the phone's keys; "yt:<title>" is the older form). */
fun phoneKeyOf(tvKey: String): String = when {
    tvKey.startsWith(HomeSections.YT_PREFIX) -> tvKey
    tvKey.startsWith("yt:") -> HomeSections.shelfKey(tvKey.removePrefix("yt:"))
    else -> tvKey
}

/** Whether [layout] hides the row [tvKey]. */
fun hiddenIn(layout: List<HomeSectionConfig>, tvKey: String): Boolean = layout.firstOrNull { it.key == phoneKeyOf(tvKey) }?.visible == false

/**
 * [rows] as [layout] arranges them: those it names, in its order, in the places those rows had; the others where they
 * were. Hidden rows are kept ([hiddenIn] tells them apart).
 */
fun <R> arrangeTvRows(rows: List<R>, layout: List<HomeSectionConfig>, key: (R) -> String): List<R> {
    val order = layout.withIndex().associate { (i, s) -> s.key to i }
    val named = rows.withIndex().filter { (_, r) -> phoneKeyOf(key(r)) in order }
    if (named.size < 2) return rows
    val sorted = named.map { it.value }.sortedBy { order.getValue(phoneKeyOf(key(it))) }
    val out = rows.toMutableList()
    named.forEachIndexed { n, (slot, _) -> out[slot] = sorted[n] }
    return out
}

/** [layout] with the row [tvKey] shown or hidden ([shown]); a row it doesn't name yet is added (after the others). */
fun layoutWithShown(layout: List<HomeSectionConfig>, tvKey: String, title: String, shown: Boolean): List<HomeSectionConfig> {
    val k = phoneKeyOf(tvKey)
    return if (layout.any { it.key == k }) layout.map { if (it.key == k) it.copy(visible = shown) else it }
    else layout + HomeSectionConfig(k, title, visible = shown)
}

/** [order] with the item at [from] moved [by] places (−1 up, +1 down), held within the list. */
fun <T> moved(order: List<T>, from: Int, by: Int): List<T> {
    if (from !in order.indices) return order
    val to = (from + by).coerceIn(0, order.lastIndex)
    if (to == from) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/** The TV Home as laid out: its [rows] in order, and where the now-playing hero goes among them (-1: hidden). */
data class TvHomeArranged(val rows: List<TvHomeRow>, val heroAt: Int)

/**
 * The TV's own Home layout: a list of the phone's [HomeSectionConfig]s in the TV's order, with [HERO] for the
 * now-playing hero (its `visible` is the hero's; the other rows' are the phone layout's, see the file's note). A row the
 * layout doesn't know yet (a new shelf from YouTube) shows after the row it follows on Home.
 */
object TvHomeLayout {
    /** The TV's now-playing hero (the phone has no such section). */
    const val HERO = "tv:now_playing"
    /** The rows Prism makes itself, in Home's own order (YouTube's shelves follow them). */
    val FIXED = listOf(HERO, KEY_PICKS, KEY_DIAL, KEY_RECENT)
    /** How many rows a layout keeps. */
    private const val MAX_KEPT = 80

    /** Whether [key] is one of the TV Home's kinds of row (not a phone-only section). */
    fun isTvRow(key: String) = key in FIXED || key.startsWith(HomeSections.YT_PREFIX)

    private fun heroShown(stored: List<HomeSectionConfig>) = stored.firstOrNull { it.key == HERO }?.visible != false

    /** [rows] (from [tvHomeRows]) as the TV's [stored] layout and the [phone]'s Home layout lay them out. */
    fun arrange(stored: List<HomeSectionConfig>, rows: List<TvHomeRow>, phone: List<HomeSectionConfig> = emptyList()): TvHomeArranged {
        val keys = if (stored.isEmpty()) listOf(HERO) + arrangeTvRows(rows, phone) { it.key }.map { it.key }.distinct()
        else merged(stored, natural(rows.map { HomeSectionConfig(it.key, it.title) })).map { it.key }
        val byKey = rows.groupBy { it.key }
        val out = ArrayList<TvHomeRow>()
        var heroAt = -1
        for (k in keys) {
            if (k == HERO) { if (heroShown(stored)) heroAt = out.size }
            else if (!hiddenIn(phone, k)) byKey[k]?.let { out += it }
        }
        return TvHomeArranged(out, heroAt)
    }

    /** Home's own order of the rows there are: the fixed ones (titled by [titles]), then [shelves] as they came. */
    fun natural(shelves: List<HomeSectionConfig>, titles: (String) -> String = { "" }): List<HomeSectionConfig> =
        (FIXED.map { HomeSectionConfig(it, titles(it)) } + shelves.filter { it.key !in FIXED }).distinctBy { it.key }

    /**
     * [stored] with each row of [natural] it doesn't have put in after the row before it in [natural], so a new shelf
     * lands where Home would have put it; rows it has take [natural]'s title (YouTube may recase it).
     */
    fun merged(stored: List<HomeSectionConfig>, natural: List<HomeSectionConfig>): List<HomeSectionConfig> {
        val titles = natural.associate { it.key to it.title }
        val out = stored.distinctBy { it.key }.map { s -> titles[s.key]?.takeIf { it.isNotBlank() }?.let { s.copy(title = it) } ?: s }.toMutableList()
        for ((i, n) in natural.withIndex()) {
            if (out.any { it.key == n.key }) continue
            val before = (i - 1 downTo 0).map { natural[it].key }.firstOrNull { k -> out.any { it.key == k } }
            out.add(if (before == null) 0 else out.indexOfFirst { it.key == before } + 1, n.copy(visible = true))
        }
        return out
    }

    /**
     * The editor's list: every row there is now ([natural], hero first), in the order Home shows them, each shown or
     * hidden as Home has it (the hero by [stored], the others by [phone]).
     */
    fun editable(stored: List<HomeSectionConfig>, natural: List<HomeSectionConfig>, phone: List<HomeSectionConfig> = emptyList()): List<HomeSectionConfig> {
        val there = natural.map { it.key }.toSet()
        val base = if (stored.isEmpty()) natural.filter { it.key == HERO } + arrangeTvRows(natural.filter { it.key != HERO }, phone) { it.key }
        else merged(stored, natural).filter { it.key in there }
        return base.map { it.copy(visible = if (it.key == HERO) heroShown(stored) else !hiddenIn(phone, it.key)) }
    }

    /** What the TV's layout keeps for the editor's [list]. */
    fun saved(list: List<HomeSectionConfig>): List<HomeSectionConfig> = list.distinctBy { it.key }.take(MAX_KEPT)

    /** [list] with [key] shown if it was hidden, hidden if it was shown. */
    fun toggled(list: List<HomeSectionConfig>, key: String): List<HomeSectionConfig> =
        list.map { if (it.key == key) it.copy(visible = !it.visible) else it }

    /** [list] with [key] moved [by] places (up is negative), stopping at either end. */
    fun moved(list: List<HomeSectionConfig>, key: String, by: Int): List<HomeSectionConfig> =
        com.prism.music.ui.tv.moved(list, list.indexOfFirst { it.key == key }, by)

    /** The [phone]'s layout with every TV row shown again (Back to default); its phone-only sections as they were. */
    fun allShown(phone: List<HomeSectionConfig>): List<HomeSectionConfig> =
        phone.map { if (!it.visible && isTvRow(it.key)) it.copy(visible = true) else it }

    /** How many of the TV's rows are hidden (Settings' line under Customize Home). */
    fun hiddenCount(stored: List<HomeSectionConfig>, phone: List<HomeSectionConfig>): Int =
        (if (heroShown(stored)) 0 else 1) + phone.count { !it.visible && isTvRow(it.key) }
}
