package com.wanderwildwood.tenken.ui

import com.wanderwildwood.tenken.ui.Elsewhere
import com.wanderwildwood.tenken.ui.LIBRARIES
import com.wanderwildwood.tenken.ui.LICENCE_TEXTS
import com.wanderwildwood.tenken.ui.SOURCE_URL
import com.wanderwildwood.tenken.ui.licenceParagraphs
import java.io.File
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicencesTest {
    private fun raw(name: String) = File("src/main/res/raw/$name.txt").readText()

    @Test
    fun theListOfWhatItIsBuiltWithIsNotEmptyAndEveryLinkOpens() {
        assertTrue(LIBRARIES.isNotEmpty())
        LIBRARIES.forEach {
            assertEquals("https", URI(it.link).scheme)
            assertTrue(it.name.isNotBlank() && it.by.isNotBlank() && it.licence.isNotBlank())
        }
        assertEquals(LIBRARIES.size, LIBRARIES.map { it.name }.toSet().size)
    }

    @Test
    fun theSourceAddressIsTheAppsRepoOnGithub() {
        val uri = URI(SOURCE_URL)
        assertEquals("github.com", uri.host)
        assertEquals("/wanderwildwood/tenken", uri.path)
    }

    @Test
    fun everyLicenceTextIsBundledAndPagesIntoParagraphs() {
        assertTrue(LICENCE_TEXTS.size >= 3)
        listOf("gpl_3_0", "apache_2_0").forEach { assertTrue(licenceParagraphs(raw(it)).size > 10) }
        assertTrue(raw("gpl_3_0").contains("GNU GENERAL PUBLIC LICENSE"))
        assertTrue(raw("apache_2_0").contains("Apache License"))
    }

    @Test
    fun paragraphsKeepEveryWord() {
        val text = "One two\n three\n\n\nFour\r\nfive"
        assertEquals(listOf("One two three", "Four five"), licenceParagraphs(text))
    }

    @Test
    fun theReminderIsForThisTimeTomorrow() {
        assertEquals(1_000L + 24L * 60 * 60 * 1000, Elsewhere.reminderStart(1_000L))
    }
}
