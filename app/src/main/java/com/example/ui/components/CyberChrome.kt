package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SilkscreenFamily

/**
 * Draws subtle CRT horizontal phosphor scanlines and a faint pixel grid using DrawScope size.
 */
fun Modifier.cyberScanlineGrid(
    accentColor: Color,
    scanlineAlpha: Float = 0.045f,
    gridAlpha: Float = 0.035f
): Modifier = this.drawBehind {
    val gridStep = 24.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = accentColor.copy(alpha = gridAlpha),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += gridStep
    }
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = accentColor.copy(alpha = gridAlpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += gridStep
    }

    val scanStep = 4.dp.toPx()
    var sy = 0f
    while (sy < size.height) {
        drawLine(
            color = Color.Black.copy(alpha = scanlineAlpha),
            start = Offset(0f, sy),
            end = Offset(size.width, sy),
            strokeWidth = 1f
        )
        sy += scanStep
    }
}

/**
 * Pixel-framed window panel inspired by retro cyber-codec terminals with glowing title bar and corner indicators.
 */
@Composable
fun CyberWindowPanel(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    headerActions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val headerBg = if (isDark) {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.18f),
                MaterialTheme.colorScheme.surfaceContainer,
                accentColor.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.14f),
                MaterialTheme.colorScheme.surfaceContainer
            )
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.25.dp, accentColor.copy(alpha = if (isDark) 0.72f else 0.55f)),
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status pixel LED
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accentColor, RoundedCornerShape(1.dp))
                )
                Text(
                    text = title.uppercase(),
                    fontFamily = SilkscreenFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.9.sp,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!badgeText.isNullOrBlank()) {
                    Text(
                        text = badgeText,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (headerActions != null) {
                    headerActions()
                } else {
                    CyberWindowGlyphs(accentColor = accentColor)
                }
            }
            HorizontalDivider(color = accentColor.copy(alpha = 0.35f))
            content()
        }
    }
}

@Composable
fun CyberWindowGlyphs(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { idx ->
            val boxColor = when (idx) {
                0 -> accentColor.copy(alpha = 0.45f)
                1 -> accentColor.copy(alpha = 0.72f)
                else -> accentColor
            }
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .border(1.dp, boxColor, RoundedCornerShape(1.dp))
                    .background(boxColor.copy(alpha = 0.15f), RoundedCornerShape(1.dp))
            )
        }
    }
}
