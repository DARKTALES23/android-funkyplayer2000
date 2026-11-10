package com.funkyplayer2000.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.funkyplayer2000.CoverLoader
import com.funkyplayer2000.MusicLibrary
import com.funkyplayer2000.PlayerViewModel
import com.funkyplayer2000.Track
import com.funkyplayer2000.UiState
import com.funkyplayer2000.fmtTime

@Composable
fun PlayerScreen(vm: PlayerViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    // ---- permissions (Android 11-12: READ_EXTERNAL_STORAGE, Android 13+: READ_MEDIA_AUDIO + notifications)
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    fun hasAudio() = ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasAudio()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAudio()
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            val perms = mutableListOf(audioPerm)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            permLauncher.launch(perms.toTypedArray())
        }
    }

    // ---- the music on the device
    var library by remember { mutableStateOf<List<Track>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var rescan by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, rescan) {
        if (granted) {
            scanning = true
            library = MusicLibrary.loadDevice(ctx)
            scanning = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addDocuments(uris)
    }

    Column(Modifier.fillMaxSize().background(Xp.Face)) {
        XpTitleBar("FunkyPlayer2000")
        NowPlaying(ui)
        Visualizer(
            playing = ui.playing,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(64.dp).field(),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(fmtTime(ui.positionMs), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            XpSeekBar(
                fraction = if (ui.durationMs > 0) ui.positionMs.toFloat() / ui.durationMs else 0f,
                onSeek = vm::seekFraction,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(fmtTime(ui.durationMs), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Transport(ui, vm)
        Spacer(Modifier.height(6.dp))

        Row(Modifier.padding(start = 8.dp)) {
            XpTab("Queue (${ui.queue.size})", tab == 0, { tab = 0 })
            XpTab("Library", tab == 1, { tab = 1 })
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).field()) {
            if (tab == 0) {
                QueuePane(ui, vm, onAddFiles = { picker.launch(arrayOf("audio/*")) })
            } else {
                LibraryPane(
                    library = library, granted = granted, scanning = scanning, vm = vm,
                    onAllow = {
                        permLauncher.launch(arrayOf(audioPerm))
                    },
                    onRescan = { rescan++ },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Xp.Face).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(ui.status, fontSize = 12.sp, color = Xp.InkSoft)
            Text("${library.size} tracks on device", fontSize = 12.sp, color = Xp.InkSoft)
        }
    }
}

@Composable
private fun NowPlaying(ui: UiState) {
    val t = ui.queue.getOrNull(ui.index)
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(124.dp).field()) { Cover(t?.uri) }
        Column(Modifier.weight(1f).height(124.dp).field().padding(8.dp)) {
            Text(
                t?.title ?: "Nothing playing",
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(t?.artist ?: "", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Xp.InkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val album = listOfNotNull(t?.album?.ifBlank { null }, t?.year?.takeIf { it > 0 }?.let { "($it)" }).joinToString(" ")
            Text(album, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Xp.InkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text(
                if (t != null) "track ${ui.index + 1}/${ui.queue.size}" else "",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Xp.InkSoft,
            )
        }
    }
}

@Composable
private fun Cover(uri: String?) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = null
        if (uri != null) value = CoverLoader.load(ctx, uri)?.asImageBitmap()
    }
    val b = bmp
    if (b != null) {
        Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1E82EB), Color(0xFF78C8DB))))
            drawCircle(
                Color.White, radius = size.minDimension * 0.33f,
                center = Offset(size.width / 2, size.height / 2), style = Stroke(3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun Transport(ui: UiState, vm: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        XpButton("◀◀", vm::previous, Modifier.weight(1f), minWidth = 36.dp)
        XpButton(if (ui.playing) "❚❚" else "▶", vm::togglePlay, Modifier.weight(1f), minWidth = 36.dp)
        XpButton("■", vm::stop, Modifier.weight(1f), minWidth = 36.dp)
        XpButton("▶▶", vm::next, Modifier.weight(1f), minWidth = 36.dp)
        XpButton("SHUF", vm::toggleShuffle, Modifier.weight(1.3f), active = ui.shuffle, minWidth = 36.dp)
        XpButton(
            when (ui.repeat) {
                Player.REPEAT_MODE_ALL -> "REP ALL"
                Player.REPEAT_MODE_ONE -> "REP 1"
                else -> "REP"
            },
            vm::cycleRepeat, Modifier.weight(1.5f), active = ui.repeat != Player.REPEAT_MODE_OFF, minWidth = 36.dp,
        )
    }
}

// ------------------------------------------------------------------ queue
@Composable
private fun QueuePane(ui: UiState, vm: PlayerViewModel, onAddFiles: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Xp.Face).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            XpButton("Add files…", onAddFiles)
            XpButton("Clear", vm::clearQueue)
        }
        if (ui.queue.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "The queue is empty.\nOpen the Library tab and tap a track,\nor use “Add files…”.",
                    color = Xp.InkSoft, fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(ui.queue) { i, t ->
                    QueueRow(i, t, i == ui.index, ui.playing, { vm.playIndex(i) }, { vm.removeAt(i) })
                }
            }
        }
    }
}

@Composable
private fun QueueRow(i: Int, t: Track, current: Boolean, playing: Boolean, onPlay: () -> Unit, onRemove: () -> Unit) {
    val bg = if (current) Xp.Sel else if (i % 2 == 1) Xp.Zebra else Color.White
    val fg = if (current) Color.White else Color.Black
    val sub = if (current) Color(0xFFDDE8FF) else Xp.InkSoft
    Row(
        Modifier.fillMaxWidth().background(bg).clickable(onClick = onPlay).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (current && playing) "▶" else "") + "${i + 1}",
            color = fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(38.dp),
        )
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Text(t.title, color = fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                t.artist + if (t.album.isNotEmpty()) " · ${t.album}" else "",
                color = sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(fmtTime(t.durationMs), color = fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 6.dp))
        Box(Modifier.size(44.dp).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Text("✕", color = fg, fontSize = 15.sp)
        }
    }
}

// ------------------------------------------------------------------ library (music on the device)
private sealed interface LibRow {
    data class Header(val artist: String, val album: String, val tracks: List<Track>) : LibRow
    data class Item(val track: Track, val group: List<Track>, val index: Int) : LibRow
}

@Composable
private fun LibraryPane(
    library: List<Track>,
    granted: Boolean,
    scanning: Boolean,
    vm: PlayerViewModel,
    onAllow: () -> Unit,
    onRescan: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val rows = remember(library, query) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) library
        else library.filter { t -> listOf(t.title, t.artist, t.album).any { it.lowercase().contains(q) } }
        val out = ArrayList<LibRow>()
        filtered.groupBy { it.albumArtist.ifBlank { it.artist } to it.album }.forEach { (key, trs) ->
            out += LibRow.Header(key.first, key.second.ifBlank { "Singles" }, trs)
            trs.forEachIndexed { i, t -> out += LibRow.Item(t, trs, i) }
        }
        out
    }

    Column(Modifier.fillMaxSize()) {
        if (!granted) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("FunkyPlayer2000 needs access to the music on your phone.", color = Xp.InkSoft, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                XpButton("Allow access", onAllow, minWidth = 140.dp)
            }
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().background(Xp.Face).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.weight(1f).height(40.dp).field().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) Text("Search…", color = Color.Gray, fontSize = 14.sp)
            }
            XpButton("Rescan", onRescan)
        }
        if (library.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (scanning) "Scanning…" else "No music found on this device.", color = Xp.InkSoft, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows) { row ->
                    when (row) {
                        is LibRow.Header -> AlbumHeader(row, vm)
                        is LibRow.Item -> LibraryTrackRow(row, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(h: LibRow.Header, vm: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFFB8D4F8), Color(0xFF8FB8EE))))
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(h.album, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${h.artist} · ${h.tracks.size} tracks", fontSize = 11.sp, color = Xp.InkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(44.dp).clickable { vm.playTracks(h.tracks, 0) }, contentAlignment = Alignment.Center) {
            Text("▶", fontSize = 16.sp)
        }
        Box(Modifier.size(44.dp).clickable { vm.addToQueue(h.tracks) }, contentAlignment = Alignment.Center) {
            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryTrackRow(r: LibRow.Item, vm: PlayerViewModel) {
    val t = r.track
    Row(
        Modifier.fillMaxWidth()
            .background(if (r.index % 2 == 1) Xp.Zebra else Color.White)
            .clickable { vm.playTracks(r.group, r.index) }
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (t.number > 0) "%02d".format(t.number) else "", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = Xp.InkSoft, modifier = Modifier.width(28.dp))
        Text(t.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        Text(fmtTime(t.durationMs), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Xp.InkSoft,
            modifier = Modifier.padding(horizontal = 6.dp))
        Box(Modifier.size(44.dp).clickable { vm.addToQueue(listOf(t)) }, contentAlignment = Alignment.Center) {
            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
