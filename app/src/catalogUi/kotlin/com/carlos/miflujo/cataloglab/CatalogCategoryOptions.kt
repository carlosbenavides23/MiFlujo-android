package com.carlos.miflujo.cataloglab

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.carlos.miflujo.domain.backup.BackupCategory
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.MovementType

internal fun CatalogMovementSelection?.withCategory(uuid: String?): CatalogMovementSelection =
    this?.takeIf { it.categoryUuid == uuid } ?: CatalogMovementSelection(uuid)

internal fun BackupCategory.visibleName(): String = if (archived) "$name (archivada)" else name

@Composable
internal fun CatalogCategoryOptions(
    categories: List<BackupCategory>,
    type: MovementType,
    allowUncategorized: Boolean,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
) {
    if (allowUncategorized) {
        DropdownMenuItem(text = { Text("Sin categoría") }, onClick = { onSelect(null) })
    }
    DropdownMenuItem(text = { Text("Crear nueva categoría") }, onClick = onCreate)
    categories.forEach { category ->
        if (!category.archived && category.scope.supports(type)) {
            DropdownMenuItem(
                text = { Text(category.name) },
                leadingIcon = {
                    CatalogIcon(category.iconKey, color = category.iconColor,
                        background = MaterialTheme.colorScheme.surfaceContainer)
                },
                onClick = { onSelect(category.uuid) },
            )
        }
    }
}
