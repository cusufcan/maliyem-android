package com.mercan.maliyem.data.receipt

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReceiptFilesTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun `receipt resolution only accepts local generated names and regular nonempty files`() {
        val directory = files.newFolder()
        val name = "6d093eb6-b924-4aaf-a7fa-8fccbe72abe0.jpg"
        val receipt = File(directory, name)
        assertNull(directory.existingReceipt(name))
        receipt.createNewFile()
        assertNull(directory.existingReceipt(name))
        receipt.writeText("photo")
        assertEquals(receipt, directory.existingReceipt(name))
        for (invalid in listOf(null, "", receipt.path, "../$name", "nested/$name", "nested\\$name", "receipt.jpg")) {
            assertNull(directory.receiptFile(invalid))
        }

        receipt.delete()
        val outside = files.newFile().apply { writeText("protected") }
        Files.createSymbolicLink(receipt.toPath(), outside.toPath())
        assertNull(directory.existingReceipt(name))
        val unknown = File(directory, "orphan.jpg").apply { writeText("orphan") }
        directory.cleanReceipts()
        assertFalse(Files.isSymbolicLink(receipt.toPath()))
        assertFalse(unknown.exists())
        assertTrue(outside.exists())
    }

    @Test fun `dimensions preserve aspect ratio without upscaling or losing the short edge`() {
        assertEquals(1600 to 1200, boundedSize(4000, 3000))
        assertEquals(900 to 1600, boundedSize(2250, 4000))
        assertEquals(640 to 480, boundedSize(640, 480))
        assertEquals(1 to 1600, boundedSize(1, 10000))
    }
}
