package com.blue.hush.ui

import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushShapes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class HomeParticle(
    val angle: Float,
    val distance: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
    val phase: Float,
    val warmth: Float,
)

private val homeParticles = List(104) { index ->
    // A fixed distribution keeps the field stable across recompositions while
    // the animated phase makes the scene feel alive instead of random.
    val seed = (index * 37 + 11) % 101
    HomeParticle(
        angle = index * 0.73f,
        distance = 0.18f + (seed / 100f) * 0.78f,
        speed = 0.12f + ((index * 17) % 23) / 100f,
        size = 1.2f + ((index * 13) % 12) / 4f,
        alpha = 0.22f + ((index * 19) % 62) / 100f,
        phase = index * 1.17f,
        warmth = ((index * 29) % 100) / 100f,
    )
}

@Composable
fun HomeParticleField(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "home-particle-field")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "home-particle-progress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(264.dp)
            .clip(HushShapes.Panel)
            .background(HushColors.Background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.52f)
            val maxDistance = size.minDimension * 0.53f
            val cycle = progress * (PI.toFloat() * 2f)
            val breath = 1f + sin(cycle * 2f) * 0.035f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        HushColors.Glow.copy(alpha = 0.52f),
                        HushColors.Surface.copy(alpha = 0.34f),
                        HushColors.Background.copy(alpha = 0f),
                    ),
                    center = center,
                    radius = size.maxDimension * 0.72f,
                ),
            )

            drawCircle(
                color = HushColors.Lavender.copy(alpha = 0.07f),
                radius = maxDistance * 0.33f * breath,
                center = center,
            )
            drawCircle(
                color = HushColors.Accent.copy(alpha = 0.12f),
                radius = maxDistance * 0.12f * breath,
                center = center,
            )

            homeParticles.forEach { particle ->
                val distance = maxDistance * particle.distance * breath
                val angle = particle.angle + cycle * particle.speed
                val drift = sin(cycle * 1.7f + particle.phase) * size.minDimension * 0.018f
                val position = Offset(
                    x = center.x + cos(angle) * distance + cos(angle * 1.8f) * drift,
                    y = center.y + sin(angle) * distance + sin(angle * 1.35f) * drift,
                )
                val twinkle = 0.76f + sin(cycle * 2.2f + particle.phase) * 0.24f
                val color = if (particle.warmth > 0.74f) {
                    HushColors.Warm
                } else {
                    HushColors.Star
                }
                val alpha = particle.alpha * twinkle

                if (particle.size > 3f) {
                    drawCircle(color.copy(alpha = alpha * 0.12f), particle.size * 4.2f, position)
                }
                drawCircle(color.copy(alpha = alpha), particle.size, position)
            }

            drawCircle(
                color = HushColors.Text.copy(alpha = 0.66f),
                radius = 2.4f,
                center = center,
            )
        }
    }
}
