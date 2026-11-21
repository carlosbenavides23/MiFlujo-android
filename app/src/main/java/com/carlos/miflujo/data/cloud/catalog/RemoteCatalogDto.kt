package com.carlos.miflujo.data.cloud.catalog

import com.carlos.miflujo.domain.category.CatalogIconColor
import com.carlos.miflujo.domain.category.CatalogRevision
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.category.StoredCategory
import com.carlos.miflujo.domain.category.StoredSubcategory
import com.carlos.miflujo.domain.category.isCanonicalCatalogUuid
import com.carlos.miflujo.domain.category.requireValidCatalogMetadata

sealed interface RemoteCatalogEntry {
    val uuid: String
    val name: String
    val iconKey: String
    val iconColor: CatalogIconColor?
    val archived: Boolean
    val sortOrder: Int
    val revision: CatalogRevision
}

data class RemoteCategoryDto(
    override val uuid: String,
    override val name: String,
    val scope: CategoryScope,
    val reportGroup: ReportGroup,
    override val iconKey: String,
    override val archived: Boolean,
    override val sortOrder: Int,
    override val revision: CatalogRevision,
    override val iconColor: CatalogIconColor? = null,
) : RemoteCatalogEntry {
    init {
        // Reuse the scope, group and metadata checks of the domain model.
        Category(uuid = uuid, name = name, scope = scope, reportGroup = reportGroup, iconKey = iconKey)
        requireRemoteTextLimits(name, iconKey)
        require(uuid !in seedSubcategoryParents) { "Reserved subcategory UUID" }
        seedCategories[uuid]?.let { require(scope == it.scope && reportGroup == it.reportGroup) }
    }
}

data class RemoteSubcategoryDto(
    override val uuid: String,
    val categoryUuid: String,
    override val name: String,
    override val iconKey: String,
    override val archived: Boolean,
    override val sortOrder: Int,
    override val revision: CatalogRevision,
    override val iconColor: CatalogIconColor? = null,
) : RemoteCatalogEntry {
    init {
        requireValidCatalogMetadata(uuid, name, iconKey)
        requireRemoteTextLimits(name, iconKey)
        require(isCanonicalCatalogUuid(categoryUuid) && categoryUuid != uuid) { "Invalid parent UUID" }
        require(uuid !in seedCategories && categoryUuid !in seedSubcategoryParents) { "Invalid catalog kind" }
        seedSubcategoryParents[uuid]?.let { require(categoryUuid == it) { "Reserved subcategory parent" } }
    }
}

private val seedCategories = LegacyCategoryCatalog.categories.associateBy { it.uuid }
private val seedSubcategoryParents = LegacyCategoryCatalog.subcategories.associate { child ->
    child.uuid to LegacyCategoryCatalog.categories.single { it.id == child.categoryId }.uuid
}

private fun requireRemoteTextLimits(name: String, iconKey: String) {
    require(name.toByteArray(Charsets.UTF_8).size <= 480) { "Catalog name exceeds 480 UTF-8 bytes" }
    require(iconKey.toByteArray(Charsets.UTF_8).size <= 128) { "Icon key exceeds 128 UTF-8 bytes" }
}

fun StoredCategory.toRemoteDto(): RemoteCategoryDto = with(value) {
    RemoteCategoryDto(uuid, name, scope, reportGroup, iconKey, archived, sortOrder, revision, iconColor)
}

fun StoredSubcategory.toRemoteDto(parent: Category): RemoteSubcategoryDto = with(value) {
    require(parent.id == categoryId) { "Wrong local parent" }
    RemoteSubcategoryDto(uuid, parent.uuid, name, iconKey, archived, sortOrder, revision, iconColor)
}

/** The UID comes from the authorized account used for the operation. The server enforces ownership. */
object CloudCatalogPath {
    fun category(authenticatedUid: String, uuid: String) = document(authenticatedUid, "categories", uuid)
    fun subcategory(authenticatedUid: String, uuid: String) = document(authenticatedUid, "subcategories", uuid)

    private fun document(uid: String, collection: String, uuid: String): String {
        require(isValidCatalogUid(uid))
        require(isCanonicalCatalogUuid(uuid))
        return "users/$uid/$collection/$uuid"
    }
}
