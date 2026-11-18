package com.rearclock.app.core

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display

object DisplayFinder {

    private const val PREFS = "rear_clock"
    private const val KEY_ID = "rear_display_id"

    fun rearDisplay(context: Context): Display? {
        val manager = context.getSystemService(DisplayManager::class.java)
        val displays = manager.displays

        val remembered = rememberedId(context)
        remembered?.let { id ->
            return displays.firstOrNull { it.displayId == id }
        }

        val candidate = displays
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .map { d ->
                val m = DisplayMetrics().also { d.getRealMetrics(it) }
                Triple(d.displayId, m.widthPixels, m.heightPixels)
            }
            .filter { (_, w, h) -> w in 300..2000 && h in 300..2000 }
            .minByOrNull { (_, w, h) -> w.toLong() * h }
            ?: return null

        val id = candidate.first
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ID, id).apply()
        return displays.firstOrNull { it.displayId == id }
    }

    fun rearIdOrLastKnown(context: Context): Int? =
        rearDisplay(context)?.displayId ?: rememberedId(context)

    fun logInfo(context: Context): String {
        val display = rearDisplay(context)
            ?: return "Arka ekran bulunamadı (cihaz bu uygulamayı desteklemiyor olabilir)"
        val m = DisplayMetrics().also { display.getRealMetrics(it) }
        return "Arka ekran: display ${display.displayId}, ${m.widthPixels} x ${m.heightPixels}"
    }

    fun naturalSize(display: Display): Size {
        val m = DisplayMetrics().also { display.getRealMetrics(it) }
        return Size(maxOf(m.widthPixels, m.heightPixels), minOf(m.widthPixels, m.heightPixels))
    }

    private fun rememberedId(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ID, -1).takeIf { it >= 0 }
}
