package com.carlos.miflujo.data.cloud.catalogmovement

import com.carlos.miflujo.data.cloud.catalog.CatalogIssueKind
import com.carlos.miflujo.data.cloud.catalog.CatalogSyncException
import com.carlos.miflujo.data.cloud.firestore.RemoteMovementDto
import com.carlos.miflujo.data.cloud.firestore.toDomain
import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import com.carlos.miflujo.domain.category.isCanonicalCatalogUuid
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate

internal data class MovementContent(
    val uuid: String,
    val draft: CatalogMovementDraft,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
) {
    init {
        require(isCanonicalCatalogUuid(uuid) && draft.date.year in 1..9999)
        listOfNotNull(createdAt, updatedAt, deletedAt).forEach { require(it in -62135596800000L..253402300799999L) }
    }
}

internal data class RemoteCatalogMovement(val content: MovementContent, val revision: Long) {
    init { require(revision > 0L) }
}

internal data class PendingCatalogMovement(val content: MovementContent, val baseRevision: Long?)

internal fun nextRemoteMovement(
    pending: PendingCatalogMovement,
    current: RemoteCatalogMovement?,
): RemoteCatalogMovement {
    if (current != null && current.content == pending.content &&
        (pending.baseRevision == null || current.revision >= pending.baseRevision)
    ) return current
    if (pending.baseRevision != current?.revision ||
        (current != null && (pending.content.uuid != current.content.uuid ||
            pending.content.createdAt != current.content.createdAt || current.content.deletedAt != null ||
            current.revision == Long.MAX_VALUE))
    ) throw CatalogSyncException(CatalogIssueKind.REVISION_CONFLICT)
    return RemoteCatalogMovement(pending.content, (current?.revision ?: 0L) + 1L)
}

internal object CatalogMovementFields {
    private val common = setOf(
        "uuid", "type", "amountMinor", "currency", "date", "detail",
        "createdAt", "updatedAt", "deletedAt", "schemaVersion",
    )

    fun encode(movement: RemoteCatalogMovement): Map<String, Any?> {
        val (uuid, draft, created, updated, deleted) = movement.content
        return mapOf(
            "schemaVersion" to 2L, "uuid" to uuid, "type" to draft.type.name, "amountMinor" to draft.amountMinor,
            "currency" to draft.currency.name, "date" to draft.date.toString(), "detail" to draft.detail,
            "categoryUuid" to draft.selection.categoryUuid, "subcategoryUuid" to draft.selection.subcategoryUuid,
            "createdAt" to created.timestamp(), "updatedAt" to updated.timestamp(), "deletedAt" to deleted?.timestamp(),
            "revision" to movement.revision,
        )
    }

    fun decode(id: String, fields: Map<String, Any?>): RemoteCatalogMovement {
        require(fields.keys == common + setOf("categoryUuid", "subcategoryUuid", "revision"))
        require(fields.integer("schemaVersion") == 2L && fields["uuid"] == id)
        val date = LocalDate.parse(fields.required("date"))
        val draft = CatalogMovementDraft(
            MovementType.valueOf(fields.required("type")), fields.integer("amountMinor"),
            Currency.valueOf(fields.required("currency")), date,
            CatalogMovementSelection(fields.optional("categoryUuid"), fields.optional("subcategoryUuid")),
            fields.optional("detail"),
        )
        val content = MovementContent(
            id, draft, fields.required<Timestamp>("createdAt").millis(), fields.required<Timestamp>("updatedAt").millis(),
            fields.optional<Timestamp>("deletedAt")?.millis(),
        )
        return RemoteCatalogMovement(content, fields.integer("revision"))
    }

    /** Strict v1 conversion for a trusted backfill during the frozen phase. */
    fun upgradeLegacy(id: String, fields: Map<String, Any?>): RemoteCatalogMovement {
        require(fields.keys == common + setOf("category", "subcategory"))
        require(fields.integer("schemaVersion") == 1L)
        val legacy = RemoteMovementDto(
            uuid = fields.required("uuid"), type = fields.required("type"), amountMinor = fields.integer("amountMinor"),
            currency = fields.required("currency"), date = fields.required("date"), category = fields.required("category"),
            subcategory = fields.optional("subcategory"), detail = fields.optional("detail"),
            createdAt = fields.required("createdAt"), updatedAt = fields.required("updatedAt"),
            deletedAt = fields.optional("deletedAt"), schemaVersion = 1,
        ).toDomain(id)
        val selection = LegacyCategoryCatalog.selection(legacy.category, legacy.subcategory)
        val parent = LegacyCategoryCatalog.categories.single { it.id == selection.categoryId }
        val child = selection.subcategoryId?.let { id -> LegacyCategoryCatalog.subcategories.single { it.id == id } }
        val draft = CatalogMovementDraft(
            legacy.type, legacy.amountMinor, legacy.currency, legacy.date,
            CatalogMovementSelection(parent.uuid, child?.uuid), legacy.detail,
        )
        return RemoteCatalogMovement(
            MovementContent(id, draft, fields.required<Timestamp>("createdAt").millis(),
                fields.required<Timestamp>("updatedAt").millis(), fields.optional<Timestamp>("deletedAt")?.millis()),
            1L,
        )
    }

    private inline fun <reified T : Any> Map<String, Any?>.required(key: String): T =
        requireNotNull(this[key] as? T) { "Invalid field: $key" }

    private inline fun <reified T : Any> Map<String, Any?>.optional(key: String): T? =
        if (this[key] == null) null else required<T>(key)

    private fun Map<String, Any?>.integer(key: String): Long = when (val value = this[key]) {
        is Long -> value
        is Int -> value.toLong()
        else -> throw IllegalArgumentException("Expected integer: $key")
    }

    private fun Long.timestamp(): Timestamp = Instant.ofEpochMilli(this).let { Timestamp(it.epochSecond, it.nano) }

    private fun Timestamp.millis(): Long {
        require(nanoseconds % 1_000_000 == 0) { "Timestamp is not representable in Room" }
        return Instant.ofEpochSecond(seconds, nanoseconds.toLong()).toEpochMilli()
    }
}
