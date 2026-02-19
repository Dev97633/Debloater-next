package com.dev.debloater.apps
import android.graphics.drawable.Drawable

/**
 * RecyclerView row model used by the combined search + filter flow.
 */
data class AppItem(
    val appLabel: String,
    val packageName: String,
    val appLabelKey: String,
    val packageNameKey: String,
    val isSystemApp: Boolean,
    val isDisabled: Boolean,
    val isInstalled: Boolean,
    val stateText: String,
    val icon: Drawable,
)
