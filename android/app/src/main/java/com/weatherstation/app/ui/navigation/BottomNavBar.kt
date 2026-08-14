package com.weatherstation.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeatherBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navShape = RoundedCornerShape(32.dp)

    // 100% Transparent outer background wrapper so wallpaper shows behind it
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 48.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pure White Minimalist Floating Dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = navShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(navShape)
                .background(Color.White)
                .border(
                    width = 1.dp,
                    color = Color(0xFFE2E8F0),
                    shape = navShape
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavScreens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val interactionSource = remember { MutableInteractionSource() }

                    val animatedScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.03f else 0.97f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "nav_scale"
                    )

                    val animatedIconTint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color(0xFF64748B),
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "nav_icon_tint"
                    )

                    val animatedTextColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color(0xFF64748B),
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "nav_text_color"
                    )

                    val pillPaddingHorizontal by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 16.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "nav_pill_padding"
                    )

                    val activeBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF4F46E5), // Indigo 600
                            Color(0xFF6366F1)  // Indigo 500
                        )
                    )

                    Box(
                        modifier = Modifier
                            .scale(animatedScale)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSelected) activeBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .then(
                                if (isSelected) {
                                    Modifier.shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color(0xFF4F46E5).copy(alpha = 0.35f))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onNavigate(screen.route)
                            }
                            .padding(horizontal = pillPaddingHorizontal, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = animatedIconTint,
                                modifier = Modifier.size(20.dp)
                            )

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = screen.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = animatedTextColor,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
