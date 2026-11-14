package com.example.pixellauncher

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(60_000L - System.currentTimeMillis() % 60_000L)
    }
}

val ClockPalette = listOf(
    Color.Unspecified,
    Color.White,
    Color(0xFFFFE082),
    Color(0xFFF8BBD0),
    Color(0xFF90CAF9),
    Color(0xFFA5D6A7)
)

fun clockColor(idx: Int, ctx: Context): Color =
    if (idx <= 0 || idx >= ClockPalette.size) pixelColorScheme(ctx, true).primary else ClockPalette[idx]

private fun typefaceFor(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= 28) {
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
    } else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

@Composable
fun PixelClock(s: LauncherSettings, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val now by rememberNow()
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    val h = if (is24) "%02d".format(now.hour) else {
        val x = now.hour % 12
        (if (x == 0) 12 else x).toString()
    }
    val m = "%02d".format(now.minute)
    val lines = if (s.clockStyle == 1) listOf(h, m) else listOf("$h:$m")

    val color = remember(s.clockColor) { clockColor(s.clockColor, ctx) }
    val argb = color.toArgb()
    val weight = s.clockWeight
    val sizePx = with(density) { s.clockSize.sp.toPx() }
    val vs = s.clockHeight

    val paint = remember(weight, sizePx, argb) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typefaceFor(weight)
            textSize = sizePx
            this.color = argb
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.02f
        }
    }
    val fm = paint.fontMetrics
    val lineH = (fm.descent - fm.ascent) * 0.8f
    val maxW = lines.maxOf { paint.measureText(it) }

    Canvas(
        modifier.size(
            with(density) { maxW.toDp() },
            with(density) { (lineH * lines.size * vs).toDp() }
        )
    ) {
        drawIntoCanvas { c ->
            val n = c.nativeCanvas
            n.save()
            n.scale(1f, vs)
            lines.forEachIndexed { i, t ->
                n.drawText(t, size.width / 2f, i * lineH - fm.ascent * 0.8f, paint)
            }
            n.restore()
        }
    }
}

private val textShadow = Shadow(Color(0x66000000), Offset(0f, 2f), 8f)

const val WEATHER_STUB = "☀  22°C · Солнечно"

@Composable
fun AtAGlance(modifier: Modifier = Modifier, center: Boolean = false) {
    val now by rememberNow()
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    Column(
        modifier,
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(date, style = TextStyle(color = Color.White, fontSize = 24.sp, shadow = textShadow))
        Spacer(Modifier.height(2.dp))
        Text(
            WEATHER_STUB,
            style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, shadow = textShadow)
        )
    }
}
