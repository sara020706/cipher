package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CipherPrimary,
    secondary = CipherSecondary,
    tertiary = CipherAccent,
    background = CipherDarkBackground,
    surface = CipherDarkSurface,
    surfaceVariant = CipherDarkSurfaceVariant,
    onPrimary = CipherDarkTextPrimary,
    onSecondary = CipherDarkTextPrimary,
    onTertiary = CipherDarkBackground,
    onBackground = CipherDarkTextSecondary,
    onSurface = CipherDarkTextPrimary,
    onSurfaceVariant = CipherDarkTextTertiary
)

private val LightColorScheme = lightColorScheme(
    primary = CipherPrimary,
    secondary = CipherSecondary,
    tertiary = CipherAccent,
    background = CipherLightBackground,
    surface = CipherLightSurface,
    surfaceVariant = CipherLightSurfaceVariant,
    onPrimary = CipherLightSurface,
    onSecondary = CipherLightSurface,
    onTertiary = CipherLightBackground,
    onBackground = CipherLightTextPrimary,
    onSurface = CipherLightTextPrimary,
    onSurfaceVariant = CipherLightTextSecondary
)

@Composable
fun CipherChatTheme(
    darkTheme: Boolean = true, // Dark theme is default
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce the beautiful brand identity
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
