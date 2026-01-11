@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.debloater

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.res.painterResource
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
    var selectedApp by rememberSaveable<AppData?>(null) { mutableStateOf(null) }

    var allAppData by remember { mutableStateOf<List<AppData>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf<String?>(null) }

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

    // Load apps only after onboarding
    LaunchedEffect(currentScreen) {
        if (currentScreen == "apps" && allAppData.isEmpty()) {
            allAppData = loadAppData(pm)
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

    when (currentScreen) {
        "onboarding" -> OnboardingScreen {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            isFirstLaunch = false
            currentScreen = "apps"
        }
        else -> {
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
}

@Composable
fun OnboardingScreen(onNext: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_shizuku), // Add your Shizuku icon in res/drawable
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .alpha(alpha),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(48.dp))

        Button(onClick = onNext) {
            Text(stringResource(R.string.onboarding_next))
        }
    }
}
