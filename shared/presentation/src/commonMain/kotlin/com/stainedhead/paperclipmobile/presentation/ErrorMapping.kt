package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError

internal fun DomainError.toErrorUi(): ErrorUi =
    when (this) {
        DomainError.Unreachable, DomainError.ConnectionLost -> ErrorUi(ErrorKey.Unreachable)
        DomainError.CertificateInvalid -> ErrorUi(ErrorKey.CertificateInvalid)
        DomainError.Timeout -> ErrorUi(ErrorKey.Timeout)
        DomainError.Unauthorized -> ErrorUi(ErrorKey.SignedOut)
        DomainError.DeviceAuthDeclined -> ErrorUi(ErrorKey.AuthDeclined)
        DomainError.KeyInvalidated -> ErrorUi(ErrorKey.KeyInvalidated)
        else -> serverAnswerUi()
    }

private fun DomainError.serverAnswerUi(): ErrorUi =
    when (this) {
        DomainError.Forbidden -> ErrorUi(ErrorKey.NoPermission)
        DomainError.NotFound -> ErrorUi(ErrorKey.NotFound)
        DomainError.Conflict -> ErrorUi(ErrorKey.Conflict)
        is DomainError.Rejected -> ErrorUi(ErrorKey.Rejected, reason)
        DomainError.RateLimited -> ErrorUi(ErrorKey.RateLimited)
        DomainError.Malformed -> ErrorUi(ErrorKey.Malformed)
        else -> ErrorUi(ErrorKey.ServerError)
    }
