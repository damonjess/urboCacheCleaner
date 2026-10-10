package com.example.turbocachecleaner.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.turbocachecleaner.HonorRepairAssistant
import com.example.turbocachecleaner.R
import com.example.turbocachecleaner.ui.components.GradientButton
import com.example.turbocachecleaner.ui.components.OutlineActionButton
import com.example.turbocachecleaner.ui.components.TurboCard
import com.example.turbocachecleaner.ui.theme.TurboCleanGradient

/**
 * Guided Honor / MagicOS walkthrough. Each step either fires a real AOSP Settings
 * intent or explains exactly where to tap — identical to the original dialog flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HonorChecklistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val steps = remember(activity) { activity?.let { HonorRepairAssistant.steps(it) }.orEmpty() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Honor / MagicOS checklist") },
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
                TurboCard {
                    Text(
                        "${steps.size} steps for MagicOS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "MagicOS aggressively suspends background services, which is the most common reason " +
                            "Turbo Clean stops part-way through. Work through these once and it will keep running.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                GradientButton(
                    text = "START GUIDED WALKTHROUGH",
                    onClick = { activity?.let { HonorRepairAssistant.show(it) } },
                    enabled = activity != null,
                    gradient = TurboCleanGradient,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            itemsIndexed(steps) { index, step ->
                TurboCard {
                    Text(
                        "STEP ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        step.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val action = step.action
                    if (action != null && step.actionLabel != null) {
                        Spacer(Modifier.height(12.dp))
                        OutlineActionButton(
                            text = step.actionLabel.uppercase(),
                            onClick = { activity?.let { action.invoke(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
