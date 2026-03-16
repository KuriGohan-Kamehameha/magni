package com.ayn.magni.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecureBrowserPrefs {
    private const val LEGACY_PREFS_NAME = "browser_prefs"
    private const val SECURE_PREFS_NAME = "browser_prefs_secure"
    private const val KEY_MIGRATED_TO_ENCRYPTED = "__secure_prefs_migrated_v1"
    private const val RESERVED_KEY_KEYSET =
        "__androidx_security_crypto_encrypted_prefs_key_keyset__"
    private const val RESERVED_VALUE_KEYSET =
        "__androidx_security_crypto_encrypted_prefs_value_keyset__"
    private const val MAX_MIGRATION_ENTRIES = 512
    private val RESERVED_ENCRYPTED_KEYS = setOf(
        RESERVED_KEY_KEYSET,
        RESERVED_VALUE_KEYSET
    )
    private val initLock = Any()

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun get(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }

        synchronized(initLock) {
            cachedPrefs?.let { return it }

            val appContext = context.applicationContext
            val encrypted = createEncryptedPrefs(appContext, SECURE_PREFS_NAME)
            if (encrypted != null) {
                migrateLegacyPrefsIfNeeded(appContext, encrypted)
            }
            val resolved = encrypted ?: appContext.getSharedPreferences(
                LEGACY_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            cachedPrefs = resolved
            return resolved
        }
    }

    private fun createEncryptedPrefs(context: Context, prefsName: String): SharedPreferences? {
        return runCatching {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                prefsName,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    private fun migrateLegacyPrefsIfNeeded(context: Context, targetPrefs: SharedPreferences) {
        if (targetPrefs.getBoolean(KEY_MIGRATED_TO_ENCRYPTED, false)) {
            return
        }

        val editor = targetPrefs.edit()

        val legacyEncrypted = createEncryptedPrefs(context, LEGACY_PREFS_NAME)
        copyPrefsEntries(source = legacyEncrypted, editor = editor)

        val legacyPlain = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyPlainKeys = legacyPlain.all.keys
        val legacyPlainIsEncryptedBacking = legacyPlainKeys.any { it in RESERVED_ENCRYPTED_KEYS }
        if (!legacyPlainIsEncryptedBacking) {
            copyPrefsEntries(source = legacyPlain, editor = editor)
        }

        editor.putBoolean(KEY_MIGRATED_TO_ENCRYPTED, true)
        val committed = editor.commit()
        if (!committed) {
            return
        }

        if (legacyPlain.all.isNotEmpty() && !legacyPlainIsEncryptedBacking) {
            legacyPlain.edit().clear().apply()
        }
    }

    private fun copyPrefsEntries(source: SharedPreferences?, editor: SharedPreferences.Editor) {
        if (source == null) {
            return
        }

        val entries = runCatching { source.all }.getOrNull() ?: return
        entries.entries.take(MAX_MIGRATION_ENTRIES).forEach { (key, value) ->
            if (key == KEY_MIGRATED_TO_ENCRYPTED || key in RESERVED_ENCRYPTED_KEYS) {
                return@forEach
            }
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
    }
}
