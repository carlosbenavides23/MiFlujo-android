package com.carlos.miflujo.data.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import com.carlos.miflujo.data.category.CategoryCatalogSchema
import com.carlos.miflujo.data.category.CategoryDao
import com.carlos.miflujo.data.category.CategoryEntity
import com.carlos.miflujo.data.category.SubcategoryEntity
import com.carlos.miflujo.data.category.stored
import com.carlos.miflujo.data.category.toEntity
import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.backup.backup
import com.carlos.miflujo.domain.category.CatalogSyncState
import com.carlos.miflujo.domain.category.Subcategory
import java.time.LocalDateTime

internal suspend fun CategoryDao.readBackupCatalog(): BackupCatalogRows =
    BackupCatalogRows(categories(null, true), allSubcategoriesForSnapshot())

internal class BackupCatalogRows(
    categories: List<CategoryEntity>,
    subcategories: List<SubcategoryEntity>,
) {
    private val parents = categories.map { it.stored() }
    private val children = subcategories.map { it.stored() }
    private val parentUuidsById = parents.associate { it.value.id to it.value.uuid }
    private val childUuidsById = children.associate { it.value.id to it.value.uuid }

    val hasSyncEvidence: Boolean
        get() = parents.any { it.syncState != CatalogSyncState.PENDING } ||
            children.any { it.syncState != CatalogSyncState.PENDING }

    fun categoryUuid(id: Long?): String? = id?.let(parentUuidsById::getValue)
    fun subcategoryUuid(id: Long?): String? = id?.let(childUuidsById::getValue)

    fun snapshot(createdAt: LocalDateTime, movements: List<BackupMovement> = emptyList()): CatalogBackup =
        CatalogBackup(
            createdAt,
            parents.map { it.value.backup(it.revision) },
            children.map { it.value.backup(parentUuidsById.getValue(it.value.categoryId), it.revision) },
            movements,
        ).validatedCopy()
}

private val catalogDeleteGuards = CategoryCatalogSchema.protectionStatements.filter {
    it.startsWith("CREATE TRIGGER categories_no_delete ") ||
        it.startsWith("CREATE TRIGGER subcategories_no_delete ")
}.also { check(it.size == 2) }

/** Caller validates the backup and owns the SQLite transaction, including restoring the guards. */
internal suspend fun replaceBackupContents(
    sql: SupportSQLiteDatabase,
    catalog: CategoryDao,
    backup: CatalogBackup,
    clearMovements: suspend () -> Unit,
    insertMovements: suspend (categoryIds: Map<String, Long>, subcategoryIds: Map<String, Long>) -> Unit,
) {
    sql.execSQL("DROP TRIGGER categories_no_delete")
    sql.execSQL("DROP TRIGGER subcategories_no_delete")
    clearMovements()
    sql.execSQL("DELETE FROM subcategories")
    sql.execSQL("DELETE FROM categories")

    val categoryIds = backup.categories.associate { category ->
        val row = category.domain().toEntity(category.revision, CatalogSyncState.PENDING)
        category.uuid to catalog.insertCategory(row)
    }
    val subcategoryIds = backup.subcategories.associate { child ->
        val value = Subcategory(
            uuid = child.uuid, categoryId = categoryIds.getValue(child.categoryUuid), name = child.name,
            iconKey = child.iconKey, archived = child.archived, sortOrder = child.sortOrder, iconColor = child.iconColor,
        )
        child.uuid to catalog.insertSubcategory(value.toEntity(child.revision, CatalogSyncState.PENDING))
    }
    insertMovements(categoryIds, subcategoryIds)
    catalogDeleteGuards.forEach(sql::execSQL)
}
