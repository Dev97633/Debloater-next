@file:OptIn(ExperimentalMaterial3Api::class)
package com.dev.debloater

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

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
fun AppDetailsScreen(
    appData: AppData,
    onBack: () -> Unit,
    onDisable: () -> Unit,
    onUninstall: () -> Unit,
    onRestore: () -> Unit
) {

    var isDisabling by remember { mutableStateOf(false) }
    var isUninstalling by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (appData.icon != null) {
                    Image(
                        bitmap = appData.icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = appData.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = appData.appName,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = appData.packageName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Type", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (appData.isSystem) "System App" else "User App",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Install Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (appData.isInstalled) "Installed" else "Uninstalled",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (appData.isInstalled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Safety", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val (labelColor, bgColor) = appData.safetyLevel.badgeColorScheme()
                     Text(
                        text = safetyLabel(appData.safetyLevel),
                        color = labelColor,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(bgColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
            if (appData.isInstalled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isDisabling = true
                            onDisable()
                            isDisabling = false
                        },
                        enabled = !isDisabling && !isUninstalling,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isDisabling) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (appData.isDisabled) "Enable" else "Disable")
                    }
                    Button(
                        onClick = {
                            isUninstalling = true
                            onUninstall()
                            isUninstalling = false
                        },
                        enabled = !isDisabling && !isUninstalling,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isUninstalling) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Uninstall")
                    }
                }
            } else {

                Button(
                    onClick = {
                        isRestoring = true
                        onRestore()
                        isRestoring = false
                    },
                    enabled = !isRestoring,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Restore")
                }
            }
        }
    }
}
