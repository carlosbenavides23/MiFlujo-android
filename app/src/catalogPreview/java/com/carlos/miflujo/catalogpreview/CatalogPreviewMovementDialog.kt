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
import com.carlos.miflujo.domain.backup.BackupSubcategory
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
    categories: List<BackupCategory>?,
    subcategories: List<BackupSubcategory>?,
    original: CatalogMovement?,
    model: CatalogPreviewModel,
    visible: Boolean,
    onLeaveReconciliation: () -> Unit,
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
    val byUuid = categories?.associateBy { it.uuid }
    val hasActiveHistoricalSubcategory = subcategories.orEmpty().any { it.uuid == baseline?.subcategoryUuid && !it.archived }
    QuickEntryDialog(
        onClose = onClose,
        visible = visible,
        onLeaveReconciliation = onLeaveReconciliation,
        catalog = QuickEntryCatalog(
            initialDraft = previous?.let {
                QuickEntryDraft(
                    type = it.type, currency = it.currency, amount = it.amountMinor.formatAmountInput(),
                    detail = it.detail.orEmpty(), date = it.date, catalogCategoryUuid = it.selection.categoryUuid,
                )
            },
            validate = { draft ->
                if (draft.pendingCatalogType != null) "Esperá a que cargue el catálogo para aplicar el cambio de tipo."
                else validateQuickEntryAmount(draft.amount).error
            },
            catalogLoaded = byUuid != null,
            changeType = { draft, type, historical ->
                draft.changeCatalogType(type, historical, byUuid, hasActiveHistoricalSubcategory)
            },
            selectCategory = { draft, uuid, historical ->
                draft.selectCatalogCategory(uuid, historical)
            },
            categoryLabel = { catalogCategoryLabel(it, byUuid) },
            categoryIcon = { uuid, modifier, background ->
                val category = byUuid?.get(uuid)
                CatalogIcon(category?.iconKey, modifier, color = category?.iconColor, background = background)
            },
            categoryPicker = { expanded, motion, type, uuid, dismiss, select, create ->
                CatalogCategoryPicker(
                    categories.orEmpty(), type, uuid, allowUncategorized = true,
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
            retainsSave = model.quickEntrySave::retains,
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
        selectionForSave(previous?.let { CatalogMovementSelection(it.categoryUuid, it.subcategoryUuid) }),
        savedDetail,
    )
}

internal fun QuickEntryDraft.changeCatalogType(
    type: MovementType,
    historical: QuickEntryDraft?,
    categories: Map<String, BackupCategory>?,
    hasActiveHistoricalSubcategory: Boolean,
): QuickEntryDraft {
    if (type == this.type) return copy(pendingCatalogType = null)
    val original = historical?.takeUnless { historicalCategoryReplaced }
    val uuid = catalogCategoryUuid ?: original?.catalogCategoryUuid
    if (uuid != null && categories == null && !catalogCategoryCreatedInEditor) {
        return copy(pendingCatalogType = type)
    }
    val category = categories?.get(uuid)
    val keepsOriginalCategory = uuid != null && uuid == original?.catalogCategoryUuid
    val returningToOriginal = keepsOriginalCategory && type == original?.type
    val compatible = when {
        returningToOriginal -> category?.scope?.supports(type) == true
        // The editor creates an active BOTH category before the next snapshot arrives.
        category == null -> catalogCategoryCreatedInEditor && uuid != null
        else -> !category.archived && category.scope.supports(type)
    }
    return copy(
        type = type,
        pendingCatalogType = null,
        catalogCategoryUuid = uuid.takeIf { compatible },
        catalogCategoryCreatedInEditor = catalogCategoryCreatedInEditor && compatible,
        // Room only accepts an archived child on the unchanged historical relationship.
        omitHistoricalSubcategory = compatible && keepsOriginalCategory &&
            !returningToOriginal && !hasActiveHistoricalSubcategory,
    )
}

internal fun catalogCategoryLabel(uuid: String?, categories: Map<String, BackupCategory>?): String =
    if (uuid == null) "Sin categoría" else categories?.get(uuid)?.visibleName() ?: "Categoría seleccionada"

internal fun QuickEntryDraft.selectCatalogCategory(uuid: String?, historical: QuickEntryDraft?): QuickEntryDraft =
    copy(
        catalogCategoryUuid = uuid,
        catalogCategoryCreatedInEditor = false,
        historicalCategoryReplaced = historicalCategoryReplaced ||
            (historical?.catalogCategoryUuid != null && uuid != historical.catalogCategoryUuid),
    )

internal fun QuickEntryDraft.selectionForSave(historical: CatalogMovementSelection?): CatalogMovementSelection =
    historical.takeUnless { historicalCategoryReplaced || omitHistoricalSubcategory }
        .withCategory(catalogCategoryUuid)
