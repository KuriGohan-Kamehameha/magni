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
    private const val KEY_HISTORY = "history_items"
    private const val MAX_ENTRIES = 250
    // NASA standard: explicit bounds constants
    private const val MAX_URL_LENGTH = 2048
    private const val MAX_TITLE_LENGTH = 256
    private const val MAX_JSON_PARSE_ITERATIONS = 500
    private val historyAccessLock = Any()

    fun list(context: Context): List<HistoryEntry> {
        val raw = safeGetHistoryRaw(context)

        // NASA standard: validate input length before parsing
        if (raw.length > 1_000_000) {
            return emptyList()
        }

        val array = try {
            JSONArray(raw)
        } catch (_: org.json.JSONException) {
            JSONArray()
        }

        val entries = mutableListOf<HistoryEntry>()
        // NASA standard: fixed loop bounds
        val maxIterations = minOf(array.length(), MAX_JSON_PARSE_ITERATIONS)
        for (index in 0 until maxIterations) {
            val item = array.optJSONObject(index) ?: continue
            val rawUrl = item.optString("url")
            // NASA standard: strict input validation
            if (rawUrl.length > MAX_URL_LENGTH) {
                continue
            }
            val url = BrowserSettingsStore.sanitizedNavigableUrl(rawUrl, forceHttps = false) ?: continue
            if (url.isBlank()) {
                continue
            }

            entries += HistoryEntry(
                id = item.optLong("id", 0L).takeIf { it > 0L }
                    ?: generateEntryId(item.optLong("visitedAt", 0L), url),
                title = item.optString("title", "").trim().take(MAX_TITLE_LENGTH),
                url = url,
                visitedAt = item.optLong("visitedAt", 0L)
            )
        }

        return entries.sortedByDescending { it.visitedAt }
    }

    fun add(context: Context, title: String, url: String, visitedAt: Long = System.currentTimeMillis()) {
        return synchronized(historyAccessLock) {
            val normalizedUrl = url.trim()
            val normalizedTitle = title.trim()
            // NASA standard: strict input validation with bounds
            if (normalizedUrl.length > MAX_URL_LENGTH || normalizedTitle.length > MAX_TITLE_LENGTH * 2) {
                return
            }
            val safeUrl = BrowserSettingsStore
                .sanitizedNavigableUrl(normalizedUrl, forceHttps = false)
                ?: return
            if (safeUrl.startsWith("about:blank")) {
                return
            }

            val merged = list(context)
                .filterNot { it.url == safeUrl }
                .toMutableList()

            merged.add(
                0,
                HistoryEntry(
                    id = generateEntryId(visitedAt, safeUrl),
                    title = normalizedTitle.ifBlank { safeUrl }.take(MAX_TITLE_LENGTH),
                    url = safeUrl,
                    visitedAt = visitedAt.coerceAtLeast(0L)
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
        synchronized(historyAccessLock) {
            SecureBrowserPrefs.get(context)
                .edit()
                .putString(KEY_HISTORY, "[]")
                .commit()
        }
    }

    private fun save(context: Context, items: List<HistoryEntry>) {
        // NASA standard: validate list bounds before processing
        val safeItems = items.take(MAX_ENTRIES)
        val output = JSONArray()
        safeItems.forEach { entry ->
            output.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title.take(MAX_TITLE_LENGTH))
                    .put("url", entry.url.take(MAX_URL_LENGTH))
                    .put("visitedAt", entry.visitedAt)
            )
        }

        SecureBrowserPrefs.get(context)
            .edit()
            .putString(KEY_HISTORY, output.toString())
            .commit()
    }

    private fun generateEntryId(timestamp: Long, url: String): Long {
        val safeTimestamp = timestamp.coerceAtLeast(1L)
        // Use a combination of timestamp and URL hash to reduce collisions
        val urlHash = (url.hashCode().toLong() and 0xFFFFFFFFL) xor (url.length.toLong() shl 32)
        val combined = safeTimestamp xor urlHash
        return (combined and Long.MAX_VALUE).takeIf { it != 0L } ?: (safeTimestamp * 31L)
    }

    private fun safeGetHistoryRaw(context: Context): String {
        val prefs = SecureBrowserPrefs.get(context)
        return runCatching { prefs.getString(KEY_HISTORY, "[]") }.getOrNull().orEmpty()
    }
}
