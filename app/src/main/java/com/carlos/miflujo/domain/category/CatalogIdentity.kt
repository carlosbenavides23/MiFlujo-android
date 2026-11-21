package com.carlos.miflujo.domain.category

import java.util.UUID

/** Same lowercase canonical RFC UUID shape as the portable movement identities. */
fun isCanonicalCatalogUuid(value: String): Boolean = try {
    val uuid = UUID.fromString(value)
    uuid.toString() == value && uuid.variant() == 2 && uuid.version() in 1..5
} catch (_: IllegalArgumentException) {
    false
}

fun newCatalogUuid(): String = UUID.randomUUID().toString()

/** Logical clock, not wall time. The installation UUID must survive restarts. */
data class CatalogRevision(val counter: Long, val actorUuid: String) : Comparable<CatalogRevision> {
    init { require(counter >= 0 && isCanonicalCatalogUuid(actorUuid)) }
    override fun compareTo(other: CatalogRevision): Int =
        compareValuesBy(this, other, { it.counter }, { it.actorUuid })

    fun nextFor(actorUuid: String): CatalogRevision {
        check(counter < Long.MAX_VALUE) { "Catalog revision exhausted" }
        return CatalogRevision(counter + 1, actorUuid)
    }
}

/** Portable tie-break: local Room IDs and translated names must never affect order. */
val categoryOrder: Comparator<Category> = compareBy<Category> { it.sortOrder }.thenBy { it.uuid }
val subcategoryOrder: Comparator<Subcategory> = compareBy<Subcategory> { it.sortOrder }.thenBy { it.uuid }
