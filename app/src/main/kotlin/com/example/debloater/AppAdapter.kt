package com.example.debloater

import android.content.pm.PackageInfo
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private var apps: List<PackageInfo>,
    private val isSystemApp: (PackageInfo) -> Boolean,
    private val isDisabledApp: (PackageInfo) -> Boolean,
    private val onActionClick: (String, String) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val pkg: TextView = view.findViewById(R.id.app_package)
        val uninstallButton: Button = view.findViewById(R.id.btn_uninstall)
        val disableButton: Button = view.findViewById(R.id.btn_disable)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = when (viewType) {
            0 -> LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false)
            else -> LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            0 -> { // Section header
                holder.itemView.findViewById<TextView>(R.id.section_title).text =
                    if (position == 0) "Enabled Apps" else "Disabled Apps"
            }
            else -> {
                val app = apps[position - getHeaderCount(position)]
                val pm = holder.itemView.context.packageManager
                val appInfo = app.applicationInfo!!

                holder.icon.setImageDrawable(appInfo.loadIcon(pm))
                holder.name.text = appInfo.loadLabel(pm)
                holder.pkg.text = app.packageName

                // Highlight system apps in orange
                if (isSystemApp(app)) {
                    holder.name.setTextColor(Color.parseColor("#FF9800")) // Orange
                } else {
                    holder.name.setTextColor(Color.BLACK)
                }

                // Dim disabled apps
                if (isDisabledApp(app)) {
                    holder.itemView.alpha = 0.5f
                } else {
                    holder.itemView.alpha = 1.0f
                }

                holder.uninstallButton.setOnClickListener {
                    onActionClick(app.packageName, "uninstall")
                }
                holder.disableButton.setOnClickListener {
                    onActionClick(app.packageName, "disable")
                }
            }
        }
    }

    override fun getItemCount(): Int {
        val enabledCount = apps.count { !isDisabledApp(it) }
        val disabledCount = apps.count { isDisabledApp(it) }
        var total = apps.size
        if (enabledCount > 0) total += 1 // Header
        if (disabledCount > 0) total += 1 // Header
        return total
    }

    override fun getItemViewType(position: Int): Int {
        var pos = position
        if (enabledCount() > 0) {
            if (pos == 0) return 0 // Enabled header
            pos--
        }
        if (pos < enabledCount()) return 1 // Enabled app
        pos -= enabledCount()
        if (disabledCount() > 0 && pos == 0) return 0 // Disabled header
        return 1 // Disabled app
    }

    private fun enabledCount() = apps.count { !isDisabledApp(it) }
    private fun disabledCount() = apps.count { isDisabledApp(it) }

    private fun getHeaderCount(position: Int): Int {
        var count = 0
        if (enabledCount() > 0) count++
        if (position > enabledCount() && disabledCount() > 0) count++
        return count
    }

    fun updateApps(newApps: List<PackageInfo>) {
        apps = newApps.sortedBy {
            packageManager.getApplicationLabel(it.applicationInfo!!).toString().lowercase()
        }
        notifyDataSetChanged()
    }
}
