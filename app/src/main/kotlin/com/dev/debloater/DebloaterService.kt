package com.dev.debloater

import android.os.Process
import com.dev.debloater.IDebloaterService
import java.io.DataOutputStream

class DebloaterService : IDebloaterService.Stub() {

    // Only a single no-arg constructor is allowed for AIDL Stub in Kotlin
    // Shizuku will instantiate it directly
    init {
        // Optional: any initialization here
    }

    override fun uninstall(packageName: String) {
        executeShellCommand("pm uninstall --user 0 $packageName")
    }

    override fun disable(packageName: String) {
        executeShellCommand("pm disable-user --user 0 $packageName")
    }

    override fun enable(packageName: String) {
    Runtime.getRuntime().exec(arrayOf("pm", "enable", packageName))
    }

    private fun executeShellCommand(command: String) {
        try {
            val process = Runtime.getRuntime().exec("sh")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            outputStream.close()
            process.waitFor()
        } catch (e: Exception) {
            throw RuntimeException("Failed to execute: $command", e)
        }
    }

    override fun destroy() {
        // Cleanly kill the privileged process
        Process.killProcess(Process.myPid())
    }
}
