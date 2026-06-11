package com.carlos.miflujo.domain.sync

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDate
import java.time.LocalDateTime

data class MovementRemoteSnapshot(
    val uuid: String,
    val type: MovementType,
    val amountMinor: Long,
    val currency: Currency,
    val date: LocalDate,
    val category: MovementCategory,
    val subcategory: MovementSubcategory?,
    val detail: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
    val schemaVersion: Int = MovementRemoteSchemaVersion,
)

sealed interface RemoteMovementInput {
    data class Valid(
        val snapshot: MovementRemoteSnapshot,
    ) : RemoteMovementInput

    data class Invalid(
        val documentId: String,
        val reason: InvalidRemoteItemReason,
    ) : RemoteMovementInput
}

enum class InvalidRemoteItemReason {
    INVALID_DOCUMENT,
    DUPLICATE_UUID,
}

fun Movement.toRemoteSnapshot(): MovementRemoteSnapshot = MovementRemoteSnapshot(
    uuid = uuid,
    type = type,
    amountMinor = amountMinor,
    currency = currency,
    date = date,
    category = category,
    subcategory = subcategory,
    detail = detail,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun MovementRemoteSnapshot.toLocalMovement(
    localId: Long,
    syncTime: LocalDateTime,
    localCreatedAt: LocalDateTime = createdAt,
): Movement = Movement(
    id = localId,
    uuid = uuid,
    type = type,
    amountMinor = amountMinor,
    currency = currency,
    date = date,
    category = category,
    subcategory = subcategory,
    detail = detail,
    createdAt = localCreatedAt,
    updatedAt = updatedAt,
    syncStatus = SyncStatus.SYNCED,
    lastSyncedAt = syncTime,
    deletedAt = deletedAt,
)

const val MovementRemoteSchemaVersion = 1
