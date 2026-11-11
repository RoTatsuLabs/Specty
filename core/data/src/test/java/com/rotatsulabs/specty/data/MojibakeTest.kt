package com.rotatsulabs.specty.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MojibakeTest {
    @Test
    fun repairsAnApostropheReadAsWindows1252() {
        assertEquals("It’s The Morning After", Mojibake.fix("Itâ€™s The Morning After"))
    }

    @Test
    fun repairsAccentedLettersReadAsLatin1() {
        assertEquals("Café", Mojibake.fix("CafÃ©"))
    }

    @Test
    fun leavesCorrectTextAlone() {
        assertEquals("Café", Mojibake.fix("Café"))
        assertEquals("It’s", Mojibake.fix("It’s"))
        assertEquals("テスト", Mojibake.fix("テスト"))
        assertEquals("Plain", Mojibake.fix("Plain"))
        assertNull(Mojibake.fix(null))
    }
}
