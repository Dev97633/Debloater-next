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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ShizukuManager – this will handle binding automatically
        ShizukuManager.init(this)

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val apps = getInstalledApps()
        adapter = AppAdapter(apps) { packageName, action ->
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
            .sortedBy { pm.getApplicationLabel(it.applicationInfo!!).toString().lowercase() }
    }
}
