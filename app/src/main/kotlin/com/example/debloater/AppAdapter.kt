package com.example.debloater

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
    private val pm: PackageManager,
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val appInfo = app.applicationInfo!!

        holder.icon.setImageDrawable(appInfo.loadIcon(pm))
        holder.name.text = appInfo.loadLabel(pm)
        holder.pkg.text = app.packageName

        // System app = orange text
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                       (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        holder.name.setTextColor(if (isSystem) Color.parseColor("#FF9800") else Color.BLACK)

        // Disabled app = dimmed
        holder.itemView.alpha = if (appInfo.enabled) 1.0f else 0.5f

        holder.uninstallButton.setOnClickListener {
            onActionClick(app.packageName, "uninstall")
        }
        holder.disableButton.setOnClickListener {
            onActionClick(app.packageName, "disable")
        }
    }

    override fun getItemCount() = apps.size

    fun updateApps(newApps: List<PackageInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
}
