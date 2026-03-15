package com.ayn.magni

import android.app.ActivityOptions
import android.app.DownloadManager
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ClipData
import android.content.ComponentCallbacks2
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.text.format.Formatter
import android.view.KeyEvent
import android.view.Display
import android.view.MotionEvent
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.webkit.WebViewClient
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ayn.magni.data.BrowserBookmarkStore
import com.ayn.magni.data.BrowserHistoryStore
import com.ayn.magni.data.BrowserPreferences
import com.ayn.magni.data.BrowserSessionStore
import com.ayn.magni.data.BrowserSettingsStore
import com.ayn.magni.data.DisplayRoleStore
import com.ayn.magni.data.SecureBrowserPrefs
import com.ayn.magni.data.SessionTab
import com.ayn.magni.data.ThemeMode
import com.ayn.magni.data.TrackerBlocker
import com.ayn.magni.data.UrlPrivacySanitizer
import com.ayn.magni.databinding.ActivityZoomBinding
import com.ayn.magni.sync.BrowserCommand
import com.ayn.magni.sync.BrowserSyncBus
import com.ayn.magni.sync.BrowserViewport
import com.ayn.magni.ui.SyncWebView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class BrowserTab(
    val id: Long,
    val webView: SyncWebView,
    var title: String = "",
    var url: String = "",
    val blockedTrackerRequests: AtomicInteger = AtomicInteger(0),
    var pinned: Boolean = false,
    var readerModeActive: Boolean = false,
    var readerSourceUrl: String = "",
    var readerSourceTitle: String = ""
)

private data class ReaderBlock(
    val tag: String,
    val text: String
)

private data class ReaderPayload(
    val title: String,
    val blocks: List<ReaderBlock>
)

private data class SnapshotCapture(
    val bitmap: Bitmap,
    val pageWidth: Int,
    val pageHeight: Int
)

private data class SnapshotPlan(
    val scale: Float,
    val width: Int,
    val height: Int
)

private enum class TabTransitionStyle {
    NONE,
    SLIDE,
    NEW_TAB
}

class ZoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityZoomBinding

    private val captureHandler = Handler(Looper.getMainLooper())
    private val uiHandler = Handler(Looper.getMainLooper())
    private val captureRunnable = Runnable { captureOverviewSnapshot() }
    private val autoHideChromeRunnable = Runnable {
        setUrlBarVisibility(visible = false, announce = false)
    }
    private val persistenceExecutor = Executors.newSingleThreadExecutor()
    private val persistSessionRunnable = Runnable { flushPendingSessionPersist() }

    private val tabs = mutableListOf<BrowserTab>()
    private val tabsByWebView = ConcurrentHashMap<SyncWebView, BrowserTab>()
    private var currentTabIndex: Int = -1
    private var nextTabId: Long = 1L

    private lateinit var currentPreferences: BrowserPreferences
    private var progress: Int = 0
    private var defaultMobileUserAgent: String = ""
    private var reusableSnapshotBitmap: Bitmap? = null
    private var lastSnapshotCaptureAtMs: Long = 0L
    private var lastValidViewport: BrowserViewport? = null
    private var pendingSessionSnapshot: List<SessionTab>? = null
    private var pendingSessionIndex: Int = 0
    private var isUrlBarVisible: Boolean = false
    private var toolbarHandleTouchStartY: Float = 0f
    private var toolbarTouchStartY: Float = 0f
    private var readerActiveButtonTextColor: Int = Color.parseColor("#EF8B2F")
    private var readerInactiveButtonTextColor: Int = Color.WHITE
    private var isL1Pressed: Boolean = false
    private var isR1Pressed: Boolean = false
    private var isL2Pressed: Boolean = false
    private var isR2Pressed: Boolean = false
    private var l1r1ComboActive: Boolean = false
    private var l2r2ComboActive: Boolean = false
    private var lastScreenSwapAtMs: Long = 0L
    private var isFinishingForScreenSwap: Boolean = false
    private var isSwapRetryScheduled: Boolean = false

    private val trackerUiRefreshScheduled = AtomicBoolean(false)
    private val lastTrackerUiRefreshAtMs = AtomicLong(0L)
    private val generalToastAtMs = AtomicLong(0L)
    private val jsDialogNoticeAtMs = AtomicLong(0L)
    private val downloadNoticeAtMs = AtomicLong(0L)
    private val downloadWindowStartAtMs = AtomicLong(0L)
    private val downloadAttemptsInWindow = AtomicInteger(0)

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val shouldClearBrowsingDataNow = result.data?.getBooleanExtra(
                SettingsActivity.EXTRA_CLEAR_BROWSING_DATA_NOW,
                false
            ) == true
            if (shouldClearBrowsingDataNow) {
                clearBrowsingDataNow()
            }

            refreshPreferences(reloadCurrent = true)

            val openUrl = result.data?.getStringExtra(SettingsActivity.EXTRA_OPEN_URL)
            if (!openUrl.isNullOrBlank()) {
                loadUrlInCurrentTab(openUrl)
            }
        }

        updateTabIndicator()
        publishBrowserState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        BrowserSettingsStore.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        // WebView debugging is disabled unconditionally for security.
        WebView.setWebContentsDebuggingEnabled(false)

        binding = ActivityZoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureImmersiveMode()

        defaultMobileUserAgent = WebSettings.getDefaultUserAgent(this)
        currentPreferences = BrowserSettingsStore.load(this)
        if (currentPreferences.privateBrowsingEnabled) {
            clearAllWebData()
        }

        configureToolbar()
        configureBackHandling()
        collectCommands()
        applyScreenRoleLayout()
        applyChromeStyle()
        BrowserSyncBus.updateChromeVisibility(isUrlBarVisible)

        restoreTabsOrCreateDefault()
        if (savedInstanceState == null) {
            handleExternalViewIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        configureImmersiveMode()
        val web = currentWebView()
        web?.onResume()
        progress = web?.progress ?: 0
        applyScreenRoleLayout()
        refreshPreferences(reloadCurrent = false)
        publishBrowserState()
        updateTabIndicator()
        scheduleCapture(120L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalViewIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureImmersiveMode()
        }
    }

    override fun onPause() {
        captureHandler.removeCallbacks(captureRunnable)
        uiHandler.removeCallbacks(autoHideChromeRunnable)
        tabs.forEach { it.webView.onPause() }
        super.onPause()
        persistTabSession(flush = true)
    }

    override fun onDestroy() {
        captureHandler.removeCallbacksAndMessages(null)
        if (::currentPreferences.isInitialized) {
            persistTabSession(flush = true)
        }
        uiHandler.removeCallbacksAndMessages(null)
        if (::currentPreferences.isInitialized &&
            isFinishing &&
            !isFinishingForScreenSwap &&
            (currentPreferences.privateBrowsingEnabled || currentPreferences.clearBrowsingDataOnExit)
        ) {
            clearAllWebData()
        }

        releaseSnapshotCache()

        val tabsCopy = tabs.toList()
        tabs.clear()
        tabsByWebView.clear()
        tabsCopy.forEach { tab ->
            safelyDestroyWebView(tab.webView)
        }
        persistenceExecutor.shutdown()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            releaseSnapshotCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        releaseSnapshotCache()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((event?.repeatCount ?: 0) > 0 && isShoulderButton(keyCode)) {
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_X -> {
                swapScreenRoles()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_L1 -> {
                isL1Pressed = true
                if (isR1Pressed && !l1r1ComboActive) {
                    l1r1ComboActive = true
                    createNewTabAndOpenUrlBar()
                }
                return true
            }

            KeyEvent.KEYCODE_BUTTON_R1 -> {
                isR1Pressed = true
                if (isL1Pressed && !l1r1ComboActive) {
                    l1r1ComboActive = true
                    createNewTabAndOpenUrlBar()
                }
                return true
            }

            KeyEvent.KEYCODE_BUTTON_L2 -> {
                isL2Pressed = true
                if (isR2Pressed && !l2r2ComboActive) {
                    l2r2ComboActive = true
                    openUrlBarWithKeyboard()
                }
                return true
            }

            KeyEvent.KEYCODE_BUTTON_R2 -> {
                isR2Pressed = true
                if (isL2Pressed && !l2r2ComboActive) {
                    l2r2ComboActive = true
                    openUrlBarWithKeyboard()
                }
                return true
            }

            KeyEvent.KEYCODE_BUTTON_Y -> {
                if (currentWebView() == null) {
                    return super.onKeyDown(keyCode, event)
                }
                applyZoomStep(delta = -1, captureDelayMs = 120L)
                return true
            }

            KeyEvent.KEYCODE_BUTTON_A -> {
                toggleUrlBarVisibility()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_START -> {
                showTabsDialog()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                openUrlBarWithKeyboard()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_B -> {
                val web = currentWebView() ?: return super.onKeyDown(keyCode, event)
                if (web.canGoBack()) {
                    web.goBack()
                    publishBrowserState()
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                isL1Pressed = false
                if (l1r1ComboActive) {
                    if (!isR1Pressed) {
                        l1r1ComboActive = false
                    }
                    return true
                }
                navigateBackWithShoulder()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_R1 -> {
                isR1Pressed = false
                if (l1r1ComboActive) {
                    if (!isL1Pressed) {
                        l1r1ComboActive = false
                    }
                    return true
                }
                navigateForwardWithShoulder()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_L2 -> {
                isL2Pressed = false
                if (l2r2ComboActive && !isR2Pressed) {
                    l2r2ComboActive = false
                }
                return true
            }

            KeyEvent.KEYCODE_BUTTON_R2 -> {
                isR2Pressed = false
                if (l2r2ComboActive && !isL2Pressed) {
                    l2r2ComboActive = false
                }
                return true
            }
        }

        return super.onKeyUp(keyCode, event)
    }

    private fun isShoulderButton(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BUTTON_L1 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_R1 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_L2 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_R2
    }

    private fun navigateBackWithShoulder() {
        val web = currentWebView() ?: return
        if (web.canGoBack()) {
            web.goBack()
            publishBrowserState()
        }
    }

    private fun navigateForwardWithShoulder() {
        val web = currentWebView() ?: return
        if (web.canGoForward()) {
            web.goForward()
            publishBrowserState()
        }
    }

    private fun createNewTabAndOpenUrlBar() {
        createTab(initialUrl = null, switchToTab = true)
        openUrlBarWithKeyboard()
    }

    private fun openUrlBarWithKeyboard() {
        ensureUrlBarVisibleForShortcut()
        openUrlDialog()
    }

    private fun ensureUrlBarVisibleForShortcut() {
        showUrlBarIfHidden()
    }

    private fun configureToolbar() {
        binding.tabInfoText.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.tabInfoText.setOnLongClickListener {
            toggleUrlBarVisibility()
            true
        }
        binding.toolbarHandle.setOnClickListener {
            toggleUrlBarVisibility()
        }
        binding.toolbarHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    toolbarHandleTouchStartY = event.rawY
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val pullThresholdPx = TOOLBAR_PULL_THRESHOLD_DP * resources.displayMetrics.density
                    val draggedUpEnough = toolbarHandleTouchStartY - event.rawY >= pullThresholdPx
                    if (draggedUpEnough) {
                        showUrlBarIfHidden()
                    } else {
                        binding.toolbarHandle.performClick()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        binding.toolbar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    toolbarTouchStartY = event.rawY
                    refreshChromeAutoHideTimer()
                    false
                }

                MotionEvent.ACTION_UP -> {
                    val dismissThresholdPx =
                        TOOLBAR_DISMISS_PULL_THRESHOLD_DP * resources.displayMetrics.density
                    val draggedDownEnough = event.rawY - toolbarTouchStartY >= dismissThresholdPx
                    if (draggedDownEnough) {
                        setUrlBarVisibility(visible = false, announce = true)
                        true
                    } else {
                        refreshChromeAutoHideTimer()
                        false
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    refreshChromeAutoHideTimer()
                    false
                }

                else -> false
            }
        }

        binding.backButton.setOnClickListener {
            currentWebView()?.let { web ->
                if (web.canGoBack()) {
                    web.goBack()
                    publishBrowserState()
                }
            }
        }
        binding.backButton.setOnLongClickListener {
            closeCurrentTab()
            true
        }

        binding.forwardButton.setOnClickListener {
            currentWebView()?.let { web ->
                if (web.canGoForward()) {
                    web.goForward()
                    publishBrowserState()
                }
            }
        }

        binding.reloadButton.setOnClickListener {
            currentWebView()?.let { web ->
                if (progress in 1..99) {
                    web.stopLoading()
                    progress = 100
                    binding.loadProgress.progress = 100
                    binding.loadProgress.isVisible = false
                } else {
                    web.reload()
                }
            }
            updateReloadButtonState()
            publishBrowserState()
            scheduleCapture(100)
        }
        binding.reloadButton.setOnLongClickListener {
            hardRefreshCurrentPage()
            true
        }

        binding.urlButton.setOnClickListener {
            openUrlDialog()
        }
        binding.urlButton.setOnLongClickListener {
            copyCurrentUrlToClipboard()
            true
        }

        binding.homeButton.setOnClickListener {
            loadHome()
        }
        binding.homeButton.setOnLongClickListener {
            toggleFavoriteForCurrentPage()
            true
        }

        binding.tabsButton.setOnClickListener {
            showTabsDialog()
        }
        binding.tabsButton.setOnLongClickListener {
            togglePinnedCurrentTab()
            true
        }

        binding.newTabButton.setOnClickListener {
            createTab(initialUrl = homeUrl(), switchToTab = true)
        }
        binding.newTabButton.setOnLongClickListener {
            val currentUrl = tabs.getOrNull(currentTabIndex)?.url
                ?.takeIf { it.isNotBlank() }
                ?: homeUrl()
            createTab(initialUrl = currentUrl, switchToTab = true)
            true
        }

        binding.settingsButton.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.settingsButton.setOnLongClickListener {
            toggleUrlBarVisibility()
            true
        }

        binding.downloadsButton.setOnClickListener {
            openDownloadsPanel()
        }
        binding.downloadsButton.setOnLongClickListener {
            showBookmarksDialog()
            true
        }

        binding.desktopButton.setOnClickListener {
            toggleDesktopModeQuick()
        }

        binding.readerButton.setOnClickListener {
            toggleReaderMode()
        }

        binding.zoomOutButton.setOnClickListener {
            applyZoomStep(delta = -1, captureDelayMs = 100L)
        }

        binding.zoomInButton.setOnClickListener {
            applyZoomStep(delta = 1, captureDelayMs = 100L)
        }
    }

    private fun hardRefreshCurrentPage() {
        val web = currentWebView() ?: return
        web.clearCache(true)
        web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        web.reload()
        web.settings.cacheMode = if (currentPreferences.privateBrowsingEnabled) {
            WebSettings.LOAD_NO_CACHE
        } else {
            WebSettings.LOAD_DEFAULT
        }
        showThrottledToast(R.string.hard_reload_toast, TOAST_COOLDOWN_MS)
    }

    private fun configureBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val web = currentWebView()
                    if (web?.canGoBack() == true) {
                        web.goBack()
                        publishBrowserState()
                        scheduleCapture(120L)
                        return
                    }

                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    private fun swapScreenRoles() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScreenSwapAtMs < SCREEN_SWAP_DEBOUNCE_MS) {
            return
        }
        lastScreenSwapAtMs = now

        if (!DisplayRoleStore.tryAcquireScreenSwapLock(SCREEN_SWAP_GLOBAL_LOCK_MS)) {
            scheduleSwapRetryIfNeeded()
            return
        }

        val hasSecondary = hasSecondaryDisplay()
        if (!hasSecondary) {
            showThrottledToast(R.string.display_single, TOAST_COOLDOWN_MS)
            DisplayRoleStore.releaseScreenSwapLock()
            return
        }

        val previousZoomOnTop = DisplayRoleStore.isZoomOnTop(this)
        val zoomOnTop = DisplayRoleStore.toggleZoomOnTop(this)
        val secondaryDisplayId = findSecondaryDisplayId(Display.DEFAULT_DISPLAY)
        val targetOverviewDisplay = if (zoomOnTop && secondaryDisplayId != null) {
            secondaryDisplayId
        } else {
            Display.DEFAULT_DISPLAY
        }
        val toastRes = if (zoomOnTop) {
            R.string.swap_zoom_top_toast
        } else {
            R.string.swap_zoom_bottom_toast
        }
        showThrottledToast(toastRes, TOAST_COOLDOWN_MS)
        persistTabSession(flush = true)
        val swapAnimation = ScreenSwapTransition.forCurrentScreenOnTop(
            isCurrentScreenOnTop = isCurrentScreenOnTop()
        )

        val relaunch = Intent(this, OverviewActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(OverviewActivity.EXTRA_FORCE_SWAP_HANDOFF, true)
            putExtra(OverviewActivity.EXTRA_TARGET_ZOOM_ON_TOP, zoomOnTop)
            putExtra(
                OverviewActivity.EXTRA_SWAP_HANDOFF_TOKEN,
                DisplayRoleStore.issueSwapHandoffToken()
            )
        }
        val started = launchOverviewOnDisplay(
            intent = relaunch,
            targetDisplayId = targetOverviewDisplay,
            animation = swapAnimation
        )
        if (!started) {
            DisplayRoleStore.setZoomOnTop(this, previousZoomOnTop)
            DisplayRoleStore.releaseScreenSwapLock()
            return
        }

        isFinishingForScreenSwap = true
        finish()
    }

    private fun scheduleSwapRetryIfNeeded() {
        if (isSwapRetryScheduled || isFinishing || isDestroyed) {
            return
        }
        isSwapRetryScheduled = true
        binding.root.postDelayed(
            {
                isSwapRetryScheduled = false
                if (!isFinishing && !isDestroyed) {
                    swapScreenRoles()
                }
            },
            SCREEN_SWAP_RETRY_DELAY_MS
        )
    }

    private fun applyScreenRoleLayout() {
        val shouldUseTopZoomLayout = DisplayRoleStore.isZoomOnTop(this) && hasSecondaryDisplay()
        binding.root.setPadding(0, 0, 0, 0)

        val containerParams = binding.webContainer.layoutParams
        if (containerParams is ViewGroup.MarginLayoutParams && containerParams.bottomMargin != 0) {
            containerParams.bottomMargin = 0
            binding.webContainer.layoutParams = containerParams
        }
        val shouldShowControls = !shouldUseTopZoomLayout
        binding.toolbar.visibility = if (shouldShowControls && isUrlBarVisible) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.toolbarHandle.visibility = if (shouldShowControls && !isUrlBarVisible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun hasSecondaryDisplay(): Boolean {
        val displayManager = getSystemService(DisplayManager::class.java) ?: return false
        return displayManager.displays.any { display ->
            display.displayId != Display.DEFAULT_DISPLAY && display.state != Display.STATE_OFF
        }
    }

    private fun findSecondaryDisplayId(currentDisplayId: Int): Int? {
        val displayManager = getSystemService(DisplayManager::class.java) ?: return null
        return displayManager.displays
            .asSequence()
            .filter { it.displayId != currentDisplayId && it.state != Display.STATE_OFF }
            .map { it.displayId }
            .firstOrNull()
    }

    private fun currentDisplayId(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.displayId
        }
    }

    private fun isCurrentScreenOnTop(): Boolean {
        return currentDisplayId() == Display.DEFAULT_DISPLAY
    }

    private fun launchOverviewOnDisplay(
        intent: Intent,
        targetDisplayId: Int,
        animation: ScreenSwapAnimation
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplayId)
            options.update(
                ActivityOptions.makeCustomAnimation(
                    this,
                    animation.enterAnimRes,
                    animation.exitAnimRes
                )
            )
            val startedWithDisplay = runCatching {
                startActivity(intent, options.toBundle())
                applySwapAnimationOverride(animation)
            }.isSuccess
            if (startedWithDisplay) {
                return true
            }
        }

        val fallbackOptions = ActivityOptions.makeCustomAnimation(
            this,
            animation.enterAnimRes,
            animation.exitAnimRes
        )
        return runCatching {
            startActivity(intent, fallbackOptions.toBundle())
            applySwapAnimationOverride(animation)
        }.isSuccess
    }

    @Suppress("DEPRECATION")
    private fun applySwapAnimationOverride(animation: ScreenSwapAnimation) {
        runCatching {
            overridePendingTransition(animation.enterAnimRes, animation.exitAnimRes)
        }
    }

    private fun toggleUrlBarVisibility() {
        setUrlBarVisibility(visible = !isUrlBarVisible, announce = true)
    }

    private fun setUrlBarVisibility(visible: Boolean, announce: Boolean) {
        val zoomIsOnTopScreen = DisplayRoleStore.isZoomOnTop(this) && hasSecondaryDisplay()
        if (zoomIsOnTopScreen) {
            return
        }

        if (isUrlBarVisible == visible) {
            return
        }

        isUrlBarVisible = visible
        applyScreenRoleLayout()
        BrowserSyncBus.updateChromeVisibility(isUrlBarVisible)
        BrowserSyncBus.sendCommand(BrowserCommand.SetChromeVisible(isUrlBarVisible))
        refreshChromeAutoHideTimer()
        if (announce) {
            val messageRes = if (isUrlBarVisible) {
                R.string.url_bar_shown_toast
            } else {
                R.string.url_bar_hidden_toast
            }
            showThrottledToast(messageRes, TOAST_COOLDOWN_MS)
        }
    }

    private fun showUrlBarIfHidden() {
        setUrlBarVisibility(visible = true, announce = false)
    }

    private fun refreshChromeAutoHideTimer() {
        uiHandler.removeCallbacks(autoHideChromeRunnable)
        if (isUrlBarVisible) {
            uiHandler.postDelayed(autoHideChromeRunnable, URL_BAR_AUTO_HIDE_DELAY_MS)
        }
    }

    private fun toggleDesktopModeQuick() {
        val nextDesktopMode = !currentPreferences.desktopMode
        currentPreferences = currentPreferences.copy(desktopMode = nextDesktopMode)
        BrowserSettingsStore.save(this, currentPreferences)

        applyPreferencesToAllTabs(currentPreferences, reloadCurrent = true)
        applyChromeStyle()

        val messageRes = if (nextDesktopMode) {
            R.string.desktop_mode_enabled_toast
        } else {
            R.string.desktop_mode_disabled_toast
        }
        showThrottledToast(messageRes, TOAST_COOLDOWN_MS)
    }

    private fun openDownloadsPanel() {
        val opened = runCatching {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        }.isSuccess
        if (!opened) {
            showThrottledToast(R.string.downloads_panel_unavailable_toast, TOAST_COOLDOWN_MS)
        }
    }

    private fun toggleReaderMode() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        if (tab.readerModeActive) {
            disableReaderMode(tab, announce = true)
            return
        }

        val web = tab.webView
        val sourceUrl = normalizeReaderSourceUrl(tab, web) ?: run {
            showThrottledToast(R.string.reader_mode_unavailable_toast, TOAST_COOLDOWN_MS)
            return
        }

        if (web.progress in 1..99) {
            showThrottledToast(R.string.reader_mode_wait_toast, TOAST_COOLDOWN_MS)
            return
        }

        showThrottledToast(
            messageRes = R.string.reader_mode_extracting_toast,
            cooldownMs = READER_EXTRACT_TOAST_COOLDOWN_MS
        )
        val requestedTabId = tab.id
        web.evaluateJavascript(READER_EXTRACTION_SCRIPT) { rawResult ->
            if (isFinishing || isDestroyed) {
                return@evaluateJavascript
            }
            val currentTab = tabs.getOrNull(currentTabIndex)
            val activeTab = tabsByWebView[web]
            if (currentTab?.id != requestedTabId || activeTab?.id != requestedTabId) {
                return@evaluateJavascript
            }

            val extracted = parseReaderPayload(rawResult)
            if (extracted == null || extracted.blocks.isEmpty()) {
                showThrottledToast(R.string.reader_mode_failed_toast, TOAST_COOLDOWN_MS)
                return@evaluateJavascript
            }

            val previousMode = activeTab.readerModeActive
            val previousSourceUrl = activeTab.readerSourceUrl
            val previousSourceTitle = activeTab.readerSourceTitle

            activeTab.readerModeActive = true
            activeTab.readerSourceUrl = sourceUrl
            activeTab.readerSourceTitle = extracted.title.ifBlank {
                activeTab.title.ifBlank { sourceUrl }
            }

            val readerHtml = buildReaderHtml(
                title = activeTab.readerSourceTitle,
                sourceUrl = sourceUrl,
                blocks = extracted.blocks
            )

            val loaded = runCatching {
                web.loadDataWithBaseURL(READER_MODE_BASE_URL, readerHtml, "text/html", "utf-8", null)
            }.isSuccess
            if (!loaded) {
                activeTab.readerModeActive = previousMode
                activeTab.readerSourceUrl = previousSourceUrl
                activeTab.readerSourceTitle = previousSourceTitle
                showThrottledToast(R.string.reader_mode_failed_toast, TOAST_COOLDOWN_MS)
                updateReaderButtonState()
                return@evaluateJavascript
            }
            updateReaderButtonState()
            showThrottledToast(R.string.reader_mode_enabled_toast, TOAST_COOLDOWN_MS)
        }
    }

    private fun normalizeReaderSourceUrl(tab: BrowserTab, webView: SyncWebView): String? {
        val rawUrl = tab.url.ifBlank { webView.url.orEmpty() }.trim()
        if (rawUrl.isBlank()) {
            return null
        }
        val normalized = normalizeMainFrameUrl(parseUriSafely(rawUrl)) ?: return null
        val scheme = parseUriSafely(normalized)?.scheme?.lowercase() ?: return null
        return if (scheme == "https" || scheme == "http") {
            normalized
        } else {
            null
        }
    }

    private fun disableReaderMode(tab: BrowserTab, announce: Boolean) {
        val sourceUrl = tab.readerSourceUrl
        tab.readerModeActive = false
        tab.readerSourceUrl = ""
        tab.readerSourceTitle = ""
        updateReaderButtonState()
        if (sourceUrl.isNotBlank()) {
            loadUrlWithPrivacyHeaders(tab.webView, sourceUrl)
        }
        if (announce) {
            showThrottledToast(R.string.reader_mode_disabled_toast, TOAST_COOLDOWN_MS)
        }
    }

    private fun createTab(
        initialUrl: String?,
        switchToTab: Boolean,
        pinned: Boolean = false,
        restoredTitle: String = "",
        persistSession: Boolean = true
    ) {
        if (tabs.size >= MAX_TABS) {
            showThrottledToast(R.string.tab_limit_reached_toast, TAB_LIMIT_TOAST_COOLDOWN_MS)
            return
        }

        val webView = buildWebView()
        val tab = BrowserTab(
            id = nextTabId++,
            webView = webView,
            title = restoredTitle,
            pinned = pinned
        )

        tabs += tab
        tabsByWebView[webView] = tab
        binding.webContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        webView.visibility = View.GONE

        if (switchToTab) {
            switchToTab(
                index = tabs.lastIndex,
                persistSession = persistSession,
                transitionStyle = TabTransitionStyle.NEW_TAB
            )
        }

        if (!initialUrl.isNullOrBlank()) {
            loadUrlWithPrivacyHeaders(webView, initialUrl)
        }

        updateTabIndicator()
        if (persistSession) {
            persistTabSession()
        }
    }

    private fun restoreTabsOrCreateDefault() {
        if (currentPreferences.privateBrowsingEnabled) {
            BrowserSessionStore.clear(this)
            createTab(initialUrl = homeUrl(), switchToTab = true, persistSession = false)
            return
        }

        val session = BrowserSessionStore.load(this)
        if (session == null || session.tabs.isEmpty()) {
            createTab(initialUrl = homeUrl(), switchToTab = true, persistSession = false)
            return
        }

        val restoredTabs = session.tabs.take(MAX_TABS)
        restoredTabs.forEach { saved ->
            val safeUrl = safeSessionUrl(saved.url)
            createTab(
                initialUrl = safeUrl,
                switchToTab = false,
                pinned = saved.pinned,
                restoredTitle = saved.title,
                persistSession = false
            )
        }

        if (tabs.isEmpty()) {
            createTab(initialUrl = homeUrl(), switchToTab = true, persistSession = false)
            return
        }

        switchToTab(
            index = session.currentIndex.coerceIn(0, tabs.lastIndex),
            persistSession = false,
            transitionStyle = TabTransitionStyle.NONE
        )
        persistTabSession()
    }

    private fun persistTabSession(flush: Boolean = false) {
        if (currentPreferences.privateBrowsingEnabled) {
            pendingSessionSnapshot = null
            uiHandler.removeCallbacks(persistSessionRunnable)
            clearSessionAsync()
            return
        }

        if (tabs.isEmpty()) {
            pendingSessionSnapshot = null
            uiHandler.removeCallbacks(persistSessionRunnable)
            clearSessionAsync()
            return
        }

        val snapshot = tabs.map { tab ->
            val rawUrl = tab.url.ifBlank { tab.webView.url.orEmpty() }
            SessionTab(
                url = safeSessionUrl(rawUrl),
                title = tab.title.ifBlank { tab.webView.title.orEmpty() },
                pinned = tab.pinned
            )
        }

        pendingSessionSnapshot = snapshot
        pendingSessionIndex = currentTabIndex.coerceIn(0, snapshot.lastIndex)
        uiHandler.removeCallbacks(persistSessionRunnable)
        if (flush) {
            flushPendingSessionPersist()
        } else {
            uiHandler.postDelayed(persistSessionRunnable, SESSION_PERSIST_DEBOUNCE_MS)
        }
    }

    private fun flushPendingSessionPersist() {
        val snapshot = pendingSessionSnapshot ?: return
        pendingSessionSnapshot = null
        persistSessionAsync(snapshot, pendingSessionIndex)
    }

    private fun persistSessionAsync(snapshot: List<SessionTab>, currentIndex: Int) {
        runCatching {
            persistenceExecutor.execute {
                BrowserSessionStore.save(
                    context = applicationContext,
                    tabs = snapshot,
                    currentIndex = currentIndex
                )
            }
        }
    }

    private fun clearSessionAsync() {
        runCatching {
            persistenceExecutor.execute {
                BrowserSessionStore.clear(applicationContext)
            }
        }
    }

    private fun safeSessionUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return homeUrl()
        }
        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return homeUrl()
        return normalizeMainFrameUrl(parsed) ?: homeUrl()
    }

    private fun switchToTab(
        index: Int,
        persistSession: Boolean = true,
        transitionStyle: TabTransitionStyle = TabTransitionStyle.SLIDE
    ) {
        if (index !in tabs.indices) {
            return
        }

        val previousIndex = currentTabIndex
        val previousTab = tabs.getOrNull(previousIndex)
        val nextTab = tabs[index]
        val shouldAnimate = transitionStyle != TabTransitionStyle.NONE &&
            previousTab != null &&
            previousIndex != index

        tabs.forEachIndexed { tabIndex, tab ->
            val isCurrent = tabIndex == index
            if (isCurrent) {
                resetTabTransform(tab.webView)
                tab.webView.visibility = View.VISIBLE
                tab.webView.onResume()
            } else {
                tab.webView.onPause()
                if (tab.webView.progress in 1..99) {
                    tab.webView.stopLoading()
                }
                val keepVisibleForExit = shouldAnimate && tabIndex == previousIndex
                if (keepVisibleForExit) {
                    tab.webView.visibility = View.VISIBLE
                } else {
                    resetTabTransform(tab.webView)
                    tab.webView.visibility = View.GONE
                }
            }
        }

        if (shouldAnimate && previousTab != null) {
            animateTabTransition(
                outgoing = previousTab.webView,
                incoming = nextTab.webView,
                transitionStyle = transitionStyle,
                isForward = index > previousIndex
            )
        }

        currentTabIndex = index
        progress = currentWebView()?.progress ?: 0

        binding.loadProgress.progress = progress
        binding.loadProgress.isVisible = progress in 1..99
        updateReloadButtonState()

        // Reset viewport cache when switching tabs for fresh state
        lastValidViewport = null
        publishBrowserState()
        scheduleCapture(100)
        updateTabIndicator()
        updateReaderButtonState()
        if (persistSession) {
            persistTabSession()
        }
    }

    private fun closeCurrentTab() {
        if (tabs.size <= 1) {
            Toast.makeText(this, R.string.tabs_close_last_blocked, Toast.LENGTH_SHORT).show()
            return
        }

        val closingIndex = currentTabIndex.coerceAtLeast(0)
        val tab = tabs.getOrNull(closingIndex) ?: return
        if (tab.pinned) {
            Toast.makeText(this, R.string.tabs_pinned_close_blocked, Toast.LENGTH_SHORT).show()
            return
        }

        tabs.removeAt(closingIndex)
        tabsByWebView.remove(tab.webView)
        safelyDestroyWebView(tab.webView)

        val fallbackIndex = (closingIndex - 1).coerceAtLeast(0)
        switchToTab(index = fallbackIndex, transitionStyle = TabTransitionStyle.NONE)
    }

    private fun animateTabTransition(
        outgoing: View,
        incoming: View,
        transitionStyle: TabTransitionStyle,
        isForward: Boolean
    ) {
        if (outgoing === incoming) {
            resetTabTransform(incoming)
            incoming.visibility = View.VISIBLE
            return
        }

        outgoing.animate().cancel()
        incoming.animate().cancel()

        when (transitionStyle) {
            TabTransitionStyle.NONE -> {
                resetTabTransform(outgoing)
                outgoing.visibility = View.GONE
                resetTabTransform(incoming)
                incoming.visibility = View.VISIBLE
            }

            TabTransitionStyle.SLIDE -> {
                val containerWidth = binding.webContainer.width
                    .takeIf { it > 0 }
                    ?: resources.displayMetrics.widthPixels
                val travelDistance = containerWidth * TAB_SWITCH_TRAVEL_RATIO
                val incomingStartOffset = if (isForward) {
                    travelDistance
                } else {
                    -travelDistance
                }
                val outgoingEndOffset = if (isForward) {
                    -travelDistance * TAB_SWITCH_OUT_TRAVEL_RATIO
                } else {
                    travelDistance * TAB_SWITCH_OUT_TRAVEL_RATIO
                }

                incoming.visibility = View.VISIBLE
                incoming.bringToFront()
                incoming.alpha = TAB_SWITCH_INCOMING_START_ALPHA
                incoming.translationX = incomingStartOffset
                incoming.translationY = 0f
                incoming.scaleX = 1f
                incoming.scaleY = 1f

                outgoing.alpha = 1f
                outgoing.translationX = 0f
                outgoing.translationY = 0f
                outgoing.scaleX = 1f
                outgoing.scaleY = 1f

                incoming.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(TAB_SWITCH_ENTER_DURATION_MS)
                    .setInterpolator(tabEnterInterpolator)
                    .setListener(null)
                    .start()

                outgoing.animate()
                    .alpha(TAB_SWITCH_OUTGOING_END_ALPHA)
                    .translationX(outgoingEndOffset)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(TAB_SWITCH_EXIT_DURATION_MS)
                    .setInterpolator(tabExitInterpolator)
                    .setListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                completeOutgoingTransition(outgoing)
                            }

                            override fun onAnimationCancel(animation: Animator) {
                                completeOutgoingTransition(outgoing)
                            }
                        }
                    )
                    .start()
            }

            TabTransitionStyle.NEW_TAB -> {
                val liftPx = TAB_NEW_TAB_LIFT_DP * resources.displayMetrics.density

                incoming.visibility = View.VISIBLE
                incoming.bringToFront()
                incoming.alpha = TAB_NEW_TAB_INCOMING_START_ALPHA
                incoming.translationX = 0f
                incoming.translationY = liftPx
                incoming.scaleX = TAB_NEW_TAB_INCOMING_START_SCALE
                incoming.scaleY = TAB_NEW_TAB_INCOMING_START_SCALE

                outgoing.alpha = 1f
                outgoing.translationX = 0f
                outgoing.translationY = 0f
                outgoing.scaleX = 1f
                outgoing.scaleY = 1f

                incoming.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(TAB_NEW_TAB_ENTER_DURATION_MS)
                    .setInterpolator(tabEnterInterpolator)
                    .setListener(null)
                    .start()

                outgoing.animate()
                    .alpha(TAB_NEW_TAB_OUTGOING_END_ALPHA)
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(TAB_NEW_TAB_OUTGOING_END_SCALE)
                    .scaleY(TAB_NEW_TAB_OUTGOING_END_SCALE)
                    .setDuration(TAB_NEW_TAB_EXIT_DURATION_MS)
                    .setInterpolator(tabExitInterpolator)
                    .setListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                completeOutgoingTransition(outgoing)
                            }

                            override fun onAnimationCancel(animation: Animator) {
                                completeOutgoingTransition(outgoing)
                            }
                        }
                    )
                    .start()
            }
        }
    }

    private fun completeOutgoingTransition(outgoing: View) {
        outgoing.animate().setListener(null)
        resetTabTransform(outgoing)
        outgoing.visibility = View.GONE
    }

    private fun resetTabTransform(view: View) {
        view.animate().setListener(null)
        view.animate().cancel()
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun buildWebView(): SyncWebView {
        val webView = SyncWebView(this)
        webView.setBackgroundColor(pageBackgroundColor(currentPreferences))

        applyWebSettings(webView, currentPreferences)

        webView.onScrollChangedListener = {
            if (webView === currentWebView()) {
                publishBrowserState()
                scheduleCapture(250)
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
            handleDownloadAttempt(
                rawUrl = url,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                contentLength = contentLength
            )
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, false, false)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // Block popup windows to avoid ad abuse and drive-by navigation.
                return false
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.cancel()
                handleBlockedJsDialog()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.cancel()
                handleBlockedJsDialog()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                result?.cancel()
                handleBlockedJsDialog()
                return true
            }

            override fun onJsBeforeUnload(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                result?.cancel()
                handleBlockedJsDialog()
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val tab = findTab(view as? SyncWebView ?: return) ?: return
                tab.progressUpdate(view.title.orEmpty(), view.url.orEmpty())

                if (view === currentWebView()) {
                    progress = newProgress.coerceIn(0, 100)
                    binding.loadProgress.progress = progress
                    binding.loadProgress.isVisible = progress in 1..99
                    updateReloadButtonState()
                    publishBrowserState()
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                val tab = findTab(view as? SyncWebView ?: return) ?: return
                tab.title = title.orEmpty()
                if (view === currentWebView()) {
                    publishBrowserState()
                    updateTabIndicator()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val webRequest = request ?: return null
                if (!webRequest.isForMainFrame && isUnsafeSubresourceUri(webRequest.url)) {
                    return blockedResourceResponse()
                }
                val prefs = currentPreferences
                if (webRequest.isForMainFrame || !prefs.blockTrackers) {
                    return null
                }

                val syncWebView = view as? SyncWebView ?: return null
                val tab = tabsByWebView[syncWebView]
                val topLevelUrl = tab?.url
                    ?.takeIf { it.isNotBlank() && !it.startsWith("about:blank", ignoreCase = true) }
                if (!TrackerBlocker.shouldBlock(webRequest.url, topLevelUrl)) {
                    return null
                }

                tab?.blockedTrackerRequests?.incrementAndGet()
                scheduleTrackerIndicatorRefresh()

                return blockedResourceResponse()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val webRequest = request ?: return false
                if (webRequest.isForMainFrame.not()) {
                    return false
                }
                if (shouldBlockInsecureMainFrameNavigation(webRequest.url)) {
                    showThrottledToast(R.string.blocked_insecure_http_toast, TOAST_COOLDOWN_MS)
                    return true
                }

                val normalized = normalizeMainFrameUrl(webRequest.url)
                if (normalized == null) {
                    showThrottledToast(R.string.blocked_navigation_toast, TOAST_COOLDOWN_MS)
                    return true
                }

                if (currentPreferences.openLinksInNewTab && webRequest.hasGesture()) {
                    createTab(initialUrl = normalized, switchToTab = true)
                    return true
                }

                val requested = webRequest.url.toString()
                val shouldAttachPrivacyHeaders = shouldSendPrivacySignals()
                val requestHeaders = webRequest.requestHeaders ?: emptyMap()
                val alreadyHasPrivacyHeaders = requestHeaders["DNT"] == "1" &&
                    requestHeaders["Sec-GPC"] == "1"
                if (normalized != requested ||
                    (shouldAttachPrivacyHeaders && !alreadyHasPrivacyHeaders)
                ) {
                    val syncWebView = view as? SyncWebView
                    if (syncWebView == null) {
                        return true
                    }
                    loadUrlWithPrivacyHeaders(syncWebView, normalized)
                    return true
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (shouldBlockInsecureMainFrameNavigation(parseUriSafely(url.orEmpty()))) {
                    view?.stopLoading()
                    if (view === currentWebView()) {
                        progress = 100
                        binding.loadProgress.progress = 100
                        binding.loadProgress.isVisible = false
                        updateReloadButtonState()
                    }
                    showThrottledToast(R.string.blocked_insecure_http_toast, TOAST_COOLDOWN_MS)
                    return
                }
                val tab = findTab(view as? SyncWebView ?: return) ?: return
                val startedUrl = url.orEmpty()
                if (!isReaderDocumentUrl(startedUrl)) {
                    tab.readerModeActive = false
                    tab.readerSourceUrl = ""
                    tab.readerSourceTitle = ""
                }
                tab.url = if (isReaderDocumentUrl(startedUrl)) {
                    tab.readerSourceUrl.ifBlank { startedUrl }
                } else {
                    startedUrl
                }
                tab.blockedTrackerRequests.set(0)

                if (view === currentWebView()) {
                    progress = 0
                    binding.loadProgress.progress = 0
                    binding.loadProgress.isVisible = true
                    updateReloadButtonState()
                    // Reset viewport cache when page starts loading
                    lastValidViewport = null
                    publishBrowserState()
                    updateTabIndicator()
                    updateReaderButtonState()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val tab = findTab(view as? SyncWebView ?: return) ?: return
                val finalUrl = url.orEmpty()
                val readerDocument = isReaderDocumentUrl(finalUrl)
                tab.url = if (readerDocument) {
                    tab.readerSourceUrl.ifBlank { finalUrl }
                } else {
                    finalUrl
                }
                tab.title = if (readerDocument) {
                    tab.readerSourceTitle.ifBlank { view.title.orEmpty() }
                } else {
                    view.title.orEmpty()
                }
                suppressPagePrintApi(view)

                if (!currentPreferences.privateBrowsingEnabled && !readerDocument) {
                    enqueueHistoryEntry(
                        title = tab.title,
                        url = tab.url
                    )
                }

                persistTabSession()

                if (view === currentWebView()) {
                    publishBrowserState()
                    scheduleCapture(120)
                    updateTabIndicator()
                    updateReaderButtonState()
                }
            }

            override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
                if (view === currentWebView()) {
                    publishBrowserState()
                    scheduleCapture(100)
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                val crashedView = view as? SyncWebView ?: return true
                handleRenderProcessGone(crashedView)
                return true
            }
        }

        return webView
    }

    private fun collectCommands() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BrowserSyncBus.commands.collect { command ->
                    handleCommand(command)
                }
            }
        }
    }

    private fun handleCommand(command: BrowserCommand) {
        when (command) {
            is BrowserCommand.ScrollTo -> {
                val web = currentWebView() ?: return
                val pageWidth = max(web.width, web.pageContentWidth()).coerceAtLeast(1)
                val pageHeight = max(web.height, web.pageContentHeight()).coerceAtLeast(1)
                val maxScrollX = (pageWidth - web.width.coerceAtLeast(1)).coerceAtLeast(0)
                val maxScrollY = (pageHeight - web.height.coerceAtLeast(1)).coerceAtLeast(0)
                val targetX = command.x.coerceIn(0, maxScrollX)
                val targetY = command.y.coerceIn(0, maxScrollY)
                if (targetX != web.scrollX || targetY != web.scrollY) {
                    web.scrollTo(targetX, targetY)
                }
            }

            is BrowserCommand.LoadUrl -> {
                loadFromInput(command.rawUrl)
                return
            }

            is BrowserCommand.SetChromeVisible -> {
                setUrlBarVisibility(visible = command.visible, announce = false)
                return
            }

            BrowserCommand.Back -> {
                currentWebView()?.takeIf { it.canGoBack() }?.goBack()
            }

            BrowserCommand.Forward -> {
                currentWebView()?.takeIf { it.canGoForward() }?.goForward()
            }

            BrowserCommand.Reload -> {
                currentWebView()?.reload()
            }

            BrowserCommand.Home -> {
                loadHome()
                return
            }

            is BrowserCommand.Zoom -> {
                applyZoomStep(delta = command.delta, captureDelayMs = 150L)
                return
            }
        }

        publishBrowserState()
        scheduleCapture(150)
    }

    private fun showTabsDialog() {
        if (tabs.isEmpty()) {
            return
        }

        val labels = tabs.mapIndexed { index, tab ->
            val marker = if (index == currentTabIndex) "* " else ""
            val pinTag = if (tab.pinned) "[PIN] " else ""
            val label = tab.displayLabel()
            "$marker${index + 1}. $pinTag$label"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tabs_dialog_title)
            .setItems(labels.toTypedArray()) { _, which ->
                switchToTab(which)
            }
            .setPositiveButton(R.string.tabs_close_current) { _, _ ->
                closeCurrentTab()
            }
            .setNeutralButton(R.string.button_new_tab) { _, _ ->
                createTab(initialUrl = homeUrl(), switchToTab = true)
            }
            .setNegativeButton(R.string.url_dialog_cancel, null)
            .show()
    }

    private fun togglePinnedCurrentTab() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        tab.pinned = !tab.pinned
        persistTabSession()
        updateTabIndicator()
        val messageRes = if (tab.pinned) {
            R.string.tabs_pinned_enabled_toast
        } else {
            R.string.tabs_pinned_disabled_toast
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarksDialog() {
        val entries = BrowserBookmarkStore.list(this).take(MAX_BOOKMARK_DIALOG_ITEMS)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bookmarks_dialog_title)
            .setPositiveButton(R.string.bookmarks_add_current) { _, _ ->
                addCurrentPageBookmark(markFavorite = false)
            }
            .setNeutralButton(R.string.bookmarks_add_favorite) { _, _ ->
                addCurrentPageBookmark(markFavorite = true)
            }
            .setNegativeButton(R.string.url_dialog_cancel, null)

        if (entries.isEmpty()) {
            dialog.setMessage(R.string.bookmarks_empty)
        } else {
            val labels = entries.map { entry ->
                val favoriteTag = if (entry.favorite) "[FAV] " else ""
                val label = entry.title.ifBlank { entry.url }
                "$favoriteTag$label"
            }
            dialog.setItems(labels.toTypedArray()) { _, which ->
                loadUrlInCurrentTab(entries[which].url)
            }
        }

        dialog.show()
    }

    private fun addCurrentPageBookmark(markFavorite: Boolean) {
        val web = currentWebView()
        val url = bookmarkableCurrentUrl(web)
        if (url == null) {
            Toast.makeText(this, R.string.bookmarks_save_failed_toast, Toast.LENGTH_SHORT).show()
            return
        }

        val title = web?.title.orEmpty().ifBlank { url }
        val updated = BrowserBookmarkStore.upsert(
            context = this,
            title = title,
            url = url,
            favoriteOverride = if (markFavorite) true else null
        ) ?: run {
            Toast.makeText(this, R.string.bookmarks_save_failed_toast, Toast.LENGTH_SHORT).show()
            return
        }

        val messageRes = if (updated.favorite) {
            R.string.bookmarks_saved_favorite_toast
        } else {
            R.string.bookmarks_saved_toast
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toggleFavoriteForCurrentPage() {
        val web = currentWebView()
        val url = bookmarkableCurrentUrl(web)
        if (url == null) {
            Toast.makeText(this, R.string.bookmarks_save_failed_toast, Toast.LENGTH_SHORT).show()
            return
        }

        val title = web?.title.orEmpty().ifBlank { url }
        val enabled = BrowserBookmarkStore.toggleFavorite(this, title, url)
        val messageRes = if (enabled) {
            R.string.bookmarks_favorite_enabled_toast
        } else {
            R.string.bookmarks_favorite_disabled_toast
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun bookmarkableCurrentUrl(web: SyncWebView?): String? {
        val raw = web?.url
            ?.ifBlank { tabs.getOrNull(currentTabIndex)?.url.orEmpty() }
            .orEmpty()
            .trim()
        if (raw.isBlank() ||
            raw.startsWith("about:blank", ignoreCase = true) ||
            raw.startsWith("javascript:", ignoreCase = true) ||
            raw.startsWith("data:", ignoreCase = true)
        ) {
            return null
        }
        return normalizeMainFrameUrl(parseUriSafely(raw))
    }

    private fun openUrlDialog() {
        val web = currentWebView()
        val textField = EditText(this).apply {
            hint = getString(R.string.url_dialog_hint)
            setSingleLine(true)
            setText(web?.url.orEmpty())
            setSelection(text.length)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.url_dialog_title)
            .setView(textField)
            .setPositiveButton(R.string.url_dialog_go) { _, _ ->
                loadFromInput(textField.text.toString())
            }
            .setNeutralButton(R.string.url_dialog_paste_go) { _, _ ->
                val clipboardText = getClipboardText()
                if (clipboardText.isNullOrBlank()) {
                    showThrottledToast(R.string.url_dialog_clipboard_empty, TOAST_COOLDOWN_MS)
                } else {
                    loadFromInput(clipboardText)
                }
            }
            .setNegativeButton(R.string.url_dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            textField.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            textField.post {
                val inputMethodManager = getSystemService(InputMethodManager::class.java)
                inputMethodManager?.showSoftInput(textField, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        dialog.show()
    }

    private fun copyCurrentUrlToClipboard() {
        val url = bookmarkableCurrentUrl(currentWebView())
        if (url.isNullOrBlank()) {
            showThrottledToast(R.string.url_copy_unavailable_toast, TOAST_COOLDOWN_MS)
            return
        }
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
        showThrottledToast(R.string.settings_link_copied_toast, TOAST_COOLDOWN_MS)
    }

    private fun getClipboardText(): String? {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
        if (!clipboard.hasPrimaryClip()) {
            return null
        }
        val item = clipboard.primaryClip?.getItemAt(0) ?: return null
        return item.coerceToText(this)?.toString()?.trim()
    }

    private fun loadHome() {
        loadUrlInCurrentTab(homeUrl())
    }

    private fun loadFromInput(rawInput: String) {
        val normalized = normalizeInput(rawInput, allowSearch = true)
        loadUrlInCurrentTab(normalized)
    }

    private fun handleExternalViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) {
            return
        }

        val normalizedUrl = normalizeMainFrameUrl(intent.data) ?: return
        val host = parseUriSafely(normalizedUrl)?.host
        showExternalViewConfirmation(normalizedUrl, host)
    }

    private fun showExternalViewConfirmation(url: String, rawHost: String?) {
        if (isFinishing || isDestroyed) {
            return
        }

        val normalizedHost = normalizeHost(rawHost)
        val hostLabel = normalizedHost ?: url
        if (normalizedHost != null && isTrustedExternalHost(normalizedHost)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.external_link_prompt_title)
                .setMessage(getString(R.string.external_link_prompt_message, hostLabel))
                .setPositiveButton(R.string.external_link_prompt_open) { _, _ ->
                    loadUrlInCurrentTab(url)
                }
                .setNegativeButton(R.string.url_dialog_cancel, null)
                .show()
            return
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.external_link_untrusted_title)
            .setMessage(getString(R.string.external_link_untrusted_message, hostLabel))
            .setPositiveButton(R.string.external_link_prompt_open_once) { _, _ ->
                loadUrlInCurrentTab(url)
            }
            .setNegativeButton(R.string.url_dialog_cancel, null)

        if (normalizedHost != null) {
            builder.setNeutralButton(R.string.external_link_prompt_trust_and_open) { _, _ ->
                rememberTrustedExternalHost(normalizedHost)
                showThrottledToast(
                    R.string.external_link_host_trusted_toast,
                    TOAST_COOLDOWN_MS
                )
                loadUrlInCurrentTab(url)
            }
        }

        builder.show()
    }

    private fun loadUrlInCurrentTab(url: String) {
        val web = currentWebView()
        if (web == null) {
            createTab(initialUrl = url, switchToTab = true)
            return
        }

        loadUrlWithPrivacyHeaders(web, url)
        publishBrowserState()
    }

    private fun homeUrl(): String {
        return BrowserSettingsStore.sanitizedHomepage(currentPreferences.homepage)
    }

    private fun normalizeInput(rawInput: String, allowSearch: Boolean): String {
        val value = rawInput.trim()
        if (value.isEmpty()) {
            return homeUrl()
        }

        if (value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("file://", ignoreCase = true)
        ) {
            return normalizeMainFrameUrl(parseUriSafely(value)) ?: homeUrl()
        }

        if (allowSearch && (value.contains(' ') || !looksLikeDomain(value))) {
            return currentPreferences.searchEngine.queryUrl(Uri.encode(value))
        }

        return "https://${value.removePrefix("//")}"
    }

    private fun refreshPreferences(reloadCurrent: Boolean) {
        val previousPreferences = currentPreferences
        currentPreferences = BrowserSettingsStore.load(this)
        val privateModeChanged =
            previousPreferences.privateBrowsingEnabled != currentPreferences.privateBrowsingEnabled

        if (privateModeChanged) {
            clearAllWebData()
            tabs.forEach { tab -> tab.blockedTrackerRequests.set(0) }
            if (currentPreferences.privateBrowsingEnabled) {
                BrowserSessionStore.clear(this)
            } else {
                persistTabSession()
            }
        }

        applyPreferencesToAllTabs(currentPreferences, reloadCurrent)
        applyChromeStyle()
    }

    private fun applyPreferencesToAllTabs(
        prefs: BrowserPreferences,
        reloadCurrent: Boolean
    ) {
        tabs.forEachIndexed { index, tab ->
            applyWebSettings(tab.webView, prefs)
            tab.webView.setBackgroundColor(pageBackgroundColor(prefs))
            if (reloadCurrent && index == currentTabIndex) {
                tab.webView.reload()
            }
        }
    }

    private fun applyWebSettings(webView: SyncWebView, prefs: BrowserPreferences) {
        @Suppress("DEPRECATION")
        with(webView.settings) {
            javaScriptEnabled = prefs.javascriptEnabled
            // Prevent persistent Web Storage writes while private browsing is enabled.
            domStorageEnabled = !prefs.privateBrowsingEnabled
            databaseEnabled = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            allowContentAccess = false
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            setGeolocationEnabled(false)
            setSaveFormData(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportZoom(true)
            textZoom = 100
            loadsImagesAutomatically = prefs.loadImages
            blockNetworkImage = !prefs.loadImages
            cacheMode = if (prefs.privateBrowsingEnabled) {
                WebSettings.LOAD_NO_CACHE
            } else {
                WebSettings.LOAD_DEFAULT
            }
            userAgentString = if (prefs.desktopMode) {
                DESKTOP_USER_AGENT
            } else {
                defaultMobileUserAgent
            }

            // Keep rasterization demand-driven to avoid excess memory pressure.
            offscreenPreRaster = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(!prefs.privateBrowsingEnabled && !prefs.blockAllCookies)
            setAcceptThirdPartyCookies(webView, false)
        }
    }

    private fun applyChromeStyle() {
        val isDsiClassic = currentPreferences.themeMode == ThemeMode.DSI_CLASSIC
        val isDsiClassicDark = currentPreferences.themeMode == ThemeMode.DSI_CLASSIC_DARK
        val isDsiFamily = isDsiClassic || isDsiClassicDark
        val isElectricPurple = currentPreferences.themeMode == ThemeMode.ELECTRIC_PURPLE
        val accent = when {
            isDsiClassicDark -> Color.parseColor("#4FC777")
            isDsiClassic -> Color.parseColor("#3CA95C")
            isElectricPurple -> Color.parseColor("#C000FF")
            else -> currentPreferences.accentPalette.solidColorInt()
        }
        val accentSoft = when {
            isDsiClassicDark -> Color.parseColor("#444FC777")
            isDsiClassic -> Color.parseColor("#443CA95C")
            isElectricPurple -> Color.parseColor("#44C000FF")
            else -> currentPreferences.accentPalette.softColorInt()
        }
        val secondaryAccent = when {
            isDsiClassicDark -> Color.parseColor("#74A2FF")
            isDsiClassic -> Color.parseColor("#4B74C9")
            isElectricPurple -> Color.parseColor("#E35DFF")
            else -> accent
        }
        val isOled = currentPreferences.themeMode == ThemeMode.DARK_OLED
        val darkChrome = isElectricPurple || isDsiClassicDark ||
            (!isDsiClassic && (isOled || isNightModeActive()))

        val shellColor = when {
            isDsiClassicDark -> Color.parseColor("#111A24")
            isDsiClassic -> Color.parseColor("#D8DDE4")
            isElectricPurple -> Color.parseColor("#06020E")
            isOled -> Color.BLACK
            darkChrome -> Color.parseColor("#151A22")
            else -> ContextCompat.getColor(this, R.color.retro_shell)
        }
        val innerColor = when {
            isDsiClassicDark -> Color.parseColor("#1A2633")
            isDsiClassic -> Color.parseColor("#F5F8FC")
            isElectricPurple -> Color.parseColor("#110526")
            isOled -> Color.BLACK
            darkChrome -> Color.parseColor("#1D2430")
            else -> ContextCompat.getColor(this, R.color.retro_inner)
        }
        val toolbarColor = when {
            isDsiClassicDark -> Color.parseColor("#15202B")
            isDsiClassic -> Color.parseColor("#C4CCD6")
            isElectricPurple -> Color.parseColor("#0D0320")
            isOled -> Color.BLACK
            darkChrome -> Color.parseColor("#212B39")
            else -> ContextCompat.getColor(this, R.color.retro_toolbar)
        }
        val trackColor = when {
            isDsiClassicDark -> Color.parseColor("#3D556D")
            isDsiClassic -> Color.parseColor("#97A2B1")
            isElectricPurple -> Color.parseColor("#3F1870")
            isOled -> Color.parseColor("#272727")
            darkChrome -> Color.parseColor("#3A4554")
            else -> ContextCompat.getColor(this, R.color.retro_progress_track)
        }
        val textColor = when {
            isDsiClassicDark -> Color.parseColor("#E3ECF7")
            isDsiClassic -> Color.parseColor("#1F2B39")
            isElectricPurple -> Color.parseColor("#F4E8FF")
            darkChrome -> Color.parseColor("#E7EDF5")
            else -> ContextCompat.getColor(this, R.color.retro_text)
        }
        val buttonColor = when {
            isDsiClassicDark -> Color.parseColor("#243447")
            isElectricPurple -> Color.parseColor("#180738")
            else -> innerColor
        }
        val handleColor = when {
            isDsiClassicDark -> Color.parseColor("#5F7A97")
            isDsiClassic -> Color.parseColor("#6E839B")
            isElectricPurple -> Color.parseColor("#B85AFF")
            isOled -> Color.parseColor("#3A3A3A")
            darkChrome -> Color.parseColor("#66748A")
            else -> Color.parseColor("#8E99A8")
        }

        binding.root.setBackgroundColor(shellColor)
        binding.webContainer.setBackgroundColor(innerColor)
        binding.toolbar.setBackgroundColor(toolbarColor)
        binding.toolbarHandle.backgroundTintList = ColorStateList.valueOf(handleColor)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        val useLightSystemBars = !darkChrome
        insetsController.isAppearanceLightStatusBars = useLightSystemBars
        insetsController.isAppearanceLightNavigationBars = useLightSystemBars
        binding.loadProgress.progressTintList = ColorStateList.valueOf(accent)
        binding.loadProgress.progressBackgroundTintList = ColorStateList.valueOf(trackColor)
        binding.tabInfoText.backgroundTintList = ColorStateList.valueOf(accentSoft)
        binding.tabInfoText.setTextColor(accent)

        val toolbarButtons = listOf(
            binding.backButton,
            binding.forwardButton,
            binding.reloadButton,
            binding.urlButton,
            binding.homeButton,
            binding.tabsButton,
            binding.newTabButton,
            binding.settingsButton,
            binding.downloadsButton,
            binding.desktopButton,
            binding.readerButton,
            binding.zoomOutButton,
            binding.zoomInButton
        )
        toolbarButtons.forEach { button ->
            button.backgroundTintList = ColorStateList.valueOf(buttonColor)
            button.setTextColor(textColor)
        }

        if (isDsiFamily) {
            listOf(binding.urlButton, binding.homeButton).forEach { button ->
                button.setTextColor(accent)
            }
            listOf(binding.tabsButton, binding.newTabButton).forEach { button ->
                button.setTextColor(secondaryAccent)
            }
        } else {
            listOf(
                binding.urlButton,
                binding.homeButton,
                binding.tabsButton,
                binding.newTabButton,
                binding.downloadsButton,
                binding.readerButton
            ).forEach { button ->
                button.setTextColor(accent)
            }
        }
        binding.desktopButton.text = if (currentPreferences.desktopMode) {
            getString(R.string.button_desktop_on)
        } else {
            getString(R.string.button_desktop)
        }
        binding.desktopButton.setTextColor(
            if (currentPreferences.desktopMode) secondaryAccent else textColor
        )
        readerActiveButtonTextColor = secondaryAccent
        readerInactiveButtonTextColor = textColor
        updateReaderButtonState()
        updateReloadButtonState()
        val web = currentWebView()
        updateNavigationButtonsState(
            canGoBack = web?.canGoBack() == true,
            canGoForward = web?.canGoForward() == true
        )

        tabs.forEach { tab ->
            tab.webView.setBackgroundColor(pageBackgroundColor(currentPreferences))
        }
    }

    private fun configureImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        @Suppress("DEPRECATION")
        run {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun isNightModeActive(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun pageBackgroundColor(prefs: BrowserPreferences): Int {
        return when (prefs.themeMode) {
            ThemeMode.DARK_OLED -> Color.BLACK
            ThemeMode.DARK -> Color.parseColor("#1B212B")
            ThemeMode.LIGHT -> Color.WHITE
            ThemeMode.ELECTRIC_PURPLE -> Color.parseColor("#090313")
            ThemeMode.DSI_CLASSIC -> Color.parseColor("#F8FBFF")
            ThemeMode.DSI_CLASSIC_DARK -> Color.parseColor("#121A24")
            ThemeMode.SYSTEM -> if (isNightModeActive()) Color.parseColor("#1B212B") else Color.WHITE
        }
    }

    private fun updateTabIndicator() {
        val index = (currentTabIndex + 1).coerceAtLeast(1)
        val count = tabs.size.coerceAtLeast(1)
        val currentTab = tabs.getOrNull(currentTabIndex)
        val blockedCount = currentTab?.blockedTrackerRequests?.get() ?: 0
        val mode = if (currentPreferences.privateBrowsingEnabled) {
            getString(R.string.tab_info_private)
        } else {
            getString(R.string.tab_info_standard)
        }
        val baseText = getString(
            R.string.tab_info_full_template,
            index,
            count,
            mode,
            blockedCount
        )
        val suffixParts = mutableListOf<String>()
        if (currentTab?.pinned == true) {
            suffixParts += getString(R.string.tab_info_pinned)
        }
        if (currentTab?.readerModeActive == true) {
            suffixParts += getString(R.string.tab_info_reader)
        }
        binding.tabInfoText.text = if (suffixParts.isEmpty()) {
            baseText
        } else {
            "$baseText  |  ${suffixParts.joinToString("  |  ")}"
        }
        binding.tabsButton.text = if (count > 1) "T$count" else getString(R.string.button_tabs)
    }

    private fun updateReaderButtonState() {
        val readerActive = tabs.getOrNull(currentTabIndex)?.readerModeActive == true
        binding.readerButton.text = if (readerActive) {
            getString(R.string.button_reader_on)
        } else {
            getString(R.string.button_reader)
        }

        binding.readerButton.setTextColor(
            if (readerActive) {
                readerActiveButtonTextColor
            } else {
                readerInactiveButtonTextColor
            }
        )
    }

    private fun updateReloadButtonState() {
        binding.reloadButton.text = if (progress in 1..99) {
            getString(R.string.button_reload_stop)
        } else {
            getString(R.string.button_reload)
        }
    }

    private fun updateNavigationButtonsState(canGoBack: Boolean, canGoForward: Boolean) {
        binding.backButton.isEnabled = canGoBack
        binding.forwardButton.isEnabled = canGoForward
        binding.backButton.alpha = if (canGoBack) ENABLED_BUTTON_ALPHA else DISABLED_BUTTON_ALPHA
        binding.forwardButton.alpha = if (canGoForward) ENABLED_BUTTON_ALPHA else DISABLED_BUTTON_ALPHA
    }

    private fun publishBrowserState() {
        val web = currentWebView() ?: return

        val contentWidth = web.pageContentWidth()
        val contentHeight = web.pageContentHeight()
        val pageWidth = max(1, max(web.width, contentWidth))
        val pageHeight = max(1, max(web.height, contentHeight))

        val viewportWidth = web.width.coerceAtLeast(1)
        val viewportHeight = web.height.coerceAtLeast(1)
        val maxScrollX = (pageWidth - viewportWidth).coerceAtLeast(0)
        val maxScrollY = (pageHeight - viewportHeight).coerceAtLeast(0)

        val rawScrollX = web.scrollX
        val rawScrollY = web.scrollY

        // Validate scroll position to prevent erroneous jumps to (0,0)
        // when WebView scroll values are temporarily unstable
        val lastViewport = lastValidViewport
        val scrollX: Int
        val scrollY: Int
        
        if (lastViewport != null && 
            rawScrollX == 0 && rawScrollY == 0 &&
            lastViewport.scrollX > 50 && lastViewport.scrollY > 50 &&
            web.url?.startsWith("about:") != true) {
            // Likely an erroneous jump to origin - use last known position
            scrollX = lastViewport.scrollX.coerceIn(0, maxScrollX)
            scrollY = lastViewport.scrollY.coerceIn(0, maxScrollY)
        } else {
            scrollX = rawScrollX.coerceIn(0, maxScrollX)
            scrollY = rawScrollY.coerceIn(0, maxScrollY)
        }

        val viewport = BrowserViewport(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            scrollX = scrollX,
            scrollY = scrollY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )

        // Cache this as the last valid viewport
        lastValidViewport = viewport

        BrowserSyncBus.updateViewport(viewport)
        BrowserSyncBus.updateNavigation(
            title = web.title.orEmpty(),
            url = web.url.orEmpty(),
            progress = progress,
            canGoBack = web.canGoBack(),
            canGoForward = web.canGoForward()
        )
        updateNavigationButtonsState(canGoBack = web.canGoBack(), canGoForward = web.canGoForward())
    }

    @Suppress("DEPRECATION")
    private fun captureOverviewSnapshot() {
        if (isDestroyed || isFinishing) return
        val web = currentWebView() ?: return
        if (!web.isAttachedToWindow || !web.isShown || web.width <= 0 || web.height <= 0) {
            return
        }

        try {
            val pictureSnapshot = capturePictureSnapshot(web)
            val snapshot = pictureSnapshot ?: captureViewportSnapshot(web)
            if (snapshot == null) {
                return
            }

            BrowserSyncBus.updateSnapshot(
                snapshot = snapshot.bitmap,
                pageWidth = snapshot.pageWidth,
                pageHeight = snapshot.pageHeight
            )
            lastSnapshotCaptureAtMs = SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            // Ignore snapshot failures; viewport sync still works.
        }
    }

    @Suppress("DEPRECATION")
    private fun capturePictureSnapshot(web: SyncWebView): SnapshotCapture? {
        val picture = web.capturePicture()
        if (picture.width <= 0 || picture.height <= 0) {
            return null
        }

        val plans = buildSnapshotPlans(picture.width, picture.height)
        for (plan in plans) {
            val bitmap = tryAcquireSnapshotBitmap(plan.width, plan.height) ?: continue
            val canvas = Canvas(bitmap)
            canvas.drawColor(pageBackgroundColor(currentPreferences))
            canvas.save()
            canvas.scale(plan.scale, plan.scale)
            picture.draw(canvas)
            canvas.restore()

            val currentUrl = web.url.orEmpty()
            if (currentUrl.isNotBlank() &&
                !currentUrl.startsWith("about:blank", ignoreCase = true) &&
                isLikelyBlankSnapshot(bitmap)
            ) {
                return null
            }

            return SnapshotCapture(
                bitmap = bitmap,
                pageWidth = picture.width,
                pageHeight = picture.height
            )
        }

        return null
    }

    private fun captureViewportSnapshot(web: SyncWebView): SnapshotCapture? {
        val pageWidth = max(1, max(web.width, web.pageContentWidth()))
        val pageHeight = max(1, max(web.height, web.pageContentHeight()))
        if (pageWidth <= 0 || pageHeight <= 0) {
            return null
        }

        val plans = buildSnapshotPlans(pageWidth, pageHeight)
        for (plan in plans) {
            val bitmap = tryAcquireSnapshotBitmap(plan.width, plan.height) ?: continue
            val canvas = Canvas(bitmap)
            canvas.drawColor(pageBackgroundColor(currentPreferences))
            canvas.save()
            canvas.scale(plan.scale, plan.scale)
            // View#draw applies -scroll internally; offset back so output is in page coordinates.
            canvas.translate(web.scrollX.toFloat(), web.scrollY.toFloat())
            web.draw(canvas)
            canvas.restore()

            return SnapshotCapture(
                bitmap = bitmap,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
        }

        return null
    }

    private fun isLikelyBlankSnapshot(bitmap: Bitmap): Boolean {
        if (bitmap.width < 3 || bitmap.height < 3) {
            return true
        }

        val sampleColumns = 6
        val sampleRows = 4
        var minLuma = 255
        var maxLuma = 0
        var sampledPixels = 0
        var whiteLikeCount = 0

        for (yIndex in 0 until sampleRows) {
            val y = ((yIndex + 0.5f) * bitmap.height / sampleRows).roundToInt()
                .coerceIn(0, bitmap.height - 1)
            for (xIndex in 0 until sampleColumns) {
                val x = ((xIndex + 0.5f) * bitmap.width / sampleColumns).roundToInt()
                    .coerceIn(0, bitmap.width - 1)
                val color = bitmap.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val luma = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt()
                minLuma = min(minLuma, luma)
                maxLuma = max(maxLuma, luma)
                sampledPixels += 1
                if (r >= 240 && g >= 240 && b >= 240) {
                    whiteLikeCount += 1
                }
            }
        }

        val mostlyWhite = sampledPixels > 0 &&
            whiteLikeCount >= (sampledPixels * 0.9f).roundToInt()
        val veryLowContrast = (maxLuma - minLuma) <= 3
        return mostlyWhite && veryLowContrast
    }

    private fun scheduleCapture(delayMs: Long) {
        val now = SystemClock.elapsedRealtime()
        val minDelay = (lastSnapshotCaptureAtMs + SNAPSHOT_MIN_CAPTURE_INTERVAL_MS - now)
            .coerceAtLeast(0L)
        val targetDelay = max(delayMs, minDelay)
        captureHandler.removeCallbacks(captureRunnable)
        captureHandler.postDelayed(captureRunnable, targetDelay)
    }

    private fun buildSnapshotPlans(sourceWidth: Int, sourceHeight: Int): List<SnapshotPlan> {
        val width = sourceWidth.coerceAtLeast(1)
        val height = sourceHeight.coerceAtLeast(1)
        return SNAPSHOT_PIXEL_BUDGETS.asIterable().mapNotNull { pixelBudget ->
            val scale = computeSnapshotScale(width, height, pixelBudget)
            val targetWidth = max(1, (width * scale).roundToInt())
            val targetHeight = max(1, (height * scale).roundToInt())
            if (targetWidth <= 0 || targetHeight <= 0) {
                null
            } else {
                SnapshotPlan(
                    scale = scale,
                    width = targetWidth,
                    height = targetHeight
                )
            }
        }.distinctBy { it.width to it.height }
    }

    private fun computeSnapshotScale(
        sourceWidth: Int,
        sourceHeight: Int,
        pixelBudget: Double
    ): Float {
        val width = sourceWidth.coerceAtLeast(1)
        val height = sourceHeight.coerceAtLeast(1)
        val edgeScale = min(1f, SNAPSHOT_MAX_EDGE_PX / max(width, height).toFloat())
        val sourcePixels = width.toDouble() * height.toDouble()
        if (sourcePixels <= 0.0) {
            return edgeScale
        }
        val pixelScale = min(1f, sqrt(pixelBudget / sourcePixels).toFloat())
        return min(edgeScale, pixelScale).coerceAtLeast(0.01f)
    }

    private fun tryAcquireSnapshotBitmap(width: Int, height: Int): Bitmap? {
        return try {
            obtainSnapshotBitmap(width, height)
        } catch (_: OutOfMemoryError) {
            reusableSnapshotBitmap = null
            null
        } catch (_: IllegalArgumentException) {
            reusableSnapshotBitmap = null
            null
        }
    }

    private fun obtainSnapshotBitmap(width: Int, height: Int): Bitmap {
        val existing = reusableSnapshotBitmap
        if (existing != null &&
            !existing.isRecycled &&
            existing.width == width &&
            existing.height == height
        ) {
            return existing
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also { created ->
            reusableSnapshotBitmap = created
        }
    }

    private fun applyZoomStep(delta: Int, captureDelayMs: Long) {
        val web = currentWebView() ?: return
        val changed = if (delta >= 0) {
            web.zoomIn()
        } else {
            web.zoomOut()
        }

        if (changed) {
            publishBrowserState()
            scheduleCapture(captureDelayMs)
        }
    }

    private fun scheduleTrackerIndicatorRefresh() {
        if (!trackerUiRefreshScheduled.compareAndSet(false, true)) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastTrackerUiRefreshAtMs.get()
        val delayMs = if (elapsed >= TRACKER_UI_MIN_UPDATE_INTERVAL_MS) {
            0L
        } else {
            TRACKER_UI_MIN_UPDATE_INTERVAL_MS - elapsed
        }

        uiHandler.postDelayed({
            if (isDestroyed || isFinishing) {
                trackerUiRefreshScheduled.set(false)
                return@postDelayed
            }
            trackerUiRefreshScheduled.set(false)
            lastTrackerUiRefreshAtMs.set(SystemClock.elapsedRealtime())
            updateTabIndicator()
        }, delayMs)
    }

    private fun handleBlockedJsDialog() {
        showThrottledToast(
            messageRes = R.string.blocked_js_dialog_toast,
            cooldownMs = JS_DIALOG_TOAST_COOLDOWN_MS,
            clock = jsDialogNoticeAtMs
        )
    }

    private fun handleDownloadAttempt(
        rawUrl: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        val now = SystemClock.elapsedRealtime()
        val currentWindowStart = downloadWindowStartAtMs.get()
        if (currentWindowStart == 0L || now - currentWindowStart > DOWNLOAD_SPAM_WINDOW_MS) {
            downloadWindowStartAtMs.set(now)
            downloadAttemptsInWindow.set(0)
        }

        val attempts = downloadAttemptsInWindow.incrementAndGet()
        if (attempts > MAX_DOWNLOAD_ATTEMPTS_PER_WINDOW) {
            showThrottledToast(
                messageRes = R.string.blocked_download_spam_toast,
                cooldownMs = DOWNLOAD_TOAST_COOLDOWN_MS,
                clock = downloadNoticeAtMs
            )
            return
        }

        val safeUrl = normalizeDownloadUrl(rawUrl)
        if (safeUrl == null) {
            showThrottledToast(
                messageRes = R.string.download_invalid_url_toast,
                cooldownMs = DOWNLOAD_TOAST_COOLDOWN_MS,
                clock = downloadNoticeAtMs
            )
            return
        }

        val fileName = URLUtil.guessFileName(safeUrl, contentDisposition, mimeType)
        val manager = getSystemService(DownloadManager::class.java)
        if (manager == null) {
            showThrottledToast(
                messageRes = R.string.download_failed_toast,
                cooldownMs = DOWNLOAD_TOAST_COOLDOWN_MS,
                clock = downloadNoticeAtMs
            )
            return
        }

        val resolvedFileName = fileName.ifBlank { "download.bin" }
        val request = runCatching {
            DownloadManager.Request(Uri.parse(safeUrl)).apply {
                setTitle(resolvedFileName)
                val sizeDescription = if (contentLength > 0L) {
                    Formatter.formatShortFileSize(this@ZoomActivity, contentLength)
                } else {
                    getString(R.string.download_size_unknown)
                }
                setDescription(getString(R.string.download_description_template, sizeDescription))
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                if (!mimeType.isNullOrBlank()) {
                    setMimeType(mimeType)
                }
                if (shouldSendPrivacySignals()) {
                    runCatching { addRequestHeader("DNT", "1") }
                    runCatching { addRequestHeader("Sec-GPC", "1") }
                }
                val appDestinationSet = runCatching {
                    setDestinationInExternalFilesDir(
                        this@ZoomActivity,
                        Environment.DIRECTORY_DOWNLOADS,
                        resolvedFileName
                    )
                }.isSuccess
                if (!appDestinationSet) {
                    throw IllegalStateException("No writable destination for download")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setRequiresCharging(false)
                }
            }
        }.getOrNull()

        if (request == null) {
            showThrottledToast(
                messageRes = R.string.download_failed_toast,
                cooldownMs = DOWNLOAD_TOAST_COOLDOWN_MS,
                clock = downloadNoticeAtMs
            )
            return
        }

        val started = runCatching { manager.enqueue(request) }.isSuccess
        if (!started) {
            showThrottledToast(
                messageRes = R.string.download_failed_toast,
                cooldownMs = DOWNLOAD_TOAST_COOLDOWN_MS,
                clock = downloadNoticeAtMs
            )
            return
        }

        showThrottledToast(
            messageRes = R.string.download_started_toast,
            cooldownMs = DOWNLOAD_TOAST_COOLDOWN_MS,
            clock = downloadNoticeAtMs
        )
    }

    private fun normalizeDownloadUrl(rawUrl: String?): String? {
        val value = rawUrl?.trim().orEmpty()
        if (value.isBlank()) {
            return null
        }
        val parsed = parseUriSafely(value) ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        val normalized = when (scheme) {
            "https" -> parsed.toString()
            "http" -> {
                if (currentPreferences.httpsOnlyMode) {
                    return null
                }
                parsed.toString()
            }
            else -> return null
        }
        val cleaned = UrlPrivacySanitizer.stripTrackingParameters(
            url = normalized,
            enabled = currentPreferences.stripTrackingParameters
        )
        val cleanedScheme = parseUriSafely(cleaned)?.scheme?.lowercase() ?: return null
        return if (cleanedScheme == "https" || cleanedScheme == "http") {
            cleaned
        } else {
            null
        }
    }

    private fun isReaderDocumentUrl(url: String): Boolean {
        return url.startsWith(READER_MODE_BASE_URL, ignoreCase = true)
    }

    private fun parseReaderPayload(rawResult: String?): ReaderPayload? {
        val decoded = decodeJavascriptStringResult(rawResult) ?: return null
        val json = runCatching { JSONObject(decoded) }.getOrNull() ?: return null
        val title = json.optString("title").orEmpty().trim()
        val blocksJson = json.optJSONArray("blocks") ?: return null
        val blocks = mutableListOf<ReaderBlock>()
        for (i in 0 until blocksJson.length()) {
            val block = blocksJson.optJSONObject(i) ?: continue
            val text = block.optString("text").orEmpty().trim()
            if (text.isEmpty()) {
                continue
            }
            val tag = block.optString("tag").orEmpty().lowercase()
            blocks += ReaderBlock(tag = tag, text = text)
        }
        return if (blocks.isEmpty()) {
            null
        } else {
            ReaderPayload(title = title, blocks = blocks)
        }
    }

    private fun decodeJavascriptStringResult(rawResult: String?): String? {
        if (rawResult.isNullOrBlank() || rawResult == "null") {
            return null
        }
        return runCatching {
            JSONArray("[$rawResult]").getString(0)
        }.getOrNull()
    }

    private fun buildReaderHtml(
        title: String,
        sourceUrl: String,
        blocks: List<ReaderBlock>
    ): String {
        val safeTitle = TextUtils.htmlEncode(title.ifBlank { sourceUrl })
        val safeSourceUrl = TextUtils.htmlEncode(sourceUrl)
        val content = buildString {
            blocks.forEach { block ->
                val safeText = TextUtils.htmlEncode(block.text)
                when (block.tag) {
                    "h1", "h2", "h3" -> append("<h2>$safeText</h2>")
                    "blockquote" -> append("<blockquote>$safeText</blockquote>")
                    "pre" -> append("<pre>$safeText</pre>")
                    "li" -> append("<p class='bullet'>&bull; $safeText</p>")
                    else -> append("<p>$safeText</p>")
                }
            }
        }
        val textColor = if (currentPreferences.themeMode == ThemeMode.DARK_OLED ||
            currentPreferences.themeMode == ThemeMode.DARK ||
            currentPreferences.themeMode == ThemeMode.ELECTRIC_PURPLE ||
            currentPreferences.themeMode == ThemeMode.DSI_CLASSIC_DARK
        ) {
            "#ECEFF4"
        } else {
            "#1A2230"
        }
        val bgColor = when (currentPreferences.themeMode) {
            ThemeMode.DARK_OLED -> "#000000"
            ThemeMode.DARK -> "#10161F"
            ThemeMode.ELECTRIC_PURPLE -> "#0D0320"
            ThemeMode.DSI_CLASSIC -> "#F7FBFF"
            ThemeMode.DSI_CLASSIC_DARK -> "#121A24"
            else -> "#F3F6FA"
        }
        val cardColor = when (currentPreferences.themeMode) {
            ThemeMode.DARK_OLED -> "#111111"
            ThemeMode.DARK -> "#18212C"
            ThemeMode.ELECTRIC_PURPLE -> "#180738"
            ThemeMode.DSI_CLASSIC -> "#EAF1F9"
            ThemeMode.DSI_CLASSIC_DARK -> "#1A2633"
            else -> "#FFFFFF"
        }
        val accentColor = when (currentPreferences.themeMode) {
            ThemeMode.DSI_CLASSIC -> "#3CA95C"
            ThemeMode.DSI_CLASSIC_DARK -> "#4FC777"
            ThemeMode.ELECTRIC_PURPLE -> "#C000FF"
            else -> String.format("#%06X", 0xFFFFFF and currentPreferences.accentPalette.solidColorInt())
        }
                val readerCsp = "default-src 'none'; base-uri 'none'; form-action 'none'; " +
                        "frame-ancestors 'none'; object-src 'none'; script-src 'none'; " +
                        "connect-src 'none'; media-src 'none'; img-src data:; font-src 'none'; " +
                        "style-src 'unsafe-inline'"

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                            <meta http-equiv="Content-Security-Policy" content="$readerCsp" />
                            <meta name="referrer" content="no-referrer" />
              <title>$safeTitle</title>
              <style>
                :root {
                  color-scheme: light dark;
                }
                body {
                  margin: 0;
                  padding: 0;
                  background: $bgColor;
                  color: $textColor;
                  font-family: "Noto Sans", sans-serif;
                }
                .page {
                  max-width: 900px;
                  margin: 0 auto;
                  padding: 24px;
                }
                .card {
                  background: $cardColor;
                  border: 1px solid rgba(0, 0, 0, 0.08);
                  border-radius: 12px;
                  padding: 18px 18px 8px;
                }
                h1 {
                  margin: 0 0 6px;
                  font-size: 1.5rem;
                  line-height: 1.3;
                }
                .source {
                  margin: 0 0 18px;
                  font-size: 0.86rem;
                  opacity: 0.86;
                }
                a {
                  color: $accentColor;
                  text-decoration: none;
                }
                p, blockquote, pre {
                  margin: 0 0 14px;
                  line-height: 1.65;
                  font-size: 1.02rem;
                }
                h2 {
                  margin: 22px 0 12px;
                  font-size: 1.1rem;
                  line-height: 1.4;
                }
                blockquote {
                  border-left: 3px solid $accentColor;
                  padding-left: 12px;
                  opacity: 0.95;
                }
                pre {
                  white-space: pre-wrap;
                  background: rgba(0, 0, 0, 0.08);
                  border-radius: 8px;
                  padding: 10px;
                }
                .bullet {
                  margin-left: 12px;
                }
              </style>
            </head>
            <body>
              <main class="page">
                <article class="card">
                  <h1>$safeTitle</h1>
                                    <p class="source"><a href="$safeSourceUrl" rel="noopener noreferrer nofollow">Open original page</a></p>
                  $content
                </article>
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun showThrottledToast(
        messageRes: Int,
        cooldownMs: Long,
        clock: AtomicLong = generalToastAtMs
    ) {
        val now = SystemClock.elapsedRealtime()
        val previous = clock.get()
        if (now - previous < cooldownMs) {
            return
        }
        if (!clock.compareAndSet(previous, now)) {
            return
        }

        runOnUiThread {
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleRenderProcessGone(webView: SyncWebView) {
        if (isDestroyed || isFinishing) return
        val crashedTab = tabsByWebView.remove(webView) ?: return
        val currentTabId = tabs.getOrNull(currentTabIndex)?.id
        val crashedIndex = tabs.indexOfFirst { it.id == crashedTab.id }
        val wasCurrentTab = currentTabId == crashedTab.id
        if (crashedIndex >= 0) {
            tabs.removeAt(crashedIndex)
        }

        safelyDestroyWebView(webView)

        if (tabs.isEmpty()) {
            createTab(initialUrl = homeUrl(), switchToTab = true)
        } else {
            val targetIndex = when {
                wasCurrentTab -> crashedIndex.coerceIn(0, tabs.lastIndex)
                crashedIndex >= 0 && currentTabIndex > crashedIndex -> {
                    (currentTabIndex - 1).coerceIn(0, tabs.lastIndex)
                }
                else -> currentTabIndex.coerceIn(0, tabs.lastIndex)
            }
            switchToTab(
                index = targetIndex,
                persistSession = false,
                transitionStyle = TabTransitionStyle.NONE
            )
        }

        persistTabSession()
        showThrottledToast(R.string.renderer_recovered_toast, TOAST_COOLDOWN_MS)
    }

    private fun suppressPagePrintApi(view: WebView?) {
        runCatching {
            view?.evaluateJavascript(
                "(function(){try{window.print=function(){return false;};}catch(e){}})();",
                null
            )
        }
    }

    private fun normalizeMainFrameUrl(uri: Uri?): String? {
        val target = uri ?: return null
        val scheme = target.scheme?.lowercase() ?: return null
        val sanitized = when (scheme) {
            "https" -> target.toString()
            "http" -> {
                if (currentPreferences.httpsOnlyMode) {
                    target.buildUpon().scheme("https").build().toString()
                } else {
                    target.toString()
                }
            }
            else -> return null
        }
        return UrlPrivacySanitizer.stripTrackingParameters(
            url = sanitized,
            enabled = currentPreferences.stripTrackingParameters
        )
    }

    private fun parseUriSafely(value: String): Uri? {
        return runCatching { Uri.parse(value) }.getOrNull()
    }

    private fun normalizeHost(rawHost: String?): String? {
        val normalized = rawHost
            ?.trim()
            ?.trimEnd('.')
            ?.lowercase()
            .orEmpty()
        if (normalized.isBlank()) {
            return null
        }
        if (normalized.any { it.isWhitespace() }) {
            return null
        }
        return normalized
    }

    private fun trustedExternalHosts(): Set<String> {
        val trusted = mutableSetOf<String>()

        normalizeHost(parseUriSafely(homeUrl())?.host)?.let(trusted::add)
        normalizeHost(parseUriSafely(BrowserSettingsStore.DEFAULT_HOMEPAGE_URL)?.host)
            ?.let(trusted::add)
        normalizeHost(parseUriSafely(currentPreferences.searchEngine.queryUrl("magni"))?.host)
            ?.let(trusted::add)

        val raw = SecureBrowserPrefs
            .get(this)
            .getString(KEY_TRUSTED_EXTERNAL_HOSTS, "[]")
            .orEmpty()
        runCatching {
            val array = JSONArray(raw)
            val count = min(array.length(), MAX_TRUSTED_EXTERNAL_HOSTS)
            for (index in 0 until count) {
                normalizeHost(array.optString(index))?.let(trusted::add)
            }
        }

        return trusted
    }

    private fun isTrustedExternalHost(host: String): Boolean {
        val normalizedHost = normalizeHost(host) ?: return false
        val trusted = trustedExternalHosts()
        return trusted.any { trustedHost ->
            normalizedHost == trustedHost || normalizedHost.endsWith(".$trustedHost")
        }
    }

    private fun rememberTrustedExternalHost(host: String) {
        val normalizedHost = normalizeHost(host) ?: return
        val updated = trustedExternalHosts()
            .plus(normalizedHost)
            .sorted()
            .take(MAX_TRUSTED_EXTERNAL_HOSTS)

        val output = JSONArray()
        updated.forEach { output.put(it) }
        SecureBrowserPrefs.get(this)
            .edit()
            .putString(KEY_TRUSTED_EXTERNAL_HOSTS, output.toString())
            .apply()
    }

    private fun isUnsafeSubresourceUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return true
        return scheme == "content" ||
            scheme == "intent" ||
            scheme == "javascript" ||
            scheme == "file"
    }

    private fun shouldBlockInsecureMainFrameNavigation(uri: Uri?): Boolean {
        if (!currentPreferences.httpsOnlyMode) {
            return false
        }
        val target = uri ?: return false
        return target.scheme.equals("http", ignoreCase = true)
    }

    private fun looksLikeDomain(value: String): Boolean {
        return value.contains('.') && value.none { it.isWhitespace() }
    }

    private fun blockedResourceResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "No Content",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun shouldSendPrivacySignals(): Boolean {
        return currentPreferences.sendDoNotTrack || currentPreferences.privateBrowsingEnabled
    }

    private fun loadUrlWithPrivacyHeaders(webView: SyncWebView, url: String) {
        if (!shouldSendPrivacySignals()) {
            webView.loadUrl(url)
            return
        }

        webView.loadUrl(
            url,
            mapOf(
                "DNT" to "1",
                "Sec-GPC" to "1"
            )
        )
    }

    private fun enqueueHistoryEntry(title: String, url: String) {
        runCatching {
            persistenceExecutor.execute {
                BrowserHistoryStore.add(
                    context = applicationContext,
                    title = title,
                    url = url
                )
            }
        }
    }

    private fun clearAllWebData() {
        tabs.forEach { tab ->
            tab.webView.apply {
                clearHistory()
                clearCache(true)
                clearFormData()
                clearSslPreferences()
            }
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()

        WebStorage.getInstance().deleteAllData()

        runCatching {
            @Suppress("DEPRECATION")
            WebViewDatabase.getInstance(this).apply {
                clearFormData()
                clearHttpAuthUsernamePassword()
                clearUsernamePassword()
            }
        }
    }

    private fun clearBrowsingDataNow() {
        clearAllWebData()
        BrowserHistoryStore.clear(this)
        BrowserSessionStore.clear(this)
        showThrottledToast(R.string.settings_browsing_data_cleared_toast, TOAST_COOLDOWN_MS)

        tabs.forEach { tab -> tab.blockedTrackerRequests.set(0) }
        val web = currentWebView()
        if (web != null) {
            loadUrlWithPrivacyHeaders(web, homeUrl())
        } else if (tabs.isEmpty()) {
            createTab(initialUrl = homeUrl(), switchToTab = true, persistSession = false)
        }
    }

    private fun safelyDestroyWebView(webView: SyncWebView) {
        try {
            webView.onScrollChangedListener = null
            webView.stopLoading()
            runCatching { webView.loadUrl("about:blank") }
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.setDownloadListener(null)
            webView.removeAllViews()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (_: Throwable) {
            // Ignore teardown exceptions from unstable WebView states.
        }
    }

    private fun releaseSnapshotCache() {
        reusableSnapshotBitmap = null
        // Preserve the snapshot in BrowserSyncBus during a screen swap so that
        // OverviewActivity can render the map immediately when it starts.
        if (!isFinishingForScreenSwap) {
            BrowserSyncBus.updateSnapshot(snapshot = null, pageWidth = 1, pageHeight = 1)
        }
    }

    private fun currentWebView(): SyncWebView? = tabs.getOrNull(currentTabIndex)?.webView

    private fun findTab(webView: SyncWebView?): BrowserTab? {
        return if (webView == null) {
            null
        } else {
            tabsByWebView[webView]
        }
    }

    private fun BrowserTab.progressUpdate(newTitle: String, newUrl: String) {
        if (newTitle.isNotBlank()) {
            title = newTitle
        }
        if (newUrl.isNotBlank()) {
            url = newUrl
        }
    }

    private fun BrowserTab.displayLabel(): String {
        if (title.isNotBlank()) {
            return title
        }
        if (url.isNotBlank()) {
            return url
        }
        return getString(R.string.tab_label_fallback)
    }

    companion object {
        private const val READER_MODE_BASE_URL = "https://reader.magni.local/"
        private const val KEY_TRUSTED_EXTERNAL_HOSTS = "trusted_external_hosts"
        private const val MAX_TABS = 10
        private const val MAX_TRUSTED_EXTERNAL_HOSTS = 64
        private const val TOAST_COOLDOWN_MS = 1800L
        private const val SCREEN_SWAP_DEBOUNCE_MS = 320L
        private const val SCREEN_SWAP_GLOBAL_LOCK_MS = 650L
        private const val SCREEN_SWAP_RETRY_DELAY_MS = 180L
        private const val TAB_LIMIT_TOAST_COOLDOWN_MS = 2500L
        private const val TRACKER_UI_MIN_UPDATE_INTERVAL_MS = 300L
        private const val JS_DIALOG_TOAST_COOLDOWN_MS = 2000L
        private const val DOWNLOAD_TOAST_COOLDOWN_MS = 2500L
        private const val READER_EXTRACT_TOAST_COOLDOWN_MS = 1200L
        private const val DOWNLOAD_SPAM_WINDOW_MS = 10_000L
        private const val MAX_DOWNLOAD_ATTEMPTS_PER_WINDOW = 3
        private const val MAX_BOOKMARK_DIALOG_ITEMS = 40
        private const val SNAPSHOT_MIN_CAPTURE_INTERVAL_MS = 200L
        private const val SESSION_PERSIST_DEBOUNCE_MS = 280L
        private const val SNAPSHOT_MAX_EDGE_PX = 4_096
        private const val TOOLBAR_PULL_THRESHOLD_DP = 18f
        private const val TOOLBAR_DISMISS_PULL_THRESHOLD_DP = 22f
        private const val URL_BAR_AUTO_HIDE_DELAY_MS = 4500L
        private const val TAB_SWITCH_TRAVEL_RATIO = 0.1f
        private const val TAB_SWITCH_OUT_TRAVEL_RATIO = 0.5f
        private const val TAB_SWITCH_ENTER_DURATION_MS = 240L
        private const val TAB_SWITCH_EXIT_DURATION_MS = 200L
        private const val TAB_SWITCH_INCOMING_START_ALPHA = 0.78f
        private const val TAB_SWITCH_OUTGOING_END_ALPHA = 0.82f
        private const val TAB_NEW_TAB_ENTER_DURATION_MS = 280L
        private const val TAB_NEW_TAB_EXIT_DURATION_MS = 210L
        private const val TAB_NEW_TAB_LIFT_DP = 18f
        private const val TAB_NEW_TAB_INCOMING_START_ALPHA = 0.72f
        private const val TAB_NEW_TAB_INCOMING_START_SCALE = 0.985f
        private const val TAB_NEW_TAB_OUTGOING_END_ALPHA = 0.8f
        private const val TAB_NEW_TAB_OUTGOING_END_SCALE = 0.992f
        // Keep overview snapshots bounded so repeated swaps/pages do not balloon memory/GPU caches.
        private val SNAPSHOT_PIXEL_BUDGETS = doubleArrayOf(
            8_000_000.0,
            6_000_000.0,
            4_000_000.0,
            3_000_000.0
        )
        private val tabEnterInterpolator = DecelerateInterpolator(1.4f)
        private val tabExitInterpolator = AccelerateInterpolator(1.15f)
        private const val DISABLED_BUTTON_ALPHA = 0.45f
        private const val ENABLED_BUTTON_ALPHA = 1f
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        private const val READER_EXTRACTION_SCRIPT = """
            (function() {
              try {
                const candidates = Array.from(
                  document.querySelectorAll(
                    "article, main, [role='main'], .post, .article, .entry-content, #content"
                  )
                ).filter((node) => (node && (node.innerText || "").trim().length > 0));

                let root = null;
                if (candidates.length > 0) {
                  candidates.sort((left, right) => {
                    const leftLen = (left.innerText || "").length;
                    const rightLen = (right.innerText || "").length;
                    return rightLen - leftLen;
                  });
                  root = candidates[0];
                } else {
                  root = document.body;
                }

                const collectBlocks = (scope) => {
                  const nodes = Array.from(
                    scope.querySelectorAll("h1,h2,h3,p,blockquote,pre,li")
                  );
                  const blocks = [];
                  for (let i = 0; i < nodes.length; i += 1) {
                    const node = nodes[i];
                    const text = (node.innerText || "")
                      .replace(/\s+/g, " ")
                      .trim();
                    if (!text) continue;
                    const tag = (node.tagName || "p").toLowerCase();
                    if ((tag === "p" || tag === "li") && text.length < 26) continue;
                    blocks.push({ tag: tag, text: text });
                    if (blocks.length >= 260) break;
                  }
                  return blocks;
                };

                let blocks = collectBlocks(root);
                if (blocks.length < 8) {
                  blocks = collectBlocks(document.body);
                }

                const title = (
                  (document.querySelector("h1") || {}).innerText ||
                  document.title ||
                  ""
                ).trim();
                return JSON.stringify({ title: title, blocks: blocks });
              } catch (_error) {
                return JSON.stringify({ title: document.title || "", blocks: [] });
              }
            })();
        """
    }
}
