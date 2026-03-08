package com.ayn.magni.sync

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference
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

/**
 * Thread-safe synchronization bus for browser state.
 * NASA standard: explicit thread-safety and defensive null handling.
 */
object BrowserSyncBus {
    private val stateFlow = MutableStateFlow(BrowserUiState())
    private val commandsFlow = MutableSharedFlow<BrowserCommand>(
        replay = 0,
        extraBufferCapacity = 48,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    // NASA standard: use explicit locking for bitmap operations to prevent race conditions
    private val snapshotLock = ReentrantReadWriteLock()
    private var currentSnapshot: Bitmap? = null

    val state: StateFlow<BrowserUiState> = stateFlow.asStateFlow()
    val commands = commandsFlow.asSharedFlow()

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
        return snapshotLock.read {
            val snapshot = currentSnapshot
            if (snapshot?.isRecycled == true) {
                null
            } else {
                snapshot
            }
        }
    }

    fun sendCommand(command: BrowserCommand) {
        commandsFlow.tryEmit(command)
    }
}
