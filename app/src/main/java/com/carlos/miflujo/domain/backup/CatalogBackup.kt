package com.carlos.miflujo.domain.backup

import com.carlos.miflujo.domain.category.*
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Portable backup DTOs, not the active financial model. No local IDs or sending state. */
data class BackupCategory(
    val uuid: String, val name: String, val scope: CategoryScope, val reportGroup: ReportGroup,
    val iconKey: String, val archived: Boolean, val sortOrder: Int, val revision: CatalogRevision,
    val iconColor: CatalogIconColor? = null,
) {
    fun domain() = Category(uuid = uuid, name = name, scope = scope, reportGroup = reportGroup,
        iconKey = iconKey, archived = archived, sortOrder = sortOrder, iconColor = iconColor)
}
data class BackupSubcategory(
    val uuid: String, val categoryUuid: String, val name: String, val iconKey: String,
    val archived: Boolean, val sortOrder: Int, val revision: CatalogRevision,
    val iconColor: CatalogIconColor? = null,
)
data class BackupMovement(
    val uuid: String, val type: MovementType, val amountMinor: Long, val currency: Currency,
    val date: LocalDate, val categoryUuid: String?, val subcategoryUuid: String?, val detail: String?,
    val createdAt: LocalDateTime, val updatedAt: LocalDateTime,
)
data class CatalogBackup(
    val createdAt: LocalDateTime, val categories: List<BackupCategory>,
    val subcategories: List<BackupSubcategory>, val movements: List<BackupMovement>,
) {
    /** Copy input lists before validation/use so the restore transaction owns its snapshot. */
    fun validatedCopy(): CatalogBackup = copy(
        categories = categories.toList(), subcategories = subcategories.toList(), movements = movements.toList(),
    ).also { it.validate() }

    private fun validate() {
        fun unique(ids: List<String>) { require(ids.toSet().size == ids.size) { "Duplicate portable UUID" } }
        unique(categories.map { it.uuid } + subcategories.map { it.uuid }); unique(movements.map { it.uuid })
        val parents = categories.associateBy { it.uuid }
        val children = subcategories.associateBy { it.uuid }
        categories.forEach { it.domain() }
        subcategories.forEach { child ->
            require(isCanonicalCatalogUuid(child.categoryUuid) && parents.containsKey(child.categoryUuid)) { "Unknown category" }
            // Reuse domain metadata validation; actual parent identity is checked above by UUID.
            Subcategory(uuid = child.uuid, categoryId = 1, name = child.name, iconKey = child.iconKey,
                archived = child.archived, sortOrder = child.sortOrder)
        }
        movements.forEach { movement ->
            // Same canonical spelling rule as legacy backup movements, without tightening v1/v2.
            require(UUID.fromString(movement.uuid).toString() == movement.uuid) { "Noncanonical movement UUID" }
            require(movement.amountMinor > 0) { "Amount must be positive" }
            if (movement.categoryUuid == null) {
                require(movement.subcategoryUuid == null) { "Subcategory without category" }
            } else {
                val parent = requireNotNull(parents[movement.categoryUuid]) { "Unknown movement category" }
                require(parent.scope.supports(movement.type)) { "Incompatible category scope" }
                movement.subcategoryUuid?.let { uuid ->
                    val child = requireNotNull(children[uuid]) { "Unknown movement subcategory" }
                    require(child.categoryUuid == parent.uuid) { "Wrong subcategory parent" }
                }
            }
        }
        // Reserved seed UUIDs cannot be repurposed with different structural properties.
        val seeds = LegacyCategoryCatalog.categories.associateBy { it.uuid }
        categories.forEach { c -> seeds[c.uuid]?.let { require(c.scope == it.scope && c.reportGroup == it.reportGroup) } }
        val seedChildren = LegacyCategoryCatalog.subcategories.associateBy { it.uuid }
        subcategories.forEach { s -> seedChildren[s.uuid]?.let { seed ->
            require(s.categoryUuid == LegacyCategoryCatalog.categories.single { it.id == seed.categoryId }.uuid)
        } }
        require(categories.none { it.uuid in seedChildren } && subcategories.none { it.uuid in seeds })
    }

    /** A full replacement may remap local IDs, but must not repurpose known portable identities. */
    fun requireCompatibleWith(previous: CatalogBackup) {
        val oldCategories = previous.categories.associateBy { it.uuid }
        val oldChildren = previous.subcategories.associateBy { it.uuid }
        require(categories.none { it.uuid in oldChildren } && subcategories.none { it.uuid in oldCategories }) { "Catalog identity changed kind" }
        categories.forEach { c -> oldCategories[c.uuid]?.let { require(c.scope == it.scope && c.reportGroup == it.reportGroup) { "Category structure changed" } } }
        subcategories.forEach { s -> oldChildren[s.uuid]?.let { require(s.categoryUuid == it.categoryUuid) { "Subcategory parent changed" } } }
    }
}

fun Category.backup(revision: CatalogRevision) = BackupCategory(uuid, name, scope, reportGroup, iconKey, archived, sortOrder, revision, iconColor)
fun Subcategory.backup(parentUuid: String, revision: CatalogRevision) =
    BackupSubcategory(uuid, parentUuid, name, iconKey, archived, sortOrder, revision, iconColor)
