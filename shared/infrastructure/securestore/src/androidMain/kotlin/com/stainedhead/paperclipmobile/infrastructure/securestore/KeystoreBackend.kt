package com.stainedhead.paperclipmobile.infrastructure.securestore

internal sealed interface SealOutcome {
    class Sealed(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) : SealOutcome

    /** The key demands that the person authenticates first. */
    data object NeedsAuthentication : SealOutcome

    data object Failed : SealOutcome
}

internal sealed interface OpenOutcome {
    class Opened(
        val plaintext: ByteArray,
    ) : OpenOutcome

    /** There is no such key. */
    data object KeyMissing : OpenOutcome

    /** The key was permanently invalidated, for example by a new fingerprint. */
    data object Invalidated : OpenOutcome

    /** The key demands that the person authenticates first. */
    data object NeedsAuthentication : OpenOutcome

    /** The key is fine but the record does not decrypt under it: corrupt, truncated or tampered with. */
    data object Undecryptable : OpenOutcome

    /** The key could not be used right now for any other reason. Never to be read as "missing". */
    data object Unreadable : OpenOutcome
}

/**
 * The few Keystore operations the store needs. The real implementation talks to the Android Keystore; tests script a
 * fake. A backend does not throw for an expected condition: it reports an outcome. [alias] names one AES-256-GCM key;
 * a key is made on first use, and an invalidated key is replaced by a new one when sealing.
 */
internal interface KeystoreBackend {
    /** Encrypts [plaintext] under the key [alias] (made as a user-authentication key when [protection]). */
    fun seal(
        alias: String,
        plaintext: ByteArray,
        aad: ByteArray,
        protection: Boolean,
    ): SealOutcome

    fun open(
        alias: String,
        iv: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): OpenOutcome

    /** Deletes the key; an absent key is fine, a key the Keystore refuses to delete throws. */
    fun deleteKey(alias: String)

    /** The aliases that start with [prefix], from the key store's index only: no key is used. */
    fun aliases(prefix: String): List<String>
}
