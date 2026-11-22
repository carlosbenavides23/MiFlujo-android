package com.carlos.miflujo.catalogpreview

import androidx.compose.runtime.Composable
import com.carlos.miflujo.cataloglab.CatalogCategoryOptions
import com.carlos.miflujo.cataloglab.CatalogIcon
import com.carlos.miflujo.cataloglab.CatalogItemEditor
import com.carlos.miflujo.cataloglab.CatalogItemInput
import com.carlos.miflujo.cataloglab.createFromEditor
import com.carlos.miflujo.cataloglab.visibleName
import com.carlos.miflujo.cataloglab.withCategory
import com.carlos.miflujo.domain.backup.BackupCategory
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.model.CatalogMovement
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.ui.movement.QuickEntryCatalog
import com.carlos.miflujo.ui.movement.QuickEntryDialog
import com.carlos.miflujo.ui.movement.QuickEntryDraft
import com.carlos.miflujo.ui.movement.formatAmountInput
import com.carlos.miflujo.ui.movement.parseAmountMinorOrNull

@Composable
internal fun CatalogPreviewMovementDialog(
    categories: List<BackupCategory>,
    original: CatalogMovement?,
    model: CatalogPreviewModel,
    onClose: () -> Unit,
) {
    val previous = original?.draft
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
            validate = { draft ->
                when {
                    (parseAmountMinorOrNull(draft.amount) ?: 0) <= 0 -> "Ingresá un monto positivo válido."
                    draft.type == MovementType.EXPENSE && draft.catalogCategoryUuid == null -> "Elegí una categoría."
                    else -> null
                }
            },
            categoryLabel = { byUuid[it]?.visibleName() ?: "Categoría" },
            categoryIcon = { uuid, modifier, background ->
                val category = byUuid[uuid]
                CatalogIcon(category?.iconKey, modifier, color = category?.iconColor, background = background)
            },
            categoryOptions = { type, select, create ->
                CatalogCategoryOptions(
                    categories, type, allowUncategorized = type == MovementType.INCOME,
                    onSelect = select, onCreate = create,
                )
            },
            categoryEditor = { type, onCreated, dismiss ->
                val scope = if (type == MovementType.INCOME) CategoryScope.INCOME else CategoryScope.EXPENSE
                CatalogItemEditor(
                    title = "Nueva categoría",
                    initial = CatalogItemInput(scope = scope),
                    saveLabel = "Guardar categoría",
                    allowScope = true,
                    scopeOptions = listOf(scope, CategoryScope.BOTH),
                    onDismiss = dismiss,
                    save = { input, uuid -> model.categories.createFromEditor(input, uuid) },
                    onSaved = onCreated,
                )
            },
            onSave = { entries ->
                if (original == null) model.saveNew(entries.map { it.id to it.draft.toCatalogDraft(null) })
                else model.edit(original, entries.single().draft.toCatalogDraft(previous))
            },
        ),
    )
}

private fun QuickEntryDraft.toCatalogDraft(previous: CatalogMovementDraft?): CatalogMovementDraft {
    val savedDetail = if (previous != null && detail == previous.detail.orEmpty()) previous.detail
        else detail.takeUnless(String::isBlank)
    return CatalogMovementDraft(
        type, checkNotNull(parseAmountMinorOrNull(amount)), currency, date,
        previous?.selection.withCategory(catalogCategoryUuid), savedDetail,
    )
}
