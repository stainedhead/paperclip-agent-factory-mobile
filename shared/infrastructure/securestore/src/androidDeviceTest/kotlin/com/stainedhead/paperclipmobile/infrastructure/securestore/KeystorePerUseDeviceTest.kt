package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.app.KeyguardManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import javax.crypto.SecretKey as JceSecretKey

/** R-001 on the real Keystore: a protected key is authorised for one use, and keys of the old design are refused. */
@RunWith(AndroidJUnit4::class)
class KeystorePerUseDeviceTest {
    private val f = DeviceStoreFixture()
    private val prefixes = f.prefixes

    @After
    fun cleanUp() = f.cleanUp()

    private fun needsALockScreen() {
        val keyguard = f.context.getSystemService(KeyguardManager::class.java)
        assumeTrue("needs a secure lock screen", keyguard.isDeviceSecure)
    }

    @Test
    fun aProtectedKeyIsAuthorisedForOneUseAndNotForAWindowOfSeconds() {
        needsALockScreen()
        val prefix = "pafm.test.${UUID.randomUUID()}/".also { prefixes += it }
        val backend = AndroidKeystoreBackend(prefix)
        val sealed = backend.seal("k", byteArrayOf(1), byteArrayOf(2), protection = true)
        assertTrue(sealed is SealOutcome.NeedsAuthentication, "got $sealed: nothing is encrypted before the prompt")
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aesKey = keyStore.getKey(prefix + "k", null) as JceSecretKey
        val info =
            SecretKeyFactory
                .getInstance(
                    aesKey.algorithm,
                    "AndroidKeyStore",
                ).getKeySpec(aesKey, KeyInfo::class.java)
        assertEquals(
            0,
            (info as KeyInfo).userAuthenticationValidityDurationSeconds,
            "a validity window lets any recent unlock or other app's authentication satisfy a protected read",
        )
    }

    @Test
    fun aKeyMadeWithTheOldFiveSecondWindowReadsAsInvalidatedSoThePersonPairsAgain() {
        needsALockScreen()
        val prefix = "pafm.test.${UUID.randomUUID()}/".also { prefixes += it }
        val spec =
            KeyGenParameterSpec
                .Builder(prefix + "legacy:p", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(KEY_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    OLD_WINDOW_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                ).build()
        KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
        val backend = AndroidKeystoreBackend(prefix)
        val opened = backend.open("legacy:p", ByteArray(IV_BYTES), ByteArray(TAG_BYTES), byteArrayOf(2))
        assertEquals(OpenOutcome.Invalidated, opened, "an old window key is not trusted; the factory pairs again")
        // Writing a protected value replaces the old key by a per-use key.
        assertTrue(
            backend.seal(
                "legacy:p",
                byteArrayOf(1),
                byteArrayOf(2),
                protection = true,
            ) is SealOutcome.NeedsAuthentication,
        )
    }

    private companion object {
        const val KEY_BITS = 256
        const val OLD_WINDOW_SECONDS = 5
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}
