package com.rotatsulabs.specty.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenSourceTest {
    /** Gradle runs unit tests in the module folder, so the project root is found by walking up. */
    private fun root(): File {
        var dir: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (dir != null && !File(dir, "gradle/libs.versions.toml").exists()) dir = dir.parentFile
        return dir ?: error("project root not found")
    }

    private fun catalogVersions(): Map<String, String> =
        File(root(), "gradle/libs.versions.toml").readLines()
            .takeWhile { !it.startsWith("[libraries]") }
            .mapNotNull { Regex("""^(\w+)\s*=\s*"([^"]+)"""").find(it) }
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun versionOf(name: String) = OpenSource.libraries.first { it.name == name }.version

    @Test
    fun listedVersionsMatchTheVersionCatalog() {
        val v = catalogVersions()
        assertEquals(v["ffmpegKit"], versionOf("FFmpegKitNext"))
    }

    @Test
    fun theFullListIsGeneratedFromTheDependencies() {
        // Dependencies are listed by the AboutLibraries plugin, so the app needs the plugin, the reader and the keep
        // rule that stops resource shrinking from dropping the generated file.
        val app = File(root(), "app/build.gradle.kts").readText()
        assertTrue(app.contains("libs.plugins.aboutlibraries"))
        assertTrue(app.contains("libs.aboutlibraries.core"))
        assertTrue(File(root(), "app/src/main/res/raw/keep.xml").readText().contains("@raw/aboutlibraries"))
    }

    @Test
    fun onlyCreditsWithANoteAreWrittenByHand() {
        assertEquals(listOf("FFmpegKitNext", "Mornye"), OpenSource.libraries.map { it.name })
    }

    @Test
    fun theLicenceTextsAreBundled() {
        for (kind in LicenseKind.entries) {
            val file = File(root(), "app/src/main/assets/" + kind.asset)
            assertTrue(kind.asset, file.length() > 5_000)
        }
        val apache = File(root(), "app/src/main/assets/" + LicenseKind.Apache2.asset).readText()
        assertTrue(apache.contains("Apache License") && apache.contains("Version 2.0"))
        val lgpl = File(root(), "app/src/main/assets/" + LicenseKind.Lgpl3.asset).readText()
        assertTrue(lgpl.contains("GNU LESSER GENERAL PUBLIC LICENSE"))
        val gpl = File(root(), "app/src/main/assets/" + LicenseKind.Gpl3.asset).readText()
        assertEquals(File(root(), "LICENSE").readText(), gpl)
    }

    @Test
    fun entriesAreCompleteAndUnique() {
        val all = OpenSource.libraries + OpenSource.app
        assertEquals(all.size, all.map { it.name }.toSet().size)
        for (lib in all) {
            assertTrue(lib.name, lib.url.startsWith("https://"))
            assertTrue(lib.name, lib.summary != 0 && lib.author.isNotBlank())
        }
        assertEquals(LicenseKind.Gpl3, OpenSource.app.license)
        assertEquals(LicenseKind.Lgpl3, OpenSource.libraries.first { it.name.startsWith("FFmpegKit") }.license)
    }
}
