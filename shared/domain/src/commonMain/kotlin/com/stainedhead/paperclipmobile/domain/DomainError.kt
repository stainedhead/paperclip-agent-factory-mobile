package com.stainedhead.paperclipmobile.domain

/** Why a call to a Paperclip instance failed (PRD FR-095 to FR-097). Raw server bodies never cross this type. */
sealed interface DomainError {
    /** No network path to the host. A read may be tried again; the phone may be offline or the server restarting. */
    data object Unreachable : DomainError

    /** The host's certificate or name could not be verified. Trying again cannot help, so reads do not retry it. */
    data object CertificateInvalid : DomainError

    data object Timeout : DomainError

    /**
     * The connection broke, or a gateway failed (502, 504), after the request may have reached the server. Unlike
     * [Unreachable] it does not say the request was never sent, so a mutation that ends here is read back and never
     * sent again (review1b F-A-5). A read treats it like [Unreachable].
     */
    data object ConnectionLost : DomainError

    /** 401: the key is missing, revoked or expired. The factory shows as Signed out and keeps its configuration. */
    data object Unauthorized : DomainError

    /**
     * The person declined the device authentication that guards this phone's key (Face ID or passcode prompt). No
     * request was sent and nothing changed: the factory stays Signed in and the person can try again (review1b F-A-2).
     */
    data object DeviceAuthDeclined : DomainError

    /**
     * The phone's key was invalidated (FR-015): on iPhone by a changed biometric set, on Android by a removed screen
     * lock (a new fingerprint does not, ADR-149). No request was sent. The factory needs re-pairing; the key on the
     * server may still be live (review1b F-A-2).
     */
    data object KeyInvalidated : DomainError

    /** 403: the role cannot do this in this company. */
    data object Forbidden : DomainError

    data object NotFound : DomainError

    /** The thing changed or was already resolved by someone else. */
    data object Conflict : DomainError

    /** 422: the server refused with a reason, for example a budget incident that cannot be raised yet. */
    data class Rejected(
        val reason: String,
    ) : DomainError

    data object ServerError : DomainError

    /** 429: the server asked the caller to slow down. A read may be tried again later; a mutation never is. */
    data object RateLimited : DomainError

    /** The body could not be understood. */
    data object Malformed : DomainError
}

/** The outcome of a call that can fail with a [DomainError]. Failures are values, never thrown. */
sealed interface DomainResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : DomainResult<T>

    data class Failure(
        val error: DomainError,
    ) : DomainResult<Nothing>
}

fun <T> DomainResult<T>.valueOrNull(): T? = (this as? DomainResult.Success)?.value
