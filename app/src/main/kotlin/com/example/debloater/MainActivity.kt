@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.debloater

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

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

@Immutable
data class AppData(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean
)

@Composable
fun DebloaterScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val activity = (LocalContext.current as ComponentActivity)
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    var allAppData by remember { mutableStateOf<List<AppData>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf<String?>(null) }
    var currentScreen by rememberSaveable { mutableStateOf("apps") }
    var selectedApp by remember { mutableStateOf<AppData?>(null) }


    // Handle system back button
    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentScreen != "apps") {
                    currentScreen = "apps"
                    selectedApp = null
                } else {
                    isEnabled = false
                    activity.onBackPressed()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        activity.onBackPressedDispatcher.addCallback(backCallback)
        onDispose { backCallback.remove() }
    }

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
                suggestions = filteredAppData.take(10),
                onSuggestionClick = { query = it; active = false },
                currentScreen = currentScreen,
                onNavigate = { currentScreen = it },
                onBack = {
                    currentScreen = "apps"
                    selectedApp = null
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                (fadeIn(tween(300)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left))
                    .togetherWith(fadeOut(tween(300)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right))
            },
            label = "screen_transition"
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredAppData, key = { it.packageName }) { appData ->
                                AppCard(
                                    appData = appData,
                                    onClick = {
                                        selectedApp = appData
                                        currentScreen = "details"
                                    },
                                    onDisable = { ShizukuManager.disable(appData.packageName) },
                                    onUninstall = { confirmUninstall = appData.packageName }
                                )
                            }
                        }
                    }
                }
                "details" -> selectedApp?.let { app ->
                    AppDetailsScreen(
                        appData = app,
                        onBack = {
                            currentScreen = "apps"
                            selectedApp = null
                        },
                        onDisable = { ShizukuManager.disable(app.packageName) },
                        onUninstall = { confirmUninstall = app.packageName }
                    )
                } ?: Box(Modifier.fillMaxSize())
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
fun DebloaterTopBar(
    query: String,
    active: Boolean,
    onQueryChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    suggestions: List<AppData>,
    onSuggestionClick: (String) -> Unit,
    currentScreen: String,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val showBack = currentScreen != "apps"

    Column {
        TopAppBar(
            title = {
                Text(
                    when (currentScreen) {
                        "details" -> "App details"
                        "about" -> "About"
                        else -> "Debloater"
                    }
                )
            },
            navigationIcon = {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
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

        // 🔍 Search ONLY on apps screen
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
                    items(suggestions, key = { it.packageName }) { appData ->
                        ListItem(
                            headlineContent = { Text(appData.appName) },
                            supportingContent = { Text(appData.packageName) },
                            modifier = Modifier.clickable {
                                onSuggestionClick(appData.appName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppCard(
    appData: AppData,
    onClick: () -> Unit,
    onDisable: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

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
            containerColor = if (appData.isSystem) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        painter = rememberAsyncImagePainter(icon),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Text(
                    text = appData.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appData.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(onClick = { onDisable(appData.packageName) }) {
                Text("Disable")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onUninstall(appData.packageName) }) {
                Text("Uninstall")
            }
        }
    }
}

private suspend fun loadAppData(pm: PackageManager): List<AppData> =
    withContext(Dispatchers.IO) {
        try {
            pm.getInstalledPackages(PackageManager.MATCH_ALL)
                .asSequence()
                .mapNotNull { pkg ->
                    val app = pkg.applicationInfo ?: return@mapNotNull null
                    AppData(
                        packageName = pkg.packageName,
                        appName = runCatching { app.loadLabel(pm).toString() }.getOrElse { pkg.packageName },
                        isSystem = app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 ||
                                app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    )
                }
                .sortedBy { it.appName.lowercase() }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
