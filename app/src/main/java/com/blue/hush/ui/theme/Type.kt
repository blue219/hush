package com.blue.hush.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun text(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight,
    fontSize = size.sp, lineHeight = height.sp,
)
val Typography = Typography(
    displayLarge = text(48, 56, FontWeight.Light),
    displayMedium = text(48, 56, FontWeight.Light),
    displaySmall = text(28, 36, FontWeight.Light),
    headlineLarge = text(28, 36, FontWeight.Light),
    headlineMedium = text(28, 36, FontWeight.Light),
    headlineSmall = text(22, 30, FontWeight.Light),
    titleLarge = text(22, 30, FontWeight.Light),
    titleMedium = text(16, 24, FontWeight.Medium),
    titleSmall = text(14, 20, FontWeight.Medium),
    bodyLarge = text(16, 24), bodyMedium = text(14, 20), bodySmall = text(12, 18),
    labelLarge = text(14, 20, FontWeight.Medium),
    labelMedium = text(12, 18, FontWeight.Medium), labelSmall = text(12, 18),
)
