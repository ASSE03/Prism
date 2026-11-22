package com.prism.music.playback

import android.app.Application
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #594: the media session was handed the very bitmap Coil keeps in its memory cache. Once that bitmap was recycled
 * (on one custom ROM, at every play), the system's MediaSession.setMetadata scaled a recycled bitmap and the app died
 * ("cannot use a recycled source in createBitmap"). The session now gets a copy of its own, and a recycled cover is
 * refused instead of passed on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionCoverCopyTest {
    private fun cover() = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }

    @Test fun theSessionGetsItsOwnCopy() {
        val cached = cover()
        val given = CoilBitmapLoader.sessionCopy(cached)
        assertNotSame(cached, given)
        assertEquals(cached.width, given.width)
        assertEquals(cached.getPixel(5, 5), given.getPixel(5, 5))
    }

    @Test fun recyclingTheCachedCoverLeavesTheSessionsCopyWhole() {
        val cached = cover()
        val given = CoilBitmapLoader.sessionCopy(cached)
        cached.recycle()
        assertFalse(given.isRecycled)
        // What the system does with a cover that is too big: scale it. A recycled source would throw here.
        Bitmap.createScaledBitmap(given, 20, 20, true)
    }

    @Test(expected = java.io.IOException::class)
    fun aRecycledCoverIsRefusedNotPassedOn() {
        val cached = cover().apply { recycle() }
        CoilBitmapLoader.sessionCopy(cached)
    }
}
