package com.dev.debloater.apps

/**
 * Active toggles selected from the UI.
 *
 * Note: systemOnly and userOnly are mutually exclusive in the UI layer.
 */
data class FilterState(
    val systemOnly: Boolean = false,
    val userOnly: Boolean = false,
    val disabledOnly: Boolean = false,
    val uninstalledOnly: Boolean = false,
)
