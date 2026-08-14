package com.weatherstation.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weatherstation.app.ui.theme.FigmaCardBg
import com.weatherstation.app.ui.theme.FigmaGlassBorder

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    backgroundColor: Color = FigmaCardBg,
    borderColor: Color = FigmaGlassBorder,
    borderWidth: Dp = 1.dp,
    padding: Dp = 16.dp,
    elevation: Dp = 8.dp,
    gradientFill: Brush? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape)
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
            .background(gradientFill ?: Brush.verticalGradient(listOf(backgroundColor, backgroundColor.copy(alpha = 0.7f))))
            .padding(padding)
    ) {
        content()
    }
}
