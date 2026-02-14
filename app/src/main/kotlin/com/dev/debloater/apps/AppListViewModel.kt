package com.dev.debloater.apps

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * MVVM holder for the app list filtering state.
 * Filtering logic lives here instead of Fragment/Activity.
 */
class AppListViewModel : ViewModel() {

    private val allApps = MutableStateFlow<List<AppItem>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val filters = MutableStateFlow(FilterState())

    val query: StateFlow<String> = searchQuery.asStateFlow()
    val filterState: StateFlow<FilterState> = filters.asStateFlow()

    val filteredApps: StateFlow<List<AppItem>> = combine(
        allApps,
        searchQuery,
        filters,
    ) { apps, query, filters ->
        filterAppsInternal(apps = apps, query = query, filters = filters)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    /**
     * Optional LiveData exposure for legacy observers.
     */
    val filteredAppsLiveData: LiveData<List<AppItem>> = filteredApps.asLiveData()

    fun setAllApps(apps: List<AppItem>) {
        allApps.value = apps
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onFilterStateChanged(newState: FilterState) {
        filters.value = newState
    }

    fun toggleSystemOnly(enabled: Boolean) {
        filters.update { current ->
            current.copy(systemOnly = enabled, userOnly = if (enabled) false else current.userOnly)
        }
    }

    fun toggleUserOnly(enabled: Boolean) {
        filters.update { current ->
            current.copy(userOnly = enabled, systemOnly = if (enabled) false else current.systemOnly)
        }
    }

    fun toggleDisabledOnly(enabled: Boolean) {
        filters.update { it.copy(disabledOnly = enabled) }
    }

    fun toggleUninstalledOnly(enabled: Boolean) {
        filters.update { it.copy(uninstalledOnly = enabled) }
    }

    /**
     * Required API from request: returns filtered list using current source apps.
     */
    fun filterApps(query: String, filters: FilterState): List<AppItem> {
        return filterAppsInternal(apps = allApps.value, query = query, filters = filters)
    }

    private fun filterAppsInternal(
        apps: List<AppItem>,
        query: String,
        filters: FilterState,
    ): List<AppItem> {
        val trimmedQuery = query.trim()

        return apps.filter { app ->
            val matchesQuery = trimmedQuery.isBlank() ||
                app.appLabel.contains(trimmedQuery, ignoreCase = true) ||
                app.packageName.contains(trimmedQuery, ignoreCase = true)

            val matchesSystemUser = when {
                filters.systemOnly -> app.isSystemApp
                filters.userOnly -> !app.isSystemApp
                else -> true
            }

            val matchesDisabled = !filters.disabledOnly || app.isDisabled
            val matchesUninstalled = !filters.uninstalledOnly || !app.isInstalled

            matchesQuery && matchesSystemUser && matchesDisabled && matchesUninstalled
        }
    }
}
