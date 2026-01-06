@file:OptIn(ExperimentalMaterial3Api::class)  // Only this one is needed

package com.example.debloater

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search

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

@Composable
fun DebloaterScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val pm = context.packageManager
    val apps by remember { mutableStateOf(getInstalledApps(pm)) }

    var showConfirmUninstall by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debloater") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppCard(
                    app = app,
                    pm = pm,
                    onDisable = { ShizukuManager.disable(it) },
                    onUninstall = { pkg ->
                        selectedPackage = pkg
                        showConfirmUninstall = true
                    }
                )
            }
        }

        // AlertDialog MUST be inside the Scaffold or DebloaterScreen, not outside
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
fun AppCard(
    app: PackageInfo,
    pm: PackageManager,
    onDisable: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    val appInfo = app.applicationInfo ?: return

    // ✅ HARD CACHED — computed once per package
    val appName = rememberSaveable(app.packageName) {
        try {
            appInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            app.packageName
        }
    }

    val appIcon = remember(app.packageName) {
        try {
            appInfo.loadIcon(pm)
        } catch (e: Exception) {
            null
        }
    }

    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystem)
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

            // ✅ NO binder calls during scroll anymore
            Image(
                painter = rememberAsyncImagePainter(appIcon),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 16.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Row {
                OutlinedButton(
                    onClick = { onDisable(app.packageName) },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Disable")
                }
                Button(onClick = { onUninstall(app.packageName) }) {
                    Text("Uninstall")
                }
            }
        }
    }
}

private fun getInstalledApps(pm: PackageManager): List<PackageInfo> {
    return pm.getInstalledPackages(PackageManager.MATCH_ALL)
        .filter { it.applicationInfo != null }
        .sortedBy { pm.getApplicationLabel(it.applicationInfo!!).toString().lowercase() }
}
