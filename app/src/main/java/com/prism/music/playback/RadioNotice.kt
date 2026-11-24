package com.prism.music.playback

/**
 * "Radio started" (Reddit, 1.8.8: the Radio buttons gave only a small vibration). A Radio button asks for the notice
 * ([asked]) with the radio's queue; it's said once that radio's first songs are in the queue ([take]), so a radio that
 * never gets going (no connection, the explicit filter, another queue started meanwhile) says nothing new.
 */
class RadioNotice {
    @Volatile private var waiting: QueueSource? = null

    fun asked(source: QueueSource) { waiting = source }

    /** True once: when [source] is the radio asked for and [added] songs joined it. */
    fun take(source: QueueSource, added: Int): Boolean {
        if (added <= 0 || waiting !== source) return false
        waiting = null
        return true
    }
}
