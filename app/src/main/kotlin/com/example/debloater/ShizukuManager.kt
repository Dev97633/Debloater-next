package com.example.debloater

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import rikka.shizuku.Shizuku

/**
 * Handles Shizuku integration: permission, binding, and operations.
 */
object ShizukuManager {

    private const val REQUEST_CODE = 1000
    private var debloaterService: IDebloaterService? = null
    private var isBound = false

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Shizuku permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var context: Context

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            debloaterService = IDebloaterService.Stub.asInterface(service)
            isBound = true
            Toast.makeText(context, "Shizuku service connected", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            debloaterService = null
            isBound = false
            Toast.makeText(context, "Shizuku service disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    fun init(context: Context) {
        this.context = context
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
    }

    fun cleanup() {
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        if (isBound) {
            Shizuku.unbindUserService(serviceConnection)
            isBound = false
        }
    }

    fun checkAndRequestPermission(): Boolean {
        if (Shizuku.isPreV11()) {
            Toast.makeText(context, "Shizuku not supported on this device", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!Shizuku.pingBinder()) {
            Toast.makeText(context, "Shizuku is not running. Start it first.", Toast.LENGTH_SHORT).show()
            return false
        }

        val permission = Shizuku.checkSelfPermission()
        if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            Toast.makeText(context, "Shizuku permission required. Please grant it in Shizuku app.", Toast.LENGTH_SHORT).show()
            return false
        } else {
            Shizuku.requestPermission(REQUEST_CODE)
            return false
        }
    }

    fun bindService() {
        if (!checkAndRequestPermission()) return

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, DebloaterService::class.java.name)
        )
            .processNameSuffix("debloater")
            .debuggable(BuildConfig.DEBUG)
            .version(1)

        Shizuku.bindUserService(args, serviceConnection)
    }

    fun uninstall(packageName: String) {
        if (!isBound || debloaterService == null) {
            Toast.makeText(context, "Shizuku not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            debloaterService?.uninstall(packageName)
            Toast.makeText(context, "Uninstalled $packageName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to uninstall $packageName: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun disable(packageName: String) {
        if (!isBound || debloaterService == null) {
            Toast.makeText(context, "Shizuku not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            debloaterService?.disable(packageName)
            Toast.makeText(context, "Disabled $packageName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to disable $packageName: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
