package com.example.turbocachecleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.turbocachecleaner.data.Prefs
import com.example.turbocachecleaner.model.AppScreen
import com.example.turbocachecleaner.ui.theme.TurboCacheTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TurboCacheApp(prefs: Prefs, resumeTick: StateFlow<Long>) {
    var screen by remember { mutableStateOf(AppScreen.HOME) }

    TurboCacheTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    prefs = prefs,
                    resumeTick = resumeTick,
                    onOpenDeviceHealth = { screen = AppScreen.DEVICE_HEALTH },
                    onOpenChecklist = { screen = AppScreen.CHECKLIST },
                    onOpenHonor = { screen = AppScreen.HONOR },
                    onOpenSettings = { screen = AppScreen.SETTINGS },
                )

                AppScreen.DEVICE_HEALTH -> {
                    BackHandler { screen = AppScreen.HOME }
                    DeviceHealthScreen(
                        onOpenHonor = { screen = AppScreen.HONOR },
                        onBack = { screen = AppScreen.HOME },
                    )
                }

                AppScreen.CHECKLIST -> {
                    BackHandler { screen = AppScreen.HOME }
                    ChecklistScreen(prefs = prefs, onBack = { screen = AppScreen.HOME })
                }

                AppScreen.HONOR -> {
                    BackHandler { screen = AppScreen.HOME }
                    HonorChecklistScreen(onBack = { screen = AppScreen.HOME })
                }

                AppScreen.SETTINGS -> {
                    BackHandler { screen = AppScreen.HOME }
                    SettingsScreen(prefs = prefs, onBack = { screen = AppScreen.HOME })
                }
            }
        }
    }
}
