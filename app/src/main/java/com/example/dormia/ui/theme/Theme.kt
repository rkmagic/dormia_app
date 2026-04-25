package com.example.dormia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DormiaPrimary,
    secondary = DormiaViolet,
    tertiary = DormiaAmber,
    background = DormiaBackground,
    surface = DormiaBackground,
    onPrimary = DormiaText,
    onSecondary = DormiaText,
    onBackground = DormiaText,
    onSurface = DormiaText
)

@Composable
fun DormiaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Keep parameters to preserve call-site compatibility.
    @Suppress("UNUSED_VARIABLE")
    val ignored = darkTheme || dynamicColor
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}