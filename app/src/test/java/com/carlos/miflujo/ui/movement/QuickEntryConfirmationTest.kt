package com.carlos.miflujo.ui.movement

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuickEntryConfirmationTest {
    @Test
    fun `a queue confirms alone when the next draft has no amount`() {
        val empty = queued.creationDraft
        val drafts = listOf(
            empty,
            empty.copy(detail = "Taxi"),
            empty.copy(date = LocalDate.of(2026, 11, 22)),
            empty.copy(catalogCategoryUuid = "00000000-0000-4000-8000-000000000001"),
            empty.copy(currency = Currency.DOLLAR),
            empty.copy(type = MovementType.INCOME),
            empty.copy(amount = " \t", detail = "Taxi"),
        )
        for (draft in drafts) {
            val state = queued.update(draft)
            val confirmed = state.confirmCatalog(::validate)
            assertNull(confirmed.message)
            assertEquals(queued.pending.size, confirmed.confirmedCount)
            assertEquals(queued.pending, state.catalogSubmissionEntries("active", editingStored = false))
            assertEquals(draft, confirmed.creationDraft)
            assertEquals(queued.pending, confirmed.pending)
        }
    }

    @Test
    fun `a nonblank invalid amount blocks the whole queue`() {
        val amounts = listOf("12.", "12,", "0", "0.00", "1.234", "1000000000", "x")
        for (amount in amounts) {
            val state = queued.update(queued.creationDraft.copy(amount = amount))
            val rejected = state.confirmCatalog(::validate)
            assertNull(amount, rejected.confirmedCount)
            assertNotNull(amount, rejected.message)
            assertEquals(amount, rejected.creationDraft.amount)
            assertEquals(queued.pending, rejected.pending)
        }
    }

    @Test
    fun `a valid amount joins the same batch with its original UUID`() {
        val draft = queued.creationDraft.copy(amount = "200,50", detail = "Taxi")
        val state = queued.update(draft)
        val confirmed = state.confirmCatalog(::validate)
        assertNull(confirmed.message)
        assertEquals(queued.pending.size + 1, confirmed.confirmedCount)
        assertEquals(
            queued.pending + PendingEntry("active", draft),
            state.catalogSubmissionEntries("active", editingStored = false),
        )
    }

    @Test
    fun `an empty queue still requires a valid current movement`() {
        val emptyQueue = QuickEntryState()
        val rejected = emptyQueue.update(emptyQueue.creationDraft.copy(detail = "Taxi")).confirmCatalog(::validate)
        assertNull(rejected.confirmedCount)
        assertNotNull(rejected.message)

        val single = emptyQueue.update(emptyQueue.creationDraft.copy(amount = "100"))
        assertEquals(1, single.confirmCatalog(::validate).confirmedCount)
        assertEquals(listOf(PendingEntry("active", single.creationDraft)), single.catalogSubmissionEntries("active", false))
    }

    private fun validate(draft: QuickEntryDraft): String? = validateQuickEntryAmount(draft.amount).error

    private val queued = QuickEntryState()
        .update(QuickEntryDraft(amount = "500", detail = "Supermercado"))
        .add(::validate, "first")
        .let { it.update(it.creationDraft.copy(amount = "800", detail = "Gasolina")) }
        .add(::validate, "second")
}
