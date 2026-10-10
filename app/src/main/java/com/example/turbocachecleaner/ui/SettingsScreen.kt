package com.example.turbocachecleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.turbocachecleaner.R
import com.example.turbocachecleaner.data.Prefs
import com.example.turbocachecleaner.ui.components.SectionHeader
import com.example.turbocachecleaner.ui.components.TurboCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    var minCache by remember { mutableLongStateOf(prefs.minCacheBytes) }
    var delay by remember { mutableLongStateOf(prefs.delayMs) }
    var systemApps by remember { mutableStateOf(prefs.showSystemApps) }
    var haptics by remember { mutableStateOf(prefs.hapticsEnabled) }
    var keepScreen by remember { mutableStateOf(prefs.keepScreenOn) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    SectionHeader("Scanning")
                    Spacer(Modifier.height(10.dp))
                    TurboCard {
                        Text("Minimum cache size", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ignore caches smaller than this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val options = listOf(0L to "All", 12_000L to "12 KB", 100_000L to "100 KB", 1_000_000L to "1 MB")
                            options.forEach { (value, label) ->
                                OptionChip(label, minCache == value) {
                                    minCache = value
                                    prefs.minCacheBytes = value
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        ToggleRow(
                            title = "Include system apps",
                            subtitle = "Show pre-installed apps in the list.",
                            checked = systemApps,
                        ) {
                            systemApps = it
                            prefs.showSystemApps = it
                        }
                    }
                }
            }

            item {
                Column {
                    SectionHeader("Cleaning")
                    Spacer(Modifier.height(10.dp))
                    TurboCard {
                        Text("Delay between apps", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "How long to wait while each app's page settles. Increase if apps are skipped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(400L to "Fast", 700L to "Normal", 1200L to "Safe").forEach { (value, label) ->
                                OptionChip(label, delay == value) {
                                    delay = value
                                    prefs.delayMs = value
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ToggleRow(
                            title = "Haptic feedback",
                            subtitle = "Vibrate when a clean starts.",
                            checked = haptics,
                        ) {
                            haptics = it
                            prefs.hapticsEnabled = it
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ToggleRow(
                            title = "Keep screen on while cleaning",
                            subtitle = "Prevents the clean run being interrupted.",
                            checked = keepScreen,
                        ) {
                            keepScreen = it
                            prefs.keepScreenOn = it
                        }
                    }
                }
            }

            item {
                Column {
                    SectionHeader("About")
                    Spacer(Modifier.height(10.dp))
                    TurboCard {
                        Text("Turbo Cache Cleaner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Version 2.0 · A fast, private one-tap cache cleaner. Everything happens on-device; nothing is uploaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
