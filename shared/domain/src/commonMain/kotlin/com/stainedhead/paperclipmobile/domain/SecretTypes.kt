package com.stainedhead.paperclipmobile.domain

/** The secrets stored per instance (PRD FR-013). */
enum class SecretName(
    val wire: String,
) {
    BoardToken("boardToken"),
    ClientCertificate("clientCertificate"),
    ClientCertificatePassword("clientCertificatePassword"),
}

/** A namespaced secret key, `instance/{instanceId}/{secretName}` (PRD FR-010). */
data class SecretKey(
    val instanceId: InstanceId,
    val name: SecretName,
) {
    val path: String get() = namespacePrefix(instanceId) + name.wire

    companion object {
        fun namespacePrefix(instanceId: InstanceId): String = "instance/${instanceId.value}/"
    }
}

/** How a secret is protected at rest. [requireUserPresence] is the "biometric protection on" mode (FR-011, FR-012). */
data class SecretPolicy(
    val requireUserPresence: Boolean,
)

enum class SecretWriteResult {
    Stored,
    Failed,
}

/**
 * The outcome of a read that must prove it authenticated the person (ADR-146). [Authenticated] is answered only when
 * this very read opened a secret protected by the device owner's authentication, after a prompt the person approved.
 * A plain [SecretReadResult.Found] proves nothing: the secret may be unprotected, or served from memory.
 */
sealed interface AttestedReadResult {
    data class Authenticated(
        val secret: Secret,
    ) : AttestedReadResult

    /** No attestation: the secret is unprotected, missing or could not be read for a reason other than a refusal. */
    data object NotAttested : AttestedReadResult

    /** The person refused or failed the prompt this read showed. */
    data object Denied : AttestedReadResult

    /** The protected key was invalidated (for example a new fingerprint); the factory needs pairing again. */
    data object Invalidated : AttestedReadResult
}

sealed interface SecretReadResult {
    data class Found(
        val secret: Secret,
    ) : SecretReadResult

    data object Missing : SecretReadResult

    /** The key was invalidated, for example by a new fingerprint. The factory needs re-pairing (FR-015). */
    data object Invalidated : SecretReadResult

    /** The person declined the device authentication that guards this secret. */
    data object Denied : SecretReadResult
}
