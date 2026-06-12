package com.carlos.miflujo.data.cloud.firestore

import android.util.Log
import com.carlos.miflujo.data.cloud.auth.MiFlujoAuthLogTag
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
                MiFlujoAuthLogTag,
                "Reading authorization document: uidLength=${uid.length}.",
            )
            val document = firestore.collection(AuthorizedUsersCollection)
                .document(uid)
                .get(Source.SERVER)
                .awaitResult()
            Log.d(
                MiFlujoAuthLogTag,
                "Authorization document exists: ${document.exists()}.",
            )
            val isEnabled = document.getBoolean(EnabledField) == true
            Log.d(
                MiFlujoAuthLogTag,
                "Authorization check result: " +
                    if (isEnabled) "Authorized." else "Unauthorized.",
            )
            isEnabled
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            Log.w(
                MiFlujoAuthLogTag,
                "Authorization check result: Failure. class=${exception.javaClass.name}, " +
                    "message=Authorization document could not be read.",
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
