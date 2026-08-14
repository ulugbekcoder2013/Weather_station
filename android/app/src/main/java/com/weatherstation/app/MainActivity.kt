package com.weatherstation.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.weatherstation.app.ui.navigation.WeatherAppRoot
import com.weatherstation.app.ui.theme.WeatherStationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android 12+ Splash Screen
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Enable true Edge-to-Edge with 100% transparent status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val app = application as WeatherApplication
        val preferencesManager = app.preferencesManager
        val repository = app.repository

        setContent {
            val appTheme by preferencesManager.appTheme.collectAsState()

            WeatherStationTheme(appTheme = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    WeatherAppRoot(
                        repository = repository,
                        preferencesManager = preferencesManager
                    )
                }
            }
        }
    }
}
