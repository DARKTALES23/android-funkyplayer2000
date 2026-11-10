package com.funkyplayer2000.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.funkyplayer2000.AudioTap

private const val BANDS = 32

/** LED-style spectrum analyzer (tap it to switch to an oscilloscope). Uses the real audio being played. */
@Composable
fun Visualizer(playing: Boolean, modifier: Modifier = Modifier) {
    var scope by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    val levels = remember { FloatArray(BANDS) }
    val peaks = remember { FloatArray(BANDS) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (t - last > 28_000_000L) {
                    last = t
                    tick = t
                }
            }
        }
    }

    Canvas(modifier.background(Xp.Lcd).clickable { scope = !scope }) {
        @Suppress("UNUSED_VARIABLE")
        val frame = tick   // reading it makes the canvas redraw on every tick
        val w = size.width
        val h = size.height

        var gy = 0f
        while (gy < h) {
            drawLine(Xp.LcdLine, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            gy += h / 6f
        }

        if (!scope) {
            val bands = if (playing) AudioTap.spectrum(BANDS) else FloatArray(BANDS)
            val bw = w / BANDS
            val seg = 4.dp.toPx()
            for (i in 0 until BANDS) {
                levels[i] = maxOf(bands[i], levels[i] - 0.05f)
                peaks[i] = maxOf(levels[i], peaks[i] - 0.015f)
                var y = 0f
                while (y < levels[i] * h) {
                    val k = y / h
                    val color = when {
                        k < 0.55f -> Color(0xFF19D83C)
                        k < 0.8f -> Color(0xFFE8E61C)
                        else -> Color(0xFFFF3B1E)
                    }
                    drawRect(color, Offset(i * bw + 1f, h - y - seg + 1f), Size(bw - 2f, seg - 1f))
                    y += seg
                }
                val py = h - peaks[i] * h
                drawRect(Color(0xFFD8FFD8), Offset(i * bw + 1f, py - 3f), Size(bw - 2f, 3f))
            }
        } else if (playing) {
            val s = AudioTap.snapshot()
            val path = Path()
            val n = 512
            val start = s.size - n
            for (i in 0 until n) {
                val x = i / (n - 1f) * w
                val y = h / 2f - (s[start + i] * 1.6f).coerceIn(-1f, 1f) * h / 2f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF4DFF6A), style = Stroke(2.dp.toPx()))
        }
    }
}
