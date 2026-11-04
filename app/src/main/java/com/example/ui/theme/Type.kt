package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Baloo 2 for headlines and Mukta for reading (both crafted for Latin and Devanagari together)
val Baloo2HeadlineFont = FontFamily(
    Font(R.font.baloo_2, FontWeight.Normal),
    Font(R.font.baloo_2, FontWeight.Medium),
    Font(R.font.baloo_2, FontWeight.SemiBold),
    Font(R.font.baloo_2, FontWeight.Bold)
)

val MuktaReadingFont = FontFamily(
    Font(R.font.mukta, FontWeight.Normal),
    Font(R.font.mukta, FontWeight.Medium),
    Font(R.font.mukta, FontWeight.SemiBold),
    Font(R.font.mukta, FontWeight.Bold)
)

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Baloo2HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Baloo2HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Baloo2HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MuktaReadingFont,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = MuktaReadingFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MuktaReadingFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = MuktaReadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)
