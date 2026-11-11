package com.funkyplayer2000.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.funkyplayer2000.PlayerViewModel
import com.funkyplayer2000.YtMusic
import com.funkyplayer2000.fmtTime

/** Search YouTube Music, or paste a link to a song, album or playlist. */
@Composable
fun YtPane(vm: PlayerViewModel) {
    val st by vm.yt.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(st.query) }
    var kind by remember { mutableStateOf(st.kind) }
    val focus = LocalFocusManager.current
    fun go(k: YtMusic.Kind = kind) {
        focus.clearFocus()
        vm.ytSearch(text, k)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Xp.Face).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.weight(1f).height(40.dp).field().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (text.isEmpty()) Text("Search or paste a link…", color = Color.Gray, fontSize = 14.sp)
            }
            XpButton("Search", { go() })
        }
        Row(
            Modifier.fillMaxWidth().background(Xp.Face).padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (k in YtMusic.Kind.values()) {
                XpButton(
                    k.label,
                    { kind = k; if (text.isNotBlank()) go(k) },
                    Modifier.weight(1f), active = kind == k,
                )
            }
        }

        val results = st.results
        when {
            results.isEmpty() && st.loading -> Message("Searching…")
            results.isEmpty() && st.error != null -> Message(st.error ?: "", error = true)
            results.isEmpty() && st.searched -> Message("Nothing found.")
            results.isEmpty() -> Message("Search songs, albums or playlists on YouTube Music,\nor paste a link.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(results) { i, r ->
                    val bg = if (i % 2 == 1) Xp.Zebra else Color.White
                    when (r) {
                        is YtMusic.Result.Song -> ResultRow(
                            bg, r.track.title,
                            r.track.artist + if (r.track.durationMs > 0) " · ${fmtTime(r.track.durationMs)}" else "",
                            false, { vm.ytPlay(r) }, { vm.ytAdd(r) },
                        )
                        is YtMusic.Result.Playlist -> ResultRow(
                            bg, r.title, r.subtitle, true, { vm.ytPlay(r) }, { vm.ytAdd(r) },
                        )
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            st.loading -> Text("Loading…", color = Xp.InkSoft, fontSize = 13.sp)
                            st.error != null -> Text(st.error ?: "", color = Color(0xFFB00020), fontSize = 13.sp)
                            st.hasMore -> XpButton("More results", { vm.ytMore() }, minWidth = 140.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, error: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (error) Color(0xFFB00020) else Xp.InkSoft, fontSize = 14.sp)
    }
}

@Composable
private fun ResultRow(
    bg: Color,
    title: String,
    subtitle: String,
    collection: Boolean,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(bg).clickable(onClick = onPlay).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (collection) {
            Box(
                Modifier.size(width = 6.dp, height = 36.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFB8D4F8), Xp.Sel))),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = if (collection) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 11.sp, color = Xp.InkSoft, fontFamily = FontFamily.Default,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(44.dp).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
