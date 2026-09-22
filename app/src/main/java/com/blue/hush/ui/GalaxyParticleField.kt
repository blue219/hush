package com.blue.hush.ui

import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushShapes

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.blue.hush.session.StateSample
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class Star(val radius: Float, val angle: Float, val phase: Float, val size: Float)

private val stars = Random(42).let { random ->
    List(720) { index ->
        val radius = sqrt(random.nextFloat())
        Star(
            radius,
            (index % 4) * PI.toFloat() / 2f + radius * 5.5f + (random.nextFloat() - 0.5f) * 0.55f,
            random.nextFloat() * 2f * PI.toFloat(),
            0.45f + random.nextFloat() * 1.15f,
        )
    }
}

@Composable
fun GalaxyParticleField(sample: StateSample?, dataGap: Boolean, paused: Boolean, modifier: Modifier = Modifier) {
    val target = rememberUpdatedState(if (dataGap) null else galaxyAgitation(sample))
    val state = rememberSaveable(saver = listSaver(
        save = { listOf(it.phase, it.agitation, it.visibility) },
        restore = { GalaxyMotion(it[0], it[1], it[2]) },
    )) { GalaxyMotion() }
    val motion = remember { mutableFloatStateOf(state.phase) }
    val agitation = remember { mutableFloatStateOf(state.agitation) }
    val visibility = remember { mutableFloatStateOf(state.visibility) }
    val view = LocalView.current
    LaunchedEffect(paused, view) {
        if (paused) return@LaunchedEffect
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (previous == 0L) 0f else (now - previous) / 1_000_000_000f
                    previous = now
                    state.advance(dt, target.value)
                    motion.floatValue = state.phase
                    agitation.floatValue = state.agitation
                    visibility.floatValue = state.visibility
                }
            }
        }
    }
    Canvas(modifier) {
        val time = motion.floatValue
        val chaos = agitation.floatValue
        val opacity = visibility.floatValue
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val radius = size.minDimension * 0.36f
        if (radius <= 0f) return@Canvas
        drawRect(HushColors.Background)
        drawRect(Brush.radialGradient(listOf(HushColors.Glow.copy(alpha = opacity * 0.65f), Color.Transparent), center, radius * 1.35f))
        stars.forEachIndexed { index, star ->
            val angle = star.angle + time
            val drift = radius * chaos * 0.43f
            val x = cos(angle) * star.radius * radius + sin(time * 5f + star.phase) * drift
            val y = sin(angle) * star.radius * radius * 0.64f + cos(time * 4.3f + star.phase * 2f) * drift
            val position = center + Offset(x * 0.94f - y * 0.34f, x * 0.34f + y * 0.94f)
            val color = when (index % 5) {
                0 -> HushColors.Warm
                1 -> HushColors.Lavender
                else -> HushColors.Star
            }
            val alpha = opacity * (0.55f + sin(time * 2f + star.phase) * 0.2f)
            val dotRadius = star.size * (size.minDimension / 360f)
            if (index % 7 == 0) drawCircle(color.copy(alpha = alpha * 0.1f), dotRadius * 4f, position)
            drawCircle(color.copy(alpha = alpha), dotRadius, position)
        }
        drawCircle(
            Brush.radialGradient(listOf(HushColors.Warm.copy(alpha = opacity * (0.6f - chaos * 0.35f)), Color.Transparent), center, radius * 0.22f),
            radius * 0.22f,
            center,
        )
    }
}
