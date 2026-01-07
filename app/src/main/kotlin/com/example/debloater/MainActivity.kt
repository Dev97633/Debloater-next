@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.debloater

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


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

@Composable
fun DebloaterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// ✅ Data class to cache app metadata off-main thread
data class AppMetadata(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

@Composable
fun DebloaterScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    
    var allAppsMetadata by remember { mutableStateOf<List<AppMetadata>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }
    var showConfirmUninstall by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // ✅ Load all app metadata on background thread ONCE
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.Default) {
            val metadata = getInstalledAppsMetadata(pm)
            allAppsMetadata = metadata
        }
    }

    // ✅ Filter metadata (very fast, just string comparison)
    val filteredApps = remember(query, allAppsMetadata) {
        if (query.isEmpty()) {
            allAppsMetadata
        } else {
            allAppsMetadata.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            DebloaterTopBar(
                query = query,
                onQueryChange = { query = it },
                active = active,
                onActiveChange = { active = it },
                filteredApps = filteredApps,
                onAppSelected = { selectedAppLabel ->
                    query = selectedAppLabel
                    active = false
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch(Dispatchers.Default) {
                    allAppsMetadata = getInstalledAppsMetadata(pm)
                    isRefreshing = false
                }
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appMetadata ->
                    AppCard(
                        appMetadata = appMetadata,
                        onDisable = { ShizukuManager.disable(it) },
                        onUninstall = { pkg ->
                            selectedPackage = pkg
                            showConfirmUninstall = true
                        }
                    )
                }
            }
        }

        if (showConfirmUninstall) {
            AlertDialog(
                onDismissRequest = { showConfirmUninstall = false },
                title = { Text("Confirm Uninstall") },
                text = { Text("Are you sure you want to uninstall $selectedPackage?") },
                confirmButton = {
                    TextButton(onClick = {
                        ShizukuManager.uninstall(selectedPackage!!)
                        showConfirmUninstall = false
                    }) {
                        Text("Uninstall")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmUninstall = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun DebloaterTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    filteredApps: List<AppMetadata>,
    onAppSelected: (String) -> Unit
) {
    Column {
        TopAppBar(
            title = { Text("Debloater") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { onActiveChange(false) },
            active = active,
            onActiveChange = onActiveChange,
            placeholder = { Text("Search apps...") },
            leadingIcon = {
                if (active) {
                    IconButton(onClick = {
                        onQueryChange("")
                        onActiveChange(false)
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            LazyColumn {
                items(filteredApps.take(10)) { appMetadata ->
                    SearchSuggestionItem(
                        appMetadata = appMetadata,
                        onSelect = { onAppSelected(appMetadata.appName) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchSuggestionItem(
    appMetadata: AppMetadata,
    onSelect: () -> Unit
) {
    ListItem(
        headlineContent = { Text(appMetadata.appName) },
        supportingContent = { Text(appMetadata.packageName) },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        modifier = Modifier.clickable(onClick = onSelect)
    )
}

@Composable
fun AppCard(
    appMetadata: AppMetadata,
    onDisable: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (appMetadata.isSystem)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ Lazy icon loading - loads after scroll stops
            Image(
                painter = rememberAsyncImagePainter(
                    model = appMetadata.icon,
                    contentScale = ContentScale.Fit
                ),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 16.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appMetadata.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = appMetadata.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Row {
                OutlinedButton(
                    onClick = { onDisable(appMetadata.packageName) },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Disable")
                }
                Button(onClick = { onUninstall(appMetadata.packageName) }) {
                    Text("Uninstall")
                }
            }
        }
    }
}

// ✅ Pre-load all metadata on background thread
private suspend fun getInstalledAppsMetadata(pm: PackageManager): List<AppMetadata> {
    return pm.getInstalledPackages(PackageManager.MATCH_ALL)
        .filter { it.applicationInfo != null }
        .map { packageInfo ->
            val appInfo = packageInfo.applicationInfo!!
            AppMetadata(
                packageName = packageInfo.packageName,
                appName = try {
                    appInfo.loadLabel(pm).toString()
                } catch (e: Exception) {
                    packageInfo.packageName
                },
                icon = try {
                    appInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                },
                isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            )
        }
        .sortedBy { it.appName.lowercase() }
}
