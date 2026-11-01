package com.prism.music.ui.tv

import com.prism.music.data.prefs.HomeSectionConfig
import com.prism.music.data.prefs.HomeSections

/*
 * Customizing the TV's Home (1.8.0): showing, hiding and moving its rows, kept in the phone's own Home layout
 * ([com.prism.music.data.prefs.AppSettings.homeLayout]) so the phone and the TV agree: a row hidden on one is hidden on
 * the other, and rows both have keep the same order. Rows the layout doesn't name stay where Home puts them, so an
 * untouched layout is the TV's Home as it was.
 */

/** A TV row's key in the phone's Home layout. */
fun phoneKeyOf(tvKey: String): String = when (tvKey) {
    KEY_PICKS -> HomeSections.QUICK_PICKS
    KEY_DIAL -> HomeSections.SPEED_DIAL
    KEY_RECENT -> HomeSections.RECENT
    else -> if (tvKey.startsWith("yt:")) com.prism.music.ui.screens.shelfKey(tvKey.removePrefix("yt:")) else tvKey
}

/** Whether [layout] hides the row [tvKey]. */
fun hiddenIn(layout: List<HomeSectionConfig>, tvKey: String): Boolean = layout.firstOrNull { it.key == phoneKeyOf(tvKey) }?.visible == false

/**
 * [rows] as [layout] arranges them: those it names, in its order, in the places those rows had; the others where they
 * were. Hidden rows are kept ([hiddenIn] tells them apart): Home leaves them out, the TV's editor lists them.
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

/**
 * [layout] after the TV's rows were put in [tvOrder] (each key with its title): every one of them named in that order,
 * in the places the layout's TV rows held, the phone's own sections (the TV doesn't have) left where they are.
 */
fun layoutInOrder(layout: List<HomeSectionConfig>, tvOrder: List<Pair<String, String>>): List<HomeSectionConfig> {
    val keys = tvOrder.map { (k, _) -> phoneKeyOf(k) }
    val byKey = layout.associateBy { it.key }
    val wanted = tvOrder.map { (k, title) -> byKey[phoneKeyOf(k)] ?: HomeSectionConfig(phoneKeyOf(k), title) }
    val slots = layout.withIndex().filter { it.value.key in keys }.map { it.index }
    val out = layout.toMutableList()
    // The TV's rows the layout already had take its slots, in the new order; the ones it didn't follow at the end.
    wanted.take(slots.size).forEachIndexed { n, s -> out[slots[n]] = s }
    return out + wanted.drop(slots.size)
}

/** [order] with the item at [from] moved [by] places (−1 up, +1 down), held within the list. */
fun <T> moved(order: List<T>, from: Int, by: Int): List<T> {
    val to = (from + by).coerceIn(0, order.lastIndex)
    if (from !in order.indices || to == from) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/** The rows Home last showed on this TV (key and title, hidden ones too), for the editor in Settings. */
internal object TvHomeRowsSeen {
    @Volatile var rows: List<Pair<String, String>> = emptyList()
}
