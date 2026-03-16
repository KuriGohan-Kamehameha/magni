package com.ayn.magni.sync

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.Display
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ZoomPresence(
    val isActive: Boolean = false,
    val displayId: Int? = null,
    val lastSeenElapsedMs: Long = 0L
)

/**
 * Thread-safe synchronization bus for browser state.
 * NASA standard: explicit thread-safety and defensive null handling.
 */
object BrowserSyncBus {
    private val stateFlow = MutableStateFlow(BrowserUiState())
    private val topDisplayHasMagniFlow = MutableStateFlow(false)
    private val zoomPresenceFlow = MutableStateFlow(ZoomPresence())
    private val commandsFlow = MutableSharedFlow<BrowserCommand>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val activeDisplayByActivityToken = linkedMapOf<Int, Int>()
    private val activityPresenceLock = Any()
    private const val MAX_ACTIVE_ACTIVITY_TOKENS = 64
    
    // NASA standard: use explicit locking for bitmap operations to prevent race conditions
    private val snapshotLock = ReentrantReadWriteLock()
    private var currentSnapshot: Bitmap? = null

    val state: StateFlow<BrowserUiState> = stateFlow.asStateFlow()
    val topDisplayHasMagni: StateFlow<Boolean> = topDisplayHasMagniFlow.asStateFlow()
    val zoomPresence: StateFlow<ZoomPresence> = zoomPresenceFlow.asStateFlow()
    val commands = commandsFlow.asSharedFlow()

    fun reportActivityDisplayPresence(activityToken: Int, active: Boolean, displayId: Int?) {
        synchronized(activityPresenceLock) {
            if (active && displayId != null) {
                activeDisplayByActivityToken[activityToken] = displayId
                while (activeDisplayByActivityToken.size > MAX_ACTIVE_ACTIVITY_TOKENS) {
                    val staleToken = activeDisplayByActivityToken.keys.firstOrNull() ?: break
                    activeDisplayByActivityToken.remove(staleToken)
                }
            } else {
                activeDisplayByActivityToken.remove(activityToken)
            }
            topDisplayHasMagniFlow.value =
                activeDisplayByActivityToken.values.any { it == Display.DEFAULT_DISPLAY }
        }
    }

    fun reportZoomPresence(active: Boolean, displayId: Int?) {
        val safeDisplayId = if (active) displayId else null
        val now = SystemClock.elapsedRealtime()
        zoomPresenceFlow.update { current ->
            if (!active && !current.isActive && current.displayId == null) {
                current
            } else
            if (current.isActive == active && current.displayId == safeDisplayId) {
                current.copy(lastSeenElapsedMs = now)
            } else {
                ZoomPresence(
                    isActive = active,
                    displayId = safeDisplayId,
                    lastSeenElapsedMs = now
                )
            }
        }
    }

    fun updateViewport(viewport: BrowserViewport) {
        stateFlow.update { current ->
            if (current.viewport == viewport) {
                current
            } else {
                current.copy(viewport = viewport)
            }
        }
    }

    fun updateNavigation(
        title: String,
        url: String,
        progress: Int,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        val clampedProgress = progress.coerceIn(0, 100)
        stateFlow.update {
            if (it.title == title &&
                it.url == url &&
                it.progress == clampedProgress &&
                it.canGoBack == canGoBack &&
                it.canGoForward == canGoForward
            ) {
                it
            } else {
                it.copy(
                    title = title,
                    url = url,
                    progress = clampedProgress,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                )
            }
        }
    }

    fun updateChromeVisibility(visible: Boolean) {
        stateFlow.update { current ->
            if (current.chromeControlsVisible == visible) {
                current
            } else {
                current.copy(chromeControlsVisible = visible)
            }
        }
    }

    fun updateSnapshot(snapshot: Bitmap?, pageWidth: Int, pageHeight: Int) {
        // NASA standard: validate inputs and handle thread-safety properly
        val safePageWidth = pageWidth.coerceAtLeast(1)
        val safePageHeight = pageHeight.coerceAtLeast(1)
        val safeSnapshot = if (snapshot?.isRecycled == true) null else snapshot
        
        snapshotLock.write {
            val previous = currentSnapshot
            currentSnapshot = safeSnapshot
            // Only recycle if the bitmap is different and not recycled
            if (previous != null && previous !== safeSnapshot && !previous.isRecycled) {
                runCatching { previous.recycle() }
            }
        }
        
        stateFlow.update {
            it.copy(
                snapshotVersion = it.snapshotVersion + 1,
                snapshotPageWidth = safePageWidth,
                snapshotPageHeight = safePageHeight
            )
        }
    }

    fun latestSnapshot(): Bitmap? {
        val snapshot = snapshotLock.read { currentSnapshot }
        if (snapshot?.isRecycled == true) {
            snapshotLock.write {
                if (currentSnapshot?.isRecycled == true) {
                    currentSnapshot = null
                }
            }
            return null
        }
        return snapshot
    }

    fun sendCommand(command: BrowserCommand) {
        commandsFlow.tryEmit(command)
    }
}
