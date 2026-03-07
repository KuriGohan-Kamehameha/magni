package com.ayn.magni.sync

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object BrowserSyncBus {
    private val stateFlow = MutableStateFlow(BrowserUiState())
    private val commandsFlow = MutableSharedFlow<BrowserCommand>(
        replay = 0,
        extraBufferCapacity = 48,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val snapshotRef = AtomicReference<Bitmap?>(null)

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
        val safeSnapshot = if (snapshot?.isRecycled == true) null else snapshot
        val previous = snapshotRef.getAndSet(safeSnapshot)
        if (previous != null && previous !== safeSnapshot && !previous.isRecycled) {
            runCatching { previous.recycle() }
        }
        stateFlow.update {
            it.copy(
                snapshotVersion = it.snapshotVersion + 1,
                snapshotPageWidth = pageWidth.coerceAtLeast(1),
                snapshotPageHeight = pageHeight.coerceAtLeast(1)
            )
        }
    }

    fun latestSnapshot(): Bitmap? {
        val snapshot = snapshotRef.get()
        if (snapshot?.isRecycled == true) {
            snapshotRef.compareAndSet(snapshot, null)
            return null
        }
        return snapshot
    }

    fun sendCommand(command: BrowserCommand) {
        commandsFlow.tryEmit(command)
    }
}
