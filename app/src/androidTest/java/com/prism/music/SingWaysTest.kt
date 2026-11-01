package com.prism.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prism.music.data.ModelStore
import com.prism.music.playback.sing.Separation
import com.prism.music.ui.player.SingSheet
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Sing on more devices (1.8.x): a phone too slow for Sing's separation sings the quick way, and its Sing sheet says so
 * (why, and what the quick way can't do) rather than leaving Sing to fall behind every song.
 */
@RunWith(AndroidJUnit4::class)
class SingWaysTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()

    private val c get() = Fixture.container
    private val pace get() = Fixture.context.getSharedPreferences("sing_pace", android.content.Context.MODE_PRIVATE)

    @After fun cleanUp() {
        pace.edit().clear().commit()
        listOfNotNull(ModelStore.Model.singRuntime(), ModelStore.Model.SING_MODEL).forEach { c.models.dir(it).deleteRecursively() }
        c.models.refresh()
        c.sing.rechoose()
    }

    @Test fun aSlowPhoneSingsTheQuickWayAndSaysWhy() {
        Fixture.ready()
        // Sing's download as if it were on the phone (only its ready marks: the quick way needs none of it).
        listOfNotNull(ModelStore.Model.singRuntime(), ModelStore.Model.SING_MODEL).forEach { m ->
            c.models.dir(m).mkdirs(); File(c.models.dir(m), ".ready").writeText("test")
        }
        c.models.refresh()
        // Timed here at less than the song's own speed, both ways.
        pace.edit().putFloat(Separation.FULL.name, 0.4f).putFloat(Separation.LIGHT.name, 0.6f).commit()
        c.sing.rechoose()
        assertEquals(Separation.QUICK, c.sing.method.value)

        val song = Fixture.songs(1, "Sing quick").first()
        compose.setContent { CompositionLocalProvider(LocalContainer provides c) { PrismTheme(c.settings.current, null) { SingSheet(song) {} } } }
        val why = if (ModelStore.Model.singRuntime() == null) tr(R.string.sing_doesn_t_run_on_this) else tr(R.string.sing_quick_slow)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("singQuickNote") and hasText(why), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun aPhoneThatKeepsUpSaysNothingOfTheQuickWay() {
        Fixture.ready()
        listOfNotNull(ModelStore.Model.singRuntime(), ModelStore.Model.SING_MODEL).forEach { m ->
            c.models.dir(m).mkdirs(); File(c.models.dir(m), ".ready").writeText("test")
        }
        c.models.refresh()
        pace.edit().putFloat(Separation.FULL.name, 7.4f).commit()
        c.sing.rechoose()
        val expected = if (ModelStore.Model.singRuntime() == null) Separation.QUICK else Separation.FULL
        assertEquals(expected, c.sing.method.value)
        val song = Fixture.songs(1, "Sing full").first()
        compose.setContent { CompositionLocalProvider(LocalContainer provides c) { PrismTheme(c.settings.current, null) { SingSheet(song) {} } } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(tr(R.string.sing))).fetchSemanticsNodes().isNotEmpty() }
        val notes = compose.onAllNodes(hasTestTag("singQuickNote"), useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(if (expected == Separation.QUICK) 1 else 0, notes)
    }
}
