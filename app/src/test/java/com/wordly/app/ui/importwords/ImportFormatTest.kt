package com.wordly.app.ui.importwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFormatTest {

    @Test
    fun `apkg is recognised by its zip header`() {
        assertTrue(ImportFormat.isZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14)))
        assertFalse(ImportFormat.isZip("word,,перевод".toByteArray()))
    }

    @Test
    fun `binary data is never decoded as CSV text`() {
        assertNull(ImportFormat.decodeText(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x08)))
        assertNull(ImportFormat.decodeText(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test
    fun `utf8 csv decodes, byte order mark dropped`() {
        assertEquals("cat,,кошка", ImportFormat.decodeText("﻿cat,,кошка".toByteArray()))
    }
}
