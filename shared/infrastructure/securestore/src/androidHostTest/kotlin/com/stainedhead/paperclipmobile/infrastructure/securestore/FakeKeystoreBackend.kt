package com.stainedhead.paperclipmobile.infrastructure.securestore

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
        if (key.protection && !authenticated) return SealOutcome.NeedsAuthentication
        return SealOutcome.Sealed(IV, scramble(plaintext) + tag(alias, aad))
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

            key.protection && !authenticated -> {
                OpenOutcome.NeedsAuthentication
            }

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
    }

    override fun deleteKey(alias: String) {
        keys.remove(alias)
    }

    override fun aliases(prefix: String): List<String> = keys.keys.filter { it.startsWith(prefix) }

    private fun scramble(bytes: ByteArray) = ByteArray(bytes.size) { (bytes[it].toInt() xor SCRAMBLE).toByte() }

    private fun tag(
        alias: String,
        aad: ByteArray,
    ): ByteArray {
        val digest = (alias.encodeToByteArray() + aad).contentHashCode().toString()
        return digest.padEnd(TAG_SIZE, '0').encodeToByteArray().copyOf(TAG_SIZE)
    }

    private companion object {
        const val SCRAMBLE = 0x5A
        const val TAG_SIZE = 16
        val IV = ByteArray(12) { (it + 1).toByte() }
    }
}
