package com.ayn.magni.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkEntry(
    val id: Long,
    val title: String,
    val url: String,
    val addedAt: Long,
    val favorite: Boolean
)

object BrowserBookmarkStore {
    private const val PREFS_NAME = "browser_prefs"
    private const val KEY_BOOKMARKS = "bookmark_items"
    private const val MAX_ENTRIES = 500
    // NASA standard: explicit bounds constants
    private const val MAX_URL_LENGTH = 2048
    private const val MAX_TITLE_LENGTH = 256
    private const val MAX_JSON_PARSE_ITERATIONS = 600
    private val bookmarkAccessLock = Any()

    fun list(context: Context): List<BookmarkEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BOOKMARKS, "[]")
            .orEmpty()

        // NASA standard: validate input length before parsing
        if (raw.length > 2_000_000) {
            return emptyList()
        }

        val array = try {
            JSONArray(raw)
        } catch (_: org.json.JSONException) {
            JSONArray()
        }

        val entries = mutableListOf<BookmarkEntry>()
        // NASA standard: fixed loop bounds
        val maxIterations = minOf(array.length(), MAX_JSON_PARSE_ITERATIONS)
        for (index in 0 until maxIterations) {
            val item = array.optJSONObject(index) ?: continue
            val rawUrl = item.optString("url")
            // NASA standard: strict input validation
            if (rawUrl.length > MAX_URL_LENGTH) {
                continue
            }
            val url = sanitizeBookmarkUrl(rawUrl) ?: continue
            if (url.isBlank()) {
                continue
            }

            entries += BookmarkEntry(
                id = item.optLong("id", 0L).takeIf { it > 0L }
                    ?: generateBookmarkId(item.optLong("addedAt", 0L), url),
                title = item.optString("title", "").trim().take(MAX_TITLE_LENGTH),
                url = url,
                addedAt = item.optLong("addedAt", 0L).coerceAtLeast(0L),
                favorite = item.optBoolean("favorite", false)
            )
        }

        return entries
            .distinctBy { it.url }
            .sortedWith(compareByDescending<BookmarkEntry> { it.favorite }.thenByDescending { it.addedAt })
    }

    fun findByUrl(context: Context, url: String): BookmarkEntry? {
        val target = url.trim()
        if (target.isBlank()) {
            return null
        }
        return list(context).firstOrNull { it.url == target }
    }

    fun upsert(
        context: Context,
        title: String,
        url: String,
        favoriteOverride: Boolean? = null
    ): BookmarkEntry? {
        return synchronized(bookmarkAccessLock) {
            val target = sanitizeBookmarkUrl(url) ?: return null
            val now = System.currentTimeMillis()
            val existing = findByUrl(context, target)
            val resolvedTitle = title.ifBlank {
                existing?.title?.ifBlank { target } ?: target
            }

            val updated = if (existing == null) {
                BookmarkEntry(
                    id = generateBookmarkId(now, target),
                    title = resolvedTitle,
                    url = target,
                    addedAt = now,
                    favorite = favoriteOverride ?: false
                )
            } else {
                existing.copy(
                    title = resolvedTitle,
                    favorite = favoriteOverride ?: existing.favorite
                )
            }

            val merged = list(context)
                .filterNot { it.url == target }
                .toMutableList()
            merged.add(0, updated)
            save(context, merged.take(MAX_ENTRIES))
            updated
        }
    }

    fun toggleFavorite(context: Context, title: String, url: String): Boolean {
        return synchronized(bookmarkAccessLock) {
            val target = sanitizeBookmarkUrl(url) ?: return false
            val existing = findByUrl(context, target) ?: return false
            val nextFavorite = !existing.favorite
            val updated = existing.copy(favorite = nextFavorite)
            val merged = list(context)
                .filterNot { it.url == target }
                .toMutableList()
            merged.add(0, updated)
            save(context, merged.take(MAX_ENTRIES))
            nextFavorite
        }
    }

    fun remove(context: Context, id: Long) {
        synchronized(bookmarkAccessLock) {
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
            .putString(KEY_BOOKMARKS, "[]")
            .apply()
    }

    private fun save(context: Context, items: List<BookmarkEntry>) {
        // NASA standard: validate list bounds before processing
        val safeItems = items.take(MAX_ENTRIES)
        val output = JSONArray()
        safeItems.forEach { entry ->
            output.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title.take(MAX_TITLE_LENGTH))
                    .put("url", entry.url.take(MAX_URL_LENGTH))
                    .put("addedAt", entry.addedAt)
                    .put("favorite", entry.favorite)
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOKMARKS, output.toString())
            .apply()
    }

    private fun sanitizeBookmarkUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("about:blank", ignoreCase = true)) {
            return null
        }
        return BrowserSettingsStore.sanitizedNavigableUrl(trimmed, forceHttps = false)
    }

    private fun generateBookmarkId(timestamp: Long, url: String): Long {
        val safeTimestamp = timestamp.coerceAtLeast(1L)
        val urlHash = url.hashCode().toLong() and 0xFFFFFFFFL
        return ((safeTimestamp shl 16) xor (urlHash and 0xFFFFL)) and Long.MAX_VALUE
    }
}
