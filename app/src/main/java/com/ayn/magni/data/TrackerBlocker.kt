package com.ayn.magni.data

import android.net.Uri

object TrackerBlocker {
    private val trackerDomains = setOf(
        "2mdn.net",
        "adform.net",
        "adnxs.com",
        "adsrvr.org",
        "analytics.google.com",
        "appsflyer.com",
        "atdmt.com",
        "bing.com",
        "branch.io",
        "chartbeat.com",
        "clicktale.net",
        "cloudflareinsights.com",
        "criteo.com",
        "criteo.net",
        "demdex.net",
        "doubleclick.net",
        "facebook.net",
        "firebaseinstallations.googleapis.com",
        "flurry.com",
        "fullstory.com",
        "googlesyndication.com",
        "googletagmanager.com",
        "google-analytics.com",
        "hotjar.com",
        "intercom.io",
        "klaviyo.com",
        "licdn.com",
        "mixpanel.com",
        "moatads.com",
        "newrelic.com",
        "nr-data.net",
        "omtrdc.net",
        "onesignal.com",
        "optimizely.com",
        "outbrain.com",
        "pardot.com",
        "pinterest.com",
        "quantserve.com",
        "redditstatic.com",
        "segment.com",
        "sentry-cdn.com",
        "scorecardresearch.com",
        "snapchat.com",
        "taboola.com",
        "tealiumiq.com",
        "tiqcdn.com",
        "tiktok.com",
        "twitter.com",
        "x.com",
        "yahoo.com",
        "yandex.ru"
    )

    private val multiPartSuffixes = setOf(
        "co.uk",
        "org.uk",
        "gov.uk",
        "ac.uk",
        "com.au",
        "net.au",
        "org.au",
        "co.jp",
        "com.br",
        "com.mx",
        "co.in"
    )

    fun shouldBlock(resourceUrl: Uri, topLevelUrl: String?): Boolean {
        val scheme = resourceUrl.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") {
            return false
        }

        val resourceHost = resourceUrl.host?.lowercase()?.trim('.') ?: return false
        if (!isKnownTracker(resourceHost)) {
            return false
        }

        val topLevelHost = parseHost(topLevelUrl) ?: return true
        return !isSameSite(resourceHost, topLevelHost)
    }

    private fun isKnownTracker(host: String): Boolean {
        var candidate = host
        while (true) {
            if (candidate in trackerDomains) {
                return true
            }
            val nextDot = candidate.indexOf('.')
            if (nextDot < 0 || nextDot == candidate.lastIndex) {
                return false
            }
            candidate = candidate.substring(nextDot + 1)
        }
    }

    private fun parseHost(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        return try {
            Uri.parse(url).host?.lowercase()?.trim('.')
        } catch (_: Throwable) {
            null
        }
    }

    private fun isSameSite(leftHost: String, rightHost: String): Boolean {
        if (leftHost == rightHost) {
            return true
        }
        if (leftHost.endsWith(".$rightHost") || rightHost.endsWith(".$leftHost")) {
            return true
        }
        return registrableDomain(leftHost) == registrableDomain(rightHost)
    }

    private fun registrableDomain(host: String): String {
        val parts = host.split('.').filter { it.isNotBlank() }
        if (parts.size <= 2) {
            return host
        }

        val suffix = parts.takeLast(2).joinToString(".")
        return if (suffix in multiPartSuffixes && parts.size >= 3) {
            parts.takeLast(3).joinToString(".")
        } else {
            suffix
        }
    }
}
