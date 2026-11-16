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
    primary = TitaniumPrimaryDark,
    onPrimary = TitaniumOnPrimaryDark,
    primaryContainer = TitaniumContainerDark,
    onPrimaryContainer = TitaniumOnContainerDark,
    secondary = SteelSecondaryDark,
    onSecondary = SteelOnSecondaryDark,
    secondaryContainer = SteelContainerDark,
    onSecondaryContainer = SteelOnContainerDark,
    tertiary = PhosphorTertiaryDark,
    onTertiary = PhosphorOnTertiaryDark,
    tertiaryContainer = PhosphorContainerDark,
    onTertiaryContainer = PhosphorOnContainerDark,
    background = CharcoalBg,
    onBackground = CharcoalOnBg,
    surface = CharcoalSurface,
    onSurface = CharcoalOnBg,
    surfaceVariant = CharcoalSurfaceContainer,
    onSurfaceVariant = CharcoalOnSurfaceVar,
    surfaceContainerLowest = CharcoalSurfaceLowest,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalSurfaceContainer,
    surfaceContainerHigh = CharcoalSurfaceHigh,
    surfaceContainerHighest = CharcoalSurfaceHighest,
    outline = CharcoalOutline,
    outlineVariant = CharcoalOutlineVariant,
    error = ErrorDark,
    onError = ErrorOnDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val IbCoderLightColorScheme = lightColorScheme(
    primary = ObsidianPrimaryLight,
    onPrimary = ObsidianOnPrimaryLight,
    primaryContainer = ObsidianContainerLight,
    onPrimaryContainer = ObsidianOnContainerLight,
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

val IbCoderShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
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
