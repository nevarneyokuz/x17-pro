package com.rearclock.app.core

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

data class ExecResult(val code: Int?, val out: String, val err: String)

object RearCommands {

    private const val AM = "/system/bin/am"
    private const val INPUT = "/system/bin/input"
    private const val CMD = "/system/bin/cmd"

    const val XIAOMI_REAR_PACKAGE = "com.xiaomi.subscreencenter"

    private val XIAOMI_REAR_LAUNCHERS = listOf(
        "$XIAOMI_REAR_PACKAGE/.SubScreenLauncher",
        "$XIAOMI_REAR_PACKAGE/.subscreenlauncher.SubScreenLauncherActivity",
        "com.xiaomi.mirror/.SubscreenLauncher",
    )

    private const val TIMEOUT_MS = 5000L
    private const val TASK_LIST_TIMEOUT_MS = 3000L
    private const val STREAM_WAIT_MS = 500L

    fun isReady() =
        try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

    /**
     * HyperOS blocks opening third-party apps directly on the rear display ("error 102"), but it
     * does allow moving an already-running task there. So the activity starts on the default
     * display first (this command), then the stack is moved over (moveTaskToDisplay).
     */
    fun launchHostLocal(packageName: String, token: String): String = run(
        AM, "start", "-n", "$packageName/.rear.RearActivity",
        "--es", "token", token,
    )

    fun moveTaskToDisplay(taskId: Int, displayId: Int) =
        run(AM, "display", "move-stack", taskId.toString(), displayId.toString())

    fun launchHost(packageName: String, displayId: Int, token: String): String = run(
        AM, "start", "--display", displayId.toString(), "-n", "$packageName/.rear.RearActivity",
        "--es", "token", token,
    )

    fun wakeDisplay(displayId: Int) =
        run(INPUT, "-d", displayId.toString(), "keyevent", "KEYCODE_WAKEUP")

    fun keepDisplayOn(displayId: Int) =
        run(CMD, "settings", "put", "system", "subscreen_display_time", "3600000")

    fun restoreDisplayTimeout(displayId: Int) =
        run(CMD, "settings", "delete", "system", "subscreen_display_time")

    fun stopXiaomiRearLauncher() = run(AM, "force-stop", XIAOMI_REAR_PACKAGE)

    fun restartXiaomiRearApp() = run(AM, "force-stop", XIAOMI_REAR_PACKAGE)

    fun restoreXiaomiRearLauncher(displayId: Int): String {
        var result = ""
        for (launcher in XIAOMI_REAR_LAUNCHERS) {
            result = run(AM, "start", "--display", displayId.toString(), "-n", launcher)
            if (result == "ok") return "ok ($launcher)"
        }
        return result
    }

    /** What `am stack list` prints, or null if it could not run. */
    fun taskList(): String? {
        if (!isReady()) return null
        return try {
            val result = exec(TASK_LIST_TIMEOUT_MS) { newProcess(arrayOf(AM, "stack", "list")) }
            if (result.code == 0) result.out else null
        } catch (e: Exception) {
            null
        }
    }

    /** True when [displayId]'s top task belongs to Xiaomi's back screen launcher, not to us. */
    fun xiaomiLauncherOnTop(tasks: String, displayId: Int): Boolean {
        val block = displayBlock(tasks, displayId) ?: return false
        val hasXiaomi = block.any { it.contains(XIAOMI_REAR_PACKAGE) }
        if (!hasXiaomi) return false
        val oursAtEnd = block.drop(1).any { it.contains("com.rearclock.app") }
        return !oursAtEnd
    }

    /** The taskId of our rear activity wherever it currently lives, or null. */
    fun findOurTaskId(tasks: String): Int? {
        val lines = tasks.lines()
        var taskId: Int? = null
        for (line in lines) {
            val taskMatch = Regex("Task id #(\\d+)").find(line)
            if (taskMatch != null) {
                taskId = taskMatch.groupValues[1].toIntOrNull()
            } else if (line.contains("com.rearclock.app") && taskId != null) {
                return taskId
            }
        }
        return null
    }

    private fun displayBlock(tasks: String, displayId: Int): List<String>? {
        val lines = tasks.lines()
        val start = lines.indexOfFirst { it.contains("Display #$displayId", ignoreCase = true) }
        if (start < 0) return null
        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (lines[i].contains("Display #")) {
                end = i
                break
            }
        }
        return lines.subList(start, end)
    }

    private fun run(vararg command: String): String {
        if (!isReady()) return "Shizuku not ready"
        return try {
            summary(exec(TIMEOUT_MS) { newProcess(arrayOf(*command)) })
        } catch (e: Exception) {
            "error: ${e.cause?.message ?: e.message}"
        }
    }

    private fun summary(result: ExecResult): String {
        val code = result.code ?: return "timed out"
        val output = result.out.trim() + "\n" + result.err.trim()
        val error = output.lines().firstOrNull { it.startsWith("Error") }
        return when {
            code != 0 -> "failed ($code): ${output.trim().lines().lastOrNull().orEmpty()}"
            error != null -> "failed: $error"
            else -> "ok"
        }
    }

    fun exec(timeoutMs: Long, start: () -> Process): ExecResult {
        val process = start()
        val out = Drain(process.inputStream)
        val err = Drain(process.errorStream)
        var code = 0
        var failure: Exception? = null
        val waiter = thread(isDaemon = true, name = "RearClock-wait") {
            try {
                code = process.waitFor()
            } catch (e: Exception) {
                failure = e
            }
        }
        waiter.join(timeoutMs)
        if (waiter.isAlive || failure != null) {
            try {
                process.destroy()
            } catch (e: Exception) {
            }
            out.close()
            err.close()
            failure?.let { throw it }
            return ExecResult(null, out.text(), err.text())
        }
        return ExecResult(code, out.text(), err.text())
    }

    private class Drain(private val stream: InputStream) : Closeable {
        private val bytes = ByteArrayOutputStream()
        private val reader = thread(isDaemon = true, name = "RearClock-read") {
            try {
                stream.copyTo(bytes)
            } catch (e: IOException) {
            }
        }

        fun text(): String {
            reader.join(STREAM_WAIT_MS)
            return bytes.toString(Charsets.UTF_8.name())
        }

        override fun close() {
            try {
                stream.close()
            } catch (e: IOException) {
            }
        }
    }

    private val newProcessMethod by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
    }

    private fun newProcess(command: Array<String>) =
        newProcessMethod.invoke(null, command, null, null) as Process
}
