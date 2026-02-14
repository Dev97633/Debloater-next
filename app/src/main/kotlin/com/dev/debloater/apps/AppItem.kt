package com.dev.debloater.apps

/**
 * RecyclerView row model used by the combined search + filter flow.
 */
data class AppItem(
    val appLabel: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val isDisabled: Boolean,
    val isInstalled: Boolean,
)
