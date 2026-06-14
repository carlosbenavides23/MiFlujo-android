package com.carlos.miflujo.data.cloud.auth

import android.content.Context
import com.carlos.miflujo.data.cloud.firestore.CloudAuthorizationChecker

class DefaultCloudAccountRepository(
    private val authDataSource: CloudAuthDataSource,
    private val authorizationChecker: CloudAuthorizationChecker,
) : CloudAccountRepository {
    override suspend fun getCurrentStatus(): CloudAccountStatus {
        val account = authDataSource.currentAccount()
        if (account == null) {
            logMiFlujoAuthDebug("Authorization check result: SignedOut.")
            return CloudAccountStatus.SignedOut
        }
        return account.toStatus()
    }

    override suspend fun signInWithGoogle(context: Context): CloudAccountStatus {
        val account = authDataSource.signInWithGoogle(context)
        logMiFlujoAuthDebug(
            "Firebase account received by repository: uidLength=${account.uid.length}.",
        )
        return account.toStatus()
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): CloudAccountStatus {
        val account = authDataSource.signInWithGoogleIdToken(idToken)
        logMiFlujoAuthDebug(
            "Firebase account received by repository: uidLength=${account.uid.length}.",
        )
        return account.toStatus()
    }

    override suspend fun signOut(context: Context) {
        authDataSource.signOut(context)
    }

    private suspend fun CloudAccount.toStatus(): CloudAccountStatus {
        logMiFlujoAuthDebug(
            "Before Firestore authorization check: uidLength=${uid.length}.",
        )
        return try {
            if (authorizationChecker.isAuthorized(uid)) {
                logMiFlujoAuthDebug("Authorization check result: Authorized.")
                CloudAccountStatus.Authorized(this)
            } else {
                logMiFlujoAuthDebug("Authorization check result: Unauthorized.")
                CloudAccountStatus.Unauthorized(this)
            }
        } catch (exception: Exception) {
            logMiFlujoAuthError(
                "Authorization check result: Failure. class=${exception.javaClass.name}, " +
                    "message=Authorization request failed.",
            )
            throw exception
        }
    }
}
