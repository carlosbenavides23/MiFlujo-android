package com.carlos.miflujo.domain.category

import com.carlos.miflujo.domain.model.MovementType

enum class CatalogSyncState { PENDING, SYNCED, ERROR }
data class StoredCategory(val value: Category, val revision: CatalogRevision, val syncState: CatalogSyncState)
data class StoredSubcategory(val value: Subcategory, val revision: CatalogRevision, val syncState: CatalogSyncState)

interface CategoryRepository {
    suspend fun createCategory(category: Category): StoredCategory
    suspend fun createSubcategory(subcategory: Subcategory): StoredSubcategory
    suspend fun category(uuid: String): StoredCategory?
    suspend fun subcategory(uuid: String): StoredSubcategory?
    suspend fun categories(type: MovementType? = null, includeArchived: Boolean = false): List<StoredCategory>
    suspend fun subcategories(parentUuid: String, includeArchived: Boolean = false): List<StoredSubcategory>
    // edited() on the domain value supports rename/icon/archive/order without structural edits.
    suspend fun updateCategory(category: Category, expected: CatalogRevision): StoredCategory
    suspend fun updateSubcategory(subcategory: Subcategory, expected: CatalogRevision): StoredSubcategory
    suspend fun reorderCategories(uuids: List<String>)
    suspend fun reorderSubcategories(parentUuid: String, uuids: List<String>)
    suspend fun pendingCategories(): List<StoredCategory>
    suspend fun pendingSubcategories(): List<StoredSubcategory>
    suspend fun acknowledgeCategory(uuid: String, revision: CatalogRevision): Boolean
    suspend fun acknowledgeSubcategory(uuid: String, revision: CatalogRevision): Boolean
    suspend fun failCategory(uuid: String, revision: CatalogRevision): Boolean
    suspend fun failSubcategory(uuid: String, revision: CatalogRevision): Boolean
}
