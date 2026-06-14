package com.carlos.miflujo.data.cloud.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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
    private val googleWebClientIdSource: String,
) : CloudAuthDataSource {
    override fun currentAccount(): CloudAccount? = firebaseAuth.currentUser?.toCloudAccount()

    override suspend fun signInWithGoogle(context: Context): CloudAccount {
        Log.d(
            MiFlujoAuthLogTag,
            "FirebaseCloudAuthDataSource sign-in context: class=${context.javaClass.name}, " +
                "isActivity=${context is Activity}.",
        )
        val activity = context.findActivity()
        if (activity == null) {
            Log.e(
                MiFlujoAuthLogTag,
                "Google sign-in rejected: Activity context is required.",
            )
            throw CloudActivityContextRequiredException()
        }
        Log.d(
            MiFlujoAuthLogTag,
            "Credential Manager Activity context: class=${activity.javaClass.name}, " +
                "isActivity=true.",
        )
        val credentialManager = CredentialManager.create(activity)
        Log.d(
            MiFlujoAuthLogTag,
            "Requesting explicit Sign in with Google credential.",
        )
        val idToken = getGoogleIdToken(
            activity = activity,
            credentialManager = credentialManager,
        )
        return signInWithGoogleIdToken(idToken)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): CloudAccount {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        Log.d(MiFlujoAuthLogTag, "Before FirebaseAuth.signInWithCredential.")
        try {
            firebaseAuth.signInWithCredential(firebaseCredential).awaitResult()
        } catch (exception: Exception) {
            Log.e(
                MiFlujoAuthLogTag,
                "FirebaseAuth failure: class=${exception.javaClass.name}, " +
                    "message=Firebase authentication request failed.",
            )
            throw exception
        }
        val currentUser = firebaseAuth.currentUser
        Log.d(
            MiFlujoAuthLogTag,
            "FirebaseAuth success: currentUserPresent=${currentUser != null}, " +
                "uidLength=${currentUser?.uid?.length ?: 0}.",
        )
        return checkNotNull(currentUser) {
            "Firebase Auth completed without a current user."
        }.toCloudAccount()
    }

    override suspend fun signOut(context: Context) {
        firebaseAuth.signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (exception: Exception) {
            Log.w(MiFlujoAuthLogTag, "Credential Manager state could not be cleared.")
        }
    }

    private suspend fun getGoogleIdToken(
        activity: Activity,
        credentialManager: CredentialManager,
    ): String {
        val googleIdOption = GetSignInWithGoogleOption.Builder(googleWebClientId)
            .build()
        Log.d(
            MiFlujoAuthLogTag,
            "Credential option type=GetSignInWithGoogleOption, " +
                "serverClientIdLength=${googleWebClientId.length}, " +
                "serverClientIdSource=$googleWebClientIdSource, " +
                "packageName=${activity.applicationContext.packageName}.",
        )
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        Log.d(MiFlujoAuthLogTag, "Primary Credential Manager sign-in started.")
        val credentialResponse = try {
            credentialManager.getCredential(
                context = activity,
                request = request,
            )
        } catch (exception: GetCredentialCancellationException) {
            logCredentialManagerFailure(exception)
            Log.d(
                MiFlujoAuthLogTag,
                "Credential Manager cancellation is eligible for legacy fallback.",
            )
            throw CloudSignInCanceledException(exception)
        } catch (exception: NoCredentialException) {
            logCredentialManagerFailure(exception)
            Log.w(
                MiFlujoAuthLogTag,
                "Credential Manager no-credential result is eligible for legacy fallback.",
            )
            throw CloudNoGoogleCredentialException(exception)
        } catch (exception: GetCredentialException) {
            logCredentialManagerFailure(exception)
            Log.e(
                MiFlujoAuthLogTag,
                "Mapping Credential Manager failure to generic credential request failure.",
            )
            throw CloudCredentialRequestException(exception)
        }
        Log.d(MiFlujoAuthLogTag, "CredentialManager.getCredential returned.")
        val credential = credentialResponse.credential
        Log.d(
            MiFlujoAuthLogTag,
            "Credential type received: class=${credential.javaClass.name}, " +
                "type=${credential.type}.",
        )

        if (
            credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.e(
                MiFlujoAuthLogTag,
                "Unsupported credential class=${credential.javaClass.name}, type=${credential.type}.",
            )
            Log.e(
                MiFlujoAuthLogTag,
                "Mapping Credential Manager result to unsupported credential failure.",
            )
            throw UnsupportedCloudCredentialException(
                "Credential Manager returned an unsupported credential type.",
            )
        }

        Log.d(MiFlujoAuthLogTag, "Before parsing GoogleIdTokenCredential.")
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken.also {
                Log.d(MiFlujoAuthLogTag, "GoogleIdTokenCredential parse succeeded.")
            }
        } catch (exception: Exception) {
            Log.e(
                MiFlujoAuthLogTag,
                "GoogleIdTokenCredential parse failed: class=${exception.javaClass.name}, " +
                    "message=Credential response could not be parsed.",
            )
            throw exception
        }
    }
}

private fun logCredentialManagerFailure(exception: GetCredentialException) {
    Log.e(
        MiFlujoAuthLogTag,
        "CredentialManager.getCredential failed: class=${exception.javaClass.name}, " +
            "message=Credential request failed.",
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> {
        val baseContext = baseContext
        if (baseContext === this) null else baseContext.findActivity()
    }
    else -> null
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
