package com.carlos.miflujo.data.cloud.auth

import com.google.android.gms.common.api.CommonStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyGoogleSignInFallbackTest {
    @Test
    fun `developer error maps to configuration error`() {
        assertEquals(
            LegacyGoogleSignInResult.ConfigurationError,
            CommonStatusCodes.DEVELOPER_ERROR.toLegacyGoogleSignInFailure(),
        )
    }

    @Test
    fun `other API error maps to generic failure`() {
        assertEquals(
            LegacyGoogleSignInResult.Failure,
            CommonStatusCodes.INTERNAL_ERROR.toLegacyGoogleSignInFailure(),
        )
    }
}
