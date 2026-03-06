package com.ayn.magni.data

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri

enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    DARK_OLED("dark_oled"),
    ELECTRIC_PURPLE("electric_purple"),
    DSI_CLASSIC("dsi_classic"),
    DSI_CLASSIC_DARK("dsi_classic_dark");

    companion object {
        fun fromId(id: String?): ThemeMode {
            return entries.firstOrNull { it.id == id } ?: SYSTEM
        }
    }
}

enum class AccentPalette(
    val id: String,
    val label: String,
    private val solidHex: String,
    private val softHex: String
) {
    ORANGE("orange", "Orange", "#EF8B2F", "#44EF8B2F"),
    CYAN("cyan", "Cyan", "#30B7D9", "#4430B7D9"),
    MINT("mint", "Mint", "#57C77A", "#4457C77A"),
    MAGENTA("magenta", "Magenta", "#C86ED9", "#44C86ED9"),
    RED("red", "Red", "#E96C5F", "#44E96C5F");

    fun solidColorInt(): Int = Color.parseColor(solidHex)

    fun softColorInt(): Int = Color.parseColor(softHex)

    companion object {
        fun fromId(id: String?): AccentPalette {
            return entries.firstOrNull { it.id == id } ?: ORANGE
        }
    }
}

enum class SearchEngine(val id: String, val label: String, private val queryPattern: String) {
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s"),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s");

    fun queryUrl(encodedQuery: String): String = queryPattern.format(encodedQuery)

    companion object {
        fun fromId(id: String?): SearchEngine {
            return entries.firstOrNull { it.id == id } ?: DUCKDUCKGO
        }
    }
}

data class BrowserPreferences(
    val themeMode: ThemeMode,
    val accentPalette: AccentPalette,
    val homepage: String,
    val searchEngine: SearchEngine,
    val javascriptEnabled: Boolean,
    val desktopMode: Boolean,
    val loadImages: Boolean,
    val openLinksInNewTab: Boolean,
    val privateBrowsingEnabled: Boolean,
    val blockAllCookies: Boolean,
    val sendDoNotTrack: Boolean,
    val blockTrackers: Boolean,
    val stripTrackingParameters: Boolean,
    val httpsOnlyMode: Boolean,
    val clearBrowsingDataOnExit: Boolean
)

object BrowserSettingsStore {
    private const val PREFS_NAME = "browser_prefs"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_PALETTE = "accent_palette"
    private const val KEY_HOMEPAGE = "homepage"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_JS_ENABLED = "js_enabled"
    private const val KEY_DESKTOP_MODE = "desktop_mode"
    private const val KEY_LOAD_IMAGES = "load_images"
    private const val KEY_OPEN_LINKS_IN_NEW_TAB = "open_links_new_tab"
    private const val KEY_PRIVATE_BROWSING = "private_browsing"
    private const val KEY_BLOCK_ALL_COOKIES = "block_all_cookies"
    private const val KEY_DO_NOT_TRACK = "do_not_track"
    private const val KEY_BLOCK_TRACKERS = "block_trackers"
    private const val KEY_STRIP_TRACKING_PARAMETERS = "strip_tracking_parameters"
    private const val KEY_HTTPS_ONLY_MODE = "https_only_mode"
    private const val KEY_CLEAR_DATA_ON_EXIT = "clear_data_on_exit"

    const val BUILTIN_HOME = "file:///android_asset/start_page.html"

    fun load(context: Context): BrowserPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return BrowserPreferences(
            themeMode = ThemeMode.fromId(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.id)),
            accentPalette = AccentPalette.fromId(
                prefs.getString(KEY_ACCENT_PALETTE, AccentPalette.ORANGE.id)
            ),
            homepage = prefs.getString(KEY_HOMEPAGE, BUILTIN_HOME).orEmpty(),
            searchEngine = SearchEngine.fromId(
                prefs.getString(KEY_SEARCH_ENGINE, SearchEngine.DUCKDUCKGO.id)
            ),
            javascriptEnabled = prefs.getBoolean(KEY_JS_ENABLED, true),
            desktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, false),
            loadImages = prefs.getBoolean(KEY_LOAD_IMAGES, true),
            openLinksInNewTab = prefs.getBoolean(KEY_OPEN_LINKS_IN_NEW_TAB, false),
            privateBrowsingEnabled = prefs.getBoolean(KEY_PRIVATE_BROWSING, false),
            blockAllCookies = prefs.getBoolean(KEY_BLOCK_ALL_COOKIES, false),
            sendDoNotTrack = prefs.getBoolean(KEY_DO_NOT_TRACK, true),
            blockTrackers = prefs.getBoolean(KEY_BLOCK_TRACKERS, true),
            stripTrackingParameters = prefs.getBoolean(KEY_STRIP_TRACKING_PARAMETERS, true),
            httpsOnlyMode = prefs.getBoolean(KEY_HTTPS_ONLY_MODE, true),
            clearBrowsingDataOnExit = prefs.getBoolean(KEY_CLEAR_DATA_ON_EXIT, false)
        )
    }

    fun save(context: Context, state: BrowserPreferences) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, state.themeMode.id)
            .putString(KEY_ACCENT_PALETTE, state.accentPalette.id)
            .putString(KEY_HOMEPAGE, state.homepage)
            .putString(KEY_SEARCH_ENGINE, state.searchEngine.id)
            .putBoolean(KEY_JS_ENABLED, state.javascriptEnabled)
            .putBoolean(KEY_DESKTOP_MODE, state.desktopMode)
            .putBoolean(KEY_LOAD_IMAGES, state.loadImages)
            .putBoolean(KEY_OPEN_LINKS_IN_NEW_TAB, state.openLinksInNewTab)
            .putBoolean(KEY_PRIVATE_BROWSING, state.privateBrowsingEnabled)
            .putBoolean(KEY_BLOCK_ALL_COOKIES, state.blockAllCookies)
            .putBoolean(KEY_DO_NOT_TRACK, state.sendDoNotTrack)
            .putBoolean(KEY_BLOCK_TRACKERS, state.blockTrackers)
            .putBoolean(KEY_STRIP_TRACKING_PARAMETERS, state.stripTrackingParameters)
            .putBoolean(KEY_HTTPS_ONLY_MODE, state.httpsOnlyMode)
            .putBoolean(KEY_CLEAR_DATA_ON_EXIT, state.clearBrowsingDataOnExit)
            .apply()
    }

    fun applyTheme(context: Context) {
        val mode = when (load(context).themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT, ThemeMode.DSI_CLASSIC -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK,
            ThemeMode.DARK_OLED,
            ThemeMode.ELECTRIC_PURPLE,
            ThemeMode.DSI_CLASSIC_DARK -> {
                AppCompatDelegate.MODE_NIGHT_YES
            }
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isOledTheme(context: Context): Boolean {
        return load(context).themeMode == ThemeMode.DARK_OLED
    }

    fun sanitizedHomepage(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) {
            return BUILTIN_HOME
        }

        if (value.startsWith(BUILTIN_HOME, ignoreCase = true)) {
            return BUILTIN_HOME
        }

        return sanitizedNavigableUrl(value) ?: BUILTIN_HOME
    }

    fun sanitizedNavigableUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) {
            return null
        }

        val normalized = when {
            value.startsWith("//") -> "https:$value"
            value.contains("://") -> value
            else -> "https://${value.removePrefix("//")}"
        }

        val parsed = runCatching { normalized.toUri() }.getOrNull() ?: return null
        val secureUri = when {
            parsed.scheme.equals("https", ignoreCase = true) -> parsed
            parsed.scheme.equals("http", ignoreCase = true) -> {
                parsed.buildUpon().scheme("https").build()
            }
            else -> return null
        }

        if (secureUri.host.isNullOrBlank()) {
            return null
        }

        return secureUri.toString()
    }
}
