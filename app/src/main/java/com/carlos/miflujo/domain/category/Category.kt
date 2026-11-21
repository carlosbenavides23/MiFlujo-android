package com.carlos.miflujo.domain.category

import com.carlos.miflujo.domain.model.MovementType

internal fun requireValidCatalogMetadata(uuid: String, name: String, iconKey: String) {
    require(isCanonicalCatalogUuid(uuid) && name.isNotBlank() && iconKey.isNotBlank())
}

/** Stable reporting keys, independent of user-visible names and icons. */
enum class ReportGroup { INCOME, FIXED_COST, MAINTENANCE, OTHER }
enum class CategoryScope {
    INCOME, EXPENSE, BOTH;
    fun supports(type: MovementType) = this == BOTH || name == type.name
}

data class Category(
    val id: Long = 0,
    val uuid: String = newCatalogUuid(),
    val name: String,
    val scope: CategoryScope,
    val reportGroup: ReportGroup,
    val iconKey: String = "category",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
) {
    init {
        require(id >= 0)
        requireValidCatalogMetadata(uuid, name, iconKey)
        require(scope == CategoryScope.INCOME || reportGroup != ReportGroup.INCOME)
        require(scope != CategoryScope.INCOME || reportGroup == ReportGroup.INCOME)
    }

    fun edited(name: String = this.name, iconKey: String = this.iconKey,
               archived: Boolean = this.archived, sortOrder: Int = this.sortOrder): Category =
        copy(name = name, iconKey = iconKey, archived = archived, sortOrder = sortOrder)

    /** Repository boundary: data-class copy and deserialization are not trusted updates. */
    fun requireSameStructureAs(previous: Category) {
        require(id == previous.id && uuid == previous.uuid && scope == previous.scope && reportGroup == previous.reportGroup)
    }
}

data class Subcategory(
    val id: Long = 0,
    val uuid: String = newCatalogUuid(),
    val categoryId: Long,
    val name: String,
    val iconKey: String = "category",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
) {
    init {
        require(id >= 0 && categoryId > 0)
        requireValidCatalogMetadata(uuid, name, iconKey)
    }

    fun edited(name: String = this.name, iconKey: String = this.iconKey,
               archived: Boolean = this.archived, sortOrder: Int = this.sortOrder): Subcategory =
        copy(name = name, iconKey = iconKey, archived = archived, sortOrder = sortOrder)

    fun requireSameStructureAs(previous: Subcategory) {
        require(id == previous.id && uuid == previous.uuid && categoryId == previous.categoryId)
    }
}

/** Target relationship for the future Movement model; not yet written by the legacy form. */
data class CategorySelection(val categoryId: Long? = null, val subcategoryId: Long? = null) {
    init {
        require(categoryId == null || categoryId > 0)
        require(subcategoryId == null || (categoryId != null && subcategoryId > 0))
    }

    /** previous/previousType must come from the stored movement, never from an editable form. */
    fun validate(
        type: MovementType, category: Category?, subcategory: Subcategory?,
        previous: CategorySelection? = null, previousType: MovementType? = null,
    ): Set<CategorySelectionError> = buildSet {
        if (categoryId == null) {
            if (category != null || subcategory != null) add(CategorySelectionError.UNEXPECTED_REFERENCE)
            return@buildSet
        }
        if (category == null || category.id != categoryId) {
            add(CategorySelectionError.UNKNOWN_CATEGORY)
        } else {
            if (!category.scope.supports(type)) add(CategorySelectionError.WRONG_SCOPE)
            if (category.archived && !(previous == this@CategorySelection && previousType == type))
                add(CategorySelectionError.ARCHIVED_CATEGORY)
        }
        if (subcategoryId == null) {
            if (subcategory != null) add(CategorySelectionError.UNEXPECTED_REFERENCE)
        } else if (subcategory == null || subcategory.id != subcategoryId) {
            add(CategorySelectionError.UNKNOWN_SUBCATEGORY)
        } else {
            if (subcategory.categoryId != categoryId) add(CategorySelectionError.WRONG_PARENT)
            if (subcategory.archived && !(previous == this@CategorySelection && previousType == type))
                add(CategorySelectionError.ARCHIVED_SUBCATEGORY)
        }
    }

    fun isAssignable(type: MovementType, category: Category?, subcategory: Subcategory?): Boolean =
        validate(type, category, subcategory).isEmpty()
}

enum class CategorySelectionError {
    UNKNOWN_CATEGORY, UNKNOWN_SUBCATEGORY, UNEXPECTED_REFERENCE, WRONG_PARENT, WRONG_SCOPE,
    ARCHIVED_CATEGORY, ARCHIVED_SUBCATEGORY,
}
