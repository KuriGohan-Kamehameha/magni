package com.ayn.magni.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.webkit.WebView

class SyncWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var onScrollChangedListener: (() -> Unit)? = null
    private var lastScrollCallbackAtMs: Long = 0L
    private val scrollCallbackThrottleMs: Long = 50L

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        
        // Throttle scroll callbacks to reduce overhead
        val now = SystemClock.elapsedRealtime()
        if (now - lastScrollCallbackAtMs >= scrollCallbackThrottleMs) {
            lastScrollCallbackAtMs = now
            onScrollChangedListener?.invoke()
        }
    }

    fun pageContentWidth(): Int = computeHorizontalScrollRange().coerceAtLeast(1)

    fun pageContentHeight(): Int = computeVerticalScrollRange().coerceAtLeast(1)
}
