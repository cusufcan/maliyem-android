package com.mercan.maliyem.data.receipt

import java.io.File
import java.nio.file.Files

internal const val RECEIPTS_DIRECTORY = "receipts"
internal const val RECEIPT_DRAFTS_DIRECTORY = "receipt-drafts"
internal const val RECEIPT_CAMERA_DIRECTORY = "receipt-camera"

private val receiptName = Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.jpg")

internal fun File.receiptFile(path: String?): File? {
    if (path == null || !receiptName.matches(path)) return null
    return File(this, path)
}

internal fun File.existingReceipt(path: String?): File? =
    receiptFile(path)?.takeIf { it.isFile && it.length() > 0L && !Files.isSymbolicLink(it.toPath()) }

internal fun File.cleanReceipts(keep: Set<String> = emptySet(), modifiedBefore: Long? = null) {
    listFiles()?.forEach { file ->
        if (file.name !in keep && (file.isFile || Files.isSymbolicLink(file.toPath())) &&
            (modifiedBefore == null || file.lastModified() < modifiedBefore)
        ) file.delete()
    }
}
