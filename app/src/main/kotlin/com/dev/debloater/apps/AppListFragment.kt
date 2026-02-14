package com.dev.debloater.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dev.debloater.R
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * Example XML + RecyclerView based screen that combines search text and filter toggles.
 */
class AppListFragment : Fragment(R.layout.fragment_app_list) {

    private val viewModel: AppListViewModel by viewModels()
    private val appAdapter = AppListAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerApps)
        val searchView = view.findViewById<SearchView>(R.id.searchApps)
        val chipSystemOnly = view.findViewById<Chip>(R.id.chipSystemOnly)
        val chipUserOnly = view.findViewById<Chip>(R.id.chipUserOnly)
        val chipDisabledOnly = view.findViewById<Chip>(R.id.chipDisabledOnly)
        val chipUninstalledOnly = view.findViewById<Chip>(R.id.chipUninstalledOnly)

        recyclerView.apply {
            adapter = appAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }

        // Load source apps once; ViewModel will handle all filtering combinations.
        viewModel.setAllApps(loadInstalledApps())

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.onSearchQueryChanged(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.onSearchQueryChanged(newText.orEmpty())
                return true
            }
        })

        chipSystemOnly.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleSystemOnly(isChecked)
            if (isChecked) chipUserOnly.isChecked = false
        }

        chipUserOnly.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleUserOnly(isChecked)
            if (isChecked) chipSystemOnly.isChecked = false
        }

        chipDisabledOnly.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleDisabledOnly(isChecked)
        }

        chipUninstalledOnly.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleUninstalledOnly(isChecked)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredApps.collect { filtered ->
                    // Adapter updates rows whenever query/filter state changes.
                    appAdapter.submitList(filtered)
                }
            }
        }
    }

    private fun loadInstalledApps(): List<AppItem> {
        val pm = requireContext().packageManager

        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES
        val appInfos: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(flags)
        }

        return appInfos
            .map { appInfo ->
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isInstalled = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
                val isDisabled = !appInfo.enabled

                AppItem(
                    appLabel = pm.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    isSystemApp = isSystem,
                    isDisabled = isDisabled,
                    isInstalled = isInstalled,
                )
            }
            .sortedBy { it.appLabel.lowercase() }
    }
}
