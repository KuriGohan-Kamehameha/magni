package com.ayn.magni

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
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
import com.ayn.magni.data.SearchEngine
import com.ayn.magni.data.ThemeMode
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
    private val searchEngines = SearchEngine.entries.toList()
    private var historyFilterQuery = ""
    private var bookmarksFilterQuery = ""

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

        binding.searchEngineSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            searchEngines.map { it.label }
        )
    }

    private fun loadPreferencesIntoUi() {
        val prefs = BrowserSettingsStore.load(this)

        binding.themeSpinner.setSelection(themeModes.indexOf(prefs.themeMode).coerceAtLeast(0))
        binding.accentSpinner.setSelection(
            accentPalettes.indexOf(prefs.accentPalette).coerceAtLeast(0)
        )
        binding.searchEngineSpinner.setSelection(searchEngines.indexOf(prefs.searchEngine).coerceAtLeast(0))

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
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_history)
                .setMessage(R.string.settings_clear_history_confirm)
                .setPositiveButton(R.string.settings_clear_history) { _, _ ->
                    BrowserHistoryStore.clear(this)
                    renderHistory()
                }
                .setNegativeButton(R.string.url_dialog_cancel, null)
                .show()
        }

        binding.clearBookmarksButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_bookmarks)
                .setMessage(R.string.settings_clear_bookmarks_confirm)
                .setPositiveButton(R.string.settings_clear_bookmarks) { _, _ ->
                    BrowserBookmarkStore.clear(this)
                    renderBookmarks()
                }
                .setNegativeButton(R.string.url_dialog_cancel, null)
                .show()
        }

        binding.clearBrowsingDataNowButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_browsing_data_now)
                .setMessage(R.string.settings_clear_browsing_data_confirm)
                .setPositiveButton(R.string.settings_clear_browsing_data_now) { _, _ ->
                    savePreferences()
                    finishWithResult(openUrl = null, clearBrowsingDataNow = true)
                }
                .setNegativeButton(R.string.url_dialog_cancel, null)
                .show()
        }
    }

    private fun configureFilters() {
        binding.historySearchEdit.doAfterTextChanged { editable ->
            historyFilterQuery = editable?.toString().orEmpty().trim()
            renderHistory()
        }

        binding.bookmarksSearchEdit.doAfterTextChanged { editable ->
            bookmarksFilterQuery = editable?.toString().orEmpty().trim()
            renderBookmarks()
        }
    }

    private fun renderHistory() {
        val entries = BrowserHistoryStore.list(this).take(40)
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
                savePreferences()
                finishWithResult(entry.url)
            }

            itemBinding.deleteButton.setOnClickListener {
                BrowserHistoryStore.remove(this, entry.id)
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
        val entries = BrowserBookmarkStore.list(this).take(80)
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
                savePreferences()
                finishWithResult(entry.url)
            }

            itemBinding.favoriteButton.setOnClickListener {
                BrowserBookmarkStore.upsert(
                    context = this,
                    title = entry.title.ifBlank { entry.url },
                    url = entry.url,
                    favoriteOverride = !entry.favorite
                )
                renderBookmarks()
            }

            itemBinding.deleteButton.setOnClickListener {
                BrowserBookmarkStore.remove(this, entry.id)
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

        val state = BrowserPreferences(
            themeMode = selectedTheme,
            accentPalette = selectedAccent,
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
            clearBrowsingDataOnExit = binding.clearDataOnExitSwitch.isChecked
        )

        BrowserSettingsStore.save(this, state)
        BrowserSettingsStore.applyTheme(this)
    }

    private fun copyUrlToClipboard(url: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
        Toast.makeText(this, R.string.settings_link_copied_toast, Toast.LENGTH_SHORT).show()
    }

    private fun finishWithResult(openUrl: String?, clearBrowsingDataNow: Boolean = false) {
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

    companion object {
        const val EXTRA_SETTINGS_CHANGED = "settings_changed"
        const val EXTRA_OPEN_URL = "open_url"
        const val EXTRA_CLEAR_BROWSING_DATA_NOW = "clear_browsing_data_now"
    }
}
