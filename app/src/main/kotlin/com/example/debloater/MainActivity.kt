package com.example.debloater

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private val pm: PackageManager by lazy { packageManager }
    private var allApps: List<PackageInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ShizukuManager.init(this)

        recyclerView = findViewById(R.id.recycler_view)
        searchView = findViewById(R.id.search_view)
        refreshButton = findViewById(R.id.btn_refresh)

        recyclerView.layoutManager = LinearLayoutManager(this)

        loadApps()

        adapter = AppAdapter(allApps, pm) { packageName, action ->
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

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText.orEmpty())
                return true
            }
        })

        refreshButton.setOnClickListener {
            loadApps()
        }
    }

    private fun loadApps() {
        allApps = pm.getInstalledPackages(0)
            .filter { it.applicationInfo != null }
            .sortedBy { pm.getApplicationLabel(it.applicationInfo!!).toString().lowercase() }

        adapter.updateApps(allApps)
        filterApps(searchView.query.toString()) // Preserve current search
    }

    private fun filterApps(query: String) {
        val lowerQuery = query.lowercase()
        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                val label = pm.getApplicationLabel(it.applicationInfo!!).toString().lowercase()
                val pkg = it.packageName.lowercase()
                label.contains(lowerQuery) || pkg.contains(lowerQuery)
            }
        }
        adapter.updateApps(filtered)
    }

    private fun refreshList() {
        Handler(Looper.getMainLooper()).postDelayed({
            loadApps()
        }, 1000) // 1 second delay for system to update
    }

    override fun onDestroy() {
        ShizukuManager.cleanup()
        super.onDestroy()
    }
}
