package com.example.turbocachecleaner.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.turbocachecleaner.DeviceHealthChecker
import com.example.turbocachecleaner.R
import com.example.turbocachecleaner.ui.components.GradientButton
import com.example.turbocachecleaner.ui.components.SectionHeader
import com.example.turbocachecleaner.ui.components.StatTile
import com.example.turbocachecleaner.ui.components.TurboCard
import com.example.turbocachecleaner.ui.theme.TurboAmber
import com.example.turbocachecleaner.ui.theme.TurboCleanGradient
import com.example.turbocachecleaner.ui.theme.TurboGreen
import com.example.turbocachecleaner.ui.theme.TurboRed

private data class SlowReason(val emoji: String, val title: String, val body: String)

private val COMMON_CAUSES = listOf(
    SlowReason("🗑️", "Cache & junk build-up", "Apps store temporary files that grow over time. Clearing them frees storage and speeds up loading."),
    SlowReason("🚀", "Too many background apps", "Apps that keep running in the background eat RAM and CPU. Close the heavy ones."),
    SlowReason("💾", "Storage almost full", "Android gets sluggish when free space drops below ~10%. Delete what you don't need."),
    SlowReason("🔋", "Battery saver mode", "Saver mode throttles the CPU. Charge up and turn it off for full speed."),
    SlowReason("🌡️", "Overheating", "A hot CPU slows itself down. Remove the case and avoid charging while gaming."),
    SlowReason("🔄", "Pending updates", "Outdated apps and OS builds often contain performance bugs. Update them."),
    SlowReason("🧩", "Too many widgets & live wallpapers", "They refresh constantly, using CPU and battery in the background."),
    SlowReason("📶", "Weak signal", "A poor network makes apps spin and retry, which feels slow and drains battery."),
)

/**
 * "Why is my phone slow?" — real, no-root diagnostics from [DeviceHealthChecker]
 * on top, plus the general causes list underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceHealthScreen(onOpenHonor: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val report = remember(refresh) { DeviceHealthChecker.run(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Device health") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { refresh += 1 }) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Re-check")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("RAM free", "${report.ramFreePercent}%", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatTile("Storage free", "${report.storageFreePercent}%", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    StatTile(
                        "Battery",
                        report.batteryPercent?.let { "$it%" } ?: "—",
                        if (report.isPowerSaveMode) TurboAmber else TurboGreen,
                        Modifier.weight(1f),
                    )
                }
            }

            item {
                TurboCard {
                    HealthRow(
                        "Free RAM",
                        "${Formatter.formatShortFileSize(context, report.availRamBytes)} of " +
                            Formatter.formatShortFileSize(context, report.totalRamBytes),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    HealthRow(
                        "Free storage",
                        "${Formatter.formatShortFileSize(context, report.freeStorageBytes)} of " +
                            Formatter.formatShortFileSize(context, report.totalStorageBytes),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    HealthRow("Battery temperature", report.batteryTempCelsius?.let { "$it °C" } ?: "Not reported")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    HealthRow("Battery saver", if (report.isPowerSaveMode) "ON — CPU is limited" else "Off")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    HealthRow("Thermal state", report.thermalStatusLabel ?: "Not reported")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    HealthRow(
                        "Exempt from battery optimisation",
                        if (report.isIgnoringBatteryOptimizations) "Yes" else "No — MagicOS can suspend this app",
                    )
                }
            }

            item {
                Column {
                    SectionHeader("What's actually slowing it down")
                    Spacer(Modifier.height(10.dp))
                    TurboCard {
                        report.likelyCauses.forEachIndexed { index, cause ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            Row {
                                Text("•", color = TurboAmber, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(0.dp))
                                Text(
                                    "  $cause",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Only signals Android actually exposes are shown here — nothing is guessed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                GradientButton(
                    text = "OPEN HONOR / MAGICOS CHECKLIST",
                    onClick = onOpenHonor,
                    gradient = TurboCleanGradient,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column {
                    SectionHeader("Common causes of a slow phone")
                    Spacer(Modifier.height(10.dp))
                }
            }

            items(COMMON_CAUSES) { reason ->
                TurboCard {
                    Text(reason.emoji, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(reason.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        reason.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
