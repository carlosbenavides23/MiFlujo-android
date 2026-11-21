package com.carlos.miflujo.data.category

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.carlos.miflujo.domain.category.*

@Entity(tableName = "categories", indices = [Index(value = ["uuid"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val scope: String,
    val reportGroup: String,
    val iconKey: String,
    val archived: Boolean,
    val sortOrder: Int,
    val revisionCounter: Long,
    val revisionActorUuid: String,
    val syncState: String,
)

@Entity(
    tableName = "subcategories",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["categoryId"])],
)
data class SubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val archived: Boolean,
    val sortOrder: Int,
    val revisionCounter: Long,
    val revisionActorUuid: String,
    val syncState: String,
)

@Entity(tableName = "catalog_installation", indices = [Index(value = ["actorUuid"], unique = true)])
data class CatalogInstallationEntity(@PrimaryKey val id: Int = 1, val actorUuid: String)

fun Category.toEntity(revision: CatalogRevision, state: CatalogSyncState) = CategoryEntity(
    id, uuid, name, scope.name, reportGroup.name, iconKey, archived, sortOrder,
    revision.counter, revision.actorUuid, state.name,
)
fun CategoryEntity.toDomain() = Category(id, uuid, name, CategoryScope.valueOf(scope), ReportGroup.valueOf(reportGroup), iconKey, archived, sortOrder)
fun Subcategory.toEntity(revision: CatalogRevision, state: CatalogSyncState) = SubcategoryEntity(
    id, uuid, categoryId, name, iconKey, archived, sortOrder, revision.counter, revision.actorUuid, state.name,
)
fun SubcategoryEntity.toDomain() = Subcategory(id, uuid, categoryId, name, iconKey, archived, sortOrder)
fun CategoryEntity.stored() = StoredCategory(toDomain(), CatalogRevision(revisionCounter, revisionActorUuid), CatalogSyncState.valueOf(syncState))
fun SubcategoryEntity.stored() = StoredSubcategory(toDomain(), CatalogRevision(revisionCounter, revisionActorUuid), CatalogSyncState.valueOf(syncState))
