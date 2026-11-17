package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

private val IbCoderDarkColorScheme = darkColorScheme(
    primary = IdeCyanPrimaryDark,
    onPrimary = IdeOnCyanPrimaryDark,
    primaryContainer = IdeCyanContainerDark,
    onPrimaryContainer = IdeOnCyanContainerDark,
    secondary = IdeSteelSecondaryDark,
    onSecondary = IdeOnSteelSecondaryDark,
    secondaryContainer = IdeSteelContainerDark,
    onSecondaryContainer = IdeOnSteelContainerDark,
    tertiary = IdeMintTertiaryDark,
    onTertiary = IdeOnMintTertiaryDark,
    tertiaryContainer = IdeMintContainerDark,
    onTertiaryContainer = IdeOnMintContainerDark,
    background = IdeBgDark,
    onBackground = IdeTextPrimaryDark,
    surface = IdePanelBgDark,
    onSurface = IdeTextPrimaryDark,
    surfaceVariant = IdeSurfaceContainerDark,
    onSurfaceVariant = IdeTextMutedDark,
    surfaceContainerLowest = IdeEditorBgDark,
    surfaceContainerLow = IdePanelBgDark,
    surfaceContainer = IdeSurfaceContainerDark,
    surfaceContainerHigh = IdeChromeHighDark,
    surfaceContainerHighest = IdeSurfaceHighestDark,
    outline = IdeOutlineDark,
    outlineVariant = IdeOutlineSubtleDark,
    error = ErrorDark,
    onError = ErrorOnDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val IbCoderLightColorScheme = lightColorScheme(
    primary = IdeBluePrimaryLight,
    onPrimary = IdeOnBluePrimaryLight,
    primaryContainer = IdeBlueContainerLight,
    onPrimaryContainer = IdeOnBlueContainerLight,
    secondary = GunmetalSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = GunmetalContainerLight,
    onSecondaryContainer = GunmetalOnContainerLight,
    tertiary = SlateTealTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = SlateTealContainerLight,
    onTertiaryContainer = SlateTealOnContainerLight,
    background = PearlBg,
    onBackground = PearlOnBg,
    surface = PearlSurface,
    onSurface = PearlOnBg,
    surfaceVariant = PearlSurfaceContainer,
    onSurfaceVariant = PearlOnSurfaceVar,
    surfaceContainerLowest = PearlSurfaceLowest,
    surfaceContainerLow = PearlSurface,
    surfaceContainer = PearlSurfaceContainer,
    surfaceContainerHigh = PearlSurfaceHigh,
    surfaceContainerHighest = PearlSurfaceHighest,
    outline = PearlOutline,
    outlineVariant = PearlOutlineVariant,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

// Crisp, technical IDE geometry matching the reference interface
val IbCoderShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
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
    val colors = if (useDark) IbCoderDarkColorScheme else IbCoderLightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = IbCoderShapes,
        content = content
    )
}
