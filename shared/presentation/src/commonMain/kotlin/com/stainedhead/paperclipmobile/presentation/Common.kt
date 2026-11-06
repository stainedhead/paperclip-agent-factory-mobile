package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.auth.KeyExpiry
import com.stainedhead.paperclipmobile.application.auth.KeyExpiryState
import com.stainedhead.paperclipmobile.application.auth.RevokeOutcome
import com.stainedhead.paperclipmobile.domain.BaseUriError
import com.stainedhead.paperclipmobile.domain.Company
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/** What is wrong with the address, as a key the platform UI turns into a localised message. */
enum class AddressIssue {
    MissingScheme,
    MustUseHttps,
    MissingHost,
    InvalidHost,
    InvalidPort,
    InvalidPath,
    RemoveCredentials,
    RemoveQueryOrFragment,
}

internal fun BaseUriError.toIssue(): AddressIssue? =
    when (this) {
        BaseUriError.Empty -> null
        BaseUriError.MissingScheme -> AddressIssue.MissingScheme
        BaseUriError.UnsupportedScheme, BaseUriError.InsecureHttp -> AddressIssue.MustUseHttps
        BaseUriError.MissingHost -> AddressIssue.MissingHost
        BaseUriError.InvalidHost -> AddressIssue.InvalidHost
        BaseUriError.InvalidPort -> AddressIssue.InvalidPort
        BaseUriError.InvalidPath -> AddressIssue.InvalidPath
        BaseUriError.UserInfoNotAllowed -> AddressIssue.RemoveCredentials
        BaseUriError.QueryOrFragmentNotAllowed -> AddressIssue.RemoveQueryOrFragment
    }

/** Keys for the messages of PRD FR-097. The platform UI owns the wording. */
enum class ErrorKey {
    Unreachable,

    /** The server's certificate or name could not be verified. */
    CertificateInvalid,
    Timeout,
    SignedOut,
    NoPermission,
    NotFound,
    Conflict,
    Rejected,
    ServerError,
    RateLimited,
    Malformed,

    /** A pasted key does not look like a board key. Nothing was sent. */
    InvalidKey,

    /** The phone's secure store refused to keep the key. */
    StorageFailed,

    /**
     * The phone could not read the key: the person declined Face ID or the passcode, or the device is locked. The
     * Keychain cannot tell these apart to the app (one "unreadable" status class, ADR-085). Nothing was sent or
     * changed; try again, or sign out / remove "here only" (F-A-2, F-R-2).
     */
    AuthDeclined,

    /**
     * The phone's key was invalidated (iPhone: changed biometrics; Android: removed screen lock): the factory
     * needs re-pairing (FR-015).
     */
    KeyInvalidated,

    /** This phone does not know which server key is its own, so it will not revoke keys (PRD FR-025). */
    CurrentKeyUnknown,
}

/** An error to show. [detail] is the server's own reason for [ErrorKey.Rejected]; it is never raw JSON. */
data class ErrorUi(
    val key: ErrorKey,
    val detail: String? = null,
)

/** Why device authentication did not go ahead. */
enum class DeviceAuthIssue {
    Cancelled,
    Failed,
    LockedOut,
    Unavailable,
}

internal fun BiometricResult.toIssue(): DeviceAuthIssue? =
    when (this) {
        BiometricResult.Approved -> null
        BiometricResult.Cancelled -> DeviceAuthIssue.Cancelled
        BiometricResult.Failed -> DeviceAuthIssue.Failed
        BiometricResult.LockedOut -> DeviceAuthIssue.LockedOut
        BiometricResult.Unavailable -> DeviceAuthIssue.Unavailable
    }

enum class FactoryStatusUi {
    SignedIn,
    SignedOut,
    NeedsRePairing,
}

internal fun InstanceStatus.toUi(): FactoryStatusUi =
    when (this) {
        InstanceStatus.SignedIn -> FactoryStatusUi.SignedIn
        InstanceStatus.SignedOut -> FactoryStatusUi.SignedOut
        InstanceStatus.NeedsRePairing -> FactoryStatusUi.NeedsRePairing
    }

enum class RoleUi {
    Owner,
    Operator,
    Viewer,
}

internal fun Role.toUi(): RoleUi =
    when (this) {
        Role.Owner -> RoleUi.Owner
        Role.Operator -> RoleUi.Operator
        Role.Viewer -> RoleUi.Viewer
    }

data class CompanyUi(
    val id: String,
    val name: String,
    val role: RoleUi,
)

internal fun Company.toUi() = CompanyUi(id.value, name, role.toUi())

enum class KeyExpiryUi {
    NoExpiry,
    Valid,
    ExpiringSoon,
    Expired,
}

/** [daysLeft] is null when the key never expires. */
data class ExpiryUi(
    val state: KeyExpiryUi,
    val daysLeft: Int?,
)

internal fun KeyExpiry.toUi() =
    ExpiryUi(
        when (state) {
            KeyExpiryState.NoExpiry -> KeyExpiryUi.NoExpiry
            KeyExpiryState.Valid -> KeyExpiryUi.Valid
            KeyExpiryState.ExpiringSoon -> KeyExpiryUi.ExpiringSoon
            KeyExpiryState.Expired -> KeyExpiryUi.Expired
        },
        daysLeft,
    )

internal fun Instance.hostName(): String =
    baseUri.value
        .substringAfter("://")
        .substringBefore('/')
        .substringBefore(':')

/** A scope for one state holder, cancelled by `close()` without touching the scope it was given. */
internal fun childScope(parent: CoroutineScope): CoroutineScope =
    CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))

/** Whether the server confirmed revoking a key when the app signed out or removed a factory. */
enum class ServerKeyOutcome {
    Revoked,
    AlreadyInvalid,
    Unconfirmed,
    NotAttempted,

    /** The person declined the key prompt: nothing was revoked or deleted (F-A-2). */
    Declined,
}

internal fun RevokeOutcome.toUi() =
    when (this) {
        RevokeOutcome.Revoked -> ServerKeyOutcome.Revoked
        RevokeOutcome.AlreadyInvalid -> ServerKeyOutcome.AlreadyInvalid
        RevokeOutcome.Unconfirmed -> ServerKeyOutcome.Unconfirmed
        RevokeOutcome.NotAttempted -> ServerKeyOutcome.NotAttempted
        RevokeOutcome.Declined -> ServerKeyOutcome.Declined
    }

/** Which factory and company a screen is for. Strings, because Swift passes ids it got from the screen before. */
data class CompanyTarget(
    val instanceId: String,
    val companyId: String,
)
