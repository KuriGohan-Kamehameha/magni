package com.ayn.magni.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SessionTab(
    val url: String,
    val title: String,
    val pinned: Boolean
)

data class BrowserSession(
    val tabs: List<SessionTab>,
    val currentIndex: Int
)

object BrowserSessionStore {
    private const val KEY_SESSION = "tab_session"
    private const val MAX_SESSION_TABS = 10
    private const val MAX_TITLE_LENGTH = 140
    // NASA standard: explicit bounds for input validation
    private const val MAX_URL_LENGTH = 2048
    private const val MAX_JSON_LENGTH = 100_000

    fun load(context: Context): BrowserSession? {
        val raw = safeGetString(context, KEY_SESSION)
            ?: return null

        // NASA standard: validate input length before parsing
        if (raw.length > MAX_JSON_LENGTH) {
            return null
        }

        val objectValue = try {
            JSONObject(raw)
        } catch (_: org.json.JSONException) {
            return null
        }

        val tabsArray = objectValue.optJSONArray("tabs") ?: JSONArray()
        val tabs = mutableListOf<SessionTab>()
        val seenUrls = HashSet<String>()
        // NASA standard: fixed loop bounds
        val maxIterations = minOf(tabsArray.length(), MAX_SESSION_TABS * 2)
        for (index in 0 until maxIterations) {
            val item = tabsArray.optJSONObject(index) ?: continue
            val rawUrl = item.optString("url").trim()
            // NASA standard: validate input bounds
            if (rawUrl.isBlank() || rawUrl.length > MAX_URL_LENGTH) {
                continue
            }
            val sanitizedUrl = BrowserSettingsStore.sanitizedNavigableUrl(
                rawUrl, forceHttps = false
            ) ?: continue
            if (!seenUrls.add(sanitizedUrl)) {
                continue
            }
            tabs += SessionTab(
                url = sanitizedUrl,
                title = item.optString("title", "").trim().take(MAX_TITLE_LENGTH),
                pinned = item.optBoolean("pinned", false)
            )
            if (tabs.size >= MAX_SESSION_TABS) {
                break
            }
        }

        if (tabs.isEmpty()) {
            return null
        }

        val currentIndex = objectValue.optInt("currentIndex", 0).coerceIn(0, tabs.lastIndex)
        return BrowserSession(tabs = tabs, currentIndex = currentIndex)
    }

    fun save(context: Context, tabs: List<SessionTab>, currentIndex: Int) {
        val seenUrls = HashSet<String>()
        val sanitizedTabs = tabs.mapNotNull { tab ->
            val normalizedUrl = tab.url.trim()
            // NASA standard: validate input bounds
            if (normalizedUrl.isBlank() || normalizedUrl.length > MAX_URL_LENGTH) {
                null
            } else {
                val safeUrl = BrowserSettingsStore.sanitizedNavigableUrl(
                    normalizedUrl, forceHttps = false
                ) ?: return@mapNotNull null
                if (!seenUrls.add(safeUrl)) {
                    null
                } else {
                    tab.copy(
                        url = safeUrl,
                        title = tab.title.trim().take(MAX_TITLE_LENGTH)
                    )
                }
            }
        }.take(MAX_SESSION_TABS)

        if (sanitizedTabs.isEmpty()) {
            clear(context)
            return
        }

        var candidateTabs = sanitizedTabs
        var outputJson = encodeSessionJson(candidateTabs, currentIndex)
        while (outputJson.length > MAX_JSON_LENGTH && candidateTabs.size > 1) {
            candidateTabs = candidateTabs.dropLast(1)
            outputJson = encodeSessionJson(candidateTabs, currentIndex)
        }
        if (outputJson.length > MAX_JSON_LENGTH) {
            clear(context)
            return
        }

        // NASA standard: use commit() for atomic writes to ensure data integrity
        SecureBrowserPrefs.get(context)
            .edit()
            .putString(KEY_SESSION, outputJson)
            .commit()
    }

    fun clear(context: Context) {
        SecureBrowserPrefs.get(context)
            .edit()
            .remove(KEY_SESSION)
            .commit()
    }

    private fun safeGetString(context: Context, key: String): String? {
        val prefs = SecureBrowserPrefs.get(context)
        return runCatching { prefs.getString(key, null) }.getOrNull()
    }

    private fun encodeSessionJson(tabs: List<SessionTab>, currentIndex: Int): String {
        val outputTabs = JSONArray()
        tabs.forEach { tab ->
            outputTabs.put(
                JSONObject()
                    .put("url", tab.url.take(MAX_URL_LENGTH))
                    .put("title", tab.title.take(MAX_TITLE_LENGTH))
                    .put("pinned", tab.pinned)
            )
        }

        return JSONObject()
            .put("tabs", outputTabs)
            .put("currentIndex", currentIndex.coerceIn(0, tabs.lastIndex))
            .toString()
    }
}
