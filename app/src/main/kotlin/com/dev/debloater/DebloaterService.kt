package com.dev.debloater

import android.os.Process
import java.io.DataOutputStream

class DebloaterService : IDebloaterService.Stub() {

    override fun uninstall(packageName: String) {
        executeShellCommand("pm uninstall --user 0 $packageName")
    }

    override fun restore(packageName: String) {
        executeShellCommand("pm install-existing --user 0 $packageName")
    }

    override fun disable(packageName: String) {
        executeShellCommand("pm disable-user --user 0 $packageName")
    }

    override fun enable(packageName: String) {
    executeShellCommand("pm enable $packageName")
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
