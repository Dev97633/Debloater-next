@file:OptIn(ExperimentalMaterial3Api::class)

package com.dev.debloater

import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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

data class AppData(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val isInstalled: Boolean,
    val isDisabled: Boolean,
    val safetyLevel: SafetyLevel,
    val icon: ImageBitmap? = null
)

data class ConfirmAction(
    val action: String,
    val packageName: String,
    val appName: String
)

data class AppFilters(
    val systemOnly: Boolean = false,
    val userOnly: Boolean = false,
    val disabledOnly: Boolean = false,
    val uninstalledOnly: Boolean = false,
)

private fun SafetyLevel.badgeColorScheme(): Pair<Color, Color> =
    when (this) {
        SafetyLevel.SAFE -> Color(0xFF1B5E20) to Color(0xFFE8F5E9)
        SafetyLevel.CAUTION -> Color(0xFFF57F17) to Color(0xFFFFF8E1)
        SafetyLevel.RISKY -> Color(0xFFB71C1C) to Color(0xFFFFEBEE)
    }

private fun safetyLabel(level: SafetyLevel): String =
    when (level) {
        SafetyLevel.SAFE -> "Safe"
        SafetyLevel.CAUTION -> "Caution"
        SafetyLevel.RISKY -> "Risky"
    }

@Composable
fun DebloaterScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val activity = context as? Activity
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
    var filters by remember { mutableStateOf(AppFilters()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    var riskyOverrideAction by remember { mutableStateOf<ConfirmAction?>(null) }
    val appListState = rememberLazyListState()
    val suggestionListState = rememberLazyListState()
    val smoothFlingBehavior = ScrollableDefaults.flingBehavior()

    fun updateAppData(packageName: String, transform: (AppData) -> AppData) {
        val updatedList = allAppData.map { appData ->
            if (appData.packageName == packageName) transform(appData) else appData
        }
        allAppData = updatedList
        selectedApp = updatedList.find { it.packageName == packageName }
    }

    BackHandler {
        if (currentScreen != "apps") {
            currentScreen = "apps"
            selectedApp = null
        } else {
            activity?.finish()
    
        }
    }

    // Load apps only after onboarding
    LaunchedEffect(currentScreen) {
        if (currentScreen == "apps" && allAppData.isEmpty()) {
            allAppData = loadAllAppDataWithIcons(pm)
        }
    }

    val filteredAppData by remember {
        derivedStateOf {
            val trimmedQuery = query.trim()

            allAppData.filter { appData ->
                val matchesQuery = trimmedQuery.isBlank() ||
                    appData.appName.contains(trimmedQuery, ignoreCase = true) ||
                    appData.packageName.contains(trimmedQuery, ignoreCase = true)

                val matchesSystemUser = when {
                    filters.systemOnly -> appData.isSystem
                    filters.userOnly -> !appData.isSystem
                    else -> true
                }

                val matchesDisabled = !filters.disabledOnly || appData.isDisabled
                val matchesUninstalled = !filters.uninstalledOnly || !appData.isInstalled

                matchesQuery && matchesSystemUser && matchesDisabled && matchesUninstalled
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
                    filters = filters,
                    onFiltersChange = { filters = it },
                    suggestionListState = suggestionListState,
                    smoothFlingBehavior = smoothFlingBehavior,
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
                        (fadeIn(tween(300)) + slideInHorizontally { it })
                            .togetherWith(fadeOut(tween(300)) + slideOutHorizontally { -it })
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
                                    state = appListState,
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    flingBehavior = smoothFlingBehavior
                                ) {
                                    items(
                                        items = filteredAppData,
                                        key = { it.packageName },
                                        contentType = { "app_item" }
                                    ) { appData ->
                                        AppListItem(
                                            appData = appData,
                                            onClick = {
                                                selectedApp = appData
                                                currentScreen = "details"
                                            },
                                            onToggle = { appData, isDisabled ->
                                                if (!isDisabled && appData.safetyLevel == SafetyLevel.RISKY) {
                                                    riskyOverrideAction = ConfirmAction(
                                                        action = "disable",
                                                        packageName = appData.packageName,
                                                        appName = appData.appName
                                                    )
                                                } else {
                                                    confirmAction =
                                                        if (isDisabled) {
                                                            ConfirmAction(
                                                                action = "enable",
                                                                packageName = appData.packageName,
                                                                appName = appData.appName
                                                            )
                                                        } else {
                                                            ConfirmAction(
                                                                action = "disable",
                                                                packageName = appData.packageName,
                                                                appName = appData.appName
                                                            )
                                                        }
                                                }
                                            },
                                            onUninstall = { appDataToRemove ->
                                                confirmAction = ConfirmAction(
                                                    action = "uninstall",
                                                    packageName = appDataToRemove.packageName,
                                                    appName = appDataToRemove.appName
                                                )
                                            },
                                            onRestore = { appDataToRestore ->
                                                confirmAction = ConfirmAction(
                                                    action = "restore",
                                                    packageName = appDataToRestore.packageName,
                                                    appName = appDataToRestore.appName
                                                )
                                            }
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
                                    if (!app.isDisabled && app.safetyLevel == SafetyLevel.RISKY) {
                                        riskyOverrideAction = ConfirmAction(
                                            action = "disable",
                                            packageName = app.packageName,
                                            appName = app.appName
                                        )
                                    } else {
                                        confirmAction =
                                            if (app.isDisabled) {
                                                ConfirmAction(
                                                    action = "enable",
                                                    packageName = app.packageName,
                                                    appName = app.appName
                                                )
                                            } else {
                                                ConfirmAction(
                                                    action = "disable",
                                                    packageName = app.packageName,
                                                    appName = app.appName
                                                )
                                            }
                                    }
                                },
                                onUninstall = {
                                    confirmAction = ConfirmAction(
                                        action = "uninstall",
                                        packageName = app.packageName,
                                        appName = app.appName
                                    )
                                    },
                                onRestore = {
                                    confirmAction = ConfirmAction(
                                        action = "restore",
                                        packageName = app.packageName,
                                        appName = app.appName
                                    )
                                }
                            )
                        } ?: Box(Modifier.fillMaxSize())

                        "about" -> AboutScreen()

                        else -> Box(Modifier.fillMaxSize()) // ✅ REQUIRED
                    }
                }
            }

            riskyOverrideAction?.let { (action, pkg, appName) ->
                AlertDialog(
                    onDismissRequest = { riskyOverrideAction = null },
                    title = { Text("Critical app warning") },
                    text = {
                        Text(
                            "$appName ($pkg) is classified as risky and may break core system functionality if disabled. Continue anyway?"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmAction = ConfirmAction(action = action, packageName = pkg, appName = appName)
                            riskyOverrideAction = null
                        }) {
                            Text("I understand, continue")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { riskyOverrideAction = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }


            confirmAction?.let { (action, pkg, appName) ->
                AlertDialog(
                    onDismissRequest = { confirmAction = null },
                    title = {
                        Text(
                            when (action) {
                                "disable" -> "Confirm disable"
                                "enable" -> "Confirm enable"
                                "restore" -> "Confirm restore"
                                else -> "Confirm uninstall"
                            }
                        )
                    },
                    text = {
                        Text(
                            when (action) {
                                "disable" -> "Disable $appName ($pkg) ? You can enable it later."
                                "enable" -> "Enable $appName ($pkg) ?"
                                "restore" -> "Restore $appName ($pkg) for this user?"
                                else -> "Uninstall $appName ($pkg) ? You can restore it later."
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    when (action) {
                                        "disable" -> {
                                            ShizukuManager.disable(pkg)
                                            updateAppData(pkg) { it.copy(isDisabled = true) }
                                        }

                                        "enable" -> {
                                            ShizukuManager.enable(pkg)
                                            updateAppData(pkg) { it.copy(isDisabled = false) }
                                        }

                                        "uninstall" -> {
                                            ShizukuManager.uninstall(pkg)
                                            updateAppData(pkg) { it.copy(isInstalled = false, isDisabled = false) }
                                        }

                                        "restore" -> {
                                            ShizukuManager.restore(pkg)
                                            allAppData = loadAllAppDataWithIcons(pm)
                                            selectedApp = allAppData.find { it.packageName == pkg }
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
                                    "restore" -> "Restore"
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
    filters: AppFilters,
    onFiltersChange: (AppFilters) -> Unit,
    suggestionListState: LazyListState,
    smoothFlingBehavior: FlingBehavior,
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
            Column {
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
                    LazyColumn(
                        state = suggestionListState,
                        flingBehavior = smoothFlingBehavior
                    ) {
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filters.systemOnly,
                        onClick = {
                            val enabled = !filters.systemOnly
                            onFiltersChange(filters.copy(systemOnly = enabled, userOnly = if (enabled) false else filters.userOnly))
                        },
                        label = { Text("System") }
                    )
                    FilterChip(
                        selected = filters.userOnly,
                        onClick = {
                            val enabled = !filters.userOnly
                            onFiltersChange(filters.copy(userOnly = enabled, systemOnly = if (enabled) false else filters.systemOnly))
                        },
                        label = { Text("User") }
                    )
                    FilterChip(
                        selected = filters.disabledOnly,
                        onClick = {
                            onFiltersChange(filters.copy(disabledOnly = !filters.disabledOnly))
                        },
                        label = { Text("Disabled") }
                    )
                    FilterChip(
                        selected = filters.uninstalledOnly,
                        onClick = {
                            onFiltersChange(filters.copy(uninstalledOnly = !filters.uninstalledOnly))
                        },
                        label = { Text("Uninstalled") }
                    )
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    appData: AppData,
    onClick: () -> Unit,
    onToggle: (AppData, Boolean) -> Unit,
    onUninstall: (AppData) -> Unit,
    onRestore: (AppData) -> Unit
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
                        bitmap = appData.icon,
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
                    val (labelColor, bgColor) = appData.safetyLevel.badgeColorScheme()
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text = safetyLabel(appData.safetyLevel),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (appData.safetyLevel) {
                                    SafetyLevel.SAFE -> Icons.Default.Verified
                                    SafetyLevel.CAUTION -> Icons.Default.WarningAmber
                                    SafetyLevel.RISKY -> Icons.Default.Dangerous
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = bgColor,
                            labelColor = labelColor,
                            leadingIconContentColor = labelColor,
                            disabledContainerColor = bgColor,
                            disabledLabelColor = labelColor,
                            disabledLeadingIconContentColor = labelColor
                        )
                    )
                    if (!appData.isInstalled) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.wrapContentWidth()
                        )
                        Text(
                            text = "Uninstalled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
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
                if (appData.isInstalled) {
                    IconButton(
                        onClick = {
                            onToggle(appData, appData.isDisabled)
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
                    IconButton(
                        onClick = { onUninstall(appData) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Uninstall",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onRestore(appData) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = "Restore",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
private suspend fun loadAllAppDataWithIcons(
    pm: PackageManager
): List<AppData> = withContext(Dispatchers.Default) {

    try {
        pm.getInstalledPackages(PackageManager.MATCH_ALL or PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .asSequence()
            .map { pkg ->
                val appInfo = pkg.applicationInfo ?: runCatching {
                    pm.getApplicationInfo(
                        pkg.packageName,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
                    )
                }.getOrNull()
                val isInstalled = appInfo?.flags
                    ?.and(ApplicationInfo.FLAG_INSTALLED)
                    ?.let { it != 0 }
                    ?: false
                AppData(
                    packageName = pkg.packageName,
                    appName = appInfo?.let {
                        runCatching { it.loadLabel(pm).toString() }.getOrNull()
                    } ?: pkg.packageName,
                    isSystem = appInfo?.let {
                        it.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                            it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    } ?: false,
                    isDisabled = appInfo?.enabled == false,
                    isInstalled = isInstalled,
                    safetyLevel = SafetyClassifier.classify(pkg.packageName),
                    // Avoid eagerly decoding every app icon into memory to prevent startup OOM crashes.
                    icon = null
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
            .toList()

    } catch (e: Exception) {
        emptyList()
    }
}
