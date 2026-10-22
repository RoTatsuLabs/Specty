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
        assertEquals(v["ffmpegKit"], versionOf("FFmpegKit (maintained fork)"))
        assertEquals("BOM " + v["composeBom"], versionOf("Jetpack Compose"))
        assertEquals(v["activityCompose"], versionOf("Activity Compose"))
        assertEquals(v["lifecycle"], versionOf("Lifecycle"))
        assertEquals(v["coreKtx"], versionOf("Core KTX"))
        assertEquals(v["kotlin"], versionOf("Kotlin Standard Library"))
        assertEquals(v["coroutines"], versionOf("kotlinx.coroutines"))
    }

    @Test
    fun everyShippedDependencyIsListed() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        val shipped = Regex("""^\s*implementation\(""", RegexOption.MULTILINE).findAll(gradle).count()
        // BOM + 6 Compose, Activity, 2 Lifecycle, Core, coroutines, ffmpeg-kit. A new dependency needs an entry in
        // OpenSource.libraries (or a note that an existing one covers it) and this count updated.
        assertEquals(13, shipped)
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
