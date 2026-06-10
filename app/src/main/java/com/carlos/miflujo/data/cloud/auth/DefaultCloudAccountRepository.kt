package com.carlos.miflujo.data.cloud.auth

import android.content.Context
import com.carlos.miflujo.data.cloud.firestore.CloudAuthorizationChecker

class DefaultCloudAccountRepository(
    private val authDataSource: CloudAuthDataSource,
    private val authorizationChecker: CloudAuthorizationChecker,
) : CloudAccountRepository {
    override suspend fun getCurrentStatus(): CloudAccountStatus {
        val account = authDataSource.currentAccount() ?: return CloudAccountStatus.SignedOut
        return account.toStatus()
    }

    override suspend fun signInWithGoogle(context: Context): CloudAccountStatus {
        return authDataSource.signInWithGoogle(context).toStatus()
    }

    override suspend fun signOut(context: Context) {
        authDataSource.signOut(context)
    }

    private suspend fun CloudAccount.toStatus(): CloudAccountStatus {
        return if (authorizationChecker.isAuthorized(uid)) {
            CloudAccountStatus.Authorized(this)
        } else {
            CloudAccountStatus.Unauthorized(this)
        }
    }
}
