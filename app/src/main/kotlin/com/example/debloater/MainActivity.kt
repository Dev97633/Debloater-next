@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.debloater

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Immutable

// -------------------- ACTIVITY --------------------

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ShizukuManager.init(this)

        setContent {
            DebloaterTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    ShizukuManager.setSnackbarHostState(snackbarHostState)
                }
                DebloaterScreen(snackbarHostState)
            }
        }
    }

    override fun onDestroy() {
        ShizukuManager.cleanup()
        super.onDestroy()
    }
}

// -------------------- THEME --------------------

@Composable
fun DebloaterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

// -------------------- MODEL --------------------

@Immutable
data class AppMetadata(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

// -------------------- SCREEN --------------------

// -------------------- NAVIGATION & SCREEN --------------------
@Composable
fun DebloaterScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    var allApps by remember { mutableStateOf<List<AppMetadata>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf<String?>(null) }

    // Navigation
    var currentScreen by rememberSaveable { mutableStateOf("apps") }  // "apps" or "about"

    // Load ONCE
    LaunchedEffect(Unit) {
        allApps = loadApps(pm)
    }

    // Filter
    val filteredApps by remember {
        derivedStateOf {
            if (query.isBlank()) allApps
            else allApps.filter {
                it.appName.contains(query, true) ||
                it.packageName.contains(query, true)
            }
        }
    }

    Scaffold(
        topBar = {
            DebloaterTopBar(
                query = query,
                active = active,
                onQueryChange = { query = it },
                onActiveChange = { active = it },
                suggestions = filteredApps.take(10),
                onSuggestionClick = {
                    query = it
                    active = false
                },
                currentScreen = currentScreen,
                onNavigate = { currentScreen = it }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (currentScreen) {
            "apps" -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            allApps = loadApps(pm)
                            isRefreshing = false
                        }
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppCard(
                                app = app,
                                onDisable = { ShizukuManager.disable(it) },
                                onUninstall = { confirmUninstall = it }
                            )
                        }
                    }
                }
            }
            "about" -> AboutScreen()
        }

        // Confirmation dialog
        confirmUninstall?.let { pkg ->
            AlertDialog(
                onDismissRequest = { confirmUninstall = null },
                title = { Text("Confirm uninstall") },
                text = { Text("Uninstall $pkg ?") },
                confirmButton = {
                    TextButton(onClick = {
                        ShizukuManager.uninstall(pkg)
                        confirmUninstall = null
                    }) { Text("Uninstall") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmUninstall = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// -------------------- UPDATED TOP BAR WITH NAVIGATION --------------------
@Composable
fun DebloaterTopBar(
    query: String,
    active: Boolean,
    onQueryChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    suggestions: List<AppMetadata>,
    onSuggestionClick: (String) -> Unit,
    currentScreen: String,
    onNavigate: (String) -> Unit
) {
    Column {
        TopAppBar(
            title = { Text(if (currentScreen == "about") "About" else "Debloater") },
            navigationIcon = {
                if (currentScreen == "about") {
                    IconButton(onClick = { onNavigate("apps") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                if (currentScreen == "apps") {
                    IconButton(onClick = { onNavigate("about") }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                }
            }
        )
        if (currentScreen == "apps") {
            SearchBar(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onActiveChange(false) },
                active = active,
                onActiveChange = onActiveChange,
                placeholder = { Text("Search apps") },
                leadingIcon = {
                    if (active) {
                        IconButton(onClick = {
                            onQueryChange("")
                            onActiveChange(false)
                        }) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                    } else {
                        Icon(Icons.Default.Search, null)
                    }
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                LazyColumn {
                    items(suggestions, key = { it.packageName }) {
                        ListItem(
                            headlineContent = { Text(it.appName) },
                            supportingContent = { Text(it.packageName) },
                            leadingContent = {
                                Image(
                                    painter = rememberAsyncImagePainter(it.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                            },
                            modifier = Modifier.clickable {
                                onSuggestionClick(it.appName)
                            }
                        )
                    }
                }
            }
        }
    }
}

// -------------------- ABOUT SCREEN --------------------
@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Debloater",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Version 1.0",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "A fast, clean debloater for Android.\nNo root required — powered by Shizuku.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Text("Developed by", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "DEV",  
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(40.dp))

        // Device Specs Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Device Information", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                DeviceSpecRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
                DeviceSpecRow("Device", Build.DEVICE)
                DeviceSpecRow("Brand", Build.BRAND)
                DeviceSpecRow("Android Version", Build.VERSION.RELEASE)
                DeviceSpecRow("API Level", Build.VERSION.SDK_INT.toString())
                DeviceSpecRow("Build ID", Build.ID)
                DeviceSpecRow("Fingerprint", Build.FINGERPRINT.take(50) + "...")  
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Source code:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "github.com/Dev97633/Debloater",  // ← CHANGE TO YOUR REPO
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("MIT Licensed", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("Thanks to Rikka for Shizuku ❤️", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DeviceSpecRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(4.dp))
}

// -------------------- CARD --------------------

@Composable
fun AppCard(
    app: AppMetadata,
    onDisable: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (app.isSystem)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(app.icon),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            OutlinedButton(onClick = { onDisable(app.packageName) }) {
                Text("Disable")
            }

            Spacer(Modifier.width(8.dp))

            Button(onClick = { onUninstall(app.packageName) }) {
                Text("Uninstall")
            }
        }
    }
}

// -------------------- LOADER --------------------

private suspend fun loadApps(pm: PackageManager): List<AppMetadata> =
    withContext(Dispatchers.Default) {
        pm.getInstalledPackages(PackageManager.MATCH_ALL)
            .asSequence()
            .mapNotNull { pkg ->
                val app = pkg.applicationInfo ?: return@mapNotNull null
                AppMetadata(
                    packageName = pkg.packageName,
                    appName = runCatching { app.loadLabel(pm).toString() }.getOrElse { pkg.packageName },
                    icon = runCatching { app.loadIcon(pm) }.getOrNull(),
                    isSystem = app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 ||
                            app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }
