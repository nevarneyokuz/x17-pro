package com.rearclock.app.rear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class RearView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var wallpaper: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var showClock: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMMM EEEE", Locale("tr"))

    private val backgroundPaint = Paint().apply { color = Color.BLACK }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-thin", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt()
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private var timeText = ""
    private var dateText = ""

    private val ticker = object : Runnable {
        override fun run() {
            updateTime()
            invalidate()
            postInvalidateDelayed(15_000L)
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updateTime()
        removeCallbacks(ticker)
        post(ticker)
    }

    fun loadWallpaper(uri: Uri, width: Int, height: Int) {
        thread(name = "wallpaper-decode") {
            val decoded = decodeSized(uri, width, height)
            post {
                wallpaper = decoded
            }
        }
    }

    fun onRemoved() {
        removeCallbacks(ticker)
    }

    private fun updateTime() {
        val now = Date()
        timeText = timeFormat.format(now)
        dateText = dateFormat.format(now)
    }

    private fun decodeSized(uri: Uri, targetW: Int, targetH: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            var sample = 1
            while (options.outWidth / (sample * 2) >= targetW && options.outHeight / (sample * 2) >= targetH) {
                sample *= 2
            }
            val loadOptions = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, loadOptions)
            }?.let { scaledCrop(it, targetW, targetH) } ?: return null
        } catch (e: Exception) {
            null
        }
    }

    private fun scaledCrop(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = maxOf(targetW.toFloat() / bitmap.width, targetH.toFloat() / bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(targetW)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(targetH)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val left = (w - targetW) / 2
        val top = (h - targetH) / 2
        val result = if (left == 0 && top == 0 && w == targetW && h == targetH) {
            scaled
        } else {
            Bitmap.createBitmap(scaled, left, top, targetW, targetH)
        }
        if (bitmap != scaled && scaled != result) bitmap.recycle()
        return result
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(canvas.clipBounds, backgroundPaint)
        wallpaper?.let {
            val scale = maxOf(w / it.width, h / it.height)
            val dw = it.width * scale
            val dh = it.height * scale
            val left = (w - dw) / 2f
            val top = (h - dh) / 2f
            canvas.drawBitmap(it, null, RectF(left, top, left + dw, top + dh), bitmapPaint)
        }

        if (!showClock) return
        val longest = maxOf(w, h)
        clockPaint.textSize = longest * 0.22f
        datePaint.textSize = longest * 0.045f

        val cx = w / 2f
        val cy = h / 2f
        val timeY = if (w > h) cy else cy - clockPaint.textSize * 0.1f
        val dateY = if (w > h) cy + clockPaint.textSize * 0.45f else cy + clockPaint.textSize * 0.75f

        canvas.drawText(timeText, cx, timeY, clockPaint)
        canvas.drawText(dateText, cx, dateY, datePaint)
    }
}
