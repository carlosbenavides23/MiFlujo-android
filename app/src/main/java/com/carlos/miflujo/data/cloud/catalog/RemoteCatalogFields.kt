package com.carlos.miflujo.data.cloud.catalog

import com.carlos.miflujo.domain.category.CatalogIconColor
import com.carlos.miflujo.domain.category.CatalogRevision
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup

/** Firestore document maps. Reading a raw map lets us reject unknown fields and coercions. */
object RemoteCatalogFields {
    const val LegacyVersion = 1L
    const val Version = 2L
    private val commonKeys = setOf(
        "schemaVersion", "uuid", "name", "iconKey", "archived", "sortOrder", "revisionCounter", "revisionActorUuid",
    )
    private val categoryKeys = commonKeys + setOf("scope", "reportGroup")
    private val subcategoryKeys = commonKeys + "categoryUuid"

    fun encode(entry: RemoteCatalogEntry, version: Long = Version): Map<String, Any?> = buildMap {
        require(version == Version || (version == LegacyVersion && entry.iconColor == null))
        put("schemaVersion", version)
        if (version == Version) put("iconColor", entry.iconColor?.key)
        put("uuid", entry.uuid)
        put("name", entry.name)
        put("iconKey", entry.iconKey)
        put("archived", entry.archived)
        put("sortOrder", entry.sortOrder.toLong())
        put("revisionCounter", entry.revision.counter)
        put("revisionActorUuid", entry.revision.actorUuid)
        when (entry) {
            is RemoteCategoryDto -> {
                put("scope", entry.scope.name)
                put("reportGroup", entry.reportGroup.name)
            }
            is RemoteSubcategoryDto -> put("categoryUuid", entry.categoryUuid)
        }
    }

    fun decodeCategory(documentId: String, fields: Map<String, Any?>): RemoteCategoryDto {
        fields.requireDocument(documentId, categoryKeys)
        return RemoteCategoryDto(
            uuid = documentId,
            name = fields.string("name"),
            scope = CategoryScope.valueOf(fields.string("scope")),
            reportGroup = ReportGroup.valueOf(fields.string("reportGroup")),
            iconKey = fields.string("iconKey"),
            archived = fields.archived(),
            sortOrder = fields.sortOrder(),
            revision = fields.revision(),
            iconColor = fields.iconColor(),
        )
    }

    fun decodeSubcategory(documentId: String, fields: Map<String, Any?>): RemoteSubcategoryDto {
        fields.requireDocument(documentId, subcategoryKeys)
        return RemoteSubcategoryDto(
            uuid = documentId,
            categoryUuid = fields.string("categoryUuid"),
            name = fields.string("name"),
            iconKey = fields.string("iconKey"),
            archived = fields.archived(),
            sortOrder = fields.sortOrder(),
            revision = fields.revision(),
            iconColor = fields.iconColor(),
        )
    }

    private fun Map<String, Any?>.requireDocument(documentId: String, expectedKeys: Set<String>) {
        val keysForVersion = when (integer("schemaVersion")) {
            LegacyVersion -> expectedKeys
            Version -> expectedKeys + "iconColor"
            else -> throw IllegalArgumentException("Unsupported catalog version")
        }
        require(keys == keysForVersion) { "Invalid catalog fields" }
        require(string("uuid") == documentId) { "Catalog UUID must match document ID" }
    }

    private fun Map<String, Any?>.iconColor(): CatalogIconColor? =
        this["iconColor"]?.let { CatalogIconColor.fromKey(string("iconColor")) }

    private fun Map<String, Any?>.string(key: String) =
        this[key] as? String ?: throw IllegalArgumentException("Expected string: $key")

    private fun Map<String, Any?>.archived() =
        this["archived"] as? Boolean ?: throw IllegalArgumentException("Expected boolean: archived")

    private fun Map<String, Any?>.integer(key: String): Long = when (val value = this[key]) {
        is Long -> value
        is Int -> value.toLong()
        else -> throw IllegalArgumentException("Expected integer: $key")
    }

    private fun Map<String, Any?>.sortOrder(): Int {
        val value = integer("sortOrder")
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "sortOrder out of range" }
        return value.toInt()
    }

    private fun Map<String, Any?>.revision() = CatalogRevision(integer("revisionCounter"), string("revisionActorUuid"))
}
