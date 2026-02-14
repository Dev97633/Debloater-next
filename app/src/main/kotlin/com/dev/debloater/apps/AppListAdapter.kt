package com.dev.debloater.apps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dev.debloater.R

/**
 * RecyclerView adapter that receives already-filtered items from ViewModel.
 */
class AppListAdapter : ListAdapter<AppItem, AppListAdapter.AppViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_row, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appLabelText: TextView = itemView.findViewById(R.id.textAppLabel)
        private val packageNameText: TextView = itemView.findViewById(R.id.textPackageName)
        private val stateText: TextView = itemView.findViewById(R.id.textState)

        fun bind(item: AppItem) {
            appLabelText.text = item.appLabel
            packageNameText.text = item.packageName

            val stateParts = buildList {
                add(if (item.isSystemApp) "System" else "User")
                if (item.isDisabled) add("Disabled")
                if (!item.isInstalled) add("Uninstalled")
            }
            stateText.text = stateParts.joinToString(separator = " • ")
        }
    }
}
