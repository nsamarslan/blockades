package com.emre.bilbakalim.arsiv.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B3A5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF8A6100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA6),
    onSecondaryContainer = Color(0xFF2B1D00),
    background = Color(0xFFFBFCFF),
    surface = Color(0xFFFBFCFF),
    surfaceVariant = Color(0xFFE0E2EC),
    error = Color(0xFFBA1A1A)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA3C9FF),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF1B4A7D),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFFFC94D),
    onSecondary = Color(0xFF472A00),
    secondaryContainer = Color(0xFF684E00),
    onSecondaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB)
)

@Composable
fun SoruArsiviTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
