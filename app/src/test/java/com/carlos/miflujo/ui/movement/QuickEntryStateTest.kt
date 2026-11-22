package com.carlos.miflujo.ui.movement

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class QuickEntryStateTest {
    private val draft = QuickEntryDraft(
        type = MovementType.EXPENSE, currency = Currency.DOLLAR, amount = "12.34",
        detail = "Luz", date = LocalDate.of(2026, 11, 1),
        category = MovementCategory.FIXED_COST, subcategory = MovementSubcategory.ELECTRICITY,
    )
    private val partialDraft = draft.copy(
        type = MovementType.INCOME,
        amount = "250.",
        detail = "Compra de materiales",
        date = LocalDate.of(2026, 11, 3),
        category = null,
        subcategory = null,
        catalogCategoryUuid = "3f77a22b-d81d-44cc-9f5a-b228650d64ad",
    )

    private fun queued() = QuickEntryState().update(draft).add()

    @Test fun `add validates and retains only type and currency for next capture`() {
        val state = queued()
        assertEquals(draft, state.pending.single().draft)
        assertEquals(draft.type, state.active.type)
        assertEquals(draft.currency, state.active.currency)
        assertEquals("", state.active.amount)
        assertEquals("", state.active.detail)
        assertNull(state.active.category)
        assertNull(state.active.subcategory)
        assertFalse(state.dirty)
    }

    @Test fun `invalid add retains active data and does not enqueue`() {
        val state = QuickEntryState().update(draft.copy(amount = "0")).add()
        assertTrue(state.pending.isEmpty())
        assertEquals("0", state.active.amount)
        assertNotNull(state.message)
    }

    @Test fun `fixed cost still requires subcategory`() {
        val state = QuickEntryState().update(draft.copy(subcategory = null)).add()
        assertTrue(state.pending.isEmpty())
        assertNotNull(state.message)
    }

    @Test fun `repeated add does not duplicate but intentional identical capture is allowed`() {
        val once = queued()
        val repeated = once.add().add()
        assertEquals(once.pending, repeated.pending)
        val intentional = repeated.update(draft).add()
        assertEquals(2, intentional.pending.size)
        assertNotEquals(intentional.pending[0].id, intentional.pending[1].id)
    }

    @Test fun `edit loads all fields and replaces same identity without duplicate`() {
        val original = queued()
        val id = original.pending.single().id
        val editing = original.edit(id)
        assertEquals(draft, editing.active)
        val saved = editing.update(draft.copy(amount = "50", detail = "Corregido")).saveEdit()
        assertEquals(id, saved.pending.single().id)
        assertEquals("50", saved.pending.single().draft.amount)
        assertEquals(original.active, saved.active)
        assertNull(saved.editingId)
        assertEquals(saved.pending, saved.saveEdit().pending)
    }

    @Test fun `invalid edit preserves original and stays in editing`() {
        val original = queued()
        val state = original.edit(original.pending.single().id).update(draft.copy(amount = "")).saveEdit()
        assertEquals(original.pending, state.pending)
        assertNotNull(state.editingId)
        assertNotNull(state.message)
    }

    @Test fun `cancel edit restores new capture and leaves original untouched`() {
        val original = queued().update(partialDraft)
        val state = original.edit(original.pending.single().id).update(draft.copy(detail = "Cambio")).cancelEdit()
        assertEquals(original.pending, state.pending)
        assertEquals(partialDraft, state.active)
        assertTrue(state.dirty)
        assertNull(state.editingId)
    }

    @Test fun `saving a pending edit restores partial capture and updates only the selected UUID`() {
        val original = queued().update(draft.copy(detail = "Otro pendiente")).add().update(partialDraft)
        val first = original.pending.first()
        val editing = original.edit(first.id)
        assertEquals(first.draft, editing.active)
        assertEquals(original.pending, editing.pending)
        val corrected = first.draft.copy(amount = "50")
        val saved = editing.update(corrected).saveEdit()
        assertEquals(listOf(first.copy(draft = corrected), original.pending.last()), saved.pending)
        assertEquals(partialDraft, saved.active)
        assertTrue(saved.dirty)
        assertNull(saved.editingId)
    }

    @Test fun `editing is exclusive and add cannot insert the edited capsule`() {
        val state = queued().update(draft).add().update(partialDraft)
        val editing = state.edit(state.pending.first().id)
        val blocked = editing.edit(state.pending.last().id)
        assertEquals(editing, blocked)
        assertEquals(editing.pending, editing.add().pending)
        assertNull(editing.confirm().confirmedCount)
        assertEquals(partialDraft, blocked.cancelEdit().active)
    }

    @Test fun `deletion requires confirmation and preserves identity of other entries`() {
        val state = queued().update(draft).add()
        val requested = state.requestDelete(state.pending.first().id)
        assertEquals(state.pending, requested.pending)
        assertEquals(state.pending, requested.copy(deleteRequestedId = null).deleteRequested().pending)
        val deleted = requested.deleteRequested()
        assertEquals(listOf(state.pending.last()), deleted.pending)
        assertEquals(deleted, deleted.deleteRequested())
    }

    @Test fun `deleting edited item discards only that edit and restores capture`() {
        val original = queued().update(draft).add().update(partialDraft)
        val id = original.pending.first().id
        val deleted = original.edit(id).update(draft.copy(amount = "99")).requestDelete(id).deleteRequested()
        assertEquals(listOf(original.pending.last()), deleted.pending)
        assertEquals(original.active, deleted.active)
        assertNull(deleted.editingId)
    }

    @Test fun `deleting another item does not disturb ongoing edit`() {
        val original = queued().update(draft).add()
        val editing = original.edit(original.pending.last().id).update(draft.copy(detail = "Cambio"))
        val deleted = editing.requestDelete(original.pending.first().id).deleteRequested()
        assertEquals(editing.active, deleted.active)
        assertEquals(editing.editingId, deleted.editingId)
        assertEquals(editing.editingId, deleted.saveEdit().pending.single().id)
    }

    @Test fun `exit requests confirmation for dirty capture queue and editing`() {
        assertFalse(QuickEntryState().requestExit().exitRequested)
        assertTrue(QuickEntryState().update(draft).requestExit().exitRequested)
        val state = queued()
        assertTrue(state.requestExit().exitRequested)
        assertTrue(state.edit(state.pending.single().id).requestExit().exitRequested)
    }

    @Test fun `confirm previews single or multiple movements without queue mutation or duplication`() {
        val single = QuickEntryState().update(draft).confirm()
        assertEquals(1, single.confirmedCount)
        assertTrue(single.pending.isEmpty())
        assertEquals(single, single.confirm())
        val queued = queued()
        assertEquals(1, queued.confirm().confirmedCount)
        val combined = queued.update(draft).confirm()
        assertEquals(2, combined.confirmedCount)
        assertEquals(queued.pending, combined.pending)
        assertEquals(draft, combined.active)
        assertNull(queued.update(draft.copy(amount = "")).confirm().confirmedCount)
    }

    @Test fun `saveable state round trip preserves queue active edit and identities`() {
        val initial = queued().update(partialDraft).let { it.edit(it.pending.single().id) }
            .update(draft.copy(detail = "Cambio")).requestExit()
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(initial) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as QuickEntryState }
        assertEquals(initial, restored)
        val continued = restored.copy(exitRequested = false)
        assertEquals(initial.pending, continued.cancelEdit().pending)
        assertEquals(partialDraft, continued.cancelEdit().active)
        assertEquals(partialDraft, continued.saveEdit().active)
    }
}
