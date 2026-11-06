package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

private const val PROVIDER = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_BITS = 256
private const val TAG_BITS = 128

/** `setUserAuthenticationParameters` timeout 0: every use of the key needs its own authentication (crypto object). */
private const val PER_USE_AUTHORIZATION = 0

/**
 * Who may open a protected key: strong biometrics or the device credential (PIN fallback, ADR-149). Because the
 * credential is allowed, enrolling a new fingerprint does not invalidate the key; removing the screen lock deletes it.
 * The unlock prompt (`CIPHER_AUTHENTICATORS` in `infrastructure/platform`) must name the same set.
 */
internal const val PROTECTED_KEY_AUTHENTICATORS =
    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL

/**
 * [KeystoreBackend] on the Android Keystore: non-exportable AES-256-GCM keys generated inside the secure hardware, in
 * StrongBox when the device has it and in the TEE otherwise (a device without StrongBox reports it by throwing
 * [StrongBoxUnavailableException], which is the capability probe). A key that demands user authentication needs a
 * secure lock screen and API 30 or newer; where that is not so, sealing fails: there is no fallback to an unprotected
 * key. Such a key is authorised for one use at a time (validity 0): the system prompt authorises one initialised
 * [Cipher], which the store completes with [finishSeal] or [finishOpen]. No window of seconds exists, so an
 * authentication made by another app or by unlocking the phone never opens a protected secret (R-001).
 * [aliasPrefix] keeps this app's keys apart from any other use of the Keystore (and tests apart from each other).
 */
internal class AndroidKeystoreBackend(
    private val aliasPrefix: String = KeystoreSecretStore.DEFAULT_KEY_ALIAS_PREFIX,
    /** Loads a key by its full alias; a seam so tests can make the Keystore refuse. */
    private val loadKey: (String) -> SecretKey? = ::keystoreKey,
) : KeystoreBackend {
    override fun seal(
        alias: String,
        plaintext: ByteArray,
        aad: ByteArray,
        protection: Boolean,
    ): SealOutcome {
        val full = aliasPrefix + alias
        return try {
            sealWith(full, plaintext, aad, protection)
        } catch (_: KeyPermanentlyInvalidatedException) {
            // The record this key sealed is useless now; the new value gets a new key.
            sealAfterReplacing(full, plaintext, aad, protection)
        } catch (_: GeneralSecurityException) {
            SealOutcome.Failed
        } catch (_: ProviderException) {
            SealOutcome.Failed
        } catch (_: IllegalStateException) {
            SealOutcome.Failed
        }
    }

    override fun finishSeal(
        cipher: Cipher,
        plaintext: ByteArray,
        aad: ByteArray,
    ): SealOutcome =
        try {
            cipher.updateAAD(aad)
            SealOutcome.Sealed(cipher.iv, cipher.doFinal(plaintext))
        } catch (_: GeneralSecurityException) {
            SealOutcome.Failed
        } catch (_: ProviderException) {
            SealOutcome.Failed
        }

    override fun open(
        alias: String,
        iv: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): OpenOutcome {
        return try {
            val key = loadKey(aliasPrefix + alias) ?: return OpenOutcome.KeyMissing
            val perUse = requiresAuthentication(key)
            // A user-authentication key from before per-use authorisation can no longer be trusted: pair again.
            if (perUse && authorisationWindowSeconds(key) != PER_USE_AUTHORIZATION) return OpenOutcome.Invalidated
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            if (perUse) return OpenOutcome.NeedsAuthentication(cipher)
            cipher.updateAAD(aad)
            OpenOutcome.Opened(cipher.doFinal(ciphertext))
        } catch (e: GeneralSecurityException) {
            openFailure(e)
        } catch (e: ProviderException) {
            openFailure(e)
        }
    }

    override fun finishOpen(
        cipher: Cipher,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): OpenOutcome =
        try {
            cipher.updateAAD(aad)
            OpenOutcome.Opened(cipher.doFinal(ciphertext))
        } catch (e: GeneralSecurityException) {
            openFailure(e)
        } catch (e: ProviderException) {
            openFailure(e)
        }

    override fun deleteKey(alias: String) {
        val store = keyStore()
        if (store.containsAlias(aliasPrefix + alias)) store.deleteEntry(aliasPrefix + alias)
    }

    override fun aliases(prefix: String): List<String> =
        keyStore()
            .aliases()
            .toList()
            .filter { it.startsWith(aliasPrefix + prefix) }
            .map { it.removePrefix(aliasPrefix) }

    private fun sealAfterReplacing(
        full: String,
        plaintext: ByteArray,
        aad: ByteArray,
        protection: Boolean,
    ): SealOutcome =
        try {
            keyStore().deleteEntry(full)
            sealWith(full, plaintext, aad, protection)
        } catch (_: GeneralSecurityException) {
            SealOutcome.Failed
        } catch (_: ProviderException) {
            SealOutcome.Failed
        } catch (_: IllegalStateException) {
            SealOutcome.Failed
        }

    private fun sealWith(
        full: String,
        plaintext: ByteArray,
        aad: ByteArray,
        protection: Boolean,
    ): SealOutcome {
        var key = loadKey(full)
        if (protection && key != null && authorisationWindowSeconds(key) != PER_USE_AUTHORIZATION) {
            // A user-authentication key from before per-use authorisation: the new value gets a new key.
            keyStore().deleteEntry(full)
            key = null
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key ?: generate(full, protection))
        if (protection) return SealOutcome.NeedsAuthentication(cipher)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return SealOutcome.Sealed(cipher.iv, ciphertext)
    }

    private fun generate(
        full: String,
        protection: Boolean,
    ): SecretKey {
        check(!protection || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "user-authentication keys need Android 11 or newer"
        }
        return try {
            generate(full, protection, strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(full, protection, strongBox = false)
        }
    }

    private fun generate(
        full: String,
        protection: Boolean,
        strongBox: Boolean,
    ): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(full, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(KEY_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setIsStrongBoxBacked(strongBox)
        if (protection) {
            spec
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    PER_USE_AUTHORIZATION,
                    PROTECTED_KEY_AUTHENTICATORS,
                ).setInvalidatedByBiometricEnrollment(true)
        }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec.build()) }
            .generateKey()
    }
}

private fun keyInfo(key: SecretKey): KeyInfo =
    SecretKeyFactory.getInstance(key.algorithm, PROVIDER).getKeySpec(key, KeyInfo::class.java) as KeyInfo

private fun requiresAuthentication(key: SecretKey): Boolean = keyInfo(key).isUserAuthenticationRequired

/** The seconds a user-authentication key stays usable after an authentication; 0 is one use (`CryptoObject`). */
private fun authorisationWindowSeconds(key: SecretKey): Int = keyInfo(key).userAuthenticationValidityDurationSeconds

private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

/** The AES key stored under [alias], or `null`. May throw what the Keystore throws. */
internal fun keystoreKey(alias: String): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

/**
 * What a failed decryption or key load means. Only a permanently invalidated key or a key the Keystore can no longer
 * recover ([UnrecoverableKeyException], R-010) is [OpenOutcome.Invalidated]; a record that does not decrypt under a
 * healthy key (bad tag, bad block, bad IV) is [OpenOutcome.Undecryptable]; everything else is
 * [OpenOutcome.Unreadable] and changes nothing.
 */
internal fun openFailure(failure: Exception): OpenOutcome =
    when (failure) {
        is KeyPermanentlyInvalidatedException, is UnrecoverableKeyException -> OpenOutcome.Invalidated

        is BadPaddingException,
        is IllegalBlockSizeException,
        is InvalidAlgorithmParameterException,
        -> OpenOutcome.Undecryptable

        else -> OpenOutcome.Unreadable
    }
