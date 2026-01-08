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
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Immutable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.CircularProgressIndicator



// Add these data classes to better structure the app data
@Immutable
data class AppData(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val iconResId: Int? = null
)

@Immutable
data class AppMetadata(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

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

@Composable
fun DebloaterScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    var allAppData by remember { mutableStateOf<List<AppData>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf<String?>(null) }
    var currentScreen by rememberSaveable { mutableStateOf("apps") }

    // Load only basic app data initially
    LaunchedEffect(Unit) {
        allAppData = loadAppData(pm)
    }

    val filteredAppData by remember {
        derivedStateOf {
            if (query.isBlank()) allAppData
            else allAppData.filter {
                it.appName.contains(query, ignoreCase = true) || 
                it.packageName.contains(query, ignoreCase = true)
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
                suggestions = emptyList(), // Simplified suggestions to reduce lag
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
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                (fadeIn(animationSpec = tween(300)) + slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left))
                    .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right))
            },
            label = "screen_animation"
        ) { screen ->
            when (screen) {
                "apps" -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                allAppData = loadAppData(pm)
                                isRefreshing = false
                            }
                        }
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(padding),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            state = rememberLazyListState()
                        ) {
                            items(
                                items = filteredAppData,
                                key = { it.packageName },
                                contentType = { it.isSystem }
                            ) { appData ->
                                AppCardComposable(
                                    appData = appData,
                                    onDisable = { ShizukuManager.disable(it) },
                                    onUninstall = { confirmUninstall = it },
                                    modifier = Modifier.animateItemPlacement() // Smooth item animations
                                )
                            }
                        }
                    }
                }
                "about" -> AboutScreen()
            }
        }

        confirmUninstall?.let { pkg ->
            AlertDialog(
                onDismissRequest = { confirmUninstall = null },
                title = { Text("Confirm uninstall") },
                text = { Text("Uninstall $pkg ?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            ShizukuManager.uninstall(pkg)
                            // Remove from list after uninstall
                            allAppData = allAppData.filter { it.packageName != pkg }
                        }
                        confirmUninstall = null
                    }) { Text("Uninstall") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmUninstall = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun AppCardComposable(
    appData: AppData,
    onDisable: (String) -> Unit,
    onUninstall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    // Load icon lazily with caching
    val icon by remember(appData.packageName) {
        derivedStateOf {
            try {
                val appInfo = pm.getApplicationInfo(appData.packageName, 0)
                appInfo.loadIcon(pm)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (appData.isSystem) MaterialTheme.colorScheme.surfaceVariant 
                           else MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Optimized icon loading with fallback
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    AsyncImage(
                        model = icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Fallback placeholder
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            appData.appName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    appData.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    appData.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Buttons with immediate feedback
            var isDisabling by remember { mutableStateOf(false) }
            var isUninstalling by remember { mutableStateOf(false) }
            
            OutlinedButton(
                onClick = {
                    isDisabling = true
                    onDisable(appData.packageName)
                    isDisabling = false
                },
                enabled = !isDisabling && !isUninstalling
            ) {
                if (isDisabling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Disable")
                }
            }
            
            Spacer(Modifier.width(8.dp))
            
            Button(
                onClick = {
                    isUninstalling = true
                    onUninstall(appData.packageName)
                    isUninstalling = false
                },
                enabled = !isDisabling && !isUninstalling
            ) {
                if (isUninstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Uninstall")
                }
            }
        }
    }
}

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
                // Simplified suggestions to reduce lag
                if (suggestions.isNotEmpty()) {
                    LazyColumn {
                        items(suggestions, key = { it.packageName }) {
                            ListItem(
                                headlineContent = { Text(it.appName) },
                                supportingContent = { Text(it.packageName) },
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
}

// Load only basic app data without icons initially
private suspend fun loadAppData(pm: PackageManager): List<AppData> =
    withContext(Dispatchers.IO) { // Use IO dispatcher for heavy operations
        try {
            pm.getInstalledPackages(PackageManager.MATCH_ALL)
                .asSequence()
                .mapNotNull { pkg ->
                    val app = pkg.applicationInfo ?: return@mapNotNull null
                    AppData(
                        packageName = pkg.packageName,
                        appName = runCatching { app.loadLabel(pm).toString() }
                            .getOrElse { pkg.packageName },
                        isSystem = app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 ||
                                app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                        iconResId = app.icon
                    )
                }
                .sortedBy { it.appName.lowercase() }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

// Keep the old loadApps function if needed elsewhere
private suspend fun loadApps(pm: PackageManager): List<AppMetadata> =
    withContext(Dispatchers.IO) {
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
