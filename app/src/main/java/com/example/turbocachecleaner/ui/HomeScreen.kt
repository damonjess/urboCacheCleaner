package com.example.turbocachecleaner.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.turbocachecleaner.R
import com.example.turbocachecleaner.TurboCleaner
import com.example.turbocachecleaner.data.CacheScanner
import com.example.turbocachecleaner.data.Prefs
import com.example.turbocachecleaner.model.AppCacheItem
import com.example.turbocachecleaner.model.CleanStatus
import com.example.turbocachecleaner.model.SortOrder
import com.example.turbocachecleaner.model.StorageInfo
import com.example.turbocachecleaner.ui.components.AppCacheRow
import com.example.turbocachecleaner.ui.components.EmptyState
import com.example.turbocachecleaner.ui.components.GradientButton
import com.example.turbocachecleaner.ui.components.OutlineActionButton
import com.example.turbocachecleaner.ui.components.PermissionCard
import com.example.turbocachecleaner.ui.components.SectionHeader
import com.example.turbocachecleaner.ui.components.StatTile
import com.example.turbocachecleaner.ui.components.StorageRing
import com.example.turbocachecleaner.ui.theme.TurboCleanGradient
import com.example.turbocachecleaner.ui.theme.TurboGreen
import com.example.turbocachecleaner.util.AppFilters
import com.example.turbocachecleaner.util.Permissions
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: Prefs,
    resumeTick: StateFlow<Long>,
    onOpenDeviceHealth: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenHonor: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { CacheScanner(context) }
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    var apps by remember { mutableStateOf<List<AppCacheItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scanning by remember { mutableStateOf(false) }
    var scanFraction by remember { mutableFloatStateOf(0f) }
    var scannedCount by remember { mutableIntStateOf(0) }
    var totalToScan by remember { mutableIntStateOf(0) }
    var hasScanned by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortOrder.SIZE_DESC) }

    var usageGranted by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    var accessGranted by remember { mutableStateOf(Permissions.isAccessibilityEnabled(context)) }
    var storage by remember { mutableStateOf(StorageInfo(0L, 0L)) }
    var showStopDialog by remember { mutableStateOf(false) }

    val cleaning by TurboCleaner.running.collectAsState()
    val cleanCurrent by TurboCleaner.currentAppName.collectAsState()
    val cleanDone by TurboCleaner.cleanedCount.collectAsState()
    val cleanTotal by TurboCleaner.totalCount.collectAsState()
    val finishTick by TurboCleaner.finishedEvent.collectAsState()
    val statuses by TurboCleaner.statuses.collectAsState()

    val tick by resumeTick.collectAsState()
    LaunchedEffect(tick) {
        usageGranted = Permissions.hasUsageAccess(context)
        accessGranted = Permissions.isAccessibilityEnabled(context)
        storage = CacheScanner.storageInfo()
    }

    SideEffect { view.keepScreenOn = cleaning && prefs.keepScreenOn }

    fun readable(bytes: Long): String = Formatter.formatFileSize(context, bytes)

    fun startCleaning(source: List<AppCacheItem> = apps, selectedSet: Set<String> = selected) {
        val target = source.filter { it.packageName in selectedSet }
        if (target.isEmpty()) return
        if (prefs.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        TurboCleaner.start(
            context = context,
            packageList = target.map { it.packageName },
            nameList = target.map { it.appName },
            delay = prefs.delayMs,
        )
    }

    fun beginScan(autoClean: Boolean) {
        if (scanning) return
        scanning = true
        scanError = null
        hasScanned = false
        scanFraction = 0f
        scannedCount = 0
        scope.launch {
            try {
                val result = scanner.scan(prefs.minCacheBytes, prefs.showSystemApps) { p ->
                    scannedCount = p.scanned
                    totalToScan = p.total
                    scanFraction = if (p.total == 0) 0f else p.scanned.toFloat() / p.total.toFloat()
                }
                apps = result
                selected = result.map { it.packageName }.toSet()
                prefs.lastScanTime = System.currentTimeMillis()
                storage = CacheScanner.storageInfo()
                if (autoClean && result.isNotEmpty()) startCleaning(result, result.map { it.packageName }.toSet())
            } catch (e: SecurityException) {
                scanError = "Usage Access is required to measure cache sizes."
            } catch (e: Exception) {
                scanError = e.localizedMessage ?: "Scan failed. Please try again."
            } finally {
                scanning = false
                hasScanned = true
            }
        }
    }

    fun oneTapClean() {
        when {
            !usageGranted -> context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            !accessGranted -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            cleaning -> showStopDialog = true
            scanning -> Unit
            apps.isEmpty() -> beginScan(autoClean = true)
            else -> startCleaning()
        }
    }

    // Refresh numbers automatically after a full clean run.
    LaunchedEffect(finishTick) {
        if (finishTick > 0L) {
            usageGranted = Permissions.hasUsageAccess(context)
            accessGranted = Permissions.isAccessibilityEnabled(context)
            beginScan(autoClean = false)
        }
    }

    val filtered = remember(apps, query, sort) { AppFilters.apply(apps, query, sort) }
    val totalCache = remember(apps) { apps.sumOf { it.cacheSize } }
    val selectedSize = remember(apps, selected) { AppFilters.selectedSize(apps, selected) }
    val maxSize = remember(apps) { apps.maxOfOrNull { it.cacheSize } ?: 1L }

    val heroValue = when {
        scanning -> "${(scanFraction * 100).toInt()}%"
        else -> readable(totalCache)
    }
    val heroLabel = when {
        scanning -> "scanning $scannedCount/$totalToScan"
        hasScanned && apps.isEmpty() -> "no cache found"
        totalCache > 0L -> "in ${apps.size} apps"
        else -> "tap to clean"
    }
    val ringFraction = when {
        scanning -> scanFraction
        storage.totalBytes > 0L -> storage.usedFraction
        else -> 0f
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (selected.isNotEmpty() && !cleaning && !scanning && usageGranted && accessGranted) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                if (selected.size == 1) "1 app selected" else "${selected.size} apps selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${readable(selectedSize)} to free",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        GradientButton(
                            text = if (selected.size == 1) "CLEAN 1 APP" else "CLEAN ${selected.size} APPS",
                            onClick = { oneTapClean() },
                            gradient = TurboCleanGradient,
                            modifier = Modifier.width(160.dp),
                        )
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Turbo Cache", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (cleaning) "Cleaning…" else "One tap to a faster phone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { beginScan(autoClean = false) }, enabled = !scanning && usageGranted) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Rescan")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Hero / one-tap clean ----
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StorageRing(
                            fraction = ringFraction,
                            centerTop = heroValue,
                            centerLabel = heroLabel,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (storage.totalBytes > 0L) {
                            Text(
                                "${readable(storage.usedBytes)} of ${readable(storage.totalBytes)} storage used",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        if (cleaning) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    cleanCurrent.ifBlank { "Preparing…" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(8.dp))
                                val p = if (cleanTotal == 0) 0f else cleanDone.toFloat() / cleanTotal.toFloat()
                                LinearProgressIndicator(
                                    progress = { p.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "$cleanDone / $cleanTotal apps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(14.dp))
                                OutlineActionButton(
                                    text = "STOP",
                                    onClick = { showStopDialog = true },
                                    modifier = Modifier.fillMaxWidth(0.55f),
                                )
                            }
                        } else {
                            val label = when {
                                !usageGranted -> "GRANT ACCESS"
                                !accessGranted -> "ENABLE AUTO-CLEAN"
                                scanning -> "SCANNING…"
                                apps.isEmpty() -> "ONE-TAP CLEAN"
                                selected.size == 1 -> "CLEAN 1 APP"
                                else -> "CLEAN ${selected.size} APPS"
                            }
                            GradientButton(
                                text = label,
                                onClick = { oneTapClean() },
                                enabled = !scanning,
                                gradient = TurboCleanGradient,
                                modifier = Modifier.fillMaxWidth(0.8f),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlineActionButton(
                                    text = "SCAN",
                                    onClick = { beginScan(autoClean = false) },
                                    enabled = !scanning && usageGranted,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlineActionButton(
                                    text = if (selected.size == apps.size && apps.isNotEmpty()) "SELECT NONE" else "SELECT ALL",
                                    onClick = {
                                        selected = if (apps.isNotEmpty() && selected.size == apps.size) {
                                            emptySet()
                                        } else {
                                            apps.map { it.packageName }.toSet()
                                        }
                                    },
                                    enabled = apps.isNotEmpty() && !cleaning,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // ---- Scanning progress ----
            if (scanning) {
                item {
                    Column {
                        LinearProgressIndicator(
                            progress = { scanFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Scanning apps… $scannedCount / $totalToScan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- Permissions ----
            if (!usageGranted) {
                item {
                    PermissionCard(
                        title = "Usage Access",
                        description = "Needed to measure how much cache each app is using.",
                        granted = false,
                        onGrant = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    )
                }
            }
            if (!accessGranted) {
                item {
                    PermissionCard(
                        title = "Auto-Clean service",
                        description = "Enables the one-tap cleaner to clear caches for you automatically.",
                        granted = false,
                        onGrant = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    )
                }
            }

            // ---- Stats ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Apps found", apps.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatTile("Selected", selected.size.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    StatTile("Will free", readable(selectedSize), TurboGreen, Modifier.weight(1f))
                }
            }

            // ---- Tools ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolTile("📈", "Why is my phone slow?", onOpenDeviceHealth, Modifier.weight(1f))
                    ToolTile("✅", "Optimization checklist", onOpenChecklist, Modifier.weight(1f))
                }
            }
            item {
                ToolTile("🛠️", "Honor / MagicOS checklist", onOpenHonor, Modifier.fillMaxWidth())
            }

            // ---- App list header with search + sort ----
            item {
                Column {
                    SectionHeader(
                        title = "Apps with cache",
                        trailing = {
                            Text(
                                "${filtered.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search apps") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortOrder.entries.forEach { option ->
                            SortChip(
                                label = option.label,
                                selected = sort == option,
                                onClick = { sort = option },
                            )
                        }
                    }
                }
            }

            if (scanError != null) {
                item {
                    Text(
                        scanError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        title = when {
                            scanning -> "Scanning…"
                            !usageGranted -> "Grant Usage Access to begin"
                            !hasScanned -> "Ready to clean"
                            query.isNotEmpty() -> "No matches"
                            else -> "Nothing to clean 🎉"
                        },
                        message = when {
                            scanning -> "Looking through your installed apps."
                            !usageGranted -> "Turbo Cache needs it to measure cache sizes."
                            !hasScanned -> "Tap ONE-TAP CLEAN and I'll handle everything."
                            query.isNotEmpty() -> "Try a different search term."
                            else -> "Your phone's caches are already tidy."
                        },
                    )
                }
            } else {
                items(filtered, key = { it.packageName }) { item ->
                    AppCacheRow(
                        item = item,
                        checked = item.packageName in selected,
                        maxSize = maxSize,
                        readableSize = readable(item.cacheSize),
                        onToggle = {
                            selected = if (item.packageName in selected) {
                                selected - item.packageName
                            } else {
                                selected + item.packageName
                            }
                        },
                        onClean = {
                            if (!usageGranted) {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            } else if (!accessGranted) {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } else if (!cleaning && !scanning) {
                                selected = setOf(item.packageName)
                                startCleaning(source = listOf(item), selectedSet = setOf(item.packageName))
                            }
                        },
                        status = statuses[item.packageName] ?: CleanStatus.WAITING,
                    )
                }
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop cleaning?") },
            text = { Text("The one-tap cleaner will stop after the current app.") },
            confirmButton = {
                Button(onClick = {
                    TurboCleaner.stop()
                    showStopDialog = false
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Continue") }
            },
        )
    }
}

@Composable
private fun ToolTile(emoji: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
