package com.example.debloater

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var searchView: SearchView
    private lateinit var refreshButton: Button

    private var allApps: List<PackageInfo> = emptyList()
    private var displayedApps: List<PackageInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ShizukuManager.init(this)

        recyclerView = findViewById(R.id.recycler_view)
        searchView = findViewById(R.id.search_view)
        refreshButton = findViewById(R.id.btn_refresh)

        recyclerView.layoutManager = LinearLayoutManager(this)

        loadApps()

        adapter = AppAdapter(displayedApps, this::isSystemApp, this::isDisabledApp) { packageName, action ->
            when (action) {
                "uninstall" -> {
                    ShizukuManager.uninstall(packageName)
                    refreshList()
                }
                "disable" -> {
                    ShizukuManager.disable(packageName)
                    refreshList()
                }
            }
        }
        recyclerView.adapter = adapter

        // Search functionality
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText.orEmpty())
                return true
            }
        })

        // Manual refresh button
        refreshButton.setOnClickListener {
            loadApps()
        }
    }

    private fun loadApps() {
        val pm = packageManager
        allApps = pm.getInstalledPackages(0)
            .filter { it.applicationInfo != null }

        displayedApps = allApps
        adapter.updateApps(displayedApps)
    }

    private fun filterApps(query: String) {
        val lowerQuery = query.lowercase()
        displayedApps = allApps.filter {
            val label = pm.getApplicationLabel(it.applicationInfo!!).toString().lowercase()
            val pkg = it.packageName.lowercase()
            label.contains(lowerQuery) || pkg.contains(lowerQuery)
        }
        adapter.updateApps(displayedApps)
    }

    private fun refreshList() {
        // Small delay to let system process the change
        recyclerView.postDelayed({ loadApps() }, 800)
    }

    private fun isSystemApp(info: PackageInfo): Boolean {
        val flags = info.applicationInfo!!.flags
        return (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
               (flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    private fun isDisabledApp(info: PackageInfo): Boolean {
        return !info.applicationInfo!!.enabled
    }

    override fun onDestroy() {
        ShizukuManager.cleanup()
        super.onDestroy()
    }
}
