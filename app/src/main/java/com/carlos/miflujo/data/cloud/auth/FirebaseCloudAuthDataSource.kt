package com.carlos.miflujo.data.cloud.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
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
            Log.d(
                CloudAuthLogTag,
                "Requesting explicit Sign in with Google credential.",
            )
            getGoogleIdToken(
                context = context,
                credentialManager = credentialManager,
            )
        } catch (exception: GetCredentialCancellationException) {
            throw CloudSignInCanceledException(exception)
        }

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        Log.d(CloudAuthLogTag, "Signing in to Firebase Auth with Google credential.")
        firebaseAuth.signInWithCredential(firebaseCredential).awaitResult()
        Log.d(CloudAuthLogTag, "Firebase Auth sign-in succeeded.")
        val currentUser = firebaseAuth.currentUser
        Log.d(CloudAuthLogTag, "Firebase Auth currentUser is null: ${currentUser == null}.")
        return checkNotNull(currentUser) {
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
    ): String {
        val googleIdOption = GetSignInWithGoogleOption.Builder(googleWebClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        Log.d(CloudAuthLogTag, "Calling CredentialManager.getCredential.")
        val credentialResponse = try {
            withTimeout(CredentialRequestTimeoutMillis) {
                credentialManager.getCredential(
                    context = context,
                    request = request,
                )
            }
        } catch (exception: TimeoutCancellationException) {
            Log.w(
                CloudAuthLogTag,
                "CredentialManager.getCredential timed out after " +
                    "$CredentialRequestTimeoutMillis ms.",
                exception,
            )
            throw CloudSignInTimedOutException(exception)
        }
        Log.d(CloudAuthLogTag, "CredentialManager.getCredential returned.")
        val credential = credentialResponse.credential
        Log.d(
            CloudAuthLogTag,
            "Credential class=${credential.javaClass.name}, type=${credential.type}.",
        )

        if (
            credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.e(
                CloudAuthLogTag,
                "Unsupported credential class=${credential.javaClass.name}, type=${credential.type}.",
            )
            error("Credential Manager returned an unsupported credential type.")
        }

        Log.d(CloudAuthLogTag, "Parsing Google ID token credential.")
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        Log.d(CloudAuthLogTag, "Google ID token extracted successfully.")
        return idToken
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
private const val CredentialRequestTimeoutMillis = 30_000L
