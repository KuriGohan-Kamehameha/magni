package com.ayn.magni.data

import android.content.Context
import android.os.SystemClock
import java.util.UUID

object DisplayRoleStore {
    private const val KEY_MOUSEPAD_MODE = "mousepad_mode"
    private const val KEY_ZOOM_ON_TOP = "zoom_on_top"
    private const val KEY_BOTTOM_SCREEN_BLACKOUT = "bottom_screen_blackout"
    private const val SWAP_TOKEN_TTL_MS = 8_000L
    private const val MAX_SWAP_LOCK_MS = 10_000L
    private var screenSwapLockUntilElapsedMs: Long = 0L
    private var pendingSwapToken: String? = null
    private var pendingSwapTokenExpiresAtMs: Long = 0L

    @Synchronized
    fun isZoomOnTop(context: Context): Boolean {
        return safeGetBoolean(context, KEY_ZOOM_ON_TOP, false)
    }

    @Synchronized
    fun setZoomOnTop(context: Context, zoomOnTop: Boolean) {
        SecureBrowserPrefs.get(context)
            .edit()
            .putBoolean(KEY_ZOOM_ON_TOP, zoomOnTop)
            .commit()
    }

    @Synchronized
    fun isMousepadModeEnabled(context: Context): Boolean {
        return safeGetBoolean(context, KEY_MOUSEPAD_MODE, false)
    }

    @Synchronized
    fun setMousepadModeEnabled(context: Context, enabled: Boolean) {
        SecureBrowserPrefs.get(context)
            .edit()
            .putBoolean(KEY_MOUSEPAD_MODE, enabled)
            .commit()
    }

    @Synchronized
    fun isBottomScreenBlackoutEnabled(context: Context): Boolean {
        return safeGetBoolean(context, KEY_BOTTOM_SCREEN_BLACKOUT, false)
    }

    @Synchronized
    fun setBottomScreenBlackoutEnabled(context: Context, enabled: Boolean) {
        SecureBrowserPrefs.get(context)
            .edit()
            .putBoolean(KEY_BOTTOM_SCREEN_BLACKOUT, enabled)
            .commit()
    }

    @Synchronized
    fun toggleBottomScreenBlackout(context: Context): Boolean {
        val next = !isBottomScreenBlackoutEnabled(context)
        setBottomScreenBlackoutEnabled(context, next)
        return next
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
        val boundedDuration = lockDurationMs.coerceIn(0L, MAX_SWAP_LOCK_MS)
        screenSwapLockUntilElapsedMs = now + boundedDuration
        return true
    }

    @Synchronized
    fun releaseScreenSwapLock() {
        screenSwapLockUntilElapsedMs = 0L
    }

    @Synchronized
    fun issueSwapHandoffToken(): String {
        val token = UUID.randomUUID().toString()
        pendingSwapToken = token
        pendingSwapTokenExpiresAtMs = SystemClock.elapsedRealtime() + SWAP_TOKEN_TTL_MS
        return token
    }

    @Synchronized
    fun consumeSwapHandoffToken(token: String?): Boolean {
        val provided = token?.trim().orEmpty()
        if (provided.isEmpty()) {
            return false
        }

        val now = SystemClock.elapsedRealtime()
        if (now > pendingSwapTokenExpiresAtMs) {
            pendingSwapToken = null
            pendingSwapTokenExpiresAtMs = 0L
            return false
        }

        val expected = pendingSwapToken ?: return false
        if (provided != expected) {
            return false
        }

        pendingSwapToken = null
        pendingSwapTokenExpiresAtMs = 0L
        return true
    }

    private fun safeGetBoolean(context: Context, key: String, fallback: Boolean): Boolean {
        val prefs = SecureBrowserPrefs.get(context)
        return runCatching { prefs.getBoolean(key, fallback) }.getOrDefault(fallback)
    }
}
