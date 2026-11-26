package com.mercan.maliyem.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoriesTest {
    private val all = DefaultCategories.all

    @Test fun `keys and names are unique`() {
        assertEquals(all.size, all.map { it.key }.toSet().size)
        assertEquals(all.size, all.map { it.nameRes }.toSet().size)
    }

    @Test fun `each type ends with its other category`() {
        assertEquals(DefaultCategories.OTHER_EXPENSE, all.last { it.type == TransactionType.EXPENSE }.key)
        assertEquals(DefaultCategories.OTHER_INCOME, all.last { it.type == TransactionType.INCOME }.key)
    }

    @Test fun `seed has seven expense and three income categories`() {
        val entities = DefaultCategories.entities()
        assertEquals(7, entities.count { it.type == TransactionType.EXPENSE })
        assertEquals(3, entities.count { it.type == TransactionType.INCOME })
        assertTrue(entities.all { it.key != null && it.name == null })
    }

    @Test fun `sort order restarts per type`() {
        DefaultCategories.entities().groupBy { it.type }.values.forEach { list ->
            assertEquals(list.indices.toList(), list.map { it.sortOrder })
        }
    }
}
