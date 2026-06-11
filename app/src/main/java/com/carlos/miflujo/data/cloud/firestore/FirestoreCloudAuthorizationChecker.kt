package com.carlos.miflujo.data.cloud.firestore

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class FirestoreCloudAuthorizationChecker(
    private val firestore: FirebaseFirestore,
) : CloudAuthorizationChecker {
    override suspend fun isAuthorized(uid: String): Boolean {
        return try {
            Log.d(
                CloudAuthorizationLogTag,
                "Reading $AuthorizedUsersCollection/${uid.toShortLogUid()}.",
            )
            val document = firestore.collection(AuthorizedUsersCollection)
                .document(uid)
                .get(Source.SERVER)
                .awaitResult()
            Log.d(
                CloudAuthorizationLogTag,
                "Authorization document exists: ${document.exists()}.",
            )
            val isEnabled = document.getBoolean(EnabledField) == true
            Log.d(CloudAuthorizationLogTag, "Authorization enabled is true: $isEnabled.")
            isEnabled
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            Log.w(
                CloudAuthorizationLogTag,
                "Authorization read failed: class=${exception.javaClass.name}, " +
                    "message=${exception.message}.",
                exception,
            )
            false
        }
    }
}

private suspend fun Task<DocumentSnapshot>.awaitResult(): DocumentSnapshot =
    suspendCoroutine { continuation ->
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

private const val AuthorizedUsersCollection = "authorizedUsers"
private const val EnabledField = "enabled"
private const val CloudAuthorizationLogTag = "MiFlujoCloudAuthorization"

private fun String.toShortLogUid(): String = take(6) + "..."
