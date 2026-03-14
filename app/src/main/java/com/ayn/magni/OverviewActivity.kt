package com.ayn.magni

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ayn.magni.data.BrowserSettingsStore
import com.ayn.magni.data.DisplayRoleStore
import com.ayn.magni.data.ThemeMode
import com.ayn.magni.databinding.ActivityOverviewBinding
import com.ayn.magni.sync.BrowserCommand
import com.ayn.magni.sync.BrowserSyncBus
import com.ayn.magni.sync.BrowserUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OverviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOverviewBinding
    private var zoomLaunchAttempted = false
    private var browserCanGoBack = false
    private var lastScreenSwapAtMs = 0L
    private var isOverviewTopBarVisible = false
    private var isDisplayAlignmentInProgress = false
    private var lastDisplayAlignmentAtMs = 0L
    private var pendingSwapAnimation: ScreenSwapAnimation? = null
    private var isSwapRetryScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        BrowserSettingsStore.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        binding = ActivityOverviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureImmersiveMode()
        applyIncomingSwapRequest(intent)

        binding.overviewMap.setOverlayMode(false)
        binding.overviewMap.onPositionRequested = { x, y ->
            BrowserSyncBus.sendCommand(BrowserCommand.ScrollTo(x, y))
        }
        binding.overviewMap.onOverviewZoomChanged = { zoom ->
            updateOverviewZoomText(zoom)
        }
        binding.overviewResetButton.setOnClickListener {
            binding.overviewMap.resetOverviewZoom()
            updateOverviewZoomText(binding.overviewMap.currentOverviewZoom())
        }

        binding.overviewSettingsButton.setOnClickListener {
            if (isFinishing || isDestroyed) {
                return@setOnClickListener
            }
            runCatching {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        applyOverviewTopBarVisibility()
        configureBackHandling()

        applyChromeStyle()
        collectCommands()
        collectBrowserState()
    }

    override fun onResume() {
        super.onResume()
        configureImmersiveMode()
        applyChromeStyle()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureImmersiveMode()
        }
    }

    override fun onStart() {
        super.onStart()
        consumePendingSwapRequestOrAlign()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIncomingSwapRequest(intent)
        if (pendingSwapAnimation != null) {
            consumePendingSwapRequestOrAlign()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_X && (event?.repeatCount ?: 0) > 0) {
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_X) {
            swapScreenRoles()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun collectBrowserState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BrowserSyncBus.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun collectCommands() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BrowserSyncBus.commands.collect { command ->
                    if (command is BrowserCommand.SetChromeVisible) {
                        setOverviewTopBarVisible(command.visible)
                    }
                }
            }
        }
    }

    private fun renderState(state: BrowserUiState) {
        if (isFinishing || isDestroyed) {
            return
        }
        browserCanGoBack = state.canGoBack
        setOverviewTopBarVisible(state.chromeControlsVisible)
        binding.titleText.text = state.title.ifBlank { getString(R.string.browser_title_default) }

        val urlText = state.url.ifBlank { "about:blank" }
        binding.metaText.text = getString(
            R.string.meta_template,
            shortUrl(urlText),
            state.progress
        )

        binding.overviewMap.renderState(
            snapshot = BrowserSyncBus.latestSnapshot(),
            snapshotPageWidth = state.snapshotPageWidth,
            snapshotPageHeight = state.snapshotPageHeight,
            viewport = state.viewport
        )
        updateOverviewZoomText(binding.overviewMap.currentOverviewZoom())
    }

    private fun applyChromeStyle() {
        val prefs = BrowserSettingsStore.load(this)
        val isDsiClassic = prefs.themeMode == ThemeMode.DSI_CLASSIC
        val isDsiClassicDark = prefs.themeMode == ThemeMode.DSI_CLASSIC_DARK
        val isElectricPurple = prefs.themeMode == ThemeMode.ELECTRIC_PURPLE
        val isOled = prefs.themeMode == ThemeMode.DARK_OLED
        val darkChrome = isElectricPurple || isDsiClassicDark || (!isDsiClassic && (
            isOled || prefs.themeMode == ThemeMode.DARK ||
                (prefs.themeMode == ThemeMode.SYSTEM && isNightModeActive())
            ))
        val accent = when {
            isDsiClassicDark -> Color.parseColor("#4FC777")
            isDsiClassic -> Color.parseColor("#3CA95C")
            isElectricPurple -> Color.parseColor("#C000FF")
            else -> prefs.accentPalette.solidColorInt()
        }
        val accentSoft = when {
            isDsiClassicDark -> Color.parseColor("#444FC777")
            isDsiClassic -> Color.parseColor("#443CA95C")
            isElectricPurple -> Color.parseColor("#44C000FF")
            else -> prefs.accentPalette.softColorInt()
        }

        val shellColor = when {
            isDsiClassicDark -> Color.parseColor("#111A24")
            isDsiClassic -> Color.parseColor("#D8DDE4")
            isElectricPurple -> Color.parseColor("#06020E")
            isOled -> Color.BLACK
            darkChrome -> Color.parseColor("#151A22")
            else -> ContextCompat.getColor(this, R.color.retro_shell)
        }
        val mapBackground = when {
            isDsiClassicDark -> Color.parseColor("#1A2633")
            isDsiClassic -> Color.parseColor("#F6FAFF")
            isElectricPurple -> Color.parseColor("#110525")
            isOled -> Color.BLACK
            darkChrome -> Color.parseColor("#1A2430")
            else -> ContextCompat.getColor(this, R.color.retro_map_empty)
        }
        val mapGrid = when {
            isDsiClassicDark -> Color.parseColor("#31475D")
            isDsiClassic -> Color.parseColor("#C8D3E3")
            isElectricPurple -> Color.parseColor("#6E2BC4")
            isOled -> Color.parseColor("#2A2F39")
            darkChrome -> Color.parseColor("#344154")
            else -> ContextCompat.getColor(this, R.color.retro_map_grid)
        }
        val textColor = when {
            isDsiClassicDark -> Color.parseColor("#E3ECF7")
            isElectricPurple -> Color.parseColor("#F5E9FF")
            darkChrome -> Color.parseColor("#E7EDF5")
            else -> Color.parseColor("#1F2B39")
        }
        val mutedColor = when {
            isDsiClassicDark -> Color.parseColor("#9EB1C8")
            isElectricPurple -> Color.parseColor("#C8A5EC")
            darkChrome -> Color.parseColor("#B7C2D5")
            else -> Color.parseColor("#4C5D72")
        }

        binding.root.setBackgroundColor(shellColor)
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
        binding.titleText.setTextColor(textColor)
        binding.metaText.setTextColor(mutedColor)
        binding.zoomText.setTextColor(mutedColor)
        binding.overviewTopBar.backgroundTintList = ColorStateList.valueOf(mapBackground)
        binding.overviewMapContainer.backgroundTintList = ColorStateList.valueOf(mapBackground)
        binding.overviewSettingsButton.backgroundTintList = ColorStateList.valueOf(mapBackground)
        binding.overviewSettingsButton.setTextColor(accent)
        binding.overviewResetButton.backgroundTintList = ColorStateList.valueOf(mapBackground)
        binding.overviewResetButton.setTextColor(accent)
        binding.overviewMap.applyThemeColors(
            mapBackgroundColor = mapBackground,
            mapGridColor = mapGrid,
            accentColor = accent,
            accentSoftColor = accentSoft
        )
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

    private fun configureBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (browserCanGoBack) {
                        BrowserSyncBus.sendCommand(BrowserCommand.Back)
                        return
                    }

                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    private fun applyIncomingSwapRequest(intent: Intent?) {
        val request = intent ?: return
        if (!request.getBooleanExtra(EXTRA_FORCE_SWAP_HANDOFF, false)) {
            return
        }

        val zoomOnTop = request.getBooleanExtra(
            EXTRA_TARGET_ZOOM_ON_TOP,
            DisplayRoleStore.isZoomOnTop(this)
        )
        zoomLaunchAttempted = false
        pendingSwapAnimation = ScreenSwapTransition.forZoomOnTop(zoomOnTop)
    }

    private fun consumePendingSwapRequestOrAlign() {
        val swapAnimation = pendingSwapAnimation
        if (swapAnimation == null) {
            alignDisplaysAndLaunchZoomIfNeeded(null)
            return
        }

        val started = alignDisplaysAndLaunchZoomIfNeeded(swapAnimation)
        if (started) {
            pendingSwapAnimation = null
            return
        }

        binding.root.postDelayed(
            {
                if (!isFinishing && !isDestroyed && pendingSwapAnimation != null) {
                    consumePendingSwapRequestOrAlign()
                }
            },
            PENDING_SWAP_RETRY_DELAY_MS
        )
    }

    private fun launchZoomActivity(swapAnimation: ScreenSwapAnimation? = null): Boolean {
        if (isFinishing || isDestroyed) {
            return false
        }
        val intent = Intent(this, ZoomActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val secondaryDisplayId = findSecondaryDisplayId(Display.DEFAULT_DISPLAY)
        val wantsZoomOnTop = DisplayRoleStore.isZoomOnTop(this)
        val canSwap = secondaryDisplayId != null
        val zoomDisplayId = when {
            wantsZoomOnTop && canSwap -> Display.DEFAULT_DISPLAY
            canSwap -> secondaryDisplayId
            else -> null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && zoomDisplayId != null) {
            try {
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(zoomDisplayId)
                if (swapAnimation != null) {
                    options.update(
                        ActivityOptions.makeCustomAnimation(
                            this,
                            swapAnimation.enterAnimRes,
                            swapAnimation.exitAnimRes
                        )
                    )
                }
                startActivity(intent, options.toBundle())
                return true
            } catch (_: Exception) {
                // Fallback below.
            }
        }

        return if (swapAnimation != null) {
            val options = ActivityOptions.makeCustomAnimation(
                this,
                swapAnimation.enterAnimRes,
                swapAnimation.exitAnimRes
            )
            runCatching {
                startActivity(intent, options.toBundle())
            }.isSuccess
        } else {
            runCatching {
                startActivity(intent)
            }.isSuccess
        }
    }

    private fun alignDisplaysAndLaunchZoomIfNeeded(
        swapAnimation: ScreenSwapAnimation? = null
    ): Boolean {
        if (isFinishing || isDestroyed) {
            return false
        }

        val now = SystemClock.elapsedRealtime()
        if (isDisplayAlignmentInProgress) {
            return false
        }
        if (swapAnimation == null && now - lastDisplayAlignmentAtMs < DISPLAY_ALIGNMENT_DEBOUNCE_MS) {
            return false
        }

        isDisplayAlignmentInProgress = true
        lastDisplayAlignmentAtMs = now
        try {
            val secondaryDisplayId = findSecondaryDisplayId(Display.DEFAULT_DISPLAY)
            val wantsZoomOnTop = DisplayRoleStore.isZoomOnTop(this)
            val canSwap = secondaryDisplayId != null
            val targetOverviewDisplay = if (wantsZoomOnTop && canSwap) {
                secondaryDisplayId
            } else {
                Display.DEFAULT_DISPLAY
            }
            val current = currentDisplayId()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                targetOverviewDisplay != null &&
                current != targetOverviewDisplay
            ) {
                try {
                    val relaunch = Intent(this, OverviewActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val options = ActivityOptions.makeBasic().setLaunchDisplayId(targetOverviewDisplay)
                    if (swapAnimation != null) {
                        options.update(
                            ActivityOptions.makeCustomAnimation(
                                this,
                                swapAnimation.enterAnimRes,
                                swapAnimation.exitAnimRes
                            )
                        )
                    }
                    startActivity(relaunch, options.toBundle())
                    // singleTask moves the existing instance to the target display
                    // via onNewIntent — do NOT finish() or the activity is destroyed.
                } catch (_: Exception) {
                    // If display launch fails, continue on current display.
                }
            }

            if (swapAnimation != null) {
                val launched = launchZoomActivity(swapAnimation)
                if (launched) {
                    zoomLaunchAttempted = true
                }
                return launched
            }

            if (!zoomLaunchAttempted) {
                zoomLaunchAttempted = true
                val launched = launchZoomActivity(swapAnimation)
                if (!launched) {
                    zoomLaunchAttempted = false
                }
                return launched
            }
            return true
        } finally {
            isDisplayAlignmentInProgress = false
        }
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

        if (findSecondaryDisplayId(Display.DEFAULT_DISPLAY) == null) {
            Toast.makeText(this, R.string.display_single, Toast.LENGTH_SHORT).show()
            DisplayRoleStore.releaseScreenSwapLock()
            return
        }

        val previousZoomOnTop = DisplayRoleStore.isZoomOnTop(this)
        val zoomOnTop = DisplayRoleStore.toggleZoomOnTop(this)
        val toastRes = if (zoomOnTop) {
            R.string.swap_zoom_top_toast
        } else {
            R.string.swap_zoom_bottom_toast
        }
        Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show()
        zoomLaunchAttempted = false
        val swapStarted = alignDisplaysAndLaunchZoomIfNeeded(
            ScreenSwapTransition.forZoomOnTop(zoomOnTop)
        )
        if (!swapStarted) {
            DisplayRoleStore.setZoomOnTop(this, previousZoomOnTop)
            DisplayRoleStore.releaseScreenSwapLock()
        }
    }

    private fun findSecondaryDisplayId(currentDisplayId: Int): Int? {
        val displayManager = getSystemService(DisplayManager::class.java) ?: return null

        val preferred = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .toList()
            .filter { it.displayId != currentDisplayId && it.state != Display.STATE_OFF }

        if (preferred.isNotEmpty()) {
            return preferred.sortedBy { areaOf(it) }.first().displayId
        }

        return displayManager.displays
            .toList()
            .filter { it.displayId != currentDisplayId && it.state != Display.STATE_OFF }
            .sortedBy { areaOf(it) }
            .firstOrNull()
            ?.displayId
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

    private fun areaOf(display: Display): Long {
        val mode = display.mode
        return mode.physicalWidth.toLong() * mode.physicalHeight.toLong()
    }

    private fun currentDisplayId(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.displayId
        }
    }

    private fun updateOverviewZoomText(zoom: Float) {
        val percent = (zoom * 100f).roundToInt().coerceAtLeast(100)
        binding.zoomText.text = getString(R.string.overview_zoom_template, percent)
    }

    private fun applyOverviewTopBarVisibility() {
        binding.overviewTopBar.visibility = if (isOverviewTopBarVisible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun setOverviewTopBarVisible(visible: Boolean) {
        if (isOverviewTopBarVisible == visible) {
            return
        }
        isOverviewTopBarVisible = visible
        applyOverviewTopBarVisibility()
    }

    private fun shortUrl(url: String): String {
        val cleaned = url.removePrefix("https://").removePrefix("http://")
        if (cleaned.isBlank()) {
            return "about:blank"
        }
        return if (cleaned.length > 30) {
            cleaned.take(27) + "..."
        } else {
            cleaned
        }
    }

    override fun onDestroy() {
        // Cleanup listeners to prevent memory leaks
        binding.overviewMap.onPositionRequested = null
        binding.overviewMap.onOverviewZoomChanged = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FORCE_SWAP_HANDOFF = "com.ayn.magni.extra.FORCE_SWAP_HANDOFF"
        const val EXTRA_TARGET_ZOOM_ON_TOP = "com.ayn.magni.extra.TARGET_ZOOM_ON_TOP"
        private const val SCREEN_SWAP_DEBOUNCE_MS = 320L
        private const val SCREEN_SWAP_GLOBAL_LOCK_MS = 650L
        private const val SCREEN_SWAP_RETRY_DELAY_MS = 180L
        private const val DISPLAY_ALIGNMENT_DEBOUNCE_MS = 420L
        private const val PENDING_SWAP_RETRY_DELAY_MS = 120L
    }
}
