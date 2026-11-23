package com.carlos.miflujo.ui.movement

import com.carlos.miflujo.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryAmountTest {
    @Test
    fun `unambiguous amounts convert exactly to minor units`() {
        val amounts = mapOf(
            "1" to 100L,
            "12" to 1200L,
            "12.5" to 1250L,
            "12.50" to 1250L,
            "12,5" to 1250L,
            "12,50" to 1250L,
            "0.50" to 50L,
            "0,50" to 50L,
            "999999999.99" to 99_999_999_999L,
        )
        for ((text, minor) in amounts) {
            val validation = validateQuickEntryAmount(text)
            assertNull(text, validation.error)
            assertEquals(text, minor, validation.amountMinor)
        }
    }

    @Test
    fun `incomplete ambiguous and oversized amounts cannot be submitted`() {
        val amounts = listOf(
            "", "0", "0.", "0.00", "12.", "12,", "-12", ".50",
            "1.234", "1,234", "0.001", "10.999", "999999999.999", "1,234.56",
            "1000000000", "1000000000.01", "92233720368547758.07",
            "92233720368547758.08", "999999999999999999999999",
        )
        for (text in amounts) {
            val validation = validateQuickEntryAmount(text)
            assertNull(text, validation.amountMinor)
            assertNotNull(text, validation.error)
        }
        assertEquals(
            "El monto máximo permitido es 999,999,999.99.",
            validateQuickEntryAmount("1000000000").error,
        )
    }

    @Test
    fun `capture preserves raw text while submission and pending edits validate it`() {
        fun validate(draft: QuickEntryDraft) = validateQuickEntryAmount(draft.amount).error

        val draft = QuickEntryDraft(type = MovementType.INCOME, amount = "12.")
        val state = QuickEntryState(creationDraft = draft)
        assertTrue(state.add(::validate).pending.isEmpty())
        assertNull(state.confirm(::validate).confirmedCount)
        assertEquals("12.", state.add(::validate).active.amount)
        assertNotNull(state.confirm().message)

        val queued = state.update(draft.copy(amount = "12,50")).add(::validate)
        val entry = queued.pending.single()
        assertEquals(1, queued.confirm(::validate).confirmedCount)
        val editing = queued.edit(entry.id)
        val rejected = editing.update(draft.copy(amount = "1,234")).saveEdit(::validate)
        assertEquals(entry, rejected.pending.single())
        assertEquals(entry.id, rejected.editingId)
        assertEquals("1,234", rejected.active.amount)

        val saved = rejected.update(draft.copy(amount = "12,5")).saveEdit(::validate)
        assertEquals(entry.id, saved.pending.single().id)
        assertEquals("12,5", saved.pending.single().draft.amount)
        assertNull(saved.editingId)
    }

    @Test
    fun `normal form parser still accepts properly grouped thousands`() {
        assertEquals(123_456L, parseAmountMinorOrNull("1,234.56"))
        assertEquals(123_456L, parseAmountMinorOrNull("1.234,56"))
    }
}
