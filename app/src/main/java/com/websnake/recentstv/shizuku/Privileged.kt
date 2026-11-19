package com.websnake.recentstv.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Where the privileged calls go. One APK, two ways to run it:
 *
 *  - [Mode.SHIZUKU]: a normal install. System services are reached through Shizuku's binder wrapper and shell
 *    commands run in a Shizuku process, both as Shizuku's identity (shell or root).
 *  - [Mode.SYSTEM]: installed by the Magisk module (`magisk/`) as a privileged system app. The app holds the
 *    permissions itself — `REAL_GET_TASKS`, `START_TASKS_FROM_RECENTS`, `FORCE_STOP_PACKAGES`, `DUMP` from the
 *    priv-app allowlist, `REMOVE_TASKS` / `MANAGE_ACTIVITY_TASKS` from the system recents role the module's
 *    framework overlay gives it — so it calls the services directly and runs commands as itself. Shizuku is not used.
 *
 * The mode is fixed for the life of the process: it only changes when the module is installed or removed (a reboot).
 */
object Privileged {

    enum class Mode { SHIZUKU, SYSTEM }

    @Volatile var mode: Mode = Mode.SHIZUKU
        private set

    /** Pick the mode once, at process start ([com.websnake.recentstv.RecentsApp.onCreate]). */
    fun init(context: Context) {
        // signature|privileged: only granted when the app sits in /system/priv-app with the module's allowlist
        val system = context.checkSelfPermission(REAL_GET_TASKS) == PackageManager.PERMISSION_GRANTED
        mode = if (system) Mode.SYSTEM else Mode.SHIZUKU
        Log.i(TAG, "privileged mode: $mode")
    }

    val isSystem get() = mode == Mode.SYSTEM

    /** Readiness for the UI gate. As a system app the access is always there. */
    fun state(): ShizukuState = if (isSystem) ShizukuState(binderAlive = true, permissionGranted = true) else ShizukuManager.state()

    fun ready(): Boolean = state().ready

    /** The caller package moveTaskToFront must name: the identity the call runs as. */
    fun callingPackage(context: Context): String = if (isSystem) context.packageName else SHELL_PKG

    /** A system service binder that carries the privileged identity. */
    fun service(name: String): IBinder? = runCatching {
        if (isSystem) {
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name) as IBinder?
        } else {
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService(name))
        }
    }.getOrElse { Log.w(TAG, "service($name) failed: ${it.cause ?: it}"); null }

    /** Run a shell command with the privileged identity; its stdout ("" on failure). */
    fun exec(command: String): String = runCatching {
        // stderr dropped so a chatty command can't fill the pipe and deadlock waitFor()
        val argv = arrayOf("sh", "-c", "$command 2>/dev/null")
        val process = if (isSystem) Runtime.getRuntime().exec(argv) else {
            val newProcess = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            newProcess.isAccessible = true
            newProcess.invoke(null, argv, null, null) as Process
        }
        BufferedReader(InputStreamReader(process.inputStream)).use { r ->
            val text = r.readText()
            process.waitFor()
            text
        }
    }.getOrElse { Log.w(TAG, "shell failed: ${it.message}"); "" }

    private const val TAG = "RTV"
    private const val REAL_GET_TASKS = "android.permission.REAL_GET_TASKS"
    private const val SHELL_PKG = "com.android.shell"
}
