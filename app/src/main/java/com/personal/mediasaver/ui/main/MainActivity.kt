package com.personal.mediasaver.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.personal.mediasaver.MediaSaverApp
import com.personal.mediasaver.domain.model.AppearanceSettings
import com.personal.mediasaver.ui.settings.SettingsScreen
import com.personal.mediasaver.ui.theme.MediaSaverTheme
import com.personal.mediasaver.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notification permission is optional; WorkManager degrades cleanly if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestPostNotificationsIfNeeded()

        val app = application as MediaSaverApp

        setContent {
            val appearance by app.container.settingsDataStore.appearanceSettingsFlow
                .collectAsState(initial = AppearanceSettings())
            val isDark = resolveDarkTheme(appearance.themeMode)

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                )
            }

            MediaSaverTheme(
                darkTheme = isDark,
                dynamicColor = appearance.dynamicColor,
            ) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "home",
                ) {
                    composable("home") {
                        HomeScreen(
                            isDarkTheme = isDark,
                            onOpenSettings = { navController.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
