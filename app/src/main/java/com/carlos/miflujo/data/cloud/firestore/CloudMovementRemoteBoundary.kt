package com.carlos.miflujo.data.cloud.firestore

import com.carlos.miflujo.domain.sync.InvalidRemoteItemReason
import com.carlos.miflujo.domain.sync.MovementRemoteSnapshot
import com.carlos.miflujo.domain.sync.RemoteMovementInput

data class RemoteMovementWrite(
    val collectionPath: String,
    val documentId: String,
    val payload: RemoteMovementDto,
)

enum class RemoteMovementWriteType {
    VISIBLE,
    TOMBSTONE,
}

class InvalidRemoteMovementWriteException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class StaleRemoteMovementWriteException(
    message: String,
) : IllegalStateException(message)

object CloudMovementPath {
    fun collection(uid: String): String {
        requireValidPathSegment("uid", uid)
        return "$UsersCollection/$uid/$MovementsCollection"
    }

    fun document(uid: String, movementUuid: String): String {
        requireValidPathSegment("movementUuid", movementUuid)
        return "${collection(uid)}/$movementUuid"
    }
}

fun prepareRemoteMovementWrite(
    uid: String,
    documentId: String,
    movement: MovementRemoteSnapshot,
    writeType: RemoteMovementWriteType,
): RemoteMovementWrite {
    val collectionPath = CloudMovementPath.collection(uid)
    requireValidPathSegment("documentId", documentId)
    if (documentId != movement.uuid) {
        invalidRemoteWrite("Remote movement UUID must match its document ID.")
    }
    when (writeType) {
        RemoteMovementWriteType.VISIBLE -> {
            if (movement.deletedAt != null) {
                invalidRemoteWrite("Visible remote movement must not contain deletedAt.")
            }
        }

        RemoteMovementWriteType.TOMBSTONE -> {
            if (movement.deletedAt == null) {
                invalidRemoteWrite("Remote tombstone must contain deletedAt.")
            }
        }
    }

    val payload = try {
        movement.toRemoteDto()
    } catch (exception: InvalidRemoteMovementException) {
        invalidRemoteWrite("Remote movement payload is invalid.", exception)
    }

    return RemoteMovementWrite(
        collectionPath = collectionPath,
        documentId = documentId,
        payload = payload,
    )
}

fun decodeRemoteMovementDocument(
    documentId: String,
    dto: RemoteMovementDto?,
): RemoteMovementInput {
    return try {
        val snapshot = dto?.toRemoteSnapshot(documentId)
            ?: return RemoteMovementInput.Invalid(
                documentId = documentId,
                reason = InvalidRemoteItemReason.INVALID_DOCUMENT,
            )
        RemoteMovementInput.Valid(snapshot)
    } catch (_: InvalidRemoteMovementException) {
        RemoteMovementInput.Invalid(
            documentId = documentId,
            reason = InvalidRemoteItemReason.INVALID_DOCUMENT,
        )
    }
}

fun requireRemoteWriteNotStale(
    current: MovementRemoteSnapshot,
    proposed: MovementRemoteSnapshot,
) {
    if (current.uuid != proposed.uuid) {
        throw StaleRemoteMovementWriteException(
            "Current and proposed remote movements must use the same UUID.",
        )
    }
    if (current.updatedAt > proposed.updatedAt) {
        throw StaleRemoteMovementWriteException(
            "Remote movement is newer than the proposed write.",
        )
    }
    if (
        current.updatedAt == proposed.updatedAt &&
        current.deletedAt != null &&
        proposed.deletedAt == null
    ) {
        throw StaleRemoteMovementWriteException(
            "A remote tombstone wins a timestamp tie against a visible movement.",
        )
    }
}

private fun requireValidPathSegment(fieldName: String, value: String) {
    if (value.isBlank() || '/' in value) {
        invalidRemoteWrite("$fieldName must be a non-empty Firestore path segment.")
    }
}

private fun invalidRemoteWrite(
    message: String,
    cause: Throwable? = null,
): Nothing = throw InvalidRemoteMovementWriteException(message, cause)

private const val UsersCollection = "users"
private const val MovementsCollection = "movements"
