@file:Suppress("DEPRECATION")

package com.carlos.miflujo.data.cloud.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

class LegacyGoogleSignInFallback(
    activity: Activity,
    googleWebClientId: String,
) {
    private val client = GoogleSignIn.getClient(
        activity,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(googleWebClientId)
            .requestEmail()
            .build(),
    )

    fun signInIntent(): Intent = client.signInIntent

    fun parseResult(data: Intent?): LegacyGoogleSignInResult {
        return try {
            val idToken = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
                .idToken
                ?.takeIf { it.isNotBlank() }
            if (idToken == null) {
                Log.e(
                    MiFlujoAuthLogTag,
                    "GoogleSignInClient fallback failed: class=MissingIdToken.",
                )
                LegacyGoogleSignInResult.MissingIdToken
            } else {
                LegacyGoogleSignInResult.Success(idToken)
            }
        } catch (exception: ApiException) {
            val status = exception.status
            if (exception.statusCode.isGoogleSignInCancellation()) {
                Log.d(
                    MiFlujoAuthLogTag,
                    "GoogleSignInClient fallback canceled: class=${exception.javaClass.name}, " +
                        "statusCode=${exception.statusCode}, " +
                        "status.statusCode=${status.statusCode}, " +
                        "status.statusMessage=${status.statusMessage}.",
                )
                return LegacyGoogleSignInResult.Canceled
            }
            if (exception.statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
                Log.e(
                    MiFlujoAuthLogTag,
                    "MiFlujo fallback differs from canary or callback intent is not equivalent.",
                )
            }
            Log.e(
                MiFlujoAuthLogTag,
                "GoogleSignInClient fallback failed: class=${exception.javaClass.name}, " +
                    "statusCode=${exception.statusCode}, " +
                    "status.statusCode=${status.statusCode}, " +
                    "status.statusMessage=${status.statusMessage}.",
            )
            exception.statusCode.toLegacyGoogleSignInFailure()
        } catch (exception: Exception) {
            Log.e(
                MiFlujoAuthLogTag,
                "GoogleSignInClient fallback failed: class=${exception.javaClass.name}.",
            )
            LegacyGoogleSignInResult.Failure
        }
    }
}

sealed interface LegacyGoogleSignInResult {
    data class Success(
        val idToken: String,
    ) : LegacyGoogleSignInResult

    data object Canceled : LegacyGoogleSignInResult
    data object ConfigurationError : LegacyGoogleSignInResult
    data object MissingIdToken : LegacyGoogleSignInResult
    data object Failure : LegacyGoogleSignInResult
}

private fun Int.isGoogleSignInCancellation(): Boolean =
    this == GoogleSignInStatusCodes.SIGN_IN_CANCELLED ||
        this == CommonStatusCodes.CANCELED

internal fun Int.toLegacyGoogleSignInFailure(): LegacyGoogleSignInResult =
    if (this == CommonStatusCodes.DEVELOPER_ERROR) {
        LegacyGoogleSignInResult.ConfigurationError
    } else {
        LegacyGoogleSignInResult.Failure
    }
