package com.ayn.magni.data

import androidx.core.net.toUri

object UrlPrivacySanitizer {
    private val exactTrackingKeys = setOf(
        "fbclid",
        "gclid",
        "gbraid",
        "wbraid",
        "dclid",
        "msclkid",
        "mc_cid",
        "mc_eid",
        "mkt_tok",
        "igshid",
        "oly_anon_id",
        "oly_enc_id",
        "vero_conv",
        "vero_id",
        "_hsenc",
        "_hsmi"
    )

    private val keyPrefixes = listOf(
        "utm_",
        "pk_",
        "ga_"
    )

    fun stripTrackingParameters(url: String, enabled: Boolean): String {
        if (!enabled) {
            return url
        }

        val parsed = runCatching { url.toUri() }.getOrNull() ?: return url
        val names = runCatching { parsed.queryParameterNames }.getOrNull() ?: return url
        if (names.isEmpty()) {
            return url
        }

        val keepNames = names.filterNot(::isTrackingParam)
        if (keepNames.size == names.size) {
            return url
        }

        val builder = parsed.buildUpon().clearQuery()
        keepNames.forEach { key ->
            val values = runCatching { parsed.getQueryParameters(key) }.getOrDefault(emptyList())
            values.forEach { value ->
                builder.appendQueryParameter(key, value)
            }
        }
        return builder.build().toString()
    }

    private fun isTrackingParam(name: String): Boolean {
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty()) {
            return false
        }
        if (normalized in exactTrackingKeys) {
            return true
        }
        return keyPrefixes.any { prefix -> normalized.startsWith(prefix) }
    }
}
