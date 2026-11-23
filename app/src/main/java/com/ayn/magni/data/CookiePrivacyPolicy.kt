package com.ayn.magni.data

object CookiePrivacyPolicy {
    fun acceptsFirstPartyCookies(
        privateBrowsingEnabled: Boolean,
        blockAllCookies: Boolean
    ): Boolean {
        return !privateBrowsingEnabled && !blockAllCookies
    }

    fun acceptsThirdPartyCookies(
        privateBrowsingEnabled: Boolean,
        blockAllCookies: Boolean,
        blockThirdPartyCookies: Boolean
    ): Boolean {
        return acceptsFirstPartyCookies(
            privateBrowsingEnabled = privateBrowsingEnabled,
            blockAllCookies = blockAllCookies
        ) && !blockThirdPartyCookies
    }
}
