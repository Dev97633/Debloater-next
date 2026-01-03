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
                // Bind immediately after permission grant
                attemptBind()
            } else {
                Toast.makeText(context, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Toast.makeText(context, "Shizuku binder received", Toast.LENGTH_SHORT).show()
        attemptBind()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isBound = false
        debloaterService = null
        Toast.makeText(context, "Shizuku binder died - restart Shizuku", Toast.LENGTH_LONG).show()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            debloaterService = IDebloaterService.Stub.asInterface(service)
            isBound = true
            Toast.makeText(context, "Shizuku service connected successfully!", Toast.LENGTH_LONG).show()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            debloaterService = null
            isBound = false
            Toast.makeText(context, "Shizuku service disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    private val userServiceArgs = lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, DebloaterService::class.java.name)
        )
            .daemon(false)
            .debuggable(false)  // Change to true only for debugging
            .version(1)
            .tag("debloater")  // Important: unique tag for identification
    }

    fun init(context: Context) {
        this.context = context.applicationContext

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        // Initial attempt
        attemptBind()
    }

    fun cleanup() {
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)

        if (isBound) {
            Shizuku.unbindUserService(userServiceArgs.value, serviceConnection, true)
            isBound = false
        }
    }

    private fun attemptBind() {
        if (isBound) return

        if (!Shizuku.pingBinder()) {
            Toast.makeText(context, "Shizuku is not running", Toast.LENGTH_LONG).show()
            return
        }

        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
            return
        }

        try {
            Shizuku.bindUserService(userServiceArgs.value, serviceConnection)
        } catch (e: Exception) {
            Toast.makeText(context, "Bind failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun uninstall(packageName: String) {
        if (!isBound || debloaterService == null) {
            Toast.makeText(context, "Shizuku not connected - retrying...", Toast.LENGTH_SHORT).show()
            attemptBind()  // Try to reconnect automatically
            return
        }

        try {
            debloaterService?.uninstall(packageName)
            Toast.makeText(context, "Uninstall command sent: $packageName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Uninstall failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun disable(packageName: String) {
        if (!isBound || debloaterService == null) {
            Toast.makeText(context, "Shizuku not connected - retrying...", Toast.LENGTH_SHORT).show()
            attemptBind()
            return
        }

        try {
            debloaterService?.disable(packageName)
            Toast.makeText(context, "Disable command sent: $packageName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Disable failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
