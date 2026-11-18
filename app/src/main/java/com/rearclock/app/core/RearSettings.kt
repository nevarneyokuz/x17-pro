package com.rearclock.app.core

import android.content.Context
import android.net.Uri
import java.util.UUID

object RearSettings {

    private const val PREFS = "rear_settings"
    private const val KEY_WALLPAPER = "wallpaper_uri"
    private const val KEY_CLOCK = "clock_enabled"
    private const val KEY_KEEP_AWAKE = "keep_awake"
    private const val KEY_ACTIVE = "active"
    private const val KEY_TOKEN = "session_token"

    fun wallpaper(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_WALLPAPER, null)?.let(Uri::parse)

    fun setWallpaper(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_WALLPAPER, uri?.toString()).apply()
    }

    fun clockEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CLOCK, true)

    fun setClockEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CLOCK, value).apply()
    }

    fun keepAwake(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_KEEP_AWAKE, false)

    fun setKeepAwake(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_KEEP_AWAKE, value).apply()
    }

    fun active(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    fun setActive(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ACTIVE, value).apply()
    }

    /** Session token: the rear activity only stays up when launched with the current one. */
    fun token(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_TOKEN, null)
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_TOKEN, fresh).apply()
        return fresh
    }
}
