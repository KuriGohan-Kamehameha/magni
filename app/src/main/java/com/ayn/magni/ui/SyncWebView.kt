package com.ayn.magni.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.accessibility.AccessibilityEvent
import android.webkit.WebView

class SyncWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var onScrollChangedListener: (() -> Unit)? = null
    private var lastScrollCallbackAtMs: Long = 0L
    private val scrollCallbackThrottleMs: Long = SCROLL_CALLBACK_THROTTLE_MS
    
    // NASA standard: track attachment state for proper cleanup
    private var isViewAttached: Boolean = false

    init {
        // Accessibility: Provide semantic description for screen readers
        contentDescription = context.getString(android.R.string.untitled)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        
        // NASA standard: validate attachment state before callback
        if (!isViewAttached) {
            return
        }
        
        // Throttle scroll callbacks to reduce overhead
        val now = SystemClock.elapsedRealtime()
        if (now - lastScrollCallbackAtMs >= scrollCallbackThrottleMs) {
            lastScrollCallbackAtMs = now
            val listener = onScrollChangedListener
            runCatching { listener?.invoke() }
            
            // Accessibility: Announce significant scroll changes for screen readers
            if (isAccessibilityFocused && (l != oldl || t != oldt)) {
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED)
            }
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isViewAttached = true
        lastScrollCallbackAtMs = 0L
    }
    
    override fun onDetachedFromWindow() {
        isViewAttached = false
        // NASA standard: clear callbacks on detach to prevent memory leaks
        onScrollChangedListener = null
        lastScrollCallbackAtMs = 0L
        super.onDetachedFromWindow()
    }

    fun pageContentWidth(): Int {
        return runCatching { computeHorizontalScrollRange() }.getOrDefault(1).coerceAtLeast(1)
    }

    fun pageContentHeight(): Int {
        return runCatching { computeVerticalScrollRange() }.getOrDefault(1).coerceAtLeast(1)
    }

    private companion object {
        private const val SCROLL_CALLBACK_THROTTLE_MS = 50L
    }
}
