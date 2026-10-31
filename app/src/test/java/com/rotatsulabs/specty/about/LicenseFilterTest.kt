package com.rotatsulabs.specty.about

import com.rotatsulabs.specty.ui.screens.LicenseEntry
import com.rotatsulabs.specty.ui.screens.filterLicenses
import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseFilterTest {
    private fun entry(name: String, author: String, licence: String) =
        LicenseEntry(name, "1.0", null, listOf(author), listOf(licence), null)

    private val all = listOf(
        entry("lifecycle", "The Android Open Source Project", "Apache-2.0"),
        entry("Kotlin Standard Library", "JetBrains", "Apache-2.0"),
        entry("FFmpegKitNext", "Arthenica", "LGPL-3.0"),
    )

    @Test
    fun noQueryListsEverythingAToZ() {
        assertEquals(listOf("FFmpegKitNext", "Kotlin Standard Library", "lifecycle"), filterLicenses(all, "  ").map { it.name })
    }

    @Test
    fun theQueryMatchesNameAuthorOrLicenceWithoutRegardToCase() {
        assertEquals(listOf("lifecycle"), filterLicenses(all, "LIFE").map { it.name })
        assertEquals(listOf("Kotlin Standard Library"), filterLicenses(all, "jetbrains").map { it.name })
        assertEquals(listOf("FFmpegKitNext"), filterLicenses(all, "lgpl").map { it.name })
        assertEquals(emptyList<String>(), filterLicenses(all, "nothing like this").map { it.name })
    }
}
