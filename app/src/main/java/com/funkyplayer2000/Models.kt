package com.funkyplayer2000

data class Track(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val durationMs: Long,
    val number: Int = 0,
    val year: Int = 0,
)

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
