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

@Composable
fun DebloaterScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val pm = context.packageManager

    var allApps by remember { mutableStateOf<List<AppMetadata>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    var confirmUninstall by remember { mutableStateOf<String?>(null) }

    // Load ONCE
    LaunchedEffect(Unit) {
        allApps = loadApps(pm)
    }

    // FAST derived filter
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                LaunchedEffect(Unit) {
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
                items(
                    items = filteredApps,
                    key = { it.packageName }
                ) { app ->
                    AppCard(
                        app = app,
                        onDisable = { ShizukuManager.disable(it) },
                        onUninstall = { confirmUninstall = it }
                    )
                }
            }
        }

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

// -------------------- TOP BAR --------------------

@Composable
fun DebloaterTopBar(
    query: String,
    active: Boolean,
    onQueryChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    suggestions: List<AppMetadata>,
    onSuggestionClick: (String) -> Unit
) {
    Column {
        TopAppBar(title = { Text("Debloater") })

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
