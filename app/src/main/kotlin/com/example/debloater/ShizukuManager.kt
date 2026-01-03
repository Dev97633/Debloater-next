package com.example.debloater

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import rikka.shizuku.Shizuku

object ShizukuManager {

    private const val REQUEST_CODE = 1000
    private var debloaterService: IDebloaterService? = null
    private var isBound = false

    private lateinit var context: Context

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Shizuku permission granted", Toast.LENGTH_SHORT).show()
                bindService()  // Bind immediately after permission is granted
            } else {
                Toast.makeText(context, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        // Bind service when Shizuku binder becomes available (e.g., after Shizuku starts or restarts)
        bindService()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isBound = false
        debloaterService = null
        Toast.makeText(context, "Shizuku binder died. Please restart Shizuku.", Toast.LENGTH_LONG).show()
    }

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

    private fun serviceArgs() = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, DebloaterService::class.java.name)
    )
        .daemon(false)
        .debuggable(false)
        .version(1)
        .tag("debloater")

    fun init(context: Context) {
        this.context = context.applicationContext
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        // Attempt initial bind if possible
        bindService()
    }

    fun cleanup() {
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        if (isBound) {
            Shizuku.unbindUserService(serviceArgs(), serviceConnection, true)  // true to call destroy() on service
            isBound = false
        }
    }

    fun checkAndRequestPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(context, "Shizuku is not running. Start it first.", Toast.LENGTH_LONG).show()
            return false
        }

        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return true
        }

        if (Shizuku.shouldShowRequestPermissionRationale()) {
            Toast.makeText(context, "Permission denied permanently. Grant in Shizuku app.", Toast.LENGTH_LONG).show()
            return false
        } else {
            Shizuku.requestPermission(REQUEST_CODE)
            return false
        }
    }

    fun bindService() {
        if (isBound) return  // Already bound

        if (!checkAndRequestPermission()) return

        try {
            Shizuku.bindUserService(serviceArgs(), serviceConnection)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to bind Shizuku service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun uninstall(packageName: String) {
        if (!isBound || debloaterService == null) {
            Toast.makeText(context, "Shizuku not connected", Toast.LENGTH_SHORT).show()
            bindService()  // Attempt re-bind if not connected
            return
        }
        try {
            debloaterService?.uninstall(packageName)
            Toast.makeText(context, "Uninstall sent for $packageName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Uninstall failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun disable(packageName: String) {
        if (!isBound || debloaterService == null) {
            Toast.makeText(context, "Shizuku not connected", Toast.LENGTH_SHORT).show()
            bindService()  // Attempt re-bind if not connected
            return
        }
        try {
            debloaterService?.disable(packageName)
            Toast.makeText(context, "Disabled $packageName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Disable failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
