package com.mercan.maliyem.ui.add

import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionType
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddTransactionLogicTest {
    private val tr = Locale.forLanguageTag("tr-TR")
    private val en = Locale.US
    private val date = LocalDate.of(2026, 11, 26)

    private fun form(amount: String = "120,50", category: Long? = 1, note: String = "") =
        AddForm(TransactionType.EXPENSE, amount, category, date, note)

    @Test fun `amount parses in both locales`() {
        assertEquals(12_050L, parseAmount("120,50", tr))
        assertEquals(123_456L, parseAmount("1.234,56", tr))
        assertEquals(12_050L, parseAmount("120.50", en))
        assertEquals(123_456L, parseAmount("1,234.56", en))
    }

    @Test fun `other separator counts as decimal when followed by one or two digits`() {
        assertEquals(12_050L, parseAmount("120.50", tr))
        assertEquals(12_050L, parseAmount("120.5", tr))
        assertEquals(12_050L, parseAmount("120,5", en))
        assertEquals(125_000L, parseAmount("1.250", tr))
        assertEquals(125_000L, parseAmount("1,250", en))
    }

    @Test fun `amount errors`() {
        assertEquals(AddFormError.AMOUNT_EMPTY, amountError("  ", tr))
        assertEquals(AddFormError.AMOUNT_INVALID, amountError("12a", tr))
        assertEquals(AddFormError.AMOUNT_INVALID, amountError("1,234", tr))
        assertEquals(AddFormError.AMOUNT_ZERO, amountError("0,00", tr))
        assertNull(amountError("0,01", tr))
    }

    @Test fun `category falls back to first of the type`() {
        val income = listOf(cat(8), cat(9))
        assertEquals(8L, pickCategory(null, income))
        assertEquals(8L, pickCategory(1, income))
        assertEquals(9L, pickCategory(9, income))
        assertNull(pickCategory(1, emptyList()))
    }

    @Test fun `valid form builds entity with trimmed note`() {
        val result = buildEntity(form(note = "  Migros "), tr, createdAt = 42)
        assertTrue(result is Result.Valid)
        val entity = (result as Result.Valid).entity
        assertEquals(12_050L, entity.amountMinor)
        assertEquals("Migros", entity.note)
        assertEquals(date, entity.date)
        assertEquals(42L, entity.createdAt)
        assertNull((buildEntity(form(note = "   "), tr, 0) as Result.Valid).entity.note)
    }

    @Test fun `amount error comes before missing category`() {
        assertEquals(Result.Invalid(AddFormError.AMOUNT_EMPTY), buildEntity(form(amount = "", category = null), tr, 0))
        assertEquals(Result.Invalid(AddFormError.CATEGORY_MISSING), buildEntity(form(category = null), tr, 0))
    }

    private fun cat(id: Long) = CategoryEntity(id = id, type = TransactionType.INCOME, icon = "dots", sortOrder = 0)

    @Test fun `edit text shows the stored amount`() {
        val tr = Locale.forLanguageTag("tr")
        assertEquals("120,50", amountInputText(12050, tr))
        assertEquals("120.05", amountInputText(12005, Locale.US))
        assertEquals("75", amountInputText(7500, tr))
    }
}
