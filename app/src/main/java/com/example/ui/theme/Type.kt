package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/** Baloo 2 and Mukta were both drawn for Latin and Devanagari together, by Ek Type in India. */
val Baloo = FontFamily(
    Font(R.font.baloo2_semibold, FontWeight.SemiBold),
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold)
)

val Mukta = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_semibold, FontWeight.SemiBold),
    Font(R.font.mukta_bold, FontWeight.Bold)
)

private fun baloo(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Baloo, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun mukta(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Mukta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val Typography = Typography(
    displayLarge = baloo(44, 48, FontWeight.ExtraBold),
    displayMedium = baloo(38, 42, FontWeight.ExtraBold),
    displaySmall = baloo(34, 38, FontWeight.ExtraBold),
    headlineLarge = baloo(30, 34, FontWeight.ExtraBold),
    headlineMedium = baloo(26, 30, FontWeight.ExtraBold),
    headlineSmall = baloo(23, 28, FontWeight.SemiBold),
    titleLarge = baloo(20, 26, FontWeight.SemiBold),
    titleMedium = mukta(17, 23, FontWeight.SemiBold),
    titleSmall = mukta(15, 20, FontWeight.SemiBold),
    bodyLarge = mukta(17, 25, FontWeight.Normal),
    bodyMedium = mukta(15, 21, FontWeight.Normal),
    bodySmall = mukta(14, 19, FontWeight.Normal),
    labelLarge = mukta(16, 20, FontWeight.SemiBold),
    labelMedium = mukta(14, 18, FontWeight.Medium),
    labelSmall = mukta(13, 16, FontWeight.Medium)
)
