package com.mercan.maliyem.ui.settings

import java.io.InputStream
import java.nio.charset.MalformedInputException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupImportLimitTest {
    @Test fun `size limit counts bytes and stops at the first extra byte`() {
        val limit = 20_000_000
        val exactSize = RepeatedBytes(limit)
        assertEquals(limit, readBackup(exactSize)?.length)

        val extraBytes = RepeatedBytes(limit + 1_000)
        assertNull(readBackup(extraBytes))
        assertEquals(limit + 1, extraBytes.consumed)
    }

    @Test fun `invalid UTF-8 still fails instead of quietly replacing characters`() {
        assertThrows(MalformedInputException::class.java) {
            readBackup(byteArrayOf(0xC3.toByte()).inputStream())
        }
    }

    private class RepeatedBytes(private val size: Int) : InputStream() {
        var consumed = 0
            private set

        override fun read(): Int = if (consumed == size) -1 else 'a'.code.also { consumed++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (consumed == size) return -1
            val count = minOf(length, size - consumed)
            buffer.fill('a'.code.toByte(), offset, offset + count)
            consumed += count
            return count
        }
    }
}
