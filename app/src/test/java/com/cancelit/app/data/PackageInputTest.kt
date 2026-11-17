package com.cancelit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageInputTest {

    @Test
    fun barePackageNamesKeepTheirCase() {
        assertEquals("com.example.app", packageFromInput("  com.example.app "))
        assertEquals("com.Slack", packageFromInput("com.Slack"))
    }

    @Test
    fun playStoreLinksGiveTheirId() {
        assertEquals(
            "com.instagram.android",
            packageFromInput("https://play.google.com/store/apps/details?id=com.instagram.android&hl=en_IN")
        )
        assertEquals("com.Slack", packageFromInput("play.google.com/store/apps/details?hl=en&id=com.Slack"))
    }

    @Test
    fun anythingElseIsRejected() {
        assertNull(packageFromInput("Instagram"))
        assertNull(packageFromInput("com."))
        assertNull(packageFromInput("com example app"))
        assertNull(packageFromInput("1com.example"))
        assertNull(packageFromInput("https://example.com/page"))
    }
}
