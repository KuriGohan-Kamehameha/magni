package com.ayn.magni.trackpad

import com.ayn.magni.sync.BrowserCommand
import com.ayn.magni.ui.BrowserTrackpadView

class TrackpadCommandConnector(
    private val sendCommand: (BrowserCommand) -> Unit
) {

    fun attach(trackpadView: BrowserTrackpadView) {
        trackpadView.onTapRequested = { x, y ->
            sendCommand(BrowserCommand.TrackpadTap(x, y))
        }
        trackpadView.onLongPressRequested = { x, y ->
            sendCommand(BrowserCommand.TrackpadLongPress(x, y))
        }
        trackpadView.onScrollRequested = { deltaX, deltaY ->
            sendCommand(BrowserCommand.TrackpadScroll(deltaX, deltaY))
        }
        trackpadView.onZoomRequested = { delta ->
            sendCommand(BrowserCommand.Zoom(delta))
        }
        trackpadView.onCursorMoved = { x, y ->
            sendCommand(BrowserCommand.TrackpadMove(x, y))
        }
    }

    fun detach(trackpadView: BrowserTrackpadView) {
        trackpadView.onTapRequested = null
        trackpadView.onLongPressRequested = null
        trackpadView.onScrollRequested = null
        trackpadView.onZoomRequested = null
        trackpadView.onCursorMoved = null
    }
}
