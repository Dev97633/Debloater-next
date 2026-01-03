package com.example.debloater

import android.os.Process
import com.example.debloater.IDebloaterService
import java.io.DataOutputStream

class DebloaterService : IDebloaterService.Stub() {

    // Only the empty constructor is required for UserService
    constructor() : super()

    override fun uninstall(packageName: String) {
        executeAsShell("pm uninstall --user 0 $packageName")
    }

    override fun disable(packageName: String) {
        executeAsShell("pm disable-user --user 0 $packageName")
    }

    private fun executeAsShell(command: String) {
        try {
            val p = Runtime.getRuntime().exec("sh")
            val os = DataOutputStream(p.outputStream)
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            p.waitFor()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }
}
