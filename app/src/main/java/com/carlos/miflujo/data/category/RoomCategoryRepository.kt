package com.carlos.miflujo.data.category

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.carlos.miflujo.domain.category.*
import com.carlos.miflujo.domain.model.MovementType

/** Uses the shared experimental catalog tables, outside the application's movement DB. */
class RoomCategoryRepository internal constructor(
    private val database: RoomDatabase,
    private val dao: CategoryDao,
) : CategoryRepository {
    constructor(database: CategoryCatalogDatabase) : this(database, database.categoryDao())
    private suspend fun next(revision: CatalogRevision) = revision.nextFor(dao.actorUuid())
    private suspend fun initialRevision(): CatalogRevision {
        val actor = dao.actorUuid()
        return CatalogRevision(0, actor).nextFor(actor)
    }

    override suspend fun createCategory(category: Category): StoredCategory = database.withTransaction {
        require(category.id == 0L) { "New category must not supply a local ID" }
        val revision = initialRevision()
        val id = dao.insertCategory(category.toEntity(revision, CatalogSyncState.PENDING))
        StoredCategory(category.copy(id = id), revision, CatalogSyncState.PENDING)
    }
    override suspend fun createSubcategory(subcategory: Subcategory): StoredSubcategory = database.withTransaction {
        require(subcategory.id == 0L) { "New subcategory must not supply a local ID" }
        val parent = requireNotNull(dao.categoryById(subcategory.categoryId)) { "Unknown parent" }
        require(!parent.archived) { "Cannot create children of an archived category" }
        val revision = initialRevision()
        val id = dao.insertSubcategory(subcategory.toEntity(revision, CatalogSyncState.PENDING))
        StoredSubcategory(subcategory.copy(id = id), revision, CatalogSyncState.PENDING)
    }
    override suspend fun category(uuid: String) = dao.category(uuid)?.stored()
    override suspend fun subcategory(uuid: String) = dao.subcategory(uuid)?.stored()
    override suspend fun categories(type: MovementType?, includeArchived: Boolean) =
        dao.categories(type?.name, includeArchived).map { it.stored() }
    override suspend fun subcategories(parentUuid: String, includeArchived: Boolean): List<StoredSubcategory> = database.withTransaction {
        val parent = requireNotNull(dao.category(parentUuid)) { "Unknown parent" }
        if (parent.archived && !includeArchived) emptyList()
        else dao.subcategories(parent.id, includeArchived).map { it.stored() }
    }
    override suspend fun updateCategory(category: Category, expected: CatalogRevision): StoredCategory = database.withTransaction {
        changeCategory(category, expected)
    }
    private suspend fun changeCategory(value: Category, expected: CatalogRevision): StoredCategory {
        val old = requireNotNull(dao.categoryById(value.id)) { "Unknown category" }.stored()
        value.requireSameStructureAs(old.value)
        check(expected == old.revision) { "Category was edited since it was read" }
        if (value == old.value) return old
        val revision = next(old.revision)
        check(dao.updateCategory(value.toEntity(revision, CatalogSyncState.PENDING)) == 1)
        return StoredCategory(value, revision, CatalogSyncState.PENDING)
    }
    override suspend fun updateSubcategory(subcategory: Subcategory, expected: CatalogRevision): StoredSubcategory = database.withTransaction {
        changeSubcategory(subcategory, expected)
    }
    private suspend fun changeSubcategory(value: Subcategory, expected: CatalogRevision): StoredSubcategory {
        val old = requireNotNull(dao.subcategoryById(value.id)) { "Unknown subcategory" }.stored()
        value.requireSameStructureAs(old.value)
        check(expected == old.revision) { "Subcategory was edited since it was read" }
        if (value == old.value) return old
        val revision = next(old.revision)
        check(dao.updateSubcategory(value.toEntity(revision, CatalogSyncState.PENDING)) == 1)
        return StoredSubcategory(value, revision, CatalogSyncState.PENDING)
    }
    override suspend fun reorderCategories(uuids: List<String>) = database.withTransaction {
        require(uuids.distinct().size == uuids.size) { "Duplicate reorder entry" }
        uuids.forEachIndexed { position, uuid ->
            val old = requireNotNull(dao.category(uuid)) { "Unknown category" }.stored()
            changeCategory(old.value.edited(sortOrder = position), old.revision)
        }
    }
    override suspend fun reorderSubcategories(parentUuid: String, uuids: List<String>) = database.withTransaction {
        require(uuids.distinct().size == uuids.size) { "Duplicate reorder entry" }
        val parent = requireNotNull(dao.category(parentUuid)) { "Unknown parent" }
        uuids.forEachIndexed { position, uuid ->
            val old = requireNotNull(dao.subcategory(uuid)) { "Unknown subcategory" }.stored()
            require(old.value.categoryId == parent.id) { "Wrong parent" }
            changeSubcategory(old.value.edited(sortOrder = position), old.revision)
        }
    }
    override suspend fun pendingCategories() = dao.pendingCategories().map { it.stored() }
    override suspend fun pendingSubcategories() = dao.pendingSubcategories().map { it.stored() }
    override suspend fun acknowledgeCategory(uuid: String, revision: CatalogRevision) = categoryState(uuid, revision, CatalogSyncState.SYNCED)
    override suspend fun acknowledgeSubcategory(uuid: String, revision: CatalogRevision) = subcategoryState(uuid, revision, CatalogSyncState.SYNCED)
    override suspend fun failCategory(uuid: String, revision: CatalogRevision) = categoryState(uuid, revision, CatalogSyncState.ERROR)
    override suspend fun failSubcategory(uuid: String, revision: CatalogRevision) = subcategoryState(uuid, revision, CatalogSyncState.ERROR)
    private suspend fun categoryState(uuid: String, revision: CatalogRevision, state: CatalogSyncState) =
        dao.setCategorySyncState(uuid, revision.counter, revision.actorUuid, state.name) == 1
    private suspend fun subcategoryState(uuid: String, revision: CatalogRevision, state: CatalogSyncState) =
        dao.setSubcategorySyncState(uuid, revision.counter, revision.actorUuid, state.name) == 1
}
