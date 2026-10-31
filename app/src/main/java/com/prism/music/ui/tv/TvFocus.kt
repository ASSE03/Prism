package com.prism.music.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/*
 * Prism TV's focus rules (plan-1.8.0 §3), one helper each, so every TV screen follows them the same way:
 * - on entering a screen focus lands on a sensible item, never nothing ([entryFocus]);
 * - leaving a row or list and coming back returns to the item that had focus ([rememberedFocus]).
 * Back (overlay → up a level → leave Prism on Home) is the shell's ([TvRoot]); media keys reach the media session.
 */

/** How long [entryFocus] keeps asking: a screen's first item can take a few frames to be placed (lists, pictures). */
private const val ENTRY_TRIES = 40
private const val ENTRY_STEP_MS = 50L

/**
 * Takes focus when it first appears (the item a screen opens on). Asks again for a moment if it isn't placed yet;
 * once it has had focus it never takes it back (the listener has moved on).
 */
@Composable
fun Modifier.entryFocus(key: Any? = Unit): Modifier {
    val requester = remember { FocusRequester() }
    var had by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        var i = 0
        while (!had && i++ < ENTRY_TRIES) {
            runCatching { requester.requestFocus() }
            delay(ENTRY_STEP_MS)
        }
    }
    return this.focusRequester(requester).onFocusChanged { if (it.hasFocus) had = true }
}

/**
 * A row or list that remembers which of its items had focus: coming back into it (D-pad from above or below, or
 * back from a page) lands there, not on its first item.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.rememberedFocus(): Modifier = this.focusRestorer()
