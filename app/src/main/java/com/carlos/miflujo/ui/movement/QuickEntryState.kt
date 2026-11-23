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
    val historicalCategoryReplaced: Boolean = false,
    val omitHistoricalSubcategory: Boolean = false,
    val catalogCategoryCreatedInEditor: Boolean = false,
) : Serializable {
    private companion object {
        // Keep compatibility with drafts saved before the category flags were added.
        private const val serialVersionUID = 5834423242141167301L
    }

    fun validationMessage(): String? = quickEntryValidationMessage(type, amount, currency, date, category, subcategory)
}

internal data class PendingEntry(val id: String, val draft: QuickEntryDraft) : Serializable

internal val QuickEntryState.includesCreationDraft: Boolean get() = dirty || pending.isEmpty()

/** Saveable form and queue state. Transitions never write to a repository. */
internal data class QuickEntryState(
    val creationDraft: QuickEntryDraft = QuickEntryDraft(),
    val baseline: QuickEntryDraft = creationDraft,
    val pending: List<PendingEntry> = emptyList(),
    private val pendingEdit: PendingEntry? = null,
    val message: String? = null,
    val exitRequested: Boolean = false,
    val deleteRequestedId: String? = null,
    val confirmedCount: Int? = null,
) : Serializable {
    val active: QuickEntryDraft get() = pendingEdit?.draft ?: creationDraft
    val editingId: String? get() = pendingEdit?.id
    val dirty: Boolean get() = creationDraft != baseline
    val hasWork: Boolean get() = pending.isNotEmpty() || dirty || editingId != null

    fun update(draft: QuickEntryDraft) = if (pendingEdit == null) {
        copy(creationDraft = draft, message = null, confirmedCount = null)
    } else {
        copy(pendingEdit = pendingEdit.copy(draft = draft), message = null, confirmedCount = null)
    }

    fun add(
        validate: (QuickEntryDraft) -> String? = QuickEntryDraft::validationMessage,
        id: String = UUID.randomUUID().toString(),
    ): QuickEntryState {
        if (editingId != null) return this
        validate(creationDraft)?.let { return copy(message = it) }
        val next = QuickEntryDraft(type = creationDraft.type, currency = creationDraft.currency)
        return copy(pending = pending + PendingEntry(id, creationDraft), creationDraft = next, baseline = next, message = null)
    }

    fun edit(id: String): QuickEntryState {
        if (pendingEdit != null) return this
        val entry = pending.find { it.id == id } ?: return this
        return copy(pendingEdit = entry, message = null)
    }

    fun saveEdit(validate: (QuickEntryDraft) -> String? = QuickEntryDraft::validationMessage): QuickEntryState {
        val edit = pendingEdit ?: return this
        validate(edit.draft)?.let { return copy(message = it) }
        return copy(pending = pending.map { if (it.id == edit.id) edit else it }, pendingEdit = null, message = null)
    }

    fun cancelEdit(): QuickEntryState = if (pendingEdit == null) this else copy(pendingEdit = null, message = null)

    fun requestDelete(id: String): QuickEntryState = if (pending.any { it.id == id }) copy(deleteRequestedId = id) else this

    fun deleteRequested(): QuickEntryState {
        val id = deleteRequestedId ?: return this
        return copy(
            pending = pending.filterNot { it.id == id },
            pendingEdit = pendingEdit?.takeUnless { it.id == id },
            deleteRequestedId = null,
            message = null,
        )
    }

    fun requestExit() = copy(exitRequested = hasWork)

    // Preview a count, never insert into the queue or report a successful save.
    fun confirm(validate: (QuickEntryDraft) -> String? = QuickEntryDraft::validationMessage): QuickEntryState {
        if (editingId != null) return this
        if (includesCreationDraft) {
            validate(creationDraft)?.let { return copy(message = it) }
            return copy(confirmedCount = pending.size + 1, message = null)
        }
        return copy(confirmedCount = pending.size, message = null)
    }
}
