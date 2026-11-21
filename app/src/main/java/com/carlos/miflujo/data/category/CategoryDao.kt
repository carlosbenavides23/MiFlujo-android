package com.carlos.miflujo.data.category

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/** Internal persistence boundary: application writes go through RoomCategoryRepository. */
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE uuid = :uuid") suspend fun category(uuid: String): CategoryEntity?
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun categoryById(id: Long): CategoryEntity?
    @Query("SELECT * FROM subcategories WHERE uuid = :uuid") suspend fun subcategory(uuid: String): SubcategoryEntity?
    @Query("SELECT * FROM subcategories WHERE id = :id") suspend fun subcategoryById(id: Long): SubcategoryEntity?
    @Query("SELECT * FROM categories WHERE (:scope IS NULL OR scope = :scope OR scope = 'BOTH') AND (:includeArchived OR archived = 0) ORDER BY sortOrder, uuid COLLATE BINARY")
    suspend fun categories(scope: String?, includeArchived: Boolean): List<CategoryEntity>
    @Query("SELECT * FROM subcategories WHERE categoryId = :parentId AND (:includeArchived OR archived = 0) ORDER BY sortOrder, uuid COLLATE BINARY")
    suspend fun subcategories(parentId: Long, includeArchived: Boolean): List<SubcategoryEntity>
    // Include every child, even on a corrupt DB: snapshot validation must not silently omit orphans.
    @Query("SELECT * FROM subcategories ORDER BY sortOrder, uuid COLLATE BINARY")
    suspend fun allSubcategoriesForSnapshot(): List<SubcategoryEntity>
    @Insert suspend fun insertCategory(category: CategoryEntity): Long
    @Insert suspend fun insertSubcategory(subcategory: SubcategoryEntity): Long
    @Update suspend fun updateCategory(category: CategoryEntity): Int
    @Update suspend fun updateSubcategory(subcategory: SubcategoryEntity): Int
    @Query("SELECT actorUuid FROM catalog_installation WHERE id = 1") suspend fun actorUuid(): String
    @Query("SELECT * FROM categories WHERE syncState != 'SYNCED' ORDER BY sortOrder, uuid COLLATE BINARY")
    suspend fun pendingCategories(): List<CategoryEntity>
    @Query("SELECT s.* FROM subcategories s INNER JOIN categories c ON c.id = s.categoryId WHERE s.syncState != 'SYNCED' ORDER BY c.uuid COLLATE BINARY, s.sortOrder, s.uuid COLLATE BINARY")
    suspend fun pendingSubcategories(): List<SubcategoryEntity>
    @Query("UPDATE categories SET syncState = :state WHERE uuid = :uuid AND revisionCounter = :counter AND revisionActorUuid = :actor AND (:state != 'ERROR' OR syncState != 'SYNCED')")
    suspend fun setCategorySyncState(uuid: String, counter: Long, actor: String, state: String): Int
    @Query("UPDATE subcategories SET syncState = :state WHERE uuid = :uuid AND revisionCounter = :counter AND revisionActorUuid = :actor AND (:state != 'ERROR' OR syncState != 'SYNCED')")
    suspend fun setSubcategorySyncState(uuid: String, counter: Long, actor: String, state: String): Int
}
