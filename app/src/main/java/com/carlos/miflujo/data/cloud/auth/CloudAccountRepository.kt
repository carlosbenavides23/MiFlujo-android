package com.carlos.miflujo.data.cloud.auth

import android.content.Context

data class CloudAccount(
    val uid: String,
    val email: String?,
    val displayName: String?,
)

sealed interface CloudAccountStatus {
    data object Loading : CloudAccountStatus
    data object SignedOut : CloudAccountStatus

    data class Authorized(
        val account: CloudAccount,
    ) : CloudAccountStatus

    data class Unauthorized(
        val account: CloudAccount,
    ) : CloudAccountStatus
}

interface CloudAccountRepository {
    suspend fun getCurrentStatus(): CloudAccountStatus

    suspend fun signInWithGoogle(context: Context): CloudAccountStatus

    suspend fun signInWithGoogleIdToken(idToken: String): CloudAccountStatus

    suspend fun signOut(context: Context)
}

class CloudSignInCanceledException(
    cause: Throwable,
) : Exception(cause)

class CloudNoGoogleCredentialException(
    cause: Throwable,
) : Exception(cause)

class CloudCredentialRequestException(
    cause: Throwable,
) : Exception(cause)

class CloudActivityContextRequiredException : Exception(
    "Google sign-in requires an Activity context.",
)

class UnsupportedCloudCredentialException(
    message: String,
) : Exception(message)
