package com.weatherstation.app.ui.screens.home

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherstation.app.R
import com.weatherstation.app.ui.components.HourlyCapsuleRail
import com.weatherstation.app.ui.components.WeatherHeroCard
import com.weatherstation.app.ui.theme.AuraGlassCapsuleBg
import com.weatherstation.app.ui.theme.AuraGlassCapsuleBorder
import com.weatherstation.app.ui.theme.DarkSlateText
import com.weatherstation.app.ui.theme.PorcelainSheetBg
import com.weatherstation.app.ui.theme.TextWhitePrimary
import com.weatherstation.app.ui.theme.TextWhiteSecondary
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val unit by viewModel.temperatureUnit.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeightDp = configuration.screenHeightDp.dp
    val coroutineScope = rememberCoroutineScope()

    val reading = uiState.reading
    val weatherType = reading?.effectiveWeatherType
    val verticalLabel = reading?.effectiveVerticalLabel.orEmpty()

    // Background Image Drawable Selector
    val bgDrawableRes = when (weatherType) {
        "sunset" -> R.drawable.weather_bg_sunset
        "nighttime" -> R.drawable.weather_bg_nighttime
        "sunrise" -> R.drawable.weather_bg_sunrise
        "rain" -> R.drawable.weather_bg_rain
        "thunderstorm" -> R.drawable.weather_bg_thunderstorm
        "snow" -> R.drawable.weather_bg_snow
        "foggy" -> R.drawable.weather_bg_foggy
        "daytime" -> R.drawable.weather_bg_daytime
        else -> null
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Dynamic Full-Bleed Atmospheric Illustrated Background with Smooth Crossfade
        if (bgDrawableRes != null) {
            Crossfade(
                targetState = bgDrawableRes,
                animationSpec = tween(durationMillis = 600),
                modifier = Modifier.fillMaxSize(),
                label = "bg_crossfade"
            ) { drawableId ->
                Image(
                    painter = painterResource(id = drawableId),
                    contentDescription = "Station lighting background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF172554), Color(0xFF312E81), Color(0xFF0F172A)))
                )
            )
        }

        // Ambient Soft Dark Overlay for optimal readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.20f)
                        )
                    )
                )
        )

        if (reading == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (uiState.isLoading) "Stansiyaga ulanmoqda..." else "Haqiqiy telemetriya kutilmoqda",
                    color = TextWhitePrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.errorMessage ?: "Arduino va ESP32 yangi sensor kadrini yuborganda u shu yerda ko'rinadi.",
                    color = TextWhiteSecondary,
                    fontSize = 12.sp
                )
            }
        } else if (isLandscape) {
            // =========================================================================
            // LANDSCAPE (HORIZONTAL) RESPONSIVE 2-COLUMN VIEW (UZBEK)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Left Pane: Weather Hero
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(end = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "📍", fontSize = 14.sp)
                            Column {
                                Text(
                                    text = reading.deviceId,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhitePrimary
                                )
                                Text(
                                    text = "Sensor station",
                                    fontSize = 10.sp,
                                    color = TextWhiteSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.refresh(manual = true) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AuraGlassCapsuleBg)
                        ) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yangilash", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    WeatherHeroCard(
                        reading = reading,
                        history = uiState.history,
                        unit = unit,
                        isOnline = uiState.isOnline
                    )
                }

                // Right pane: recorded station readings
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(36.dp))
                        .background(PorcelainSheetBg)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Bugungi ob-havo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DarkSlateText
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    HourlyCapsuleRail(
                        readings = uiState.history,
                        currentReading = reading,
                        unit = unit
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        } else {
            // =========================================================================
            // PORTRAIT: FULL-SCREEN HERO VIEWPORT FIRST, THEN SCROLL FOR WEATHER TODAY
            // =========================================================================
            val scrollState = rememberScrollState()

            // Subtle animated bouncing offset for scroll indicator cue
            val infiniteTransition = rememberInfiniteTransition(label = "scroll_cue_anim")
            val cueBounceOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "cue_bounce"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // =====================================================================
                // 1. FULL-SCREEN HERO VIEWPORT (Occupies Full Initial Viewport)
                // =====================================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeightDp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 42.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top App Bar (Uzbek)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "📍", fontSize = 18.sp)
                                Column {
                                    Text(
                                        text = reading.deviceId,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhitePrimary
                                    )
                                    Text(
                                        text = "Sensor station",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextWhiteSecondary
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.refresh(manual = true) },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(AuraGlassCapsuleBg)
                                    .border(1.dp, AuraGlassCapsuleBorder, CircleShape)
                            ) {
                                if (uiState.isRefreshing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Yangilash",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Center: Main Weather Hero with Vertical Label
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            WeatherHeroCard(
                                reading = reading,
                                history = uiState.history,
                                unit = unit,
                                isOnline = uiState.isOnline
                            )

                            // Vertical Small-Caps Editorial Label on Right Edge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 24.dp)
                            ) {
                                Text(
                                    text = verticalLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhitePrimary.copy(alpha = 0.85f),
                                    letterSpacing = 2.5.sp
                                )
                            }
                        }

                        // Bottom Viewport Anchor: Scroll Prompt Pill ("Bugungi ob-havo ⌄")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset(y = cueBounceOffset.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                                    .clickable {
                                        coroutineScope.launch {
                                            scrollState.animateScrollTo(screenHeightDp.value.toInt() * 2)
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Bugungi ob-havo",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextWhitePrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Batafsil ko'rish",
                                        tint = TextWhitePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================================
                // 2. "BUGUNGI OB-HAVO" PORCELAIN SHEET (Revealed upon scrolling down)
                // =====================================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 25.dp, shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                        .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                        .background(PorcelainSheetBg)
                        .padding(top = 14.dp, bottom = 32.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Sheet Handle Pill
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFCBD5E1))
                                .align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Sheet Header (Uzbek)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bugungi ob-havo",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DarkSlateText,
                                letterSpacing = (-0.3).sp
                            )

                            // AI Condition Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFEEF2FF))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "AI Tahlili",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Hourly Timeline Carousel (Past, Now, and Future Slots)
                        HourlyCapsuleRail(
                            readings = uiState.history,
                            currentReading = reading,
                            unit = unit
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Spacer(modifier = Modifier.height(90.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AIInsightBanner(
    ai: com.weatherstation.app.domain.model.AIWeatherModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF8FAFC),
                        Color(0xFFEFF6FF)
                    )
                )
            )
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "✨", fontSize = 15.sp)
                    Text(
                        text = ai.headline,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFDCFCE7))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Qulaylik: ${ai.comfortIndex}/100",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D)
                    )
                }
            }

            Text(
                text = ai.summary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF475569),
                lineHeight = 19.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "💡", fontSize = 12.sp)
                Text(
                    text = "Kiyim tavsiyasi: ${ai.clothingAdvice}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2563EB)
                )
            }
        }
    }
}
