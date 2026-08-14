package com.weatherstation.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Jonli", Icons.Default.WbSunny)
    object Trends : Screen("trends", "Trendlar", Icons.AutoMirrored.Filled.ShowChart)
}

val BottomNavScreens = listOf(
    Screen.Home,
    Screen.Trends
)
