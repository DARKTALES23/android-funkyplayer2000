package com.funkyplayer2000

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Passes the audio through untouched while copying the samples to AudioTap (for the visualizer). */
@OptIn(UnstableApi::class)
class TapProcessor : BaseAudioProcessor() {
    private var channels = 2
    private var is16Bit = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channels = inputAudioFormat.channelCount
        is16Bit = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (is16Bit) {
            AudioTap.feed(inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer(), channels)
        }
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }
}

/** Plays in the background, with the notification, lock-screen and headset controls. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(TapProcessor()))
                .build()
        }
        // Files and content:// URIs play normally; "ytm://VIDEOID" is resolved to a real YouTube audio URL
        // at the moment ExoPlayer opens it (so it never uses an expired link).
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val resolving = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == "ytm") {
                val id = uri.schemeSpecificPart.removePrefix("//")
                try {
                    val r = YtStreams.resolve(id)
                    dataSpec.buildUpon()
                        .setUri(Uri.parse(r.url))
                        .setHttpRequestHeaders(dataSpec.httpRequestHeaders + ("User-Agent" to r.userAgent))
                        .build()
                } catch (e: IOException) {
                    throw e
                } catch (e: Exception) {
                    throw IOException(e.message ?: "Could not open the YouTube stream", e)
                }
            } else {
                dataSpec
            }
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
