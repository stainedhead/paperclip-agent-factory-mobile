package com.stainedhead.paperclipmobile.infrastructure.securestore

import javax.crypto.Cipher

internal sealed interface SealOutcome {
    class Sealed(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) : SealOutcome

    /**
     * The key demands that the person authenticates for this one use: [cipher] is initialised and unused, and goes to
     * the system prompt as its crypto object. The operation completes with `finishSeal` on the cipher the prompt
     * returns.
     */
    class NeedsAuthentication(
        val cipher: Cipher,
    ) : SealOutcome

    data object Failed : SealOutcome
}

internal sealed interface OpenOutcome {
    class Opened(
        val plaintext: ByteArray,
    ) : OpenOutcome

    /** There is no such key. */
    data object KeyMissing : OpenOutcome

    /**
     * The key was permanently invalidated, for example by a removed screen lock (a new fingerprint does not,
     * ADR-149).
     */
    data object Invalidated : OpenOutcome

    /** Like [SealOutcome.NeedsAuthentication]: the operation completes with `finishOpen`. */
    class NeedsAuthentication(
        val cipher: Cipher,
    ) : OpenOutcome

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
    /** Encrypts [plaintext] under the key [alias] (made as a per-use user-authentication key when [protection]; then
     * the outcome is [SealOutcome.NeedsAuthentication] and nothing is encrypted until `finishSeal`). */
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

    /** Completes a protected seal with the [cipher] the system prompt authorised (one use, then it is spent). */
    fun finishSeal(
        cipher: Cipher,
        plaintext: ByteArray,
        aad: ByteArray,
    ): SealOutcome

    /** Completes a protected open with the [cipher] the system prompt authorised. */
    fun finishOpen(
        cipher: Cipher,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): OpenOutcome

    /** Deletes the key; an absent key is fine, a key the Keystore refuses to delete throws. */
    fun deleteKey(alias: String)

    /** The aliases that start with [prefix], from the key store's index only: no key is used. */
    fun aliases(prefix: String): List<String>
}
