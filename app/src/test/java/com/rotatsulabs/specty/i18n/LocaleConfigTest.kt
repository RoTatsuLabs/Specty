package com.rotatsulabs.specty.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocaleConfigTest {
    private fun res(): File {
        var root: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (root != null && !File(root, "gradle/libs.versions.toml").exists()) root = root.parentFile
        return File(root, "app/src/main/res")
    }

    /** The language picker in Settings offers these tags (see AppLanguage.Choice). */
    private val offered = setOf("en", "id", "zh-CN")

    @Test
    fun theLocaleConfigListsExactlyTheOfferedLanguages() {
        val xml = File(res(), "xml/locales_config.xml").readText()
        val tags = Regex("""android:name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()
        assertEquals(offered, tags)
    }

    @Test
    fun everyOfferedLanguageHasItsOwnResources() {
        val dirs = mapOf("en" to "values", "id" to "values-in", "zh-CN" to "values-zh-rCN")
        for (tag in offered) assertTrue("no strings for $tag", File(res(), "${dirs.getValue(tag)}/strings.xml").isFile)
    }

    @Test
    fun theChoicesInAppLanguageMatchTheOfferedTags() {
        val source = File(res(), "../java/com/rotatsulabs/specty/ui/AppLanguage.kt").readText()
        val tags = Regex("""(?:English|Indonesian|Chinese)\("([^"]+)",""").findAll(source).map { it.groupValues[1] }.toSet()
        assertEquals(offered, tags)
    }
}
