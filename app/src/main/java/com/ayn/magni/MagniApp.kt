package com.ayn.magni

import android.app.Application
import android.webkit.WebView
import com.ayn.magni.data.BrowserSettingsStore

class MagniApp : Application() {
    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        // Keep whole-document capture available for overview map snapshots.
        WebView.enableSlowWholeDocumentDraw()
        BrowserSettingsStore.applyTheme(this)
    }
}
