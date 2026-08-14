package com.weatherstation.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.domain.repository.WeatherRepository
import com.weatherstation.app.ui.screens.home.HomeScreen
import com.weatherstation.app.ui.screens.home.HomeViewModel
import com.weatherstation.app.ui.screens.trends.TrendsScreen
import com.weatherstation.app.ui.screens.trends.TrendsViewModel

@Composable
fun WeatherAppRoot(
    repository: WeatherRepository,
    preferencesManager: UserPreferencesManager,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            WeatherBottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Home.route) {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel {
                    HomeViewModel(repository, preferencesManager)
                }
                // HomeScreen fills entire screen top-to-bottom behind transparent bars
                HomeScreen(viewModel = viewModel)
            }

            composable(Screen.Trends.route) {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel {
                    TrendsViewModel(repository, preferencesManager)
                }
                TrendsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
                )
            }
        }
    }
}
