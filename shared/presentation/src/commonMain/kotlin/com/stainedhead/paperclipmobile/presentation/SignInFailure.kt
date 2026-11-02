package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.auth.SignInResult

/** How a sign-in that did not succeed shows on the first run and edit screens: one rule for both. */
internal data class SignInFailure(
    val pairing: PairingStatus,
    val error: ErrorUi?,
)

/** Null for [SignInResult.SignedIn]. There is no `else`: a new result must be mapped here (F-B-3). */
internal fun SignInResult.toFailure(): SignInFailure? =
    when (this) {
        is SignInResult.SignedIn -> null
        SignInResult.Cancelled -> SignInFailure(PairingStatus.Cancelled, null)
        SignInResult.Expired -> SignInFailure(PairingStatus.Expired, null)
        SignInResult.NotFound -> SignInFailure(PairingStatus.Failed, ErrorUi(ErrorKey.NotFound))
        SignInResult.InvalidKey -> SignInFailure(PairingStatus.Failed, ErrorUi(ErrorKey.InvalidKey))
        SignInResult.StorageFailed -> SignInFailure(PairingStatus.Failed, ErrorUi(ErrorKey.StorageFailed))
        is SignInResult.Failed -> SignInFailure(PairingStatus.Failed, error.toErrorUi())
    }
