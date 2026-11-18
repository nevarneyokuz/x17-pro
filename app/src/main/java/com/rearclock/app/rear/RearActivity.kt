package com.rearclock.app.rear

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.rearclock.app.core.DisplayFinder
import com.rearclock.app.core.RearCommands
import com.rearclock.app.core.RearSettings

/**
 * The one window on the back screen. It is started from the shell (over Shizuku) with the
 * session token; without it the activity finishes at once.
 */
class RearActivity : Activity() {

    private lateinit var rearView: RearView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent.getStringExtra("token")
        if (token != RearSettings.token(applicationContext)) {
            finishAndRemoveTask()
            return
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        rearView = RearView(this)
        rearView.showClock = RearSettings.clockEnabled(this)
        rearView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(rearView)

        rearView.setOnLongClickListener {
            exitToXiaomi()
            true
        }

        RearSettings.wallpaper(applicationContext)?.let { uri ->
            val size = DisplayFinder.naturalSize(display)
            rearView.loadWallpaper(uri, size.width, size.height)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            rearView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    override fun onDestroy() {
        rearView.onRemoved()
        super.onDestroy()
    }

    private fun exitToXiaomi() {
        KeeperService.stop(this)
        val displayId = DisplayFinder.rearIdOrLastKnown(applicationContext)
        if (displayId != null) {
            RearCommands.restoreXiaomiRearLauncher(displayId)
        }
        finishAndRemoveTask()
    }
}
