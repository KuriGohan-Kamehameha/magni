package com.ayn.magni.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: Long,
    val title: String,
    val url: String,
    val visitedAt: Long
)

object BrowserHistoryStore {
    private const val PREFS_NAME = "browser_prefs"
    private const val KEY_HISTORY = "history_items"
    private const val MAX_ENTRIES = 250
    private val historyAccessLock = Any()

    fun list(context: Context): List<HistoryEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]")
            .orEmpty()

        val array = try {
            JSONArray(raw)
        } catch (_: Throwable) {
            JSONArray()
        }

        val entries = mutableListOf<HistoryEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val url = BrowserSettingsStore.sanitizedNavigableUrl(item.optString("url")) ?: continue
            if (url.isBlank()) {
                continue
            }

            entries += HistoryEntry(
                id = item.optLong("id", 0L).takeIf { it > 0L }
                    ?: generateEntryId(item.optLong("visitedAt", 0L), url),
                title = item.optString("title", "").trim(),
                url = url,
                visitedAt = item.optLong("visitedAt", 0L)
            )
        }

        return entries.sortedByDescending { it.visitedAt }
    }

    fun add(context: Context, title: String, url: String, visitedAt: Long = System.currentTimeMillis()) {
        return synchronized(historyAccessLock) {
            val safeUrl = BrowserSettingsStore.sanitizedNavigableUrl(url) ?: return
            if (safeUrl.startsWith("about:blank") ||
                safeUrl.startsWith(BrowserSettingsStore.BUILTIN_HOME)
            ) {
                return
            }

            val merged = list(context)
                .filterNot { it.url == safeUrl }
                .toMutableList()

            merged.add(
                0,
                HistoryEntry(
                    id = generateEntryId(visitedAt, safeUrl),
                    title = title.ifBlank { safeUrl }.take(140),
                    url = safeUrl,
                    visitedAt = visitedAt
                )
            )

            save(context, merged.take(MAX_ENTRIES))
        }
    }

    fun remove(context: Context, id: Long) {
        return synchronized(historyAccessLock) {
            val updated = list(context).toMutableList()
            val index = updated.indexOfFirst { it.id == id }
            if (index >= 0) {
                updated.removeAt(index)
                save(context, updated)
            }
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, "[]")
            .apply()
    }

    private fun save(context: Context, items: List<HistoryEntry>) {
        val output = JSONArray()
        items.forEach { entry ->
            output.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("url", entry.url)
                    .put("visitedAt", entry.visitedAt)
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, output.toString())
            .apply()
    }

    private fun generateEntryId(timestamp: Long, url: String): Long {
        val safeTimestamp = timestamp.coerceAtLeast(1L)
        // Use a combination of timestamp and URL hash to reduce collisions
        val urlHash = url.hashCode().toLong() and 0xFFFFFFFFL
        val combined = safeTimestamp xor urlHash
        return (combined and Long.MAX_VALUE).takeIf { it != 0L } ?: safeTimestamp
    }
}
