package com.blue.hush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors

@Composable
internal fun ParticlePanel(sample: StateSample?, dataGap: Boolean) {
    Card(shape = com.blue.hush.ui.theme.HushShapes.Panel) {
        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val alpha = sample?.alpha?.toFloat() ?: 0.2f
                val theta = sample?.theta?.toFloat() ?: 0.2f
                val beta = sample?.beta?.toFloat() ?: 0.2f
                val stillness = sample?.stillness?.toFloat() ?: 0f
                val radius = 42f + stillness * 38f
                for (index in 0 until 72) {
                    val angle = index * 0.47f + alpha * 2.4f
                    val distance = radius + (index % 9) * (8f + theta * 10f) + beta * 12f
                    val x = center.x + kotlin.math.cos(angle.toDouble()).toFloat() * distance
                    val y = center.y + kotlin.math.sin(angle.toDouble()).toFloat() * distance
                    val color = androidx.compose.ui.graphics.lerp(HushColors.Star, HushColors.Lavender,
                        theta.coerceIn(0f, 1f)).copy(alpha = if (sample?.valid == true) 0.72f else 0.18f)
                    drawCircle(color, radius = 2.2f + (index % 3), center = Offset(x, y))
                }
                drawCircle(HushColors.Accent.copy(alpha = if (sample?.valid == true) 0.18f else 0.08f), radius = radius, center = center, style = Stroke(width = 18f))
            }
            if (dataGap) Text("Data gap", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TrendChart(samples: List<StateSample>) {
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        if (samples.count { it.valid } < 2) return@Canvas
        val colors = HushColors.Trends
        val values = listOf<(StateSample) -> Double?>({ it.alpha }, { it.theta }, { it.beta }, { it.stillness })
        values.forEachIndexed { seriesIndex, selector ->
            var path: Path? = null
            samples.forEachIndexed { index, sample ->
                val value = selector(sample)
                if (!sample.valid || value == null || !value.isFinite()) {
                    path?.let { drawPath(it, colors[seriesIndex], style = Stroke(width = 4f)) }
                    path = null
                    return@forEachIndexed
                }
                val x = index.toFloat() / (samples.lastIndex).coerceAtLeast(1) * size.width
                val y = size.height - value.toFloat().coerceIn(0f, 1f) * size.height
                if (path == null) path = Path().also { it.moveTo(x, y) } else path?.lineTo(x, y)
            }
            path?.let { drawPath(it, colors[seriesIndex], style = Stroke(width = 4f)) }
        }
    }
}


@Composable
internal fun HushNavIcon(tab: AppTab) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(1.5.dp.toPx())
        if (tab == AppTab.HISTORY) {
            drawCircle(color, size.minDimension * 0.4f, style = stroke)
            drawLine(color, center, Offset(center.x, size.height * 0.24f), strokeWidth = stroke.width)
            drawLine(color, center, Offset(size.width * 0.68f, size.height * 0.6f), strokeWidth = stroke.width)
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.45f)
                lineTo(size.width * 0.5f, size.height * 0.1f)
                lineTo(size.width * 0.9f, size.height * 0.45f)
                lineTo(size.width * 0.9f, size.height * 0.9f)
                lineTo(size.width * 0.1f, size.height * 0.9f)
                close()
            }
            drawPath(path, color, style = stroke)
        }
    }
}

// Decorative session identifier; this thumbnail does not encode physiological data.
@Composable
internal fun MindprintThumbnail(id: Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        for (i in 0 until 72) {
            val radius = size.minDimension * 0.44f * kotlin.math.sqrt(i / 72f)
            val angle = i * 2.4f + (id % 31).toFloat()
            val point = center + Offset(kotlin.math.cos(angle) * radius, kotlin.math.sin(angle) * radius * 0.75f)
            drawCircle(HushColors.Trends[i % 4].copy(alpha = 0.7f), 1.dp.toPx(), point)
        }
    }
}
