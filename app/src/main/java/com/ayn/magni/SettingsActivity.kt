package com.ayn.magni

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import com.ayn.magni.data.BrowserBookmarkStore
import com.ayn.magni.data.BrowserHistoryStore
import com.ayn.magni.data.BrowserPreferences
import com.ayn.magni.data.BrowserSettingsStore
import com.ayn.magni.data.AccentPalette
import com.ayn.magni.data.BottomScreenFeedbackEffect
import com.ayn.magni.data.CursorShape
import com.ayn.magni.data.FrameRateMode
import com.ayn.magni.data.SearchEngine
import com.ayn.magni.data.ThemeMode
import com.ayn.magni.data.ToolbarPillEdge
import com.ayn.magni.databinding.ActivitySettingsBinding
import com.ayn.magni.databinding.ItemBookmarkEntryBinding
import com.ayn.magni.databinding.ItemHistoryEntryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val themeModes = listOf(
        ThemeMode.SYSTEM,
        ThemeMode.LIGHT,
        ThemeMode.DARK,
        ThemeMode.DARK_OLED,
        ThemeMode.ELECTRIC_PURPLE,
        ThemeMode.DSI_CLASSIC,
        ThemeMode.DSI_CLASSIC_DARK
    )
    private val accentPalettes = AccentPalette.entries.toList()
    private val cursorShapes = CursorShape.entries.toList()
    private val bottomFeedbackEffects = BottomScreenFeedbackEffect.entries.toList()
    private val frameRateModes = FrameRateMode.entries.toList()
    private val searchEngines = SearchEngine.entries.toList()
    private val toolbarPillEdges = ToolbarPillEdge.entries.toList()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var activeDialog: AlertDialog? = null
    private var cachedHistoryEntries = emptyList<com.ayn.magni.data.HistoryEntry>()
    private var cachedBookmarkEntries = emptyList<com.ayn.magni.data.BookmarkEntry>()
    private var historyFilterQuery = ""
    private var bookmarksFilterQuery = ""
    private val renderHistoryRunnable = Runnable { renderHistory() }
    private val renderBookmarksRunnable = Runnable { renderBookmarks() }

    private val dateFormatter: DateFormat by lazy {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        BrowserSettingsStore.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPickers()
        loadPreferencesIntoUi()
        refreshHistoryData()
        refreshBookmarksData()
        configureFilters()
        renderHistory()
        renderBookmarks()
        configureActions()
        applyChromeStyle()
    }

    private fun setupPickers() {
        val themeLabels = listOf(
            getString(R.string.settings_theme_system),
            getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark),
            getString(R.string.settings_theme_dark_oled),
            getString(R.string.settings_theme_electric_purple),
            getString(R.string.settings_theme_dsi_classic),
            getString(R.string.settings_theme_dsi_classic_dark)
        )
        binding.themeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            themeLabels
        )

        binding.accentSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            accentPalettes.map { it.label }
        )

        val cursorShapeLabels = listOf(
            getString(R.string.settings_cursor_shape_crosshair),
            getString(R.string.settings_cursor_shape_dot),
            getString(R.string.settings_cursor_shape_ring)
        )
        binding.cursorShapeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            cursorShapeLabels
        )

        val feedbackEffectLabels = listOf(
            getString(R.string.settings_bottom_feedback_aurora),
            getString(R.string.settings_bottom_feedback_ring),
            getString(R.string.settings_bottom_feedback_comet),
            getString(R.string.settings_bottom_feedback_pixel)
        )
        binding.bottomFeedbackEffectSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            feedbackEffectLabels
        )

        val frameRateLabels = listOf(
            getString(R.string.settings_frame_rate_30),
            getString(R.string.settings_frame_rate_60),
            getString(R.string.settings_frame_rate_120)
        )
        binding.frameRateSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            frameRateLabels
        )

        binding.searchEngineSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            searchEngines.map { it.label }
        )

        val toolbarPillEdgeLabels = toolbarPillEdges.map { edge ->
            when (edge) {
                ToolbarPillEdge.TOP -> getString(R.string.settings_pill_edge_top)
                ToolbarPillEdge.BOTTOM -> getString(R.string.settings_pill_edge_bottom)
                ToolbarPillEdge.LEFT -> getString(R.string.settings_pill_edge_left)
                ToolbarPillEdge.RIGHT -> getString(R.string.settings_pill_edge_right)
            }
        }
        binding.toolbarPillEdgeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            toolbarPillEdgeLabels
        )
    }

    private fun loadPreferencesIntoUi() {
        val prefs = BrowserSettingsStore.load(this)

        binding.themeSpinner.setSelection(themeModes.indexOf(prefs.themeMode).coerceAtLeast(0))
        binding.accentSpinner.setSelection(
            accentPalettes.indexOf(prefs.accentPalette).coerceAtLeast(0)
        )
        binding.cursorShapeSpinner.setSelection(
            cursorShapes.indexOf(prefs.cursorShape).coerceAtLeast(0)
        )
        binding.bottomFeedbackEffectSpinner.setSelection(
            bottomFeedbackEffects.indexOf(prefs.bottomScreenFeedbackEffect).coerceAtLeast(0)
        )
        binding.frameRateSpinner.setSelection(
            frameRateModes.indexOf(prefs.frameRateMode).coerceAtLeast(0)
        )
        binding.searchEngineSpinner.setSelection(searchEngines.indexOf(prefs.searchEngine).coerceAtLeast(0))
        binding.toolbarPillEdgeSpinner.setSelection(
            toolbarPillEdges.indexOf(prefs.toolbarPillEdge).coerceAtLeast(0)
        )

        binding.homepageEdit.setText(prefs.homepage)

        binding.jsSwitch.isChecked = prefs.javascriptEnabled
        binding.desktopSwitch.isChecked = prefs.desktopMode
        binding.imagesSwitch.isChecked = prefs.loadImages
        binding.openLinksInNewTabSwitch.isChecked = prefs.openLinksInNewTab
        binding.privateModeSwitch.isChecked = prefs.privateBrowsingEnabled
        binding.blockAllCookiesSwitch.isChecked = prefs.blockAllCookies
        binding.doNotTrackSwitch.isChecked = prefs.sendDoNotTrack
        binding.blockTrackersSwitch.isChecked = prefs.blockTrackers
        binding.stripTrackingParamsSwitch.isChecked = prefs.stripTrackingParameters
        binding.httpsOnlyModeSwitch.isChecked = prefs.httpsOnlyMode
        binding.clearDataOnExitSwitch.isChecked = prefs.clearBrowsingDataOnExit
    }

    private fun configureActions() {
        binding.saveButton.setOnClickListener {
            savePreferences()
            finishWithResult(null)
        }

        binding.closeButton.setOnClickListener {
            finish()
        }

        binding.clearHistoryButton.setOnClickListener {
            if (isFinishing || isDestroyed) {
                return@setOnClickListener
            }
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_history)
                .setMessage(R.string.settings_clear_history_confirm)
                .setPositiveButton(R.string.settings_clear_history) { _, _ ->
                    BrowserHistoryStore.clear(this)
                    refreshHistoryData()
                    renderHistory()
                }
                .setNegativeButton(R.string.url_dialog_cancel, null)
                .create()
            showManagedDialog(dialog)
        }

        binding.clearBookmarksButton.setOnClickListener {
            if (isFinishing || isDestroyed) {
                return@setOnClickListener
            }
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_bookmarks)
                .setMessage(R.string.settings_clear_bookmarks_confirm)
                .setPositiveButton(R.string.settings_clear_bookmarks) { _, _ ->
                    BrowserBookmarkStore.clear(this)
                    refreshBookmarksData()
                    renderBookmarks()
                }
                .setNegativeButton(R.string.url_dialog_cancel, null)
                .create()
            showManagedDialog(dialog)
        }

        binding.clearBrowsingDataNowButton.setOnClickListener {
            if (isFinishing || isDestroyed) {
                return@setOnClickListener
            }
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_browsing_data_now)
                .setMessage(R.string.settings_clear_browsing_data_confirm)
                .setPositiveButton(R.string.settings_clear_browsing_data_now) { _, _ ->
                    savePreferences()
                    finishWithResult(openUrl = null, clearBrowsingDataNow = true)
                }
                .setNegativeButton(R.string.url_dialog_cancel, null)
                .create()
            showManagedDialog(dialog)
        }
    }

    private fun configureFilters() {
        binding.historySearchEdit.doAfterTextChanged { editable ->
            val nextQuery = editable?.toString().orEmpty().trim()
            if (nextQuery == historyFilterQuery) {
                return@doAfterTextChanged
            }
            historyFilterQuery = nextQuery
            uiHandler.removeCallbacks(renderHistoryRunnable)
            uiHandler.postDelayed(renderHistoryRunnable, FILTER_RENDER_DEBOUNCE_MS)
        }

        binding.bookmarksSearchEdit.doAfterTextChanged { editable ->
            val nextQuery = editable?.toString().orEmpty().trim()
            if (nextQuery == bookmarksFilterQuery) {
                return@doAfterTextChanged
            }
            bookmarksFilterQuery = nextQuery
            uiHandler.removeCallbacks(renderBookmarksRunnable)
            uiHandler.postDelayed(renderBookmarksRunnable, FILTER_RENDER_DEBOUNCE_MS)
        }
    }

    private fun renderHistory() {
        if (isFinishing || isDestroyed) {
            return
        }
        val entries = cachedHistoryEntries.take(40)
        val filtered = if (historyFilterQuery.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.title.contains(historyFilterQuery, ignoreCase = true) ||
                    entry.url.contains(historyFilterQuery, ignoreCase = true)
            }
        }

        binding.historyCountText.text = getString(
            R.string.settings_showing_count,
            filtered.size,
            entries.size
        )
        binding.historyContainer.removeAllViews()
        binding.emptyHistoryText.isVisible = filtered.isEmpty()
        binding.emptyHistoryText.text = if (entries.isEmpty()) {
            getString(R.string.settings_no_history)
        } else {
            getString(R.string.settings_no_matches)
        }

        filtered.forEach { entry ->
            val itemBinding = ItemHistoryEntryBinding.inflate(layoutInflater, binding.historyContainer, false)
            itemBinding.titleText.text = entry.title.ifBlank { entry.url }
            itemBinding.urlText.text = entry.url
            itemBinding.timeText.text = dateFormatter.format(Date(entry.visitedAt))

            itemBinding.openButton.setOnClickListener {
                if (isFinishing || isDestroyed) {
                    return@setOnClickListener
                }
                savePreferences()
                finishWithResult(entry.url)
            }

            itemBinding.deleteButton.setOnClickListener {
                if (isFinishing || isDestroyed) {
                    return@setOnClickListener
                }
                BrowserHistoryStore.remove(this, entry.id)
                refreshHistoryData()
                renderHistory()
            }

            val copyAction = View.OnLongClickListener {
                copyUrlToClipboard(entry.url)
                true
            }
            itemBinding.root.setOnLongClickListener(copyAction)
            itemBinding.urlText.setOnLongClickListener(copyAction)

            binding.historyContainer.addView(itemBinding.root)
        }
    }

    private fun renderBookmarks() {
        if (isFinishing || isDestroyed) {
            return
        }
        val entries = cachedBookmarkEntries.take(80)
        val filtered = if (bookmarksFilterQuery.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.title.contains(bookmarksFilterQuery, ignoreCase = true) ||
                    entry.url.contains(bookmarksFilterQuery, ignoreCase = true)
            }
        }

        binding.bookmarksCountText.text = getString(
            R.string.settings_showing_count,
            filtered.size,
            entries.size
        )
        binding.bookmarksContainer.removeAllViews()
        binding.emptyBookmarksText.isVisible = filtered.isEmpty()
        binding.emptyBookmarksText.text = if (entries.isEmpty()) {
            getString(R.string.settings_no_bookmarks)
        } else {
            getString(R.string.settings_no_matches)
        }

        filtered.forEach { entry ->
            val itemBinding = ItemBookmarkEntryBinding.inflate(layoutInflater, binding.bookmarksContainer, false)
            val titlePrefix = if (entry.favorite) "[FAV] " else ""
            itemBinding.titleText.text = titlePrefix + entry.title.ifBlank { entry.url }
            itemBinding.urlText.text = entry.url
            itemBinding.timeText.text = dateFormatter.format(Date(entry.addedAt))
            itemBinding.favoriteButton.text = if (entry.favorite) {
                getString(R.string.settings_favorite_on_short)
            } else {
                getString(R.string.settings_favorite_short)
            }

            itemBinding.openButton.setOnClickListener {
                if (isFinishing || isDestroyed) {
                    return@setOnClickListener
                }
                savePreferences()
                finishWithResult(entry.url)
            }

            itemBinding.favoriteButton.setOnClickListener {
                if (isFinishing || isDestroyed) {
                    return@setOnClickListener
                }
                BrowserBookmarkStore.upsert(
                    context = this,
                    title = entry.title.ifBlank { entry.url },
                    url = entry.url,
                    favoriteOverride = !entry.favorite
                )
                refreshBookmarksData()
                renderBookmarks()
            }

            itemBinding.deleteButton.setOnClickListener {
                if (isFinishing || isDestroyed) {
                    return@setOnClickListener
                }
                BrowserBookmarkStore.remove(this, entry.id)
                refreshBookmarksData()
                renderBookmarks()
            }

            val copyAction = View.OnLongClickListener {
                copyUrlToClipboard(entry.url)
                true
            }
            itemBinding.root.setOnLongClickListener(copyAction)
            itemBinding.urlText.setOnLongClickListener(copyAction)

            binding.bookmarksContainer.addView(itemBinding.root)
        }
    }

    private fun applyChromeStyle() {
        val prefs = BrowserSettingsStore.load(this)
        val isDsiClassic = prefs.themeMode == ThemeMode.DSI_CLASSIC
        val isDsiClassicDark = prefs.themeMode == ThemeMode.DSI_CLASSIC_DARK
        val isPurple = prefs.themeMode == ThemeMode.ELECTRIC_PURPLE
        val isOled = prefs.themeMode == ThemeMode.DARK_OLED
        val darkChrome = isPurple || isDsiClassicDark || (!isDsiClassic && (
            isOled ||
                prefs.themeMode == ThemeMode.DARK ||
                (prefs.themeMode == ThemeMode.SYSTEM && isNightModeActive())
            ))

        val accentColor = when {
            isPurple -> Color.parseColor("#C000FF")
            isDsiClassicDark -> Color.parseColor("#4FC777")
            isDsiClassic -> Color.parseColor("#3CA95C")
            else -> prefs.accentPalette.solidColorInt()
        }
        val accentSoftColor = when {
            isPurple -> Color.parseColor("#44C000FF")
            isDsiClassicDark -> Color.parseColor("#444FC777")
            isDsiClassic -> Color.parseColor("#443CA95C")
            else -> prefs.accentPalette.softColorInt()
        }

        val shellColor = when {
            isPurple -> Color.parseColor("#06020E")
            isDsiClassicDark -> Color.parseColor("#111A24")
            isDsiClassic -> Color.parseColor("#D8DDE4")
            isOled -> Color.BLACK
            darkChrome -> Color.parseColor("#151A22")
            else -> ContextCompat.getColor(this, R.color.retro_shell)
        }
        val panelColor = when {
            isPurple -> Color.parseColor("#110526")
            isDsiClassicDark -> Color.parseColor("#1A2633")
            isDsiClassic -> Color.parseColor("#F6FAFF")
            isOled -> Color.BLACK
            darkChrome -> Color.parseColor("#1D2430")
            else -> ContextCompat.getColor(this, R.color.retro_inner)
        }
        val controlColor = when {
            isPurple -> Color.parseColor("#19073A")
            isDsiClassicDark -> Color.parseColor("#253447")
            isDsiClassic -> Color.parseColor("#EDF2F8")
            isOled -> Color.parseColor("#101010")
            darkChrome -> Color.parseColor("#242E3B")
            else -> ContextCompat.getColor(this, R.color.retro_button)
        }
        val textColor = when {
            isPurple -> Color.parseColor("#F4E8FF")
            isDsiClassicDark -> Color.parseColor("#E3ECF7")
            isDsiClassic -> Color.parseColor("#1F2B39")
            darkChrome -> Color.parseColor("#E7EDF5")
            else -> ContextCompat.getColor(this, R.color.retro_text)
        }
        val mutedColor = when {
            isPurple -> Color.parseColor("#CAA6EE")
            isDsiClassicDark -> Color.parseColor("#9EB1C8")
            isDsiClassic -> Color.parseColor("#4C5D72")
            darkChrome -> Color.parseColor("#B7C2D5")
            else -> ContextCompat.getColor(this, R.color.retro_muted)
        }

        binding.root.setBackgroundColor(shellColor)
        window.statusBarColor = shellColor
        window.navigationBarColor = shellColor
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        val useLightSystemBars = !darkChrome
        insetsController.isAppearanceLightStatusBars = useLightSystemBars
        insetsController.isAppearanceLightNavigationBars = useLightSystemBars

        tintViewTree(
            view = binding.root,
            panelColor = panelColor,
            controlColor = controlColor,
            textColor = textColor,
            mutedColor = mutedColor,
            accentColor = accentColor,
            accentSoftColor = accentSoftColor
        )
    }

    private fun tintViewTree(
        view: View,
        panelColor: Int,
        controlColor: Int,
        textColor: Int,
        mutedColor: Int,
        accentColor: Int,
        accentSoftColor: Int
    ) {
        when (view) {
            is Button -> {
                view.backgroundTintList = ColorStateList.valueOf(controlColor)
                view.setTextColor(accentColor)
            }
            is EditText -> {
                view.backgroundTintList = ColorStateList.valueOf(controlColor)
                view.setTextColor(textColor)
                view.setHintTextColor(mutedColor)
            }
            is Spinner -> {
                view.backgroundTintList = ColorStateList.valueOf(controlColor)
            }
            is SwitchCompat -> {
                view.setTextColor(textColor)
                view.thumbTintList = ColorStateList.valueOf(accentColor)
                view.trackTintList = ColorStateList.valueOf(accentSoftColor)
            }
            is TextView -> {
                if (view.background != null) {
                    view.backgroundTintList = ColorStateList.valueOf(panelColor)
                }
                val textSizeSp = view.textSize / resources.displayMetrics.scaledDensity
                view.setTextColor(if (textSizeSp <= 11.5f) mutedColor else textColor)
            }
            is ViewGroup -> {
                if (view !is ScrollView && view.background != null) {
                    view.backgroundTintList = ColorStateList.valueOf(panelColor)
                }
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                tintViewTree(
                    view = view.getChildAt(index),
                    panelColor = panelColor,
                    controlColor = controlColor,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    accentSoftColor = accentSoftColor
                )
            }
        }
    }

    private fun isNightModeActive(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun savePreferences() {
        val selectedTheme = themeModes.getOrElse(binding.themeSpinner.selectedItemPosition) {
            ThemeMode.SYSTEM
        }
        val selectedSearch = searchEngines.getOrElse(binding.searchEngineSpinner.selectedItemPosition) {
            SearchEngine.DUCKDUCKGO
        }
        val selectedAccent = accentPalettes.getOrElse(binding.accentSpinner.selectedItemPosition) {
            AccentPalette.ORANGE
        }
        val selectedCursorShape = cursorShapes.getOrElse(binding.cursorShapeSpinner.selectedItemPosition) {
            CursorShape.CROSSHAIR
        }
        val selectedFeedbackEffect = bottomFeedbackEffects.getOrElse(
            binding.bottomFeedbackEffectSpinner.selectedItemPosition
        ) {
            BottomScreenFeedbackEffect.AURORA
        }
        val selectedFrameRate = frameRateModes.getOrElse(
            binding.frameRateSpinner.selectedItemPosition
        ) {
            FrameRateMode.FPS_30
        }
        val selectedPillEdge = toolbarPillEdges.getOrElse(
            binding.toolbarPillEdgeSpinner.selectedItemPosition
        ) {
            ToolbarPillEdge.BOTTOM
        }

        val state = BrowserPreferences(
            themeMode = selectedTheme,
            accentPalette = selectedAccent,
            cursorShape = selectedCursorShape,
            bottomScreenFeedbackEffect = selectedFeedbackEffect,
            frameRateMode = selectedFrameRate,
            homepage = BrowserSettingsStore.sanitizedHomepage(
                binding.homepageEdit.text?.toString().orEmpty()
            ),
            searchEngine = selectedSearch,
            javascriptEnabled = binding.jsSwitch.isChecked,
            desktopMode = binding.desktopSwitch.isChecked,
            loadImages = binding.imagesSwitch.isChecked,
            openLinksInNewTab = binding.openLinksInNewTabSwitch.isChecked,
            privateBrowsingEnabled = binding.privateModeSwitch.isChecked,
            blockAllCookies = binding.blockAllCookiesSwitch.isChecked,
            sendDoNotTrack = binding.doNotTrackSwitch.isChecked,
            blockTrackers = binding.blockTrackersSwitch.isChecked,
            stripTrackingParameters = binding.stripTrackingParamsSwitch.isChecked,
            httpsOnlyMode = binding.httpsOnlyModeSwitch.isChecked,
            toolbarPillEdge = selectedPillEdge,
            clearBrowsingDataOnExit = binding.clearDataOnExitSwitch.isChecked
        )

        BrowserSettingsStore.save(this, state)
        BrowserSettingsStore.applyTheme(this)
    }

    private fun copyUrlToClipboard(url: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val copied = runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
        }.isSuccess
        if (!copied) {
            Toast.makeText(this, R.string.url_copy_unavailable_toast, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.settings_link_copied_toast, Toast.LENGTH_SHORT).show()
    }

    private fun finishWithResult(openUrl: String?, clearBrowsingDataNow: Boolean = false) {
        if (isFinishing || isDestroyed) {
            return
        }
        val data = Intent().apply {
            putExtra(EXTRA_SETTINGS_CHANGED, true)
            if (!openUrl.isNullOrBlank()) {
                putExtra(EXTRA_OPEN_URL, openUrl)
            }
            putExtra(EXTRA_CLEAR_BROWSING_DATA_NOW, clearBrowsingDataNow)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(renderHistoryRunnable)
        uiHandler.removeCallbacks(renderBookmarksRunnable)
        runCatching { activeDialog?.dismiss() }
        activeDialog = null
        super.onDestroy()
    }

    private fun showManagedDialog(dialog: AlertDialog) {
        runCatching { activeDialog?.dismiss() }
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog === dialog) {
                activeDialog = null
            }
        }
        runCatching {
            if (!isFinishing && !isDestroyed) {
                dialog.show()
            }
        }
    }

    private fun refreshHistoryData() {
        cachedHistoryEntries = BrowserHistoryStore.list(this)
    }

    private fun refreshBookmarksData() {
        cachedBookmarkEntries = BrowserBookmarkStore.list(this)
    }

    companion object {
        const val EXTRA_SETTINGS_CHANGED = "settings_changed"
        const val EXTRA_OPEN_URL = "open_url"
        const val EXTRA_CLEAR_BROWSING_DATA_NOW = "clear_browsing_data_now"
        private const val FILTER_RENDER_DEBOUNCE_MS = 120L
    }
}
