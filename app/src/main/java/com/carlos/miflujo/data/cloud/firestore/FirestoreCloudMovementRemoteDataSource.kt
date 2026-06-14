package com.carlos.miflujo.data.cloud.firestore

import com.carlos.miflujo.domain.sync.MovementRemoteSnapshot
import com.carlos.miflujo.domain.sync.RemoteMovementInput
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class FirestoreCloudMovementRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : CloudMovementRemoteDataSource {
    override suspend fun fetchAll(uid: String): List<RemoteMovementInput> {
        val collectionPath = CloudMovementPath.collection(uid)
        return firestore.collection(collectionPath)
            .get(Source.SERVER)
            .awaitResult()
            .documents
            .map { document ->
                val dto = try {
                    document.toObject(RemoteMovementDto::class.java)
                } catch (_: RuntimeException) {
                    null
                }
                decodeRemoteMovementDocument(
                    documentId = document.id,
                    dto = dto,
                )
            }
    }

    override suspend fun upsertVisible(
        uid: String,
        movement: MovementRemoteSnapshot,
    ) {
        upsertConditionally(
            prepareRemoteMovementWrite(
                uid = uid,
                documentId = movement.uuid,
                movement = movement,
                writeType = RemoteMovementWriteType.VISIBLE,
            ),
            movement,
        )
    }

    override suspend fun upsertTombstone(
        uid: String,
        movement: MovementRemoteSnapshot,
    ) {
        upsertConditionally(
            prepareRemoteMovementWrite(
                uid = uid,
                documentId = movement.uuid,
                movement = movement,
                writeType = RemoteMovementWriteType.TOMBSTONE,
            ),
            movement,
        )
    }

    private suspend fun upsertConditionally(
        write: RemoteMovementWrite,
        proposed: MovementRemoteSnapshot,
    ) {
        val documentReference = firestore.collection(write.collectionPath)
            .document(write.documentId)
        firestore.runTransaction { transaction ->
            val currentDocument = transaction.get(documentReference)
            val payload = if (currentDocument.exists()) {
                val currentDto = try {
                    currentDocument.toObject(RemoteMovementDto::class.java)
                } catch (exception: RuntimeException) {
                    throw InvalidRemoteMovementWriteException(
                        "Current remote movement could not be decoded.",
                        exception,
                    )
                } ?: throw InvalidRemoteMovementWriteException(
                    "Current remote movement is empty.",
                )
                val current = when (
                    val input = decodeRemoteMovementDocument(
                        documentId = currentDocument.id,
                        dto = currentDto,
                    )
                ) {
                    is RemoteMovementInput.Valid -> input.snapshot
                    is RemoteMovementInput.Invalid -> {
                        throw InvalidRemoteMovementWriteException(
                            "Current remote movement is invalid.",
                        )
                    }
                }
                requireRemoteWriteNotStale(current, proposed)
                write.payload.copy(createdAt = currentDto.createdAt)
            } else {
                write.payload
            }

            transaction.set(documentReference, payload)
            null
        }
            .awaitResult()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firestore task failed."),
            )
        }
    }
}
