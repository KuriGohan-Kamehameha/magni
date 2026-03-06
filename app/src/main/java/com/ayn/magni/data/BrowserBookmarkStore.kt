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

    fun list(context: Context): List<BookmarkEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BOOKMARKS, "[]")
            .orEmpty()

        val array = try {
            JSONArray(raw)
        } catch (_: Throwable) {
            JSONArray()
        }

        val entries = mutableListOf<BookmarkEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val rawUrl = item.optString("url")
            val url = sanitizeBookmarkUrl(rawUrl) ?: continue
            if (url.isBlank()) {
                continue
            }

            entries += BookmarkEntry(
                id = item.optLong("id", 0L),
                title = item.optString("title", "").trim(),
                url = url,
                addedAt = item.optLong("addedAt", 0L),
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
        val target = sanitizeBookmarkUrl(url) ?: return null
        val now = System.currentTimeMillis()
        val existing = findByUrl(context, target)
        val resolvedTitle = title.ifBlank {
            existing?.title?.ifBlank { target } ?: target
        }

        val updated = if (existing == null) {
            BookmarkEntry(
                id = now,
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
        return updated
    }

    fun toggleFavorite(context: Context, title: String, url: String): Boolean {
        val target = sanitizeBookmarkUrl(url) ?: return false
        val existing = findByUrl(context, target)
        val nextFavorite = existing?.favorite?.not() ?: true
        upsert(context, title = title, url = target, favoriteOverride = nextFavorite)
        return nextFavorite
    }

    fun remove(context: Context, id: Long) {
        val updated = list(context).filterNot { it.id == id }
        save(context, updated)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOKMARKS, "[]")
            .apply()
    }

    private fun save(context: Context, items: List<BookmarkEntry>) {
        val output = JSONArray()
        items.forEach { entry ->
            output.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("url", entry.url)
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
        if (trimmed.startsWith("about:blank", ignoreCase = true) ||
            trimmed.startsWith(BrowserSettingsStore.BUILTIN_HOME, ignoreCase = true)
        ) {
            return null
        }
        return BrowserSettingsStore.sanitizedNavigableUrl(trimmed)
    }
}
