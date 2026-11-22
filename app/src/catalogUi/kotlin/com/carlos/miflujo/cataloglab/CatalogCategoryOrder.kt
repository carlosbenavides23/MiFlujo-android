package com.carlos.miflujo.cataloglab

import com.carlos.miflujo.domain.backup.BackupCategory
import com.carlos.miflujo.domain.backup.CatalogBackup
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The snapshot contains visible persisted movements. Their original registration times are UTC. */
internal fun CatalogBackup.categoriesByRecentUsage(
    now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
): List<BackupCategory> {
    val cutoff = now.minusDays(90)
    val usage = mutableMapOf<String, CategoryUsage>()
    for (movement in movements) {
        val uuid = movement.categoryUuid ?: continue
        val created = movement.createdAt
        if (created < cutoff || created > now) continue
        val category = usage.getOrPut(uuid) { CategoryUsage(lastUsed = created) }
        category.count++
        category.lastUsed = maxOf(category.lastUsed, created)
    }
    return categories.sortedWith(
        compareByDescending<BackupCategory> { usage[it.uuid]?.count ?: 0 }
            .thenByDescending { usage[it.uuid]?.lastUsed }
            .thenBy { it.sortOrder }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.uuid },
    )
}

private data class CategoryUsage(var lastUsed: LocalDateTime, var count: Int = 0)
