package com.prism.music.connect

import com.prism.music.playback.together.fromPeer
import kotlin.math.abs

/**
 * Playing on a Prism TV from the phone (1.8.0): the rules both sides follow, worked out without a player. The phone
 * leads what plays (its own player carries on, muted, and sends the songs around the one playing); the TV leads where
 * (it's what's heard, so the phone's playhead follows the TV's, as when casting).
 */
object TvSync {
    /** Off by more than this, the phone's playhead goes to the TV's (Listen Together's tolerance). */
    const val DRIFT_MS = 650L
    /** A song the TV has nearly finished isn't cut short when the phone got to the next one first: it runs out. */
    const val LET_FINISH_MS = 4_000L
    /** The phone sends the song before the one playing (so Previous on the TV works) and this many after it. */
    const val BEFORE = 1
    const val AHEAD = 24

    /** The TV's queue as it stands: the songs' ids, the one playing, and how much of it is left (ms; -1: not known). */
    data class Have(val ids: List<String>, val index: Int, val remainingMs: Long)

    /**
     * What the TV does with a [FollowState]: [load] the songs and start at [startMs]; or keep the song playing and only
     * set the songs [around] it; [seekTo] a place in it; [wait] for the song to run out into the next by itself.
     */
    data class Plan(
        val playing: Boolean,
        val load: Boolean = false,
        val startMs: Long = 0,
        val around: Boolean = false,
        val seekTo: Long? = null,
        val wait: Boolean = false,
    )

    fun plan(have: Have, f: FollowState): Plan {
        // Nothing the TV can play (a file on the phone): it pauses, and the phone plays it itself.
        val target = f.songs.getOrNull(f.index)?.id ?: return Plan(playing = false)
        val ids = f.songs.map { it.id }
        val seek = f.seekTo.takeIf { it >= 0 }
        val current = have.ids.getOrNull(have.index)
        return when {
            current == target -> Plan(f.playing, around = have.ids != ids, seekTo = seek)
            seek == null && have.ids.getOrNull(have.index + 1) == target && have.remainingMs in 0..LET_FINISH_MS -> Plan(f.playing, wait = true)
            else -> Plan(f.playing, load = true, startMs = seek ?: 0)
        }
    }

    /**
     * The play-order window the phone sends around [current] (queue indices, by [previous] and [next] in play order):
     * up to [before] songs before it, it, and up to [ahead] after; with where [current] is in it. A queue that loops
     * (repeat all) is sent once round.
     */
    fun window(current: Int, previous: (Int) -> Int?, next: (Int) -> Int?, before: Int = BEFORE, ahead: Int = AHEAD): Pair<List<Int>, Int> {
        val seen = hashSetOf(current)
        val back = ArrayList<Int>()
        var i: Int? = previous(current)
        while (i != null && back.size < before && seen.add(i)) { back.add(0, i); i = previous(i) }
        val forward = ArrayList<Int>()
        i = next(current)
        while (i != null && forward.size < ahead && seen.add(i)) { forward += i; i = next(i) }
        return (back + current + forward) to back.size
    }

    /**
     * Where the phone's playhead goes to stay with the TV ([w], received [ageMs] ago), or null when it's close enough or
     * the TV isn't moving (paused, or still loading the song).
     */
    fun follow(phoneMs: Long, w: TvWhere, ageMs: Long): Long? {
        if (!w.playing || w.buffering) return null
        val tv = w.positionMs + ageMs.coerceIn(0, 2_000)
        return if (abs(phoneMs - tv) > DRIFT_MS) tv else null
    }

    /** At most this many songs either way ([FollowState.songs], or a queue the TV started of its own). */
    const val MAX_SONGS = 200

    /** [s] as it goes between the phone and the TV, or null when it can't: only YouTube songs ([fromPeer]). */
    fun goes(s: com.prism.music.data.model.Song): com.prism.music.data.model.Song? =
        com.prism.music.TestHooks.prismTvSong?.invoke(s) ?: s.fromPeer()

    /**
     * A [FollowState] made safe to play on the TV: only YouTube songs, pictures only from YouTube's own image servers
     * (as any song from another device), [FollowState.index] moved to match (-1 when the song playing was one that
     * can't come).
     */
    fun FollowState.cleaned(): FollowState {
        val kept = songs.take(MAX_SONGS).mapIndexedNotNull { i, s -> goes(s)?.let { i to it } }
        return copy(
            songs = kept.map { it.second }, index = kept.indexOfFirst { it.first == index },
            seekTo = seekTo.coerceAtLeast(-1), title = title.take(200),
        )
    }

    /**
     * A song picked on the TV ([act], a [TvAct.PICK]) as the phone's queue index: the [sent] window's index for it, if it's
     * still the song that was sent there ([sentIds]).
     */
    fun picked(act: TvAct, sent: List<Int>, sentIds: List<String>): Int? {
        if (act.kind != TvAct.PICK) return null
        val at = act.index.takeIf { it in sent.indices } ?: return null
        return sent[at].takeIf { sentIds.getOrNull(at) == act.songId }
    }
}
