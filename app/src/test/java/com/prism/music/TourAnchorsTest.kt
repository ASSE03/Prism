package com.prism.music

import com.prism.music.ui.tour.TourAnchor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The coach's anchors (1.8.8, plan-tour.md §5): every [TourAnchor] is attached to its control exactly once in the
 * app's code (outside ui/tour, where the steps name them), with `tourAnchor(…)`; and every anchor a step in ui/tour
 * names is one that exists. A source scan, like [I18nGuardTest].
 */
class TourAnchorsTest {
    private val src = File("src/main/java/com/prism/music")
    private val tour = File(src, "ui/tour")
    private fun kotlin(dir: File) = dir.walkTopDown().filter { it.extension == "kt" }
    private val use = Regex("""TourAnchor\.([A-Z_]+)\b""")

    /**
     * Anchors with a second home that never shows at the same time as the first: Home's profile picture carries
     * SETTINGS_TAB only when the bar has no Settings tab (1.8.8, a fresh install's bar has none).
     */
    private val alsoOn = mapOf(TourAnchor.SETTINGS_TAB to "HomeScreen.kt")

    @Test fun everyAnchorIsAttachedExactlyOnce() {
        val app = kotlin(src).filter { !it.startsWith(tour) }.toList()
        val uses = app.flatMap { f -> f.readLines().mapIndexedNotNull { n, line -> use.findAll(line).map { it.groupValues[1] to "${f.relativeTo(src)}:${n + 1}" }.toList().takeIf { it.isNotEmpty() } }.flatten() }
        val byName = uses.groupBy({ it.first }, { it.second })
        val wrong = TourAnchor.entries.mapNotNull { a ->
            val second = alsoOn[a]
            val at = byName[a.name].orEmpty().filterNot { second != null && File(it.substringBefore(':')).name == second }
            if (at.size == 1) null else "${a.name}: attached ${at.size} times ${at.joinToString()}"
        }
        assertTrue("Each anchor goes on its control once:\n" + wrong.joinToString("\n"), wrong.isEmpty())
        // And attached as a modifier, not only named (the tabs' helper picks one of two in the line before).
        val attached = app.count { f -> f.readText().contains(".tourAnchor(") }
        assertTrue("tourAnchor(…) is used in the app", attached > 0)
    }

    @Test fun everyStepNamesAnAnchorThatExists() {
        val names = TourAnchor.entries.map { it.name }.toSet()
        val unknown = kotlin(tour).flatMap { f -> use.findAll(f.readText()).map { it.groupValues[1] }.filter { it !in names }.map { "${f.name}: $it" } }.toList()
        assertTrue("Unknown anchors: $unknown", unknown.isEmpty())
    }
}
