package com.prism.music.playback

/*
 * When a music video that isn't showing gives way to its song (1.8.0, the owner's TV: after a fast skip or a long
 * seek the video often never came back, only the cover). The rules are plain functions so they're tested on the PC.
 */

/** How often the watchdog looks for the video's picture. */
const val VIDEO_WATCH_STEP_MS = 20_000L
/** How long a video still loading is waited for before its song plays instead. */
const val VIDEO_WAIT_MAX_MS = 80_000L
/** A video's streams are asked for afresh after an error at most this often (more errors sooner: back to the song). */
const val VIDEO_RETRY_WINDOW_MS = 60_000L

enum class VideoWatch { FINE, WAIT, GIVE_UP }

/**
 * The watchdog's verdict [waitedMs] after the video was switched on: it has a picture (fine); it's still loading and
 * hasn't been waited for too long (wait); or the song plays on without a picture, or loading never ends (give up).
 */
fun videoWatchVerdict(hasPicture: Boolean, buffering: Boolean, waitedMs: Long): VideoWatch = when {
    hasPicture -> VideoWatch.FINE
    buffering && waitedMs < VIDEO_WAIT_MAX_MS -> VideoWatch.WAIT
    else -> VideoWatch.GIVE_UP
}

/** Whether a video that just failed may have its streams asked for afresh: never yet, or not in the last minute. */
fun videoRetryAllowed(lastRetryAt: Long?, now: Long): Boolean = lastRetryAt == null || now - lastRetryAt >= VIDEO_RETRY_WINDOW_MS

/**
 * A music video's retry: its streams asked for afresh, and with [forgetCachedSound], its sound's cached part dropped too.
 * [serverBusy] (#514): its server kept answering 5xx, so the lookup asks another way first rather than the refused-link
 * steps (the same way over IPv4 only, then NewPipe, both visionOS links again).
 */
data class VideoRetry(val forgetCachedSound: Boolean, val serverBusy: Boolean = false)

/**
 * Whether a music video that failed gets another go ([videoRetryAllowed]) and how; null: back to the song. Refused (403,
 * 410), its server busy (5xx, #514), unreadable or a network failure is worth one. [unreadable] (#411): its bytes couldn't
 * be read, so its sound's cached part goes too, as a song's does: asking afresh alone read the same cached bytes again and
 * failed at once.
 */
fun videoRetry(httpCode: Int?, unreadable: Boolean, network: Boolean, lastRetryAt: Long?, now: Long): VideoRetry? {
    val busy = com.prism.music.data.stream.serverBusy(httpCode)
    return if (videoRetryAllowed(lastRetryAt, now) && (httpCode == 403 || httpCode == 410 || busy || unreadable || network)) {
        VideoRetry(forgetCachedSound = unreadable, serverBusy = busy)
    } else null
}

/** How near its end the song may take over from its video and still be heard ([videoTakeOver]). */
const val SONG_TAKEOVER_END_MS = 2_000L

/** What follows a music video that failed: its song from [atMs], or the video once more where it was. */
sealed interface VideoTakeOver {
    data class Song(val atMs: Long) : VideoTakeOver
    data object VideoAgain : VideoTakeOver
}

/**
 * A music video that failed gives way to its song at the same moment: [videoPosMs] less the video's lead [lagMs] (its
 * intro; 0 when not measured). A video often runs on past its song (scenes after the music, an intro the lead doesn't
 * know of), so that moment can be at or past the end of the song's own length [songMs]: the song then ended at once and
 * the next one started while the video still had a while to go (Ko-fi, Android TV, a Xiaomi box: "some music videos
 * skip to the next song before they end"). There the video gets one more go instead, the first time ([triedAgain]
 * false); after that the song takes over as before. [songMs] 0 (not known): the song, as before.
 */
fun videoTakeOver(videoPosMs: Long, lagMs: Long, songMs: Long, triedAgain: Boolean): VideoTakeOver {
    val at = (videoPosMs - lagMs).coerceAtLeast(0)
    val pastSong = songMs > 0 && at >= songMs - SONG_TAKEOVER_END_MS
    return if (pastSong && !triedAgain) VideoTakeOver.VideoAgain else VideoTakeOver.Song(at)
}
