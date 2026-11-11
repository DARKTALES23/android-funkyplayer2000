package com.funkyplayer2000

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Cover art: the system thumbnail for MediaStore files, the embedded picture for any other file. */
object CoverLoader {
    private val cache = LruCache<String, Bitmap>(24)

    /** Cover of a track: downloaded from its URL (YouTube Music) or read from the device. */
    suspend fun load(ctx: Context, track: Track): Bitmap? =
        if (track.coverUrl != null) loadUrl(track.coverUrl) else load(ctx, track.uri)

    private suspend fun loadUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        var bmp: Bitmap? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            try {
                bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                c.disconnect()
            }
        } catch (_: Exception) {
        }
        if (bmp != null) cache.put(url, bmp)
        bmp
    }

    suspend fun load(ctx: Context, uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(uriString)?.let { return@withContext it }
        val uri = Uri.parse(uriString)
        var bmp: Bitmap? = null
        if (uri.authority == MediaStore.AUTHORITY) {
            try {
                bmp = ctx.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            } catch (_: Exception) {
            }
        }
        if (bmp == null) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                val bytes = r.embeddedPicture
                if (bytes != null) bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        }
        if (bmp != null) cache.put(uriString, bmp)
        bmp
    }
}
