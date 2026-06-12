package com.carlos.miflujo.data.cloud.auth

import android.content.Context
import com.carlos.miflujo.data.cloud.firestore.CloudAuthorizationChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DefaultCloudAccountRepositoryTest {
    @Test
    fun `current status is signed out when Firebase has no user`() = runBlocking {
        val repository = createRepository(account = null, isAuthorized = true)

        assertEquals(CloudAccountStatus.SignedOut, repository.getCurrentStatus())
    }

    @Test
    fun `current status is authorized when allowlist enables the UID`() = runBlocking {
        val account = testAccount()
        val repository = createRepository(account = account, isAuthorized = true)

        assertEquals(
            CloudAccountStatus.Authorized(account),
            repository.getCurrentStatus(),
        )
    }

    @Test
    fun `current status is unauthorized when allowlist does not enable the UID`() = runBlocking {
        val account = testAccount()
        val repository = createRepository(account = account, isAuthorized = false)

        assertEquals(
            CloudAccountStatus.Unauthorized(account),
            repository.getCurrentStatus(),
        )
    }

    @Test
    fun `authorization checker failure is not converted into an authorized status`() = runBlocking {
        val repository = DefaultCloudAccountRepository(
            authDataSource = FakeCloudAuthDataSource(testAccount()),
            authorizationChecker = CloudAuthorizationChecker {
                throw IllegalStateException("Authorization unavailable.")
            },
        )

        try {
            repository.getCurrentStatus()
            fail("Expected authorization failure.")
        } catch (_: IllegalStateException) {
            // Expected.
        }
    }

    @Test
    fun `fallback ID token uses normal Firebase account and authorization flow`() = runBlocking {
        val account = testAccount()
        val authDataSource = FakeCloudAuthDataSource(account)
        val repository = DefaultCloudAccountRepository(
            authDataSource = authDataSource,
            authorizationChecker = CloudAuthorizationChecker { true },
        )

        val status = repository.signInWithGoogleIdToken("fallback-id-token")

        assertEquals("fallback-id-token", authDataSource.receivedIdToken)
        assertEquals(CloudAccountStatus.Authorized(account), status)
    }

    private fun createRepository(
        account: CloudAccount?,
        isAuthorized: Boolean,
    ): DefaultCloudAccountRepository = DefaultCloudAccountRepository(
        authDataSource = FakeCloudAuthDataSource(account),
        authorizationChecker = CloudAuthorizationChecker { isAuthorized },
    )

    private fun testAccount(): CloudAccount = CloudAccount(
        uid = "test-uid",
        email = "user@example.com",
        displayName = "Test User",
    )
}

private class FakeCloudAuthDataSource(
    private val account: CloudAccount?,
) : CloudAuthDataSource {
    var receivedIdToken: String? = null

    override fun currentAccount(): CloudAccount? = account

    override suspend fun signInWithGoogle(context: Context): CloudAccount {
        error("Not used in this test.")
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): CloudAccount {
        receivedIdToken = idToken
        return checkNotNull(account)
    }

    override suspend fun signOut(context: Context) = Unit
}
