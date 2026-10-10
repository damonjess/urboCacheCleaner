package com.example.turbocachecleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.turbocachecleaner.ui.components.TurboCard
import com.example.turbocachecleaner.ui.theme.TurboGreen

private data class ChecklistItem(val id: String, val title: String, val detail: String)

private val CHECKLIST = listOf(
    ChecklistItem("cache", "Clear app caches regularly", "Cached data builds up fast. Run one-tap clean once a week."),
    ChecklistItem("storage", "Keep at least 10% storage free", "Full storage slows the whole system down. Delete big files."),
    ChecklistItem("unused", "Uninstall unused apps", "Apps you never open still run background services."),
    ChecklistItem("updates", "Update apps and system software", "Updates fix performance and battery bugs."),
    ChecklistItem("animations", "Reduce or disable animations", "Developer options → set animation scales to 0.5x."),
    ChecklistItem("widgets", "Remove unused widgets", "Widgets refresh constantly and cost battery + RAM."),
    ChecklistItem("livewall", "Use a static wallpaper", "Live wallpapers drain CPU and battery all day."),
    ChecklistItem("lite", "Try Lite versions of heavy apps", "Facebook Lite, Messenger Lite, etc. use far less RAM."),
    ChecklistItem("photos", "Delete duplicate photos & downloads", "Big media libraries slow down the gallery and backups."),
    ChecklistItem("restart", "Restart your phone weekly", "A reboot clears stuck processes and frees RAM."),
    ChecklistItem("battery", "Keep battery above 20%", "Low battery and battery-saver modes throttle performance."),
    ChecklistItem("refresh", "Limit background refresh", "Not every app needs to sync in the background."),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(prefs: Prefs, onBack: () -> Unit) {
    var done by remember { mutableStateOf(prefs.checklistDone) }

    fun toggle(id: String) {
        done = if (id in done) done - id else done + id
        prefs.checklistDone = done
    }

    val progress = if (CHECKLIST.isEmpty()) 0f else done.size.toFloat() / CHECKLIST.size.toFloat()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Optimization checklist") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TurboCard {
                    Text(
                        "${(progress * 100).toInt()}% optimised",
                        style = MaterialTheme.typography.titleLarge,
                        color = TurboGreen,
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = TurboGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${done.size} of ${CHECKLIST.size} steps complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(CHECKLIST, key = { it.id }) { entry ->
                val checked = entry.id in done
                TurboCard(modifier = Modifier.clickable { toggle(entry.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { toggle(entry.id) })
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                entry.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
