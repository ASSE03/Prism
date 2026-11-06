package com.prism.music

import com.prism.music.ui.theme.Backdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * #378: "your own photo shows only black". The value saved for a background was built from a translated string, so a
 * German phone saved "FOTO|…", which isn't a background Prism knows: Home fell back to Ambient (black with Pure black),
 * and Backgrounds ticked Ambient while its "Your photo" card showed the picture. 8 of the 31 languages had it.
 */
class BackdropPhotoKeyTest {
    @Test fun aPhotoSavedInAnyLanguageIsStillAPhoto() {
        for (word in listOf("PHOTO", "FOTO", "FOTÓ", "ZDJĘCIE", "ФОТО", "LARAWAN")) {
            val (style, path) = Backdrop.parse("$word|/data/user/0/com.prism.music/files/backdrops/all-1.jpg")
            assertEquals(word, Backdrop.PHOTO, style)
            assertEquals(word, "/data/user/0/com.prism.music/files/backdrops/all-1.jpg", path)
        }
    }

    @Test fun otherValuesReadAsBefore() {
        assertEquals(Backdrop.AURORA to null, Backdrop.parse("AURORA"))
        assertEquals(Backdrop.DEFAULT to null, Backdrop.parse(null))
        assertEquals(Backdrop.DEFAULT to null, Backdrop.parse("NOT_A_BACKGROUND"))
        // A Look's kept photo, before it's resolved to its file.
        assertEquals(Backdrop.PHOTO to "asset:abc", Backdrop.parse("PHOTO|asset:abc"))
        assertNull(Backdrop.parse("PHOTO").second)
    }

    @Test fun whatWasSavedIsPutRight() {
        assertEquals("PHOTO|/data/a.jpg", com.prism.music.data.looks.Backgrounds.photoKeyFixed("FOTO|/data/a.jpg"))
        assertEquals("PHOTO|/data/a.jpg", com.prism.music.data.looks.Backgrounds.photoKeyFixed("PHOTO|/data/a.jpg"))
        assertEquals("AURORA", com.prism.music.data.looks.Backgrounds.photoKeyFixed("AURORA"))
        assertEquals("PHOTO|asset:abc", com.prism.music.data.looks.Backgrounds.photoKeyFixed("PHOTO|asset:abc"))
        assertEquals("PHOTO|/x", Backdrop.photoValue("/x"))
    }

    /** What's saved never comes from a translation again; the other saved strings stay the same in every language. */
    @Test fun whatsSavedIsntTranslated() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
        assertFalse(sources.any { it.readText().contains("R.string.photo,") })
        val res = File("src/main/res")
        for (key in listOf("songs_albums", "mix_3")) {
            val line = Regex("<string name=\"$key\">([^<]*)</string>")
            val own = line.find(File(res, "values/strings.xml").readText())!!.groupValues[1]
            res.listFiles { f -> f.name.startsWith("values-") }!!.mapNotNull { File(it, "strings.xml").takeIf(File::isFile) }.forEach { f ->
                line.find(f.readText())?.let { assertEquals("$key in ${f.parentFile!!.name}", own, it.groupValues[1]) }
            }
        }
    }
}
