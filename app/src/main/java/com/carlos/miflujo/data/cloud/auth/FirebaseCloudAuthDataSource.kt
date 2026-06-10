package com.carlos.miflujo.data.cloud.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class FirebaseCloudAuthDataSource(
    private val firebaseAuth: FirebaseAuth,
    private val googleWebClientId: String,
) : CloudAuthDataSource {
    override fun currentAccount(): CloudAccount? = firebaseAuth.currentUser?.toCloudAccount()

    override suspend fun signInWithGoogle(context: Context): CloudAccount {
        val credentialManager = CredentialManager.create(context)
        val idToken = try {
            try {
                getGoogleIdToken(
                    context = context,
                    credentialManager = credentialManager,
                    filterByAuthorizedAccounts = true,
                )
            } catch (_: NoCredentialException) {
                getGoogleIdToken(
                    context = context,
                    credentialManager = credentialManager,
                    filterByAuthorizedAccounts = false,
                )
            }
        } catch (exception: GetCredentialCancellationException) {
            throw CloudSignInCanceledException(exception)
        }

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(firebaseCredential).awaitResult()
        return checkNotNull(firebaseAuth.currentUser) {
            "Firebase Auth completed without a current user."
        }.toCloudAccount()
    }

    override suspend fun signOut(context: Context) {
        firebaseAuth.signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (exception: Exception) {
            Log.w(CloudAuthLogTag, "Credential Manager state could not be cleared.", exception)
        }
    }

    private suspend fun getGoogleIdToken(
        context: Context,
        credentialManager: CredentialManager,
        filterByAuthorizedAccounts: Boolean,
    ): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(googleWebClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val credential = credentialManager.getCredential(
            context = context,
            request = request,
        ).credential

        if (
            credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Credential Manager returned an unsupported credential type.")
        }

        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}

private fun FirebaseUser.toCloudAccount(): CloudAccount = CloudAccount(
    uid = uid,
    email = email,
    displayName = displayName,
)

private suspend fun <T> Task<T>.awaitResult(): T = suspendCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase task failed."),
            )
        }
    }
}

private const val CloudAuthLogTag = "MiFlujoCloudAuth"
