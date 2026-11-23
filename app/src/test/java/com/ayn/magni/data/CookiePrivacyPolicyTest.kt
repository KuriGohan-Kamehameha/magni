package com.ayn.magni.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookiePrivacyPolicyTest {

    @Test
    fun firstPartyCookiesFollowPrivateModeAndBlockAllCookies() {
        assertTrue(
            CookiePrivacyPolicy.acceptsFirstPartyCookies(
                privateBrowsingEnabled = false,
                blockAllCookies = false
            )
        )

        assertFalse(
            CookiePrivacyPolicy.acceptsFirstPartyCookies(
                privateBrowsingEnabled = true,
                blockAllCookies = false
            )
        )

        assertFalse(
            CookiePrivacyPolicy.acceptsFirstPartyCookies(
                privateBrowsingEnabled = false,
                blockAllCookies = true
            )
        )
    }

    @Test
    fun thirdPartyCookiesDefaultToBlockedUnlessExplicitlyAllowed() {
        assertFalse(
            CookiePrivacyPolicy.acceptsThirdPartyCookies(
                privateBrowsingEnabled = false,
                blockAllCookies = false,
                blockThirdPartyCookies = true
            )
        )

        assertTrue(
            CookiePrivacyPolicy.acceptsThirdPartyCookies(
                privateBrowsingEnabled = false,
                blockAllCookies = false,
                blockThirdPartyCookies = false
            )
        )

        assertFalse(
            CookiePrivacyPolicy.acceptsThirdPartyCookies(
                privateBrowsingEnabled = false,
                blockAllCookies = true,
                blockThirdPartyCookies = false
            )
        )
    }
}
