package com.ayn.magni

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.webkit.WebView
import com.ayn.magni.data.BrowserSettingsStore
import com.ayn.magni.sync.BrowserSyncBus

class MagniApp : Application() {
    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        // Keep whole-document capture available for overview map snapshots.
        runCatching {
            WebView.enableSlowWholeDocumentDraw()
        }
        BrowserSettingsStore.applyTheme(this)
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) {
                    BrowserSyncBus.reportActivityDisplayPresence(
                        activityToken = System.identityHashCode(activity),
                        active = true,
                        displayId = activityDisplayId(activity)
                    )
                }

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) {
                    BrowserSyncBus.reportActivityDisplayPresence(
                        activityToken = System.identityHashCode(activity),
                        active = false,
                        displayId = null
                    )
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    BrowserSyncBus.reportActivityDisplayPresence(
                        activityToken = System.identityHashCode(activity),
                        active = false,
                        displayId = null
                    )
                }
            }
        )
    }

    private fun activityDisplayId(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.displayId
        }
    }
}
