package com.prism.music.data

import android.content.Context
import com.prism.music.AppContainer
import com.prism.music.R
import com.prism.music.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A milestone with what it's about, ready to show: the song's title and cover for a song's plays. */
data class ShownMilestone(val milestone: Milestone, val text: String, val thumbnail: String?, val fresh: Boolean)

/**
 * Milestones for Replay's list and for the notice (1.8.5). They're read from the history ([Milestones.of]); kept here
 * is only when they were first looked at on this phone (earlier ones aren't news) and which have been seen.
 */
class MilestoneWatch(private val c: AppContainer) {
    private val prefs by lazy { c.app.getSharedPreferences("milestones", Context.MODE_PRIVATE) }

    /** When milestones were first looked at here: set on the first look, so years of history don't arrive as news. */
    private fun since(now: Long): Long = prefs.getLong(SINCE, 0L).takeIf { it > 0 } ?: now.also { prefs.edit().putLong(SINCE, it).apply() }
    private fun seen(): Set<String> = prefs.getStringSet(SEEN, emptySet()).orEmpty()

    /** Every milestone in what Replay counts, newest first, with the words to show. */
    suspend fun all(now: Long = System.currentTimeMillis()): List<ShownMilestone> = withContext(Dispatchers.IO) {
        val stats = c.imports.replay()
        val songs = stats.topSongs(0, now, MAX_SONGS).associateBy { it.songId }
        val events = stats.events(0, now)
        val since = since(now)
        val seen = seen()
        Milestones.of(events) { songs[it]?.artistName?.substringBefore(", ") }
            .asReversed()
            .mapNotNull { m ->
                val song = m.songId?.let { songs[it] }
                // A song's plays without the song (an imported play not matched yet) has nothing to show.
                if (m.kind == MilestoneKind.SONG_PLAYS && song == null) null
                else ShownMilestone(m, line(m, song?.title), song?.thumbnail, fresh = m.at > since && m.key !in seen)
            }
    }

    /** The list was opened: what's in it now is seen. */
    fun markSeen(shown: List<ShownMilestone>) {
        val fresh = shown.filter { it.fresh }.map { it.milestone.key }
        if (fresh.isNotEmpty()) prefs.edit().putStringSet(SEEN, seen() + fresh).apply()
    }

    @Volatile private var lastCheck = 0L

    /**
     * After a play is saved: with the notice turned on, and not more than once in [CHECK_EVERY_MS], looks for a
     * milestone reached since the last look and tells about it. Off (as it comes), nothing is read at all.
     */
    fun afterPlay(now: Long = System.currentTimeMillis()) {
        if (!c.settings.current.behavior.milestoneNotify) return
        if (now - lastCheck < CHECK_EVERY_MS) return
        lastCheck = now
        c.scope.launch(Dispatchers.IO) { runCatching { tell(now) } }
    }

    internal suspend fun tell(now: Long): List<ShownMilestone> {
        val fresh = all(now).filter { it.fresh }
        if (fresh.isEmpty()) return fresh
        markSeen(fresh)
        // One notice: the newest, and how many more.
        val text = fresh.first().text + if (fresh.size > 1) " " + tr(R.string.ms_and_more, fresh.size - 1) else ""
        Notifications.post(c.app, Notifications.REPLAY_NEWS, 5300, tr(R.string.ms_new), text, "prism://replay")
        return fresh
    }

    companion object {
        private const val SINCE = "since"
        private const val SEEN = "seen"
        private const val MAX_SONGS = 100_000
        const val CHECK_EVERY_MS = 30 * 60_000L

        /** What a milestone says: "You've played Song 100 times", "500 hours of music". */
        fun line(m: Milestone, songTitle: String?): String = when (m.kind) {
            MilestoneKind.SONG_PLAYS -> tr(R.string.ms_song_plays, songTitle ?: "", m.value)
            MilestoneKind.HOURS -> tr(R.string.ms_hours, m.value)
            MilestoneKind.SONGS -> tr(R.string.ms_songs, m.value)
            MilestoneKind.STREAK -> tr(R.string.ms_streak, m.value)
            MilestoneKind.TOP_ARTIST -> tr(R.string.ms_top_artist, m.subject ?: "", m.value)
        }
    }
}
