package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.domain.model.ThemeMode

private val AiCoderDarkColorScheme = darkColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = CyanOnPrimaryDark,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = CyanOnContainerDark,
    secondary = VioletSecondaryDark,
    onSecondary = VioletOnSecondaryDark,
    secondaryContainer = VioletContainerDark,
    onSecondaryContainer = VioletOnContainerDark,
    tertiary = EmeraldTertiaryDark,
    onTertiary = Color(0xFF03261B),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = EmeraldOnContainerDark,
    background = ObsidianBg,
    onBackground = ObsidianOnBg,
    surface = ObsidianSurface,
    onSurface = ObsidianOnBg,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = ObsidianOnSurfaceVar,
    surfaceContainerLowest = Color(0xFF080B10),
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = Color(0xFF28344B),
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1F293D),
    error = ErrorDark,
    onError = Color(0xFF3F0B11),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val AiCoderLightColorScheme = lightColorScheme(
    primary = CeruleanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CeruleanContainerLight,
    onPrimaryContainer = CeruleanOnContainerLight,
    secondary = IndigoSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = IndigoContainerLight,
    onSecondaryContainer = IndigoOnContainerLight,
    tertiary = EmeraldTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = EmeraldOnContainerLight,
    background = AlabasterBg,
    onBackground = AlabasterOnBg,
    surface = AlabasterSurface,
    onSurface = AlabasterOnBg,
    surfaceVariant = AlabasterSurfaceHigh,
    onSurfaceVariant = AlabasterOnSurfaceVar,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = AlabasterSurface,
    surfaceContainer = AlabasterSurfaceHigh,
    surfaceContainerHigh = AlabasterSurfaceHighest,
    surfaceContainerHighest = Color(0xFFD5DFED),
    outline = AlabasterOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

@Composable
fun AiCoderTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (useDark) AiCoderDarkColorScheme else AiCoderLightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
