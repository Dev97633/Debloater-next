package com.example.debloater

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ShizukuManager.init(this)

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val apps = getInstalledApps()

        // Pass packageManager to the adapter
        adapter = AppAdapter(apps, packageManager) { packageName, action ->
            when (action) {
                "uninstall" -> ShizukuManager.uninstall(packageName)
                "disable" -> ShizukuManager.disable(packageName)
            }
        }
        recyclerView.adapter = adapter
    }

    override fun onDestroy() {
        ShizukuManager.cleanup()
        super.onDestroy()
    }

    private fun getInstalledApps(): List<PackageInfo> {
        val pm = packageManager
        return pm.getInstalledPackages(PackageManager.MATCH_ALL)
            .filter { it.applicationInfo != null }
            .sortedBy { 
                pm.getApplicationLabel(it.applicationInfo!!).toString().lowercase()
            }
    }

    // Optional: Add a refresh function later
    // fun refreshAppList() {
    //     adapter.updateApps(getInstalledApps())
    // }
}
