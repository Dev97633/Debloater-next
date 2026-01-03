package com.example.debloater

import android.content.Context
import android.content.pm.PackageManager
import com.example.debloater.IDebloaterService

/**
 * This service runs in the privileged Shizuku process.
 * It uses the provided Context to access PackageManager with elevated privileges.
 */
class DebloaterService : IDebloaterService.Stub {

    private lateinit var context: Context

    constructor() {
        // Required no-arg constructor
    }

    constructor(context: Context) {
        this.context = context
    }

    override fun uninstall(packageName: String) {
        // Uninstall the app (works for system/user apps with privileges)
        context.packageManager.deletePackage(packageName, null, PackageManager.DELETE_ALL_USERS)
    }

    override fun disable(packageName: String) {
        // Disable the app (works for system apps)
        context.packageManager.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun destroy() {
        // Clean up and exit the process
        System.exit(0)
    }
}
