package com.funkyplayer2000

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.content.ContentUris
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicLibrary {

    private fun clean(s: String?, fallback: String): String =
        if (s.isNullOrBlank() || s == "<unknown>") fallback else s

    /** All the music the device knows about (MediaStore). Needs the audio permission. */
    suspend fun loadDevice(ctx: Context): List<Track> = withContext(Dispatchers.IO) {
        val out = ArrayList<Track>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        try {
            ctx.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                while (c.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, c.getLong(iId))
                    val artist = clean(c.getString(iArtist), "Unknown artist")
                    out += Track(
                        uri = uri.toString(),
                        title = clean(c.getString(iTitle), "Unknown title"),
                        artist = artist,
                        album = clean(c.getString(iAlbum), ""),
                        albumArtist = clean(c.getString(iAlbumArtist), artist),
                        durationMs = c.getLong(iDur),
                        number = c.getInt(iTrack) % 1000,      // MediaStore stores disc*1000 + track
                        year = c.getInt(iYear),
                    )
                }
            }
        } catch (_: SecurityException) {
            // permission revoked while running: return what we have (nothing)
        }
        out.sortedWith(
            compareBy<Track>({ it.albumArtist.lowercase() }, { it.album.lowercase() }, { it.number }, { it.title.lowercase() })
        )
    }

    /** Reads the tags of a file picked with the system file picker. */
    fun readFile(ctx: Context, uri: Uri): Track {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var duration = 0L
        var number = 0
        var year = 0
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            albumArtist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            number = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            year = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            // unreadable tags: fall back to the file name below
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        val fallbackName = displayName(ctx, uri)
        val art = clean(artist, "Unknown artist")
        return Track(
            uri = uri.toString(),
            title = clean(title, fallbackName),
            artist = art,
            album = clean(album, ""),
            albumArtist = clean(albumArtist, art),
            durationMs = duration,
            number = number,
            year = year,
        )
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Unknown title"
    }
}
