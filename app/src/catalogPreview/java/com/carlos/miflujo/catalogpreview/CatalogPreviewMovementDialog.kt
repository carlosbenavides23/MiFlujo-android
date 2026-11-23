package com.carlos.miflujo.catalogpreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.carlos.miflujo.cataloglab.CatalogIcon
import com.carlos.miflujo.cataloglab.CatalogCategoryPicker
import com.carlos.miflujo.cataloglab.CatalogItemEditor
import com.carlos.miflujo.cataloglab.CatalogItemInput
import com.carlos.miflujo.cataloglab.createFromEditor
import com.carlos.miflujo.cataloglab.visibleName
import com.carlos.miflujo.cataloglab.withCategory
import com.carlos.miflujo.domain.backup.BackupCategory
import com.carlos.miflujo.domain.model.CatalogMovement
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.ui.movement.PendingEntry
import com.carlos.miflujo.ui.movement.QuickEntryCatalog
import com.carlos.miflujo.ui.movement.QuickEntryDialog
import com.carlos.miflujo.ui.movement.QuickEntryDraft
import com.carlos.miflujo.ui.movement.formatAmountInput
import com.carlos.miflujo.ui.movement.validateQuickEntryAmount
import java.io.Serializable

@Composable
internal fun CatalogPreviewMovementDialog(
    categories: List<BackupCategory>,
    original: CatalogMovement?,
    model: CatalogPreviewModel,
    onClose: () -> Unit,
) {
    val previous = original?.draft
    val baseline = rememberSaveable {
        mutableStateOf(original?.let {
            EditBaseline(
                it.updatedAtEpochMillis, it.draft.selection.categoryUuid,
                it.draft.selection.subcategoryUuid, it.draft.detail,
            )
        })
    }.value
    fun normalized(entries: List<PendingEntry>) = entries.map {
        (original?.uuid ?: it.id) to it.draft.toCatalogDraft(baseline)
    }
    val byUuid = categories.associateBy { it.uuid }
    QuickEntryDialog(
        onClose = onClose,
        catalog = QuickEntryCatalog(
            initialDraft = previous?.let {
                QuickEntryDraft(
                    type = it.type, currency = it.currency, amount = it.amountMinor.formatAmountInput(),
                    detail = it.detail.orEmpty(), date = it.date, catalogCategoryUuid = it.selection.categoryUuid,
                )
            },
            validate = { draft -> validateQuickEntryAmount(draft.amount).error },
            categoryLabel = { byUuid[it]?.visibleName() ?: "Sin categoría" },
            categoryIcon = { uuid, modifier, background ->
                val category = byUuid[uuid]
                CatalogIcon(category?.iconKey, modifier, color = category?.iconColor, background = background)
            },
            categoryPicker = { expanded, motion, type, uuid, dismiss, select, create ->
                CatalogCategoryPicker(
                    categories, type, uuid, allowUncategorized = type == MovementType.INCOME,
                    onDismiss = dismiss, onSelect = select, onCreate = create,
                    expanded = expanded, motion = motion,
                )
            },
            categoryEditor = { onCreated, dismiss ->
                CatalogItemEditor(
                    title = "Nueva categoría",
                    initial = CatalogItemInput(),
                    saveLabel = "Guardar categoría",
                    onDismiss = dismiss,
                    save = { input, uuid -> model.categories.createFromEditor(input, uuid) },
                    onSaved = onCreated,
                )
            },
            startSave = { id, entries ->
                model.quickEntrySave.start(id) {
                    val drafts = normalized(entries)
                    if (original == null) {
                        model.saveNew(drafts)
                    } else {
                        check(original.updatedAtEpochMillis == baseline?.updatedAt)
                        model.edit(original, drafts.single().second)
                    }
                }
            },
            awaitSave = { id, entries ->
                model.quickEntrySave.await(id) { model.wasSaved(normalized(entries)) }
            },
        ),
    )
}

// Keep the historical fields needed to compare the exact requested edit after restoration.
private data class EditBaseline(
    val updatedAt: Long,
    val categoryUuid: String?,
    val subcategoryUuid: String?,
    val detail: String?,
) : Serializable

private fun QuickEntryDraft.toCatalogDraft(previous: EditBaseline?): CatalogMovementDraft {
    val savedDetail = if (previous != null && detail == previous.detail.orEmpty()) previous.detail
        else detail.takeUnless(String::isBlank)
    return CatalogMovementDraft(
        type, checkNotNull(validateQuickEntryAmount(amount).amountMinor), currency, date,
        previous?.let { CatalogMovementSelection(it.categoryUuid, it.subcategoryUuid) }
            .withCategory(catalogCategoryUuid), savedDetail,
    )
}
