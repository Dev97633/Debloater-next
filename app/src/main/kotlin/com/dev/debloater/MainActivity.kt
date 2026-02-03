@file:OptIn(ExperimentalMaterial3Api::class)

package com.dev.debloater

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val PREFS_NAME = "DebloaterPrefs"
private const val KEY_FIRST_LAUNCH = "first_launch"

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
    val isSystem: Boolean,
    val isDisabled: Boolean,
    val icon: Drawable? = null
)

@Composable
fun DebloaterScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val activity = (LocalContext.current as ComponentActivity)
    val pm = context.packageManager
    val scope = rememberCoroutineScope()

    // First launch check
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean(KEY_FIRST_LAUNCH, true)) }

    var currentScreen by rememberSaveable { mutableStateOf(if (isFirstLaunch) "onboarding" else "apps") }
    var selectedApp by remember { mutableStateOf<AppData?>(null) }

    var allAppData by remember { mutableStateOf<List<AppData>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Handle system back button
    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentScreen == "onboarding") {
                    isEnabled = false
                    activity.onBackPressed()
                } else if (currentScreen != "apps") {
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

    // Load apps only after onboarding
    LaunchedEffect(currentScreen) {
        if (currentScreen == "apps" && allAppData.isEmpty()) {
            allAppData = loadAllAppDataWithIcons(pm)
        }
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

    if (currentScreen == "onboarding") {
        OnboardingScreen {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            currentScreen = "apps"
        }
    } else {
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
            if (allAppData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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
    modifier = Modifier.padding(padding), 
    isRefreshing = isRefreshing,
    onRefresh = {
        scope.launch {
            isRefreshing = true
            allAppData = loadAllAppDataWithIcons(pm)
            isRefreshing = false
        }
    }
) {
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filteredAppData, key = { it.packageName }) { appData ->
            AppListItem(
    appData = appData,
    onClick = {
        selectedApp = appData
        currentScreen = "details"
    },
    onToggle = { pkg, isDisabled ->
        confirmAction =
            if (isDisabled) "enable" to pkg
            else "disable" to pkg
    },
    onUninstall = { confirmAction = "uninstall" to it }
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
                                onDisable = {
    confirmAction = "disable" to app.packageName
},
onUninstall = {
    confirmAction = "uninstall" to app.packageName
}
                            )
                        } ?: Box(Modifier.fillMaxSize())
                        "about" -> AboutScreen()
                    }
                }
            }

           confirmAction?.let { (action, pkg) ->
    AlertDialog(
        onDismissRequest = { confirmAction = null },
        title = {
            Text(
                if (action == "disable")
                    "Confirm disable"
                else
                    "Confirm uninstall"
            )
        },
        text = {
            Text(
                if (action == "disable")
                    "Disable $pkg ? You can enable it later."
                else
                    "Uninstall $pkg ? This cannot be undone."
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        when (action) {
                            "disable" -> ShizukuManager.disable(pkg)
                            "enable" -> ShizukuManager.enable(pkg)
                            "uninstall" -> {
                                ShizukuManager.uninstall(pkg)
                                allAppData = allAppData.filter {
                                    it.packageName != pkg
                                }
                            }
                        }
                    }
                    confirmAction = null
                }
            ) {
                Text(
    when (action) {
        "disable" -> "Disable"
        "enable" -> "Enable"
        else -> "Uninstall"
    }
)

            }
        },
        dismissButton = {
            TextButton(onClick = { confirmAction = null }) {
                Text("Cancel")
            }
        }
    )
}
        }
    }
}

// Onboarding Screens
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentStep by rememberSaveable { mutableStateOf(0) }
    var hasConfirmedWarning by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (index == currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        )
                    }
                }

                Button(
                    onClick = {
                        if (currentStep < 2) {
                            currentStep++
                        } else {
             ShizukuManager.requestPermissionAndBind()
             onComplete()
                       }
                    },
                    enabled = if (currentStep == 1) hasConfirmedWarning else true
                ) {
                    Text(if (currentStep == 2) "Grant Permission" else "Next")
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it } + fadeOut())
            },
            modifier = Modifier.padding(padding)
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (step) {
                    0 -> OnboardingStep1()
                    1 -> OnboardingStep2(hasConfirmedWarning) { hasConfirmedWarning = it }
                    2 -> OnboardingStep3()
                }
            }
        }
    }
}

@Composable
fun OnboardingStep1() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.DeleteSweep,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_step1_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_step1_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun OnboardingStep2(hasConfirmed: Boolean, onConfirmedChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_step2_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_step2_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Checkbox(
                checked = hasConfirmed,
                onCheckedChange = onConfirmedChange
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_step2_confirm))
        }
    }
}

@Composable
fun OnboardingStep3() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_step3_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_step3_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    onToggle: (String, Boolean) -> Unit,
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
                    onClick = {
        onToggle(appData.packageName, appData.isDisabled)
    },
    modifier = Modifier.size(36.dp)
) {
    Icon(
        if (appData.isDisabled) Icons.Default.CheckCircle else Icons.Default.Block,
        contentDescription = if (appData.isDisabled) "Enable" else "Disable",
        modifier = Modifier.size(18.dp),
        tint = if (appData.isDisabled)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.error
    )
}
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
                    return@mapNotNull AppData(
    packageName = pkg.packageName,
    appName = runCatching { app.loadLabel(pm).toString() }.getOrElse { pkg.packageName },
    isSystem =
        app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 ||
        app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
    isDisabled = !app.enabled,
    icon = icon
)

       }
                .sortedBy { it.appName.lowercase() }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
