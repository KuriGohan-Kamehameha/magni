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
    private const val PREFS_NAME = "browser_prefs"
    private const val KEY_SESSION = "tab_session"
    private const val MAX_SESSION_TABS = 10
    private const val MAX_TITLE_LENGTH = 140

    fun load(context: Context): BrowserSession? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SESSION, null)
            ?: return null

        val objectValue = try {
            JSONObject(raw)
        } catch (_: Throwable) {
            return null
        }

        val tabsArray = objectValue.optJSONArray("tabs") ?: JSONArray()
        val tabs = mutableListOf<SessionTab>()
        val seenUrls = HashSet<String>()
        for (index in 0 until tabsArray.length()) {
            val item = tabsArray.optJSONObject(index) ?: continue
            val url = item.optString("url").trim()
            if (url.isBlank()) {
                continue
            }
            if (!seenUrls.add(url)) {
                continue
            }
            tabs += SessionTab(
                url = url,
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
            if (normalizedUrl.isBlank()) {
                null
            } else if (!seenUrls.add(normalizedUrl)) {
                null
            } else {
                tab.copy(
                    url = normalizedUrl,
                    title = tab.title.trim().take(MAX_TITLE_LENGTH)
                )
            }
        }.take(MAX_SESSION_TABS)

        if (sanitizedTabs.isEmpty()) {
            clear(context)
            return
        }

        val outputTabs = JSONArray()
        sanitizedTabs.forEach { tab ->
            outputTabs.put(
                JSONObject()
                    .put("url", tab.url)
                    .put("title", tab.title)
                    .put("pinned", tab.pinned)
            )
        }

        val output = JSONObject()
            .put("tabs", outputTabs)
            .put("currentIndex", currentIndex.coerceIn(0, sanitizedTabs.lastIndex))

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSION, output.toString())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION)
            .apply()
    }
}
