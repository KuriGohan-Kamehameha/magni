package com.ayn.magni.data

import android.content.Context
import android.os.SystemClock

object DisplayRoleStore {
    private const val PREFS_NAME = "browser_prefs"
    private const val KEY_ZOOM_ON_TOP = "zoom_on_top"
    private var screenSwapLockUntilElapsedMs: Long = 0L

    @Synchronized
    fun isZoomOnTop(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ZOOM_ON_TOP, false)
    }

    @Synchronized
    fun setZoomOnTop(context: Context, zoomOnTop: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ZOOM_ON_TOP, zoomOnTop)
            .apply()
    }

    @Synchronized
    fun toggleZoomOnTop(context: Context): Boolean {
        val next = !isZoomOnTop(context)
        setZoomOnTop(context, next)
        return next
    }

    @Synchronized
    fun tryAcquireScreenSwapLock(lockDurationMs: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now < screenSwapLockUntilElapsedMs) {
            return false
        }
        screenSwapLockUntilElapsedMs = now + lockDurationMs.coerceAtLeast(0L)
        return true
    }

    @Synchronized
    fun releaseScreenSwapLock() {
        screenSwapLockUntilElapsedMs = 0L
    }
}
