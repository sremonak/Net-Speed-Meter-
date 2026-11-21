package com.sremonak.netspeedmeter.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sremonak.netspeedmeter.data.DailyUsage
import com.sremonak.netspeedmeter.monitor.SpeedSample
import com.sremonak.netspeedmeter.ui.theme.Brand
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/** Logarithmic 0..1 mapping so both 100 Kbps and 1 Gbps are readable on the same dial. */
fun gaugeFraction(mbps: Double, maxMbps: Double = 1000.0): Float =
    (log10(1 + mbps.coerceAtLeast(0.0)) / log10(1 + maxMbps)).toFloat().coerceIn(0f, 1f)

@Composable
fun SpeedGauge(
    mbps: Double,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    startColor: Color = Brand.GradientStart,
    endColor: Color = Brand.GradientEnd,
) {
    val fraction by animateFloatAsState(
        targetValue = gaugeFraction(mbps),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "gauge"
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = tickColor, fontSize = 10.sp)

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val dim = size.minDimension
            val stroke = dim * 0.065f
            val inset = stroke / 2 + dim * 0.10f
            val arcSize = Size(dim - inset * 2, dim - inset * 2)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            val startAngle = 150f
            val sweep = 240f
            val center = Offset(size.width / 2, size.height / 2)
            val radius = arcSize.width / 2

            drawArc(track, startAngle, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val brush = Brush.linearGradient(listOf(startColor, Brand.GradientMid, endColor), start = Offset(0f, 0f), end = Offset(size.width, 0f))
            // soft glow
            drawArc(brush, startAngle, sweep * fraction, false, topLeft, arcSize, alpha = 0.25f, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
            drawArc(brush, startAngle, sweep * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

            // ticks
            val ticks = 40
            for (i in 0..ticks) {
                val a = Math.toRadians((startAngle + sweep * i / ticks).toDouble())
                val major = i % 5 == 0
                val r1 = radius - stroke * 1.1f
                val r2 = r1 - if (major) stroke * 0.9f else stroke * 0.45f
                drawLine(
                    color = tickColor.copy(alpha = if (major) 0.7f else 0.3f),
                    start = Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                    end = Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()),
                    strokeWidth = if (major) 2.5f else 1.5f,
                    cap = StrokeCap.Round
                )
            }
            // scale labels (Mbps, log scale)
            listOf(0, 1, 5, 10, 50, 100, 500, 1000).forEach { mark ->
                val f = gaugeFraction(mark.toDouble())
                val a = Math.toRadians((startAngle + sweep * f).toDouble())
                val r = radius + stroke * 1.6f
                val text = if (mark >= 1000) "1G" else mark.toString()
                val layout = measurer.measure(text, labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(
                        center.x + r * cos(a).toFloat() - layout.size.width / 2f,
                        center.y + r * sin(a).toFloat() - layout.size.height / 2f
                    )
                )
            }
            // needle head
            val a = Math.toRadians((startAngle + sweep * fraction).toDouble())
            val head = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
            drawCircle(Color.White, radius = stroke * 0.55f, center = head)
            drawCircle(endColor, radius = stroke * 0.30f, center = head)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
            Text(value, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun LiveSpeedChart(
    samples: List<SpeedSample>,
    modifier: Modifier = Modifier,
    capacity: Int = 60,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        for (i in 0..3) {
            val y = h * i / 3f
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.5f, pathEffect = dash)
        }
        if (samples.size < 2) return@Canvas
        val maxV = max(samples.maxOf { max(it.rx, it.tx) }.toFloat(), 64f * 1024f) * 1.15f
        val n = samples.size
        fun x(i: Int) = w * (i + capacity - n).toFloat() / (capacity - 1)
        fun y(v: Long) = h - (v / maxV) * h

        fun smooth(sel: (SpeedSample) -> Long): Path = Path().apply {
            moveTo(x(0), y(sel(samples[0])))
            for (i in 1 until n) {
                val px = x(i - 1)
                val py = y(sel(samples[i - 1]))
                val cx = x(i)
                val cy = y(sel(samples[i]))
                val mx = (px + cx) / 2
                cubicTo(mx, py, mx, cy, cx, cy)
            }
        }

        val down = smooth { it.rx }
        val fill = Path().apply {
            addPath(down)
            lineTo(x(n - 1), h)
            lineTo(x(0), h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(Brand.Download.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(down, Brand.Download, style = Stroke(5f, cap = StrokeCap.Round))
        val up = smooth { it.tx }
        val upFill = Path().apply {
            addPath(up)
            lineTo(x(n - 1), h)
            lineTo(x(0), h)
            close()
        }
        drawPath(upFill, Brush.verticalGradient(listOf(Brand.Upload.copy(alpha = 0.18f), Color.Transparent)))
        drawPath(up, Brand.Upload, style = Stroke(4f, cap = StrokeCap.Round))
        drawCircle(Brand.Download, 7f, Offset(x(n - 1), y(samples.last().rx)))
        drawCircle(Brand.Upload, 6f, Offset(x(n - 1), y(samples.last().tx)))
    }
}

@Composable
fun MiniLineChart(points: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val maxV = max(points.max(), 1f) * 1.1f
        val step = size.width / (points.size - 1)
        val path = Path().apply {
            points.forEachIndexed { i, v ->
                val px = i * step
                val py = size.height - v / maxV * size.height
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(path, color, style = Stroke(4f, cap = StrokeCap.Round))
    }
}

@Composable
fun UsageDonut(mobile: Long, wifi: Long, modifier: Modifier = Modifier, strokeWidth: Float = 26f) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val total = (mobile + wifi).toFloat()
    val mobileF by animateFloatAsState(if (total > 0) mobile / total else 0f, label = "donutMobile")
    val wifiF by animateFloatAsState(if (total > 0) wifi / total else 0f, label = "donutWifi")
    Canvas(modifier.aspectRatio(1f)) {
        val inset = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val tl = Offset(inset, inset)
        drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(strokeWidth))
        val gap = if (mobileF > 0f && wifiF > 0f) 6f else 0f
        if (mobileF > 0f) drawArc(Brand.Mobile, -90f, (360f * mobileF - gap).coerceAtLeast(1f), false, tl, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        if (wifiF > 0f) drawArc(Brand.Wifi, -90f + 360f * mobileF, (360f * wifiF - gap).coerceAtLeast(1f), false, tl, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
    }
}

@Composable
fun UsageBarChart(days: List<DailyUsage>, modifier: Modifier = Modifier, labelEvery: Int = 1, dayLabel: (DailyUsage) -> String) {
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val maxTotal = max(days.maxOfOrNull { it.total } ?: 0L, 1L).toFloat()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            if (days.isEmpty()) return@Canvas
            val slot = size.width / days.size
            val barW = (slot * 0.62f).coerceAtMost(46f)
            val radius = CornerRadius(barW / 2.5f, barW / 2.5f)
            days.forEachIndexed { i, d ->
                val left = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(left, 0f), Size(barW, size.height), radius)
                val mobileH = d.mobile / maxTotal * size.height
                val wifiH = d.wifi / maxTotal * size.height
                if (wifiH > 0f) drawRoundRect(Brand.Wifi, Offset(left, size.height - wifiH - mobileH), Size(barW, wifiH + mobileH), radius)
                if (mobileH > 0f) drawRoundRect(Brand.Mobile, Offset(left, size.height - mobileH), Size(barW, mobileH), radius)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, d ->
                Text(
                    if (i % labelEvery == 0 || i == days.lastIndex) dayLabel(d) else "",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == days.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
