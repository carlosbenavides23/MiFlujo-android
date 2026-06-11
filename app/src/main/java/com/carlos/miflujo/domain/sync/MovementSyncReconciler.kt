package com.carlos.miflujo.domain.sync

import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.SyncStatus
import com.carlos.miflujo.domain.validation.MovementBusinessRuleValidator
import java.time.LocalDateTime
import java.util.UUID

data class SyncReconciliationPlan(
    val actions: List<SyncReconciliationAction>,
)

sealed interface SyncReconciliationAction {
    data class UploadLocalMovement(
        val payload: MovementRemoteSnapshot,
    ) : SyncReconciliationAction

    data class UploadLocalTombstone(
        val payload: MovementRemoteSnapshot,
    ) : SyncReconciliationAction

    data class InsertRemoteLocally(
        val movement: Movement,
    ) : SyncReconciliationAction

    data class UpdateLocalFromRemote(
        val movement: Movement,
    ) : SyncReconciliationAction

    data class MarkLocalSynced(
        val localId: Long,
        val uuid: String,
        val lastSyncedAt: LocalDateTime,
    ) : SyncReconciliationAction

    data class MarkLocalSyncError(
        val localId: Long,
        val uuid: String,
        val reason: LocalSyncErrorReason,
    ) : SyncReconciliationAction

    data class SkipInvalidRemote(
        val documentId: String,
        val reason: InvalidRemoteItemReason,
    ) : SyncReconciliationAction
}

enum class LocalSyncErrorReason {
    PENDING_DELETE_WITHOUT_TOMBSTONE,
    INVALID_LOCAL_MOVEMENT,
    DUPLICATE_LOCAL_UUID,
}

object MovementSyncReconciler {
    fun reconcile(
        localMovements: List<Movement>,
        remoteInputs: List<RemoteMovementInput>,
        syncTime: LocalDateTime,
    ): SyncReconciliationPlan {
        val actions = mutableListOf<SyncReconciliationAction>()
        val localByUuid = mutableMapOf<String, Movement>()
        val validRemoteByUuid = mutableMapOf<String, MovementRemoteSnapshot>()
        val blockedUuids = mutableSetOf<String>()

        localMovements
            .groupBy(Movement::uuid)
            .toSortedMap()
            .forEach { (uuid, movements) ->
                if (movements.size > 1) {
                    blockedUuids += uuid
                    movements
                        .sortedBy(Movement::id)
                        .forEach { movement ->
                            actions += SyncReconciliationAction.MarkLocalSyncError(
                                localId = movement.id,
                                uuid = uuid,
                                reason = LocalSyncErrorReason.DUPLICATE_LOCAL_UUID,
                            )
                        }
                } else {
                    localByUuid[uuid] = movements.single()
                }
            }

        remoteInputs
            .filterIsInstance<RemoteMovementInput.Invalid>()
            .sortedBy(RemoteMovementInput.Invalid::documentId)
            .forEach { invalid ->
                blockedUuids += invalid.documentId
                actions += SyncReconciliationAction.SkipInvalidRemote(
                    documentId = invalid.documentId,
                    reason = invalid.reason,
                )
            }

        remoteInputs
            .filterIsInstance<RemoteMovementInput.Valid>()
            .map(RemoteMovementInput.Valid::snapshot)
            .groupBy(MovementRemoteSnapshot::uuid)
            .toSortedMap()
            .forEach { (uuid, snapshots) ->
                if (snapshots.size > 1) {
                    blockedUuids += uuid
                    actions += SyncReconciliationAction.SkipInvalidRemote(
                        documentId = uuid,
                        reason = InvalidRemoteItemReason.DUPLICATE_UUID,
                    )
                } else {
                    val snapshot = snapshots.single()
                    if (snapshot.isValid()) {
                        validRemoteByUuid[uuid] = snapshot
                    } else {
                        blockedUuids += uuid
                        actions += SyncReconciliationAction.SkipInvalidRemote(
                            documentId = uuid,
                            reason = InvalidRemoteItemReason.INVALID_DOCUMENT,
                        )
                    }
                }
            }

        val uuids = (localByUuid.keys + validRemoteByUuid.keys)
            .filterNot(blockedUuids::contains)
            .sorted()

        uuids.forEach { uuid ->
            val local = localByUuid[uuid]
            val remote = validRemoteByUuid[uuid]
            actions += reconcileItem(local, remote, syncTime)
        }

        return SyncReconciliationPlan(actions)
    }

    private fun reconcileItem(
        local: Movement?,
        remote: MovementRemoteSnapshot?,
        syncTime: LocalDateTime,
    ): List<SyncReconciliationAction> = when {
        local != null && remote == null -> reconcileLocalOnly(local)
        local == null && remote != null -> listOf(
            SyncReconciliationAction.InsertRemoteLocally(
                movement = remote.toLocalMovement(localId = 0, syncTime = syncTime),
            ),
        )
        local != null && remote != null -> reconcileBoth(local, remote, syncTime)
        else -> emptyList()
    }

    private fun reconcileLocalOnly(local: Movement): List<SyncReconciliationAction> {
        local.validationError()?.let { error ->
            return listOf(
                SyncReconciliationAction.MarkLocalSyncError(
                    localId = local.id,
                    uuid = local.uuid,
                    reason = error,
                ),
            )
        }

        val payload = local.toRemoteSnapshot()
        return if (local.isTombstone()) {
            listOf(SyncReconciliationAction.UploadLocalTombstone(payload))
        } else {
            listOf(SyncReconciliationAction.UploadLocalMovement(payload))
        }
    }

    private fun reconcileBoth(
        local: Movement,
        remote: MovementRemoteSnapshot,
        syncTime: LocalDateTime,
    ): List<SyncReconciliationAction> {
        local.validationError()?.let { error ->
            return listOf(
                SyncReconciliationAction.MarkLocalSyncError(
                    localId = local.id,
                    uuid = local.uuid,
                    reason = error,
                ),
            )
        }

        if (remote.deletedAt != null && remote.updatedAt >= local.updatedAt) {
            return if (remote.isEquivalentTo(local)) {
                listOf(local.markSynced(syncTime))
            } else {
                listOf(
                    SyncReconciliationAction.UpdateLocalFromRemote(
                        remote.toLocalMovement(
                            localId = local.id,
                            syncTime = syncTime,
                            localCreatedAt = local.createdAt,
                        ),
                    ),
                )
            }
        }

        if (local.isTombstone() && local.updatedAt >= remote.updatedAt) {
            return listOf(
                SyncReconciliationAction.UploadLocalTombstone(local.toRemoteSnapshot()),
            )
        }

        return when {
            local.updatedAt > remote.updatedAt -> listOf(
                SyncReconciliationAction.UploadLocalMovement(local.toRemoteSnapshot()),
            )
            remote.updatedAt > local.updatedAt -> listOf(
                SyncReconciliationAction.UpdateLocalFromRemote(
                    remote.toLocalMovement(
                        localId = local.id,
                        syncTime = syncTime,
                        localCreatedAt = local.createdAt,
                    ),
                ),
            )
            remote.isEquivalentTo(local) -> listOf(local.markSynced(syncTime))
            local.syncStatus == SyncStatus.SYNCED -> listOf(
                SyncReconciliationAction.UpdateLocalFromRemote(
                    remote.toLocalMovement(
                        localId = local.id,
                        syncTime = syncTime,
                        localCreatedAt = local.createdAt,
                    ),
                ),
            )
            else -> listOf(
                SyncReconciliationAction.UploadLocalMovement(local.toRemoteSnapshot()),
            )
        }
    }
}

private fun Movement.validationError(): LocalSyncErrorReason? {
    if (syncStatus == SyncStatus.PENDING_DELETE && deletedAt == null) {
        return LocalSyncErrorReason.PENDING_DELETE_WITHOUT_TOMBSTONE
    }
    if (!uuid.isCanonicalUuid()) {
        return LocalSyncErrorReason.INVALID_LOCAL_MOVEMENT
    }
    if (
        MovementBusinessRuleValidator.validate(
            amountMinor = amountMinor,
            type = type,
            category = category,
            subcategory = subcategory,
        ).isNotEmpty()
    ) {
        return LocalSyncErrorReason.INVALID_LOCAL_MOVEMENT
    }
    return null
}

private fun MovementRemoteSnapshot.isValid(): Boolean =
    schemaVersion == MovementRemoteSchemaVersion &&
        uuid.isCanonicalUuid() &&
        MovementBusinessRuleValidator.validate(
            amountMinor = amountMinor,
            type = type,
            category = category,
            subcategory = subcategory,
        ).isEmpty()

private fun Movement.isTombstone(): Boolean =
    deletedAt != null || syncStatus == SyncStatus.PENDING_DELETE

private fun MovementRemoteSnapshot.isEquivalentTo(local: Movement): Boolean =
    uuid == local.uuid &&
        type == local.type &&
        amountMinor == local.amountMinor &&
        currency == local.currency &&
        date == local.date &&
        category == local.category &&
        subcategory == local.subcategory &&
        detail == local.detail &&
        updatedAt == local.updatedAt &&
        deletedAt == local.deletedAt

private fun Movement.markSynced(syncTime: LocalDateTime): SyncReconciliationAction.MarkLocalSynced =
    SyncReconciliationAction.MarkLocalSynced(
        localId = id,
        uuid = uuid,
        lastSyncedAt = syncTime,
    )

private fun String.isCanonicalUuid(): Boolean = try {
    UUID.fromString(this).toString() == this
} catch (_: IllegalArgumentException) {
    false
}
