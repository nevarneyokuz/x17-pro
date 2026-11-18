package com.rearclock.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rearclock.app.R
import com.rearclock.app.core.DisplayFinder
import com.rearclock.app.core.RearCommands
import com.rearclock.app.core.RearSettings
import com.rearclock.app.rear.KeeperService
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    private val status: TextView by lazy { findViewById(R.id.status) }
    private val displayInfo: TextView by lazy { findViewById(R.id.display_info) }
    private val shizukuButton: Button by lazy { findViewById(R.id.grant_shizuku) }
    private val pickButton: Button by lazy { findViewById(R.id.pick_image) }
    private val showButton: Button by lazy { findViewById(R.id.show_rear) }
    private val hideButton: Button by lazy { findViewById(R.id.hide_rear) }
    private val clockSwitch: Switch by lazy { findViewById(R.id.clock_switch) }
    private val awakeSwitch: Switch by lazy { findViewById(R.id.awake_switch) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Shizuku.addBinderReceivedListenerSticky { runOnUiThread { updateUi() } }
        Shizuku.addBinderDeadListener { runOnUiThread { updateUi() } }
        Shizuku.addRequestPermissionResultListener(this)

        shizukuButton.setOnClickListener { requestShizuku() }
        pickButton.setOnClickListener { pickImage() }
        showButton.setOnClickListener { startRear() }
        hideButton.setOnClickListener { stopRear() }

        clockSwitch.setOnCheckedChangeListener { _, checked ->
            RearSettings.setClockEnabled(this, checked)
        }
        awakeSwitch.setOnCheckedChangeListener { _, checked ->
            RearSettings.setKeepAwake(this, checked)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        super.onDestroy()
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        updateUi()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            RearSettings.setWallpaper(this, uri)
            updateUi()
        }
    }

    private fun updateUi() {
        val shizukuReady = RearCommands.isReady()
        shizukuButton.isEnabled = !shizukuReady && Shizuku.pingBinder()
        clockSwitch.isChecked = RearSettings.clockEnabled(this)
        awakeSwitch.isChecked = RearSettings.keepAwake(this)

        val active = RearSettings.active(this)
        showButton.isEnabled = shizukuReady && !active
        hideButton.isEnabled = active

        status.text = when {
            !Shizuku.pingBinder() -> getString(R.string.status_shizuku_needed)
            !shizukuReady -> "Shizuku kurulu ama izin henüz verilmedi."
            RearSettings.wallpaper(this) == null -> "Duvar kağıdı seçilmedi — sadece saat gösterilir."
            else -> "Hazır! 'Arka ekranda göster' ile başlat."
        }
        displayInfo.text = DisplayFinder.logInfo(this)
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            updateUi()
            return
        }
        Shizuku.requestPermission(REQUEST_SHIZUKU)
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK)
    }

    private fun startRear() {
        if (!RearCommands.isReady()) {
            requestShizuku()
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= 33
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        val displayId = DisplayFinder.rearIdOrLastKnown(this)
        if (displayId == null) {
            status.text = "Arka ekran bulunamadı."
            return
        }

        val result = when(RearCommands.launchHostLocal(packageName, RearSettings.token(this))) {
            "ok" -> true.also {
                RearSettings.setActive(this, true)
                status.text = "Arka ekrana taşınıyor..."
            }
            else -> false
        }
        if (result) {
            moveClockToRear(attempt = 1, displayId = displayId)
        } else {
            status.text = "Başlatılamadı: uygulama açılamadı."
        }
        updateUi()
    }

    /**
     * HyperOS refuses to open apps directly on the rear display, but allows moving a running
     * task there. We give the activity a moment to appear, then move its stack.
     */
    private fun moveClockToRear(attempt: Int, displayId: Int) {
        if (!RearSettings.active(this)) return
        if (attempt > 8) {
            status.text = "Taşıma başarısız: görev bulunamadı ('am stack list' boş)."
            return
        }
        android.os.Handler(mainLooper).postDelayed({
            if (!RearSettings.active(this)) return@postDelayed
            val tasks = RearCommands.taskList()
            val taskId = tasks?.let { RearCommands.findOurTaskId(it) }
            if (taskId == null) {
                moveClockToRear(attempt + 1, displayId)
                return@postDelayed
            }
            val moved = RearCommands.moveTaskToDisplay(taskId, displayId)
            if (moved == "ok") {
                status.text = "Arka ekranda gösteriliyor ✓"
                RearCommands.stopXiaomiRearLauncher()
                KeeperService.start(this)
                updateUi()
            } else {
                status.text = "Taşıma başarısız: $moved (tekrar deneniyor)"
                moveClockToRear(attempt + 1, displayId)
            }
        }, 600L)
    }

    private fun stopRear() {
        RearSettings.setActive(this, false)
        KeeperService.stop(this)
        val displayId = DisplayFinder.rearIdOrLastKnown(this)
        if (displayId != null) {
            RearCommands.restoreDisplayTimeout(displayId)
            RearCommands.restoreXiaomiRearLauncher(displayId)
        }
        updateUi()
    }

    companion object {
        private const val REQUEST_SHIZUKU = 1001
        private const val REQUEST_PICK = 1002
        private const val REQUEST_NOTIFICATIONS = 1003
    }
}
