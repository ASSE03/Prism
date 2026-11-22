package com.prism.music

import com.prism.music.playback.VideoTakeOver
import com.prism.music.playback.videoTakeOver
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ko-fi (Android TV, a Xiaomi box): "some music videos skip to the next song before they end". A video that failed gave
 * way to its song at the same moment, even past the song's own end (the video's outro): the song was over at once and
 * the next one started while the video still had a while to go. Past the song's end the video now gets one more go.
 */
class VideoTakeOverTest {
    @Test fun inTheMusicTheSongTakesOverAtTheSameMoment() {
        // A 3:30 song, its video 40 s ahead (an intro): at 2:10 of the video the song is at 1:30.
        assertEquals(VideoTakeOver.Song(90_000), videoTakeOver(videoPosMs = 130_000, lagMs = 40_000, songMs = 210_000, triedAgain = false))
        assertEquals(VideoTakeOver.Song(0), videoTakeOver(videoPosMs = 10_000, lagMs = 40_000, songMs = 210_000, triedAgain = false))
    }

    @Test fun pastTheSongsEndTheVideoGetsOneMoreGo() {
        // The same video at 4:20, in its outro: the song (3:30) would be at 3:40, past its end. Before, the song took over
        // there, ended at once, and the next song started.
        assertEquals(VideoTakeOver.VideoAgain, videoTakeOver(videoPosMs = 260_000, lagMs = 40_000, songMs = 210_000, triedAgain = false))
        // A lead not measured (0) and a video whose music runs longer: the same.
        assertEquals(VideoTakeOver.VideoAgain, videoTakeOver(videoPosMs = 215_000, lagMs = 0, songMs = 210_000, triedAgain = false))
        // In the song's last two seconds there's nothing left to hear of it either.
        assertEquals(VideoTakeOver.VideoAgain, videoTakeOver(videoPosMs = 249_000, lagMs = 40_000, songMs = 210_000, triedAgain = false))
    }

    @Test fun onceTriedAgainTheSongTakesOverAsBefore() {
        assertEquals(VideoTakeOver.Song(220_000), videoTakeOver(videoPosMs = 260_000, lagMs = 40_000, songMs = 210_000, triedAgain = true))
    }

    @Test fun aSongOfUnknownLengthTakesOverAsBefore() {
        assertEquals(VideoTakeOver.Song(220_000), videoTakeOver(videoPosMs = 260_000, lagMs = 40_000, songMs = 0, triedAgain = false))
    }
}
