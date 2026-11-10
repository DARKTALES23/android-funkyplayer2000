package com.funkyplayer2000

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val stopped: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val note: String? = null,
    val connected: Boolean = false,
) {
    val status: String
        get() = note ?: when {
            !connected -> "Starting…"
            buffering -> "Loading…"
            playing -> "Playing"
            stopped || queue.isEmpty() -> "Stopped"
            else -> "Paused"
        }
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var controller: MediaController? = null
    private var stopped = true
    private var noteJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(rebuild = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    init {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                _ui.update { it.copy(connected = true) }
                refresh(rebuild = true)
                startTicker()
            } catch (e: Exception) {
                note("Could not connect to the player service")
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    // ---- state -> screen
    private fun toTrack(item: MediaItem): Track {
        val md = item.mediaMetadata
        val extras = md.extras
        return Track(
            uri = item.mediaId,
            title = md.title?.toString() ?: "Unknown title",
            artist = md.artist?.toString() ?: "Unknown artist",
            album = md.albumTitle?.toString() ?: "",
            albumArtist = md.albumArtist?.toString() ?: (md.artist?.toString() ?: ""),
            durationMs = extras?.getLong("dur") ?: 0L,
            number = extras?.getInt("num") ?: 0,
            year = extras?.getInt("year") ?: 0,
        )
    }

    private fun toItem(t: Track): MediaItem {
        val extras = Bundle().apply {
            putLong("dur", t.durationMs)
            putInt("num", t.number)
            putInt("year", t.year)
        }
        return MediaItem.Builder()
            .setMediaId(t.uri)
            .setUri(Uri.parse(t.uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setAlbumArtist(t.albumArtist)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun refresh(rebuild: Boolean = false) {
        val c = controller ?: return
        val old = _ui.value
        val queue = if (rebuild || old.queue.size != c.mediaItemCount) {
            List(c.mediaItemCount) { toTrack(c.getMediaItemAt(it)) }
        } else old.queue
        val idx = if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex
        val duration = if (c.duration != C.TIME_UNSET && c.duration > 0) c.duration
        else queue.getOrNull(idx)?.durationMs ?: 0L
        if (c.isPlaying) stopped = false
        _ui.value = old.copy(
            queue = queue,
            index = idx,
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            stopped = stopped,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
        )
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (true) {
                delay(250)
                if (controller?.isPlaying == true) refresh()
            }
        }
    }

    fun note(msg: String) {
        _ui.update { it.copy(note = msg) }
        noteJob?.cancel()
        noteJob = viewModelScope.launch {
            delay(3000)
            _ui.update { it.copy(note = null) }
        }
    }

    // ---- queue
    fun playTracks(list: List<Track>, start: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        stopped = false
        c.setMediaItems(list.map { toItem(it) }, start.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
        refresh(rebuild = true)
    }

    fun addToQueue(list: List<Track>) {
        val c = controller ?: return
        if (list.isEmpty()) return
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItems(list.map { toItem(it) })
        if (wasEmpty) c.prepare()
        refresh(rebuild = true)
        note("${list.size} track(s) added")
    }

    fun addDocuments(uris: List<Uri>) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    try {
                        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {
                    }
                    MusicLibrary.readFile(ctx, uri)
                }
            }
            addToQueue(tracks)
        }
    }

    fun playIndex(i: Int) {
        val c = controller ?: return
        if (i !in 0 until c.mediaItemCount) return
        stopped = false
        c.seekToDefaultPosition(i)
        c.play()
        refresh()
    }

    fun removeAt(i: Int) {
        val c = controller ?: return
        if (i in 0 until c.mediaItemCount) c.removeMediaItem(i)
        refresh(rebuild = true)
    }

    fun clearQueue() {
        val c = controller ?: return
        c.clearMediaItems()
        stopped = true
        refresh(rebuild = true)
    }

    // ---- transport
    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.mediaItemCount == 0) return
            stopped = false
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
        refresh()
    }

    fun stop() {
        val c = controller ?: return
        c.pause()
        c.seekTo(0)
        stopped = true
        refresh()
    }

    fun next() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) {
            stopped = false
            c.seekToNextMediaItem()
            c.play()
        }
        refresh()
    }

    fun previous() {
        val c = controller ?: return
        stopped = false
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0)
        c.play()
        refresh()
    }

    fun seekFraction(f: Float) {
        val c = controller ?: return
        val dur = _ui.value.durationMs
        if (dur > 0) c.seekTo((f.coerceIn(0f, 1f) * dur).toLong())
        refresh()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        refresh()
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        refresh()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
