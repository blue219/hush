package com.blue.hush.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

object HushSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val contentWidth = 640.dp
}
object HushMotion { const val TransitionMillis = 250 }
object HushShapes {
    val Panel = RoundedCornerShape(24.dp)
    val Control = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(50)
}
private val Colors = darkColorScheme(
    primary = HushColors.Accent, onPrimary = HushColors.OnAccent,
    primaryContainer = HushColors.Glow, onPrimaryContainer = HushColors.Text,
    secondary = HushColors.Lavender, onSecondary = HushColors.OnAccent,
    secondaryContainer = HushColors.SurfaceRaised, onSecondaryContainer = HushColors.Accent,
    tertiary = HushColors.Star, onTertiary = HushColors.OnAccent,
    tertiaryContainer = HushColors.Glow, onTertiaryContainer = HushColors.Text,
    surfaceTint = HushColors.Accent,
    background = HushColors.Background, onBackground = HushColors.Text,
    surface = HushColors.Surface, onSurface = HushColors.Text,
    surfaceVariant = HushColors.SurfaceRaised, onSurfaceVariant = HushColors.Muted,
    outline = HushColors.Border, outlineVariant = HushColors.Border, error = HushColors.Error,
)
@Composable
fun HushTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography,
        shapes = Shapes(small = HushShapes.Control, medium = HushShapes.Panel, large = HushShapes.Panel),
        content = content)
}
