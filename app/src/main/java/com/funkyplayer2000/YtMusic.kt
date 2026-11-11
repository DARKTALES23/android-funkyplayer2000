package com.funkyplayer2000

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.search.SearchQueryHandler
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The network layer NewPipeExtractor asks us to provide (it does not do HTTP by itself). */
class DownloaderImpl private constructor() : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()
        var body: RequestBody? = data?.toRequestBody(null)
        if (body == null && (method == "POST" || method == "PUT" || method == "PATCH")) {
            body = ByteArray(0).toRequestBody(null)
        }
        val builder = okhttp3.Request.Builder().method(method, body).url(url).addHeader("User-Agent", USER_AGENT)
        for ((name, values) in request.headers()) {
            if (values.size > 1) {
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            } else if (values.size == 1) {
                builder.header(name, values[0])
            }
        }
        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", url)
            val text = resp.body?.string()
            return Response(resp.code, resp.message, resp.headers.toMultimap(), text, resp.request.url.toString())
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        val instance: DownloaderImpl by lazy { DownloaderImpl() }
    }
}

/** Search, albums and playlists from YouTube Music (no login needed). */
object YtMusic {
    enum class Kind(val filter: String, val label: String) {
        Songs("music_songs", "Songs"),
        Albums("music_albums", "Albums"),
        Playlists("music_playlists", "Playlists"),
    }

    sealed interface Result {
        data class Song(val track: Track) : Result
        data class Playlist(
            val title: String,
            val subtitle: String,
            val url: String,
            val coverUrl: String?,
            val isAlbum: Boolean,
        ) : Result
    }

    class SearchPage(val results: List<Result>, val next: Page?, val handler: SearchQueryHandler)

    private var ready = false
    private val service: StreamingService get() = ServiceList.YouTube

    @Synchronized
    fun ensureInit() {
        if (!ready) {
            NewPipe.init(DownloaderImpl.instance)
            ready = true
        }
    }

    // ---- helpers
    private val bigCover = Regex("=w\\d+-h\\d+[^&]*$")

    private fun bestThumb(images: List<org.schabi.newpipe.extractor.Image>?): String? {
        val url = images?.maxByOrNull { it.height * it.width }?.url ?: images?.firstOrNull()?.url ?: return null
        return if ((url.contains("googleusercontent") || url.contains("ggpht")) && url.contains("=w"))
            url.replace(bigCover, "=w600-h600-l90-rj") else url
    }

    private fun videoId(url: String): String =
        Regex("[?&]v=([\\w-]{11})").find(url)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([\\w-]{11})").find(url)?.groupValues?.get(1)
            ?: url.substringAfterLast('/')

    private fun songTrack(
        s: StreamInfoItem,
        album: String = "",
        albumArtist: String = "",
        cover: String? = null,
        number: Int = 0,
    ): Track {
        val artist = (s.uploaderName ?: "").removeSuffix(" - Topic").ifBlank { "Unknown artist" }
        return Track(
            uri = "ytm://" + videoId(s.url),
            title = s.name ?: "Unknown title",
            artist = artist,
            album = album,
            albumArtist = albumArtist.ifBlank { artist },
            durationMs = s.duration.coerceAtLeast(0L) * 1000L,
            number = number,
            coverUrl = cover ?: bestThumb(s.thumbnails),
        )
    }

    private fun toResult(item: InfoItem, albumMode: Boolean): Result? = when (item) {
        is StreamInfoItem -> Result.Song(songTrack(item))
        is PlaylistInfoItem -> Result.Playlist(
            title = item.name ?: "Untitled",
            subtitle = listOfNotNull(
                item.uploaderName?.takeIf { it.isNotBlank() },
                item.streamCount.takeIf { it > 0 }?.let { "$it tracks" },
            ).joinToString(" · "),
            url = item.url,
            coverUrl = bestThumb(item.thumbnails),
            isAlbum = albumMode,
        )
        else -> null
    }

    // ---- search
    suspend fun search(query: String, kind: Kind): SearchPage = withContext(Dispatchers.IO) {
        ensureInit()
        val handler = service.searchQHFactory.fromQuery(query, listOf(kind.filter), "")
        val info = SearchInfo.getInfo(service, handler)
        SearchPage(info.relatedItems.mapNotNull { toResult(it, kind == Kind.Albums) }, info.nextPage, handler)
    }

    suspend fun more(handler: SearchQueryHandler, page: Page, kind: Kind): SearchPage = withContext(Dispatchers.IO) {
        ensureInit()
        val p: ListExtractor.InfoItemsPage<InfoItem> = SearchInfo.getMoreItems(service, handler, page)
        SearchPage(p.items.mapNotNull { toResult(it, kind == Kind.Albums) }, p.nextPage, handler)
    }

    // ---- albums and playlists
    suspend fun loadPlaylist(url: String, isAlbum: Boolean): List<Track> = withContext(Dispatchers.IO) {
        ensureInit()
        val info = PlaylistInfo.getInfo(service, url)
        val cover = bestThumb(info.thumbnails)
        val items = ArrayList<StreamInfoItem>(info.relatedItems)
        var page = info.nextPage
        var guard = 0
        while (page != null && guard++ < 10 && items.size < 500) {
            val more = PlaylistInfo.getMoreItems(service, url, page)
            items += more.items
            page = more.nextPage
        }
        val albumName = if (isAlbum) (info.name ?: "") else ""
        val albumArtist = if (isAlbum) (info.uploaderName ?: "") else ""
        items.mapIndexed { i, s ->
            songTrack(
                s, album = albumName, albumArtist = albumArtist,
                cover = if (isAlbum) cover else null, number = if (isAlbum) i + 1 else 0,
            )
        }
    }

    // ---- pasted links
    fun looksLikeLink(text: String) = text.contains("youtube.com") || text.contains("youtu.be")

    suspend fun fromLink(text: String): List<Result> = withContext(Dispatchers.IO) {
        ensureInit()
        val list = Regex("[?&]list=([\\w-]+)").find(text)?.groupValues?.get(1)
        val v = Regex("[?&]v=([\\w-]{11})").find(text)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([\\w-]{11})").find(text)?.groupValues?.get(1)
        when {
            list != null && (text.contains("/playlist") || v == null) -> {
                val url = "https://music.youtube.com/playlist?list=$list"
                val info = PlaylistInfo.getInfo(service, url)
                listOf(
                    Result.Playlist(
                        title = info.name ?: "Playlist",
                        subtitle = listOfNotNull(
                            info.uploaderName?.takeIf { it.isNotBlank() },
                            info.streamCount.takeIf { it > 0 }?.let { "$it tracks" },
                        ).joinToString(" · "),
                        url = url,
                        coverUrl = bestThumb(info.thumbnails),
                        isAlbum = list.startsWith("OLAK5uy"),
                    )
                )
            }
            v != null -> {
                val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$v")
                listOf(
                    Result.Song(
                        Track(
                            uri = "ytm://$v",
                            title = info.name ?: "Unknown title",
                            artist = (info.uploaderName ?: "").removeSuffix(" - Topic").ifBlank { "Unknown artist" },
                            album = "", albumArtist = "",
                            durationMs = info.duration.coerceAtLeast(0L) * 1000L,
                            coverUrl = bestThumb(info.thumbnails),
                        )
                    )
                )
            }
            else -> throw IOException("I could not understand that link.")
        }
    }
}

/** Turns "ytm://VIDEOID" into a real audio URL right before playback (YouTube URLs expire). */
object YtStreams {
    class Resolved(val url: String, val userAgent: String)

    private val cache = ConcurrentHashMap<String, Pair<Long, Resolved>>()
    private const val TTL_MS = 60 * 60 * 1000L

    @Throws(IOException::class)
    fun resolve(videoId: String): Resolved {
        val now = System.currentTimeMillis()
        cache[videoId]?.let { (at, r) -> if (now - at < TTL_MS) return r }
        try {
            YtMusic.ensureInit()
            val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
            val best = info.audioStreams
                .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .maxByOrNull { it.averageBitrate }
                ?: throw IOException("No playable audio stream found for this track")
            val r = Resolved(best.content, userAgentFor(best.content))
            cache[videoId] = now to r
            return r
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: "YouTube extraction failed", e)
        }
    }

    /** YouTube checks that the user agent matches the client that generated the URL. */
    private fun userAgentFor(url: String): String {
        val client = Uri.parse(url).getQueryParameter("c")
        val method = when (client) {
            "ANDROID" -> "getAndroidUserAgent"
            "IOS" -> "getIosUserAgent"
            else -> null
        }
        if (method != null) {
            try {
                val helper = Class.forName("org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper")
                val ua = helper.getMethod(method, Localization::class.java).invoke(null, Localization.DEFAULT) as? String
                if (!ua.isNullOrBlank()) return ua
            } catch (_: Throwable) {
            }
        }
        return DownloaderImpl.USER_AGENT
    }
}
