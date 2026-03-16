package com.ayn.magni.sync

data class BrowserViewport(
    val pageWidth: Int = 1,
    val pageHeight: Int = 1,
    val scrollX: Int = 0,
    val scrollY: Int = 0,
    val viewportWidth: Int = 1,
    val viewportHeight: Int = 1
)

data class BrowserUiState(
    val title: String = "",
    val url: String = "",
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val chromeControlsVisible: Boolean = false,
    val viewport: BrowserViewport? = null,
    val snapshotVersion: Long = 0,
    val snapshotPageWidth: Int = 1,
    val snapshotPageHeight: Int = 1
)

sealed interface BrowserCommand {
    data class ScrollTo(val x: Int, val y: Int) : BrowserCommand
    data class LoadUrl(val rawUrl: String) : BrowserCommand
    data class SetChromeVisible(val visible: Boolean) : BrowserCommand
    data class SetMousepadMode(val enabled: Boolean) : BrowserCommand
    data class SetBottomScreenBlackout(val enabled: Boolean) : BrowserCommand
    data class TrackpadTap(val normalizedX: Float, val normalizedY: Float) : BrowserCommand
    data class TrackpadLongPress(val normalizedX: Float, val normalizedY: Float) : BrowserCommand
    data class TrackpadScroll(val deltaX: Float, val deltaY: Float) : BrowserCommand
    data class TrackpadMove(val normalizedX: Float, val normalizedY: Float) : BrowserCommand
    data object Back : BrowserCommand
    data object Forward : BrowserCommand
    data object Reload : BrowserCommand
    data object Home : BrowserCommand
    data class Zoom(val delta: Int) : BrowserCommand
}
