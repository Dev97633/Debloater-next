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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painting.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

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
        darkTheme -> darkColorScheme
        else -> lightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
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
            items(apps) { app ->
                AppCard(app, pm, onDisable = { ShizukuManager.disable(it) }) { pkg ->
                    selectedPackage = pkg
                    showConfirmUninstall = true
                }
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

@Composable
fun AppCard(
    app: PackageInfo,
    pm: PackageManager,
    onDisable: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    val appInfo = app.applicationInfo ?: return

    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystem) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon: Drawable? = try { appInfo.loadIcon(pm) } catch (e: Exception) { null }
            Image(
                painter = rememberDrawablePainter(icon),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 16.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = try { appInfo.loadLabel(pm).toString() } catch (e: Exception) { app.packageName },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
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

@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter {
    return remember(drawable) {
        object : Painter() {
            override val intrinsicSize: Size = Size(
                drawable?.intrinsicWidth?.toFloat() ?: 0f,
                drawable?.intrinsicHeight?.toFloat() ?: 0f
            )

            override fun DrawScope.onDraw() {
                drawIntoCanvas { canvas ->
                    drawable?.bounds = android.graphics.Rect(0, 0, size.width.toInt(), size.height.toInt())
                    drawable?.draw(canvas.nativeCanvas)
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
