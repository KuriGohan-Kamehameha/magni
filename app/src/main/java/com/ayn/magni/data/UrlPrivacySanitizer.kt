package com.ayn.magni.data

import androidx.core.net.toUri

object UrlPrivacySanitizer {
    private const val MAX_URL_LENGTH = 8192
    private const val MAX_QUERY_PARAMS = 128
    private const val MAX_PARAM_NAME_LENGTH = 128

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
        "_hsmi",
        // Additional tracking parameters for comprehensive privacy
        "ttclid",
        "twclid",
        "li_fat_id",
        "rb_clickid",
        "s_kwcid",
        "ef_id",
        "yclid",
        "_openstat",
        "epik",
        "pp",
        "si",
        "ref",
        "ref_",
        "spm",
        "scm",
        "algo_exp_id",
        "algo_pvid",
        "vt_",
        "ml_",
        "trk_",
        "affiliate_id",
        "affid",
        "clickref",
        "source_caller"
    )

    private val keyPrefixes = arrayOf(
        "utm_",
        "pk_",
        "ga_",
        // Additional tracking prefixes
        "mtm_",
        "piwik_",
        "hsa_",
        "icid_",
        "itm_",
        "mc_",
        "wt.",
        "__hstc",
        "__hssc",
        "__hsfp",
        "_ga_"
    )

    fun stripTrackingParameters(url: String, enabled: Boolean): String {
        if (!enabled) {
            return url
        }
        if (url.length > MAX_URL_LENGTH) {
            return url
        }

        val parsed = runCatching { url.toUri() }.getOrNull() ?: return url
        val names = runCatching { parsed.queryParameterNames }.getOrNull() ?: return url
        if (names.isEmpty()) {
            return url
        }

        val boundedNames = names.take(MAX_QUERY_PARAMS)
        if (boundedNames.isEmpty()) {
            return url
        }

        val keepNames = boundedNames.filterNot(::isTrackingParam)
        if (keepNames.size == boundedNames.size && names.size <= MAX_QUERY_PARAMS) {
            return url
        }

        val builder = parsed.buildUpon().clearQuery()
        keepNames.forEach { key ->
            val values = runCatching { parsed.getQueryParameters(key) }.getOrDefault(emptyList())
            values.forEach { value ->
                builder.appendQueryParameter(key, value)
            }
        }
        return runCatching { builder.build().toString() }.getOrDefault(url)
    }

    private fun isTrackingParam(name: String): Boolean {
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty() || normalized.length > MAX_PARAM_NAME_LENGTH) {
            return false
        }
        if (normalized in exactTrackingKeys) {
            return true
        }
        return keyPrefixes.any { prefix -> normalized.startsWith(prefix) }
    }
}
