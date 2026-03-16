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

enum class CursorShape(val id: String, val label: String) {
    CROSSHAIR("crosshair", "Crosshair"),
    DOT("dot", "Dot"),
    RING("ring", "Ring");

    companion object {
        fun fromId(id: String?): CursorShape {
            return entries.firstOrNull { it.id == id } ?: CROSSHAIR
        }
    }
}

enum class BottomScreenFeedbackEffect(val id: String) {
    AURORA("aurora"),
    RING("ring"),
    COMET("comet"),
    PIXEL("pixel");

    companion object {
        fun fromId(id: String?): BottomScreenFeedbackEffect {
            return entries.firstOrNull { it.id == id } ?: AURORA
        }
    }
}

enum class FrameRateMode(val id: String, val fps: Int) {
    FPS_30("30", 30),
    FPS_60("60", 60),
    FPS_120("120", 120);

    fun frameIntervalMs(): Long {
        return when (this) {
            FPS_30 -> 33L
            FPS_60 -> 16L
            FPS_120 -> 8L
        }
    }

    companion object {
        fun fromId(id: String?): FrameRateMode {
            return entries.firstOrNull { it.id == id } ?: FPS_30
        }
    }
}

enum class ToolbarPillEdge(val id: String) {
    TOP("top"),
    BOTTOM("bottom"),
    LEFT("left"),
    RIGHT("right");

    companion object {
        fun fromId(id: String?): ToolbarPillEdge {
            return entries.firstOrNull { it.id == id } ?: BOTTOM
        }
    }
}

data class BrowserPreferences(
    val themeMode: ThemeMode,
    val accentPalette: AccentPalette,
    val cursorShape: CursorShape,
    val bottomScreenFeedbackEffect: BottomScreenFeedbackEffect,
    val frameRateMode: FrameRateMode,
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
    val toolbarPillEdge: ToolbarPillEdge,
    val clearBrowsingDataOnExit: Boolean
)

object BrowserSettingsStore {
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_PALETTE = "accent_palette"
    private const val KEY_CURSOR_SHAPE = "cursor_shape"
    private const val KEY_BOTTOM_FEEDBACK_EFFECT = "bottom_feedback_effect"
    private const val KEY_FRAME_RATE_MODE = "frame_rate_mode"
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
    private const val KEY_TOOLBAR_PILL_EDGE = "toolbar_pill_edge"
    private const val KEY_CLEAR_DATA_ON_EXIT = "clear_data_on_exit"
    private const val MAX_NAVIGABLE_URL_LENGTH = 4096
    private const val MAX_HOST_LENGTH = 253

    const val DEFAULT_HOMEPAGE_URL = "https://github.com/kurigohan-kamehameha/magni"

    fun load(context: Context): BrowserPreferences {
        val prefs = SecureBrowserPrefs.get(context)
        return BrowserPreferences(
            themeMode = ThemeMode.fromId(
                safeGetString(prefs, KEY_THEME_MODE, ThemeMode.SYSTEM.id)
            ),
            accentPalette = AccentPalette.fromId(
                safeGetString(prefs, KEY_ACCENT_PALETTE, AccentPalette.ORANGE.id)
            ),
            cursorShape = CursorShape.fromId(
                safeGetString(prefs, KEY_CURSOR_SHAPE, CursorShape.CROSSHAIR.id)
            ),
            bottomScreenFeedbackEffect = BottomScreenFeedbackEffect.fromId(
                safeGetString(
                    prefs,
                    KEY_BOTTOM_FEEDBACK_EFFECT,
                    BottomScreenFeedbackEffect.AURORA.id
                )
            ),
            frameRateMode = FrameRateMode.fromId(
                safeGetString(prefs, KEY_FRAME_RATE_MODE, FrameRateMode.FPS_30.id)
            ),
            homepage = sanitizedHomepage(
                safeGetString(prefs, KEY_HOMEPAGE, DEFAULT_HOMEPAGE_URL).orEmpty()
            ),
            searchEngine = SearchEngine.fromId(
                safeGetString(prefs, KEY_SEARCH_ENGINE, SearchEngine.DUCKDUCKGO.id)
            ),
            javascriptEnabled = safeGetBoolean(prefs, KEY_JS_ENABLED, false),
            desktopMode = safeGetBoolean(prefs, KEY_DESKTOP_MODE, false),
            loadImages = safeGetBoolean(prefs, KEY_LOAD_IMAGES, true),
            openLinksInNewTab = safeGetBoolean(prefs, KEY_OPEN_LINKS_IN_NEW_TAB, false),
            privateBrowsingEnabled = safeGetBoolean(prefs, KEY_PRIVATE_BROWSING, false),
            blockAllCookies = safeGetBoolean(prefs, KEY_BLOCK_ALL_COOKIES, false),
            sendDoNotTrack = safeGetBoolean(prefs, KEY_DO_NOT_TRACK, true),
            blockTrackers = safeGetBoolean(prefs, KEY_BLOCK_TRACKERS, true),
            stripTrackingParameters = safeGetBoolean(prefs, KEY_STRIP_TRACKING_PARAMETERS, true),
            httpsOnlyMode = safeGetBoolean(prefs, KEY_HTTPS_ONLY_MODE, true),
            toolbarPillEdge = ToolbarPillEdge.fromId(
                safeGetString(prefs, KEY_TOOLBAR_PILL_EDGE, ToolbarPillEdge.BOTTOM.id)
            ),
            clearBrowsingDataOnExit = safeGetBoolean(prefs, KEY_CLEAR_DATA_ON_EXIT, false)
        )
    }

    fun save(context: Context, state: BrowserPreferences) {
        SecureBrowserPrefs.get(context)
            .edit()
            .putString(KEY_THEME_MODE, state.themeMode.id)
            .putString(KEY_ACCENT_PALETTE, state.accentPalette.id)
            .putString(KEY_CURSOR_SHAPE, state.cursorShape.id)
            .putString(KEY_BOTTOM_FEEDBACK_EFFECT, state.bottomScreenFeedbackEffect.id)
            .putString(KEY_FRAME_RATE_MODE, state.frameRateMode.id)
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
            .putString(KEY_TOOLBAR_PILL_EDGE, state.toolbarPillEdge.id)
            .putBoolean(KEY_CLEAR_DATA_ON_EXIT, state.clearBrowsingDataOnExit)
                .commit()
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
            return DEFAULT_HOMEPAGE_URL
        }

        return sanitizedNavigableUrl(value, forceHttps = true) ?: DEFAULT_HOMEPAGE_URL
    }

    fun sanitizedNavigableUrl(raw: String, forceHttps: Boolean = true): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > MAX_NAVIGABLE_URL_LENGTH) {
            return null
        }

        val normalized = when {
            value.startsWith("//") -> "https:$value"
            value.contains("://") -> value
            else -> "https://${value.removePrefix("//")}"
        }
        if (normalized.length > MAX_NAVIGABLE_URL_LENGTH) {
            return null
        }

        val parsed = runCatching { normalized.toUri() }.getOrNull() ?: return null
        val secureUri = when {
            parsed.scheme.equals("https", ignoreCase = true) -> parsed
            parsed.scheme.equals("http", ignoreCase = true) -> {
                if (forceHttps) {
                    parsed.buildUpon().scheme("https").build()
                } else {
                    parsed
                }
            }
            else -> return null
        }

        val host = secureUri.host?.trim()?.trimEnd('.')
        if (host.isNullOrBlank() || host.length > MAX_HOST_LENGTH || host.any { it.isWhitespace() }) {
            return null
        }

        val output = secureUri.toString()
        if (output.length > MAX_NAVIGABLE_URL_LENGTH) {
            return null
        }
        return output
    }

    private fun safeGetString(
        prefs: android.content.SharedPreferences,
        key: String,
        fallback: String
    ): String {
        return runCatching { prefs.getString(key, fallback) ?: fallback }.getOrDefault(fallback)
    }

    private fun safeGetBoolean(
        prefs: android.content.SharedPreferences,
        key: String,
        fallback: Boolean
    ): Boolean {
        return runCatching { prefs.getBoolean(key, fallback) }.getOrDefault(fallback)
    }
}
