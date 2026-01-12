// ✅ NEW: System App Warning Dialog (shows first)
        if (showSystemAppWarningDialog) {
            SystemAppWarningDialog(
                onDismiss = {
                    showSystemAppWarningDialog = false
                    // Mark as shown in SharedPreferences
                    PreferencesManager.setSystemAppWarningShown(true)
                    // Now show Shizuku dialog
                    showShizukuInfoDialog = true
                }
            )
        }
        
        // ✅ NEW: Shizuku Info Dialog (shows after System App warning)
        if (showShizukuInfoDialog && !showSystemAppWarningDialog) {
            ShizukuInfoDialog(
                onDismiss = {
                    showShizukuInfoDialog = false
                    // Mark as shown in SharedPreferences
                    PreferencesManager.setShizukuInfoShown(true)
                    // Trigger Shizuku prompt after user taps Next
                    ShizukuManager.requestShizukuPermission()
                }
            )
        }
    }
}@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.debloater

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
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
        // ✅ Initialize PreferencesManager first
        PreferencesManager.init(this)
        setContent {
            DebloaterTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    ShizukuManager.setSnackbarHostState(snackbarHostState)
                    // Initialize Shizuku after setting snackbar state
                    ShizukuManager.init(this@MainActivity)
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
    val isSystem: Boolean,
    val icon: Drawable? = null
)

@Composable
fun DebloaterScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val activity = (LocalContext.current as ComponentActivity)
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    
    var appDataSnapshot by remember { mutableStateOf<List<AppData>?>(null) }
    var isLoadingApps by remember { mutableStateOf(true) }
    
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf<String?>(null) }
    var currentScreen by rememberSaveable { mutableStateOf("apps") }
    var selectedApp by remember { mutableStateOf<AppData?>(null) }
    
    // ✅ Track if System App warning dialog has been shown (from SharedPreferences)
    var showSystemAppWarningDialog by remember { 
        mutableStateOf(!PreferencesManager.isSystemAppWarningShown())
    }
    
    // ✅ Track if Shizuku info dialog has been shown (from SharedPreferences)
    var showShizukuInfoDialog by remember { 
        mutableStateOf(!PreferencesManager.isShizukuInfoShown() && PreferencesManager.isSystemAppWarningShown())
    }

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
        scope.launch(Dispatchers.Default) {
            val data = loadAllAppDataWithIcons(pm)
            appDataSnapshot = data
            isLoadingApps = false
        }
    }

    val filteredAppData by remember {
        derivedStateOf {
            val apps = appDataSnapshot ?: return@derivedStateOf emptyList()
            if (query.isBlank()) apps
            else apps.filter {
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
        if (isLoadingApps) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                    Text("Loading apps...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
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
                                    appDataSnapshot = loadAllAppDataWithIcons(pm)
                                    isRefreshing = false
                                }
                            },
                            modifier = Modifier.padding(padding)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                                flingBehavior = androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
                            ) {
                                items(filteredAppData, key = { it.packageName }) { appData ->
                                    AppListItem(
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
                            appDataSnapshot = appDataSnapshot?.filter { it.packageName != pkg }
                        }
                        confirmUninstall = null
                    }) { Text("Uninstall") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmUninstall = null }) { Text("Cancel") }
                }
            )
        }

        // ✅ NEW: Shizuku Info Dialog
        if (showShizukuInfoDialog) {
            ShizukuInfoDialog(
                onDismiss = {
                    showShizukuInfoDialog = false
                    // Mark as shown in SharedPreferences
                    PreferencesManager.setShizukuInfoShown(true)
                    // Trigger Shizuku prompt after user taps Next
                    ShizukuManager.requestShizukuPermission()
                }
            )
        }
    }
}

// ✅ NEW: Shizuku Information Dialog
@Composable
fun ShizukuInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text("Shizuku Required")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Shizuku is necessary to use this app.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Shizuku provides the elevated permissions required to disable and uninstall system apps safely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Make sure Shizuku is installed and running on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Next")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}

// ✅ NEW: System App Warning Dialog (shown at startup)
@Composable
fun SystemAppWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text("⚠️ System App Warning")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Uninstalling system apps may break your device or cause unexpected behavior.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "System apps are critical for device functionality. Removing them could render your device unstable or unusable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "This action is irreversible without a factory reset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Use at your own risk!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("I Understand")
            }
        }
    )
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
fun AppListItem(
    appData: AppData,
    onClick: () -> Unit,
    onDisable: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (appData.icon != null) {
                    Image(
                        painter = rememberAsyncImagePainter(appData.icon),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            appData.appName.take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 250.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = appData.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = appData.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (appData.isSystem) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.wrapContentWidth()
                        )
                        Text(
                            text = "System",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                IconButton(
                    onClick = { onDisable(appData.packageName) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = "Disable",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(
                    onClick = { onUninstall(appData.packageName) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Uninstall",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Divider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 68.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp
        )
    }
}

private suspend fun loadAllAppDataWithIcons(pm: PackageManager): List<AppData> =
    withContext(Dispatchers.Default) {
        try {
            pm.getInstalledPackages(PackageManager.MATCH_ALL)
                .asSequence()
                .mapNotNull { pkg ->
                    val app = pkg.applicationInfo ?: return@mapNotNull null
                    val icon = try {
                        app.loadIcon(pm)
                    } catch (e: Exception) {
                        null
                    }
                    
                    AppData(
                        packageName = pkg.packageName,
                        appName = runCatching { app.loadLabel(pm).toString() }.getOrElse { pkg.packageName },
                        isSystem = app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 ||
                                app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                        icon = icon
                    )
                }
                .sortedBy { it.appName.lowercase() }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
