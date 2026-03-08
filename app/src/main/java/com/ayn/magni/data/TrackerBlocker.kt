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
        "yandex.ru",
        // Additional high-priority trackers
        "adobe.com",
        "omniture.com",
        "2o7.net",
        "amplitude.com",
        "heapanalytics.com",
        "bugsnag.com",
        "rollbar.com",
        "mouseflow.com",
        "luckyorange.com",
        "crazyegg.com",
        "clarity.ms",
        "zdassets.com",
        "zendesk.com",
        "drift.com",
        "hubspot.com",
        "hs-analytics.net",
        "braze.com",
        "leanplum.com",
        "urbanairship.com",
        "airship.com"
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
        if (host.isBlank() || host.length > MAX_HOST_LENGTH) {
            return false
        }
        var candidate = host
        // NASA standard: fixed loop bounds to prevent potential infinite loops
        var iterations = 0
        while (iterations < MAX_SUBDOMAIN_DEPTH) {
            iterations++
            if (candidate in trackerDomains) {
                return true
            }
            val nextDot = candidate.indexOf('.')
            if (nextDot < 0 || nextDot == candidate.lastIndex) {
                return false
            }
            candidate = candidate.substring(nextDot + 1)
        }
        return false
    }

    // NASA standard: explicit constants for bounds
    private const val MAX_HOST_LENGTH = 253
    private const val MAX_SUBDOMAIN_DEPTH = 127

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
