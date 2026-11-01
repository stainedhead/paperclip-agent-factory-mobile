package com.stainedhead.paperclipmobile.domain.port

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret

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
