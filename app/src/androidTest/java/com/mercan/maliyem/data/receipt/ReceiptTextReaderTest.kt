package com.mercan.maliyem.data.receipt

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptTextReaderTest {
    @Test
    fun recognizesBundledReceiptWithoutInternetPermission() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))

        val fixture = File.createTempFile("ocr-receipt-", ".jpg", context.cacheDir)
        val drafts = ReceiptDrafts(context)
        val name = drafts.newName()
        try {
            instrumentation.context.assets.open("ocr-receipt.jpg").use { input ->
                fixture.outputStream().use { output -> input.copyTo(output) }
            }
            drafts.importPhoto(Uri.fromFile(fixture), name)
            val clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneOffset.UTC)
            val result = withTimeout(30_000) { ReceiptTextReader(clock).read(checkNotNull(drafts.draftFile(name))) }
            assertEquals(ReceiptSuggestion(23_790L, LocalDate.of(2026, 11, 26), "Migros"), result)
        } finally {
            drafts.discardDraft(name)
            fixture.delete()
        }
        Unit
    }
}
