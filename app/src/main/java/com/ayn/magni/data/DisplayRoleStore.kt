package com.ayn.magni.data

import android.content.Context

object DisplayRoleStore {
    private const val PREFS_NAME = "browser_prefs"
    private const val KEY_ZOOM_ON_TOP = "zoom_on_top"

    fun isZoomOnTop(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ZOOM_ON_TOP, false)
    }

    fun setZoomOnTop(context: Context, zoomOnTop: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ZOOM_ON_TOP, zoomOnTop)
            .apply()
    }

    fun toggleZoomOnTop(context: Context): Boolean {
        val next = !isZoomOnTop(context)
        setZoomOnTop(context, next)
        return next
    }
}
