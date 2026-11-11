package com.funkyplayer2000.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funkyplayer2000.R

/** Windows XP "Luna" look. */
object Xp {
    val Face = Color(0xFFECE9D8)
    val FaceDark = Color(0xFFACA899)
    val FieldBorder = Color(0xFF7F9DB9)
    val Blue1 = Color(0xFF0A5AE6)
    val Blue2 = Color(0xFF3D95FF)
    val BlueDark = Color(0xFF0838A8)
    val Sel = Color(0xFF316AC5)
    val Zebra = Color(0xFFF4F2EA)
    val Lcd = Color(0xFF0B1A0B)
    val LcdLine = Color(0xFF12301A)
    val InkSoft = Color(0xFF4A4A4A)
    val ButtonBorder = Color(0xFF003C74)
    val TitleBrush = Brush.verticalGradient(listOf(Blue2, Blue1, Color(0xFF0A4FD0), BlueDark))
}

/** White "sunken" box, like a text field or list in XP. */
fun Modifier.field(): Modifier =
    this.background(Color.White).border(1.dp, Xp.FieldBorder)

@Composable
fun XpTitleBar(title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Xp.TitleBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(com.funkyplayer2000.R.drawable.funky_icon), null, Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun XpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = 44.dp,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val down = pressed || active
    val shape = RoundedCornerShape(3.dp)
    val colors = if (down) listOf(Color(0xFFD6D0C0), Color(0xFFF4F2EA))
    else listOf(Color.White, Xp.Face, Color(0xFFD6D0C0))
    Box(
        modifier
            .defaultMinSize(minWidth = minWidth, minHeight = 40.dp)
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(1.dp, Xp.ButtonBorder, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.Black else Color.Gray,
            maxLines = 1,
        )
    }
}

@Composable
fun XpTab(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    Box(
        modifier
            .height(if (selected) 38.dp else 34.dp)
            .clip(shape)
            .background(if (selected) Color.White else Color(0xFFE3DFCC))
            .border(1.dp, Xp.FaceDark, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Seek bar: white trough, blue fill, rectangular thumb. Tap or drag. */
@Composable
fun XpSeekBar(fraction: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: fraction).coerceIn(0f, 1f)
    Box(
        modifier
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { off -> onSeek((off.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let(onSeek); dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val troughH = 10.dp.toPx()
            val top = (size.height - troughH) / 2
            drawRect(Color.White, Offset(0f, top), Size(size.width, troughH))
            drawRect(Xp.Sel, Offset(0f, top), Size(size.width * shown, troughH))
            drawRect(Xp.FieldBorder, Offset(0f, top), Size(size.width, troughH), style = Stroke(1.dp.toPx()))
            val tw = 12.dp.toPx()
            val th = 24.dp.toPx()
            val x = (size.width - tw) * shown
            drawRect(
                Brush.horizontalGradient(listOf(Color.White, Color(0xFFCFD8E8)), startX = x, endX = x + tw),
                Offset(x, (size.height - th) / 2),
                Size(tw, th),
            )
            drawRect(Xp.ButtonBorder, Offset(x, (size.height - th) / 2), Size(tw, th), style = Stroke(1.dp.toPx()))
        }
    }
}
