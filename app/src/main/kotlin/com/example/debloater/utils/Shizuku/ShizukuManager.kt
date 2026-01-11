package com.example.debloater

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

object ShizukuManager {

    private const val REQUEST_CODE = 1000
    private var debloaterService: IDebloaterService? = null
    private var isBound = false

    private lateinit var context: Context

    private val scope = CoroutineScope(Dispatchers.Main)

    private var snackbarHostState: SnackbarHostState? = null

    fun setSnackbarHostState(hostState: SnackbarHostState) {
        snackbarHostState = hostState
    }

    private fun showMessage(msg: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        scope.launch {
            snackbarHostState?.showSnackbar(msg, duration = duration) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showMessage("Shizuku permission granted")
                attemptBind()
            } else {
                showMessage("Shizuku permission denied")
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        showMessage("Shizuku binder received")
        attemptBind()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isBound = false
        debloaterService = null
        showMessage("Shizuku binder died - restart Shizuku", SnackbarDuration.Long)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            debloaterService = IDebloaterService.Stub.asInterface(service)
            isBound = true
            showMessage("Shizuku service connected successfully!", SnackbarDuration.Long)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            debloaterService = null
            isBound = false
            showMessage("Shizuku service disconnected")
        }
    }

    private val userServiceArgs = lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, DebloaterService::class.java.name)
        )
            .processNameSuffix("service")
            .daemon(false)
            .debuggable(false)
            .version(1)
            .tag("debloater")
    }

    fun init(context: Context) {
        this.context = context.applicationContext

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

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
            showMessage("Shizuku is not running", SnackbarDuration.Long)
            return
        }

        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
            return
        }

        try {
            Shizuku.bindUserService(userServiceArgs.value, serviceConnection)
        } catch (e: Exception) {
            showMessage("Bind failed: ${e.message}", SnackbarDuration.Long)
        }
    }

    fun uninstall(packageName: String) {
        if (!isBound || debloaterService == null) {
            showMessage("Shizuku not connected - retrying...")
            attemptBind()
            return
        }

        try {
            debloaterService?.uninstall(packageName)
            showMessage("Uninstall command sent: $packageName")
        } catch (e: Exception) {
            showMessage("Uninstall failed: ${e.message}", SnackbarDuration.Long)
        }
    }

    fun disable(packageName: String) {
        if (!isBound || debloaterService == null) {
            showMessage("Shizuku not connected - retrying...")
            attemptBind()
            return
        }

        try {
            debloaterService?.disable(packageName)
            showMessage("Disable command sent: $packageName")
        } catch (e: Exception) {
            showMessage("Disable failed: ${e.message}", SnackbarDuration.Long)
        }
    }
}
