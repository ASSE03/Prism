package com.prism.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future

/**
 * The media session's covers (notification, lock screen, Android Auto, the watch), loaded like every other image in
 * Prism: from the covers kept on the phone first ([com.prism.music.data.ArtworkStore]), so a downloaded song shows its
 * cover offline too. Media3's own loader only knew the network.
 */
@UnstableApi
class CoilBitmapLoader(private val context: Context, private val scope: CoroutineScope) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        BitmapFactory.decodeByteArray(data, 0, data.size)?.let { Futures.immediateFuture(it) }
            ?: Futures.immediateFailedFuture(IllegalArgumentException("Couldn't decode the cover"))

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = scope.future {
        val req = ImageRequest.Builder(context).data(uri.toString()).size(SIZE).allowHardware(false).build()
        val cached = (SingletonImageLoader.get(context).execute(req) as? SuccessResult)?.image?.toBitmap()
            ?: throw java.io.IOException("Couldn't load the cover")
        sessionCopy(cached)
    }

    companion object {
        private const val SIZE = 544

        /**
         * #594: the bitmap Coil returns is the one in its memory cache, shared with every other user of that cover.
         * Media3 keeps what we give it and the system scales it again at each metadata update, so a cover recycled
         * elsewhere crashed the app. The session gets a copy nobody else holds (about 1 MB at 544 px), and a cover
         * already recycled is refused, which media3 treats as "no artwork" instead of a crash.
         */
        fun sessionCopy(cached: Bitmap): Bitmap {
            if (cached.isRecycled) throw java.io.IOException("The cover was recycled")
            return cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, false)
                ?: throw java.io.IOException("Couldn't copy the cover")
        }
    }
}
