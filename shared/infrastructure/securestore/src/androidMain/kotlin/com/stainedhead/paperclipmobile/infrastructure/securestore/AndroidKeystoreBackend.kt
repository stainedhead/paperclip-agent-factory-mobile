package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val PROVIDER = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_BITS = 256
private const val TAG_BITS = 128

/** How long after the person authenticated a user-authentication key may be used, in seconds. */
private const val AUTH_WINDOW_SECONDS = 5

/**
 * [KeystoreBackend] on the Android Keystore: non-exportable AES-256-GCM keys generated inside the secure hardware, in
 * StrongBox when the device has it and in the TEE otherwise (a device without StrongBox reports it by throwing
 * [StrongBoxUnavailableException], which is the capability probe). A key that demands user authentication needs a
 * secure lock screen and API 30 or newer; where that is not so, sealing fails: there is no fallback to an unprotected
 * key. [aliasPrefix] keeps this app's keys apart from any other use of the Keystore (and tests apart from each other).
 */
internal class AndroidKeystoreBackend(
    private val aliasPrefix: String = KeystoreSecretStore.DEFAULT_KEY_ALIAS_PREFIX,
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
        } catch (_: UserNotAuthenticatedException) {
            SealOutcome.NeedsAuthentication
        } catch (_: GeneralSecurityException) {
            SealOutcome.Failed
        } catch (_: ProviderException) {
            SealOutcome.Failed
        } catch (_: IllegalStateException) {
            SealOutcome.Failed
        }
    }

    override fun open(
        alias: String,
        iv: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): OpenOutcome {
        val key = keyStore().getKey(aliasPrefix + alias, null) as? SecretKey ?: return OpenOutcome.KeyMissing
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad)
            OpenOutcome.Opened(cipher.doFinal(ciphertext))
        } catch (e: GeneralSecurityException) {
            openFailure(e)
        } catch (e: ProviderException) {
            openFailure(e)
        }
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
        } catch (_: UserNotAuthenticatedException) {
            SealOutcome.NeedsAuthentication
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
        val key = keyStore().getKey(full, null) as? SecretKey ?: generate(full, protection)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
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
                    AUTH_WINDOW_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                ).setInvalidatedByBiometricEnrollment(true)
        }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec.build()) }
            .generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
}

/**
 * What a failed decryption means. Only a permanently invalidated key is [OpenOutcome.Invalidated]; a record that does
 * not decrypt under a healthy key (bad tag, bad block, bad IV) is [OpenOutcome.Undecryptable]; everything else is
 * [OpenOutcome.Unreadable] and changes nothing.
 */
internal fun openFailure(failure: Exception): OpenOutcome =
    when (failure) {
        is KeyPermanentlyInvalidatedException -> OpenOutcome.Invalidated

        is UserNotAuthenticatedException -> OpenOutcome.NeedsAuthentication

        is BadPaddingException,
        is IllegalBlockSizeException,
        is InvalidAlgorithmParameterException,
        -> OpenOutcome.Undecryptable

        else -> OpenOutcome.Unreadable
    }
