package com.rearclock.app.rear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.rearclock.app.R
import com.rearclock.app.core.DisplayFinder
import com.rearclock.app.core.RearCommands
import com.rearclock.app.core.RearSettings
import kotlin.concurrent.thread

/**
 * While [RearSettings.active] is true this service keeps the rear activity in front on the back
 * screen: Xiaomi's own launcher task comes back whenever the rear display dims, so the watcher
 * puts the clock back the same way. It also wakes the rear display on demand.
 */
class KeeperService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val appContext: Context = this

    private val watchTask = object : Runnable {
        override fun run() {
            if (!RearSettings.active(appContext)) {
                stopSelf()
                return
            }
            thread(name = "rear-watch") {
                val displayId = DisplayFinder.rearIdOrLastKnown(appContext)
                if (displayId == null) {
                    handler.postDelayed(this, WATCH_INTERVAL_MS)
                    return@thread
                }
                if (RearSettings.keepAwake(appContext)) {
                    RearCommands.wakeDisplay(displayId)
                }
                val tasks = RearCommands.taskList()
                if (tasks != null && RearCommands.xiaomiLauncherOnTop(tasks, displayId)) {
                    recoverRearClock(displayId)
                }
                handler.postDelayed(this, WATCH_INTERVAL_MS)
            }
        }
    }

    /**
     * Xiaomi's back screen took over. Our activity may still be alive on the default display,
     * in which case moving it back works; otherwise relaunch it locally first, then move.
     */
    private fun recoverRearClock(displayId: Int) {
        val tasks = RearCommands.taskList() ?: return
        val taskId = RearCommands.findOurTaskId(tasks)
        if (taskId != null) {
            if (RearCommands.moveTaskToDisplay(taskId, displayId) == "ok") return
        }
        val launched = RearCommands.launchHostLocal(packageName, RearSettings.token(appContext))
        if (launched == "ok") {
            Thread.sleep(600)
            val refreshed = RearCommands.taskList() ?: return
            val newTaskId = RearCommands.findOurTaskId(refreshed) ?: return
            RearCommands.moveTaskToDisplay(newTaskId, displayId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(watchTask)
        handler.post(watchTask)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchTask)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Arka ekran saati açık")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "keeper"
        private const val NOTIFICATION_ID = 1
        private const val WATCH_INTERVAL_MS = 3000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, KeeperService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeeperService::class.java))
        }
    }
}
