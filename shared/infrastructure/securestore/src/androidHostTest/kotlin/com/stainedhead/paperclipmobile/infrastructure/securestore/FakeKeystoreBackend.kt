package com.stainedhead.paperclipmobile.infrastructure.securestore

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * An in-memory [KeystoreBackend]: a "key" is an entry that can be invalidated or made to demand authentication, and a
 * sealed value is the plaintext scrambled with a tag that binds it to the key and the associated data. It lets the
 * store's decisions be tested on the JVM; the real Keystore is exercised on an emulator.
 */
internal class FakeKeystoreBackend : KeystoreBackend {
    class Key(
        val protection: Boolean,
        var invalidated: Boolean = false,
    )

    val keys = mutableMapOf<String, Key>()
    var authenticated = false
    var failNextSeal = false
    var unreadable = false
    var sealCalls = 0
    var openCalls = 0

    private class Pending(
        val alias: String,
        val iv: ByteArray?,
    )

    private val pending = java.util.IdentityHashMap<Cipher, Pending>()

    override fun seal(
        alias: String,
        plaintext: ByteArray,
        aad: ByteArray,
        protection: Boolean,
    ): SealOutcome {
        sealCalls++
        if (failNextSeal) {
            failNextSeal = false
            return SealOutcome.Failed
        }
        var key = keys[alias]
        if (key == null || key.invalidated) {
            key = Key(protection)
            keys[alias] = key
        }
        if (key.protection) return SealOutcome.NeedsAuthentication(pendingCipher(Pending(alias, null)))
        return SealOutcome.Sealed(IV, scramble(plaintext) + tag(alias, aad))
    }

    override fun finishSeal(
        cipher: Cipher,
        plaintext: ByteArray,
        aad: ByteArray,
    ): SealOutcome {
        val pending = pending.remove(cipher) ?: return SealOutcome.Failed
        if (!consumeAuthentication()) return SealOutcome.Failed
        return SealOutcome.Sealed(IV, scramble(plaintext) + tag(pending.alias, aad))
    }

    override fun open(
        alias: String,
        iv: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): OpenOutcome {
        openCalls++
        val key = keys[alias]
        return when {
            unreadable -> {
                OpenOutcome.Unreadable
            }

            key == null -> {
                OpenOutcome.KeyMissing
            }

            key.invalidated -> {
                OpenOutcome.Invalidated
            }

            key.protection -> {
                OpenOutcome.NeedsAuthentication(pendingCipher(Pending(alias, iv)))
            }

            else -> {
                decrypt(alias, iv, ciphertext, aad)
            }
        }
    }

    override fun finishOpen(
        cipher: Cipher,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): OpenOutcome {
        val pending = pending.remove(cipher) ?: return OpenOutcome.Unreadable
        // Without the person's approval of this very operation the Keystore refuses to use the key.
        if (!consumeAuthentication()) return OpenOutcome.Unreadable
        return decrypt(pending.alias, checkNotNull(pending.iv), ciphertext, aad)
    }

    private fun decrypt(
        alias: String,
        iv: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): OpenOutcome =
        when {
            iv.size != IV.size || ciphertext.size < TAG_SIZE -> {
                OpenOutcome.Undecryptable
            }

            !ciphertext.copyOfRange(ciphertext.size - TAG_SIZE, ciphertext.size).contentEquals(tag(alias, aad)) -> {
                OpenOutcome.Undecryptable
            }

            else -> {
                OpenOutcome.Opened(scramble(ciphertext.copyOfRange(0, ciphertext.size - TAG_SIZE)))
            }
        }

    override fun deleteKey(alias: String) {
        keys.remove(alias)
    }

    override fun aliases(prefix: String): List<String> = keys.keys.filter { it.startsWith(prefix) }

    private fun pendingCipher(operation: Pending): Cipher {
        // A real software cipher: it only has to travel through the prompt and come back.
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(SOFTWARE_KEY_BYTES), "AES"))
        pending[cipher] = operation
        return cipher
    }

    /** A user-authentication key is authorised for one use: the approval is spent by the operation it allowed. */
    private fun consumeAuthentication(): Boolean = authenticated.also { authenticated = false }

    private companion object {
        val IV = ByteArray(12) { (it + 1).toByte() }
    }
}

private const val SCRAMBLE = 0x5A
private const val TAG_SIZE = 16
private const val SOFTWARE_KEY_BYTES = 16

private fun scramble(bytes: ByteArray) = ByteArray(bytes.size) { (bytes[it].toInt() xor SCRAMBLE).toByte() }

private fun tag(
    alias: String,
    aad: ByteArray,
): ByteArray {
    val digest = (alias.encodeToByteArray() + aad).contentHashCode().toString()
    return digest.padEnd(TAG_SIZE, '0').encodeToByteArray().copyOf(TAG_SIZE)
}
