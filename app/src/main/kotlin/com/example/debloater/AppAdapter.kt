package com.example.debloater

import android.content.pm.PackageInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.content.pm.PackageManager

class AppAdapter(
    private val apps: List<PackageInfo>,
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
        val pm = holder.itemView.context.packageManager
        val appInfo = app.applicationInfo ?: return  // Skip if null (rare cases)

        holder.icon.setImageDrawable(appInfo.loadIcon(pm))
        holder.name.text = appInfo.loadLabel(pm)
        holder.pkg.text = app.packageName

        holder.uninstallButton.setOnClickListener {
            onActionClick(app.packageName, "uninstall")
        }
        holder.disableButton.setOnClickListener {
            onActionClick(app.packageName, "disable")
        }
    }

    override fun getItemCount() = apps.size
}
