package com.carlos.miflujo.ui.movement

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.io.Serializable
import java.time.LocalDate
import java.util.UUID

internal data class QuickEntryDraft(
    val type: MovementType = MovementType.EXPENSE,
    val currency: Currency = Currency.CORDOBA,
    val amount: String = "",
    val detail: String = "",
    val date: LocalDate = LocalDate.now(),
    val category: MovementCategory? = null,
    val subcategory: MovementSubcategory? = null,
    val catalogCategoryUuid: String? = null,
) : Serializable {
    fun validationMessage(): String? = quickEntryValidationMessage(type, amount, currency, date, category, subcategory)
}

internal data class PendingEntry(val id: String, val draft: QuickEntryDraft) : Serializable

/** In-memory demonstration only. No repository, remote identity or persistence side effects. */
internal data class QuickEntryState(
    val active: QuickEntryDraft = QuickEntryDraft(),
    val baseline: QuickEntryDraft = active,
    val pending: List<PendingEntry> = emptyList(),
    val editingId: String? = null,
    val message: String? = null,
    val exitRequested: Boolean = false,
    val deleteRequestedId: String? = null,
    val confirmedCount: Int? = null,
) : Serializable {
    val dirty: Boolean get() = active != baseline
    val hasWork: Boolean get() = pending.isNotEmpty() || dirty || editingId != null

    fun update(draft: QuickEntryDraft) = copy(active = draft, message = null, confirmedCount = null)

    fun add(
        validate: (QuickEntryDraft) -> String? = QuickEntryDraft::validationMessage,
        id: String = UUID.randomUUID().toString(),
    ): QuickEntryState {
        if (editingId != null) return this
        validate(active)?.let { return copy(message = it) }
        val next = QuickEntryDraft(type = active.type, currency = active.currency)
        return copy(pending = pending + PendingEntry(id, active), active = next, baseline = next, message = null)
    }

    fun edit(id: String): QuickEntryState {
        if (editingId != null || dirty) return copy(message = "Termine la captura o guarde/cancele la edición antes de editar otro movimiento.")
        val entry = pending.find { it.id == id } ?: return this
        return copy(active = entry.draft, editingId = id, message = null)
    }

    fun saveEdit(validate: (QuickEntryDraft) -> String? = QuickEntryDraft::validationMessage): QuickEntryState {
        val id = editingId ?: return this
        validate(active)?.let { return copy(message = it) }
        return copy(pending = pending.map { if (it.id == id) it.copy(draft = active) else it }, active = baseline, editingId = null, message = null)
    }

    fun cancelEdit(): QuickEntryState = if (editingId == null) this else copy(active = baseline, editingId = null, message = null)

    fun requestDelete(id: String): QuickEntryState = if (pending.any { it.id == id }) copy(deleteRequestedId = id) else this

    fun deleteRequested(): QuickEntryState {
        val id = deleteRequestedId ?: return this
        return copy(pending = pending.filterNot { it.id == id }, active = if (editingId == id) baseline else active,
            editingId = if (editingId == id) null else editingId, deleteRequestedId = null, message = null)
    }

    fun requestExit() = copy(exitRequested = hasWork)

    // Preview a count, never insert into the queue or report a successful save.
    fun confirm(validate: (QuickEntryDraft) -> String? = QuickEntryDraft::validationMessage): QuickEntryState {
        if (editingId != null) return this
        if (dirty || pending.isEmpty()) {
            validate(active)?.let { return copy(message = it) }
            return copy(confirmedCount = pending.size + 1, message = null)
        }
        return copy(confirmedCount = pending.size, message = null)
    }
}
