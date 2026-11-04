package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a real Keystore does to a user-authentication key when the secure lock screen is removed, in two runs with the
 * change between them. An emulator cannot enrol a fingerprint without a person at the screen, so this is the closest
 * a script can get to invalidation; it is not the same event. Run by hand (see `docs/technical-details.md`): set a PIN,
 * run phase 1, clear the PIN, run phase 2. Without the `invalidationPhase` instrumentation argument both phase tests
 * are skipped. The mapping of `KeyPermanentlyInvalidatedException` itself is checked by the last test.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreInvalidationDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private val backend = AndroidKeystoreBackend("pafm.invalidation/")
    private val phase = InstrumentationRegistry.getArguments().getString("invalidationPhase")
    private val iv = ByteArray(12) { it.toByte() }
    private val ciphertext = ByteArray(32) { it.toByte() }

    @Test
    fun invalidationPhaseOneCreatesAUserAuthenticationKeyUnderALockScreen() {
        assumeTrue(phase == "1")
        assertTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure, "set a PIN first")
        val sealed = backend.seal("key", byteArrayOf(1), byteArrayOf(2), protection = true)
        assertTrue(sealed is SealOutcome.Sealed || sealed is SealOutcome.NeedsAuthentication, "got $sealed")
        assertEquals(listOf("key"), backend.aliases(""))
        // Right after the PIN was set the authentication window may still be open; either way garbage never opens.
        val opened = backend.open("key", iv, ciphertext, byteArrayOf(2))
        assertTrue(opened == OpenOutcome.NeedsAuthentication || opened == OpenOutcome.Undecryptable, "got $opened")
    }

    @Test
    fun invalidationPhaseTwoFindsNoUsableKeyAfterTheLockScreenWasRemoved() {
        assumeTrue(phase == "2")
        // Observed on the Pixel 9 Pro emulator (API 37): the system deletes the key; a device may instead keep it and
        // throw KeyPermanentlyInvalidatedException. Both must read as "this record cannot be opened", never as data.
        val outcome = backend.open("key", iv, ciphertext, byteArrayOf(2))
        assertTrue(outcome == OpenOutcome.KeyMissing || outcome == OpenOutcome.Invalidated, "got $outcome")
        backend.deleteKey("key")
        assertEquals(emptyList(), backend.aliases(""))
    }

    @Test
    fun everyKeystoreFailureMapsToTheOutcomeTheStoreExpects() {
        assertEquals(OpenOutcome.Invalidated, openFailure(KeyPermanentlyInvalidatedException()))
        assertEquals(OpenOutcome.NeedsAuthentication, openFailure(UserNotAuthenticatedException()))
        assertEquals(OpenOutcome.Undecryptable, openFailure(AEADBadTagException()))
        assertEquals(OpenOutcome.Undecryptable, openFailure(IllegalBlockSizeException()))
        assertEquals(OpenOutcome.Undecryptable, openFailure(InvalidAlgorithmParameterException()))
        assertEquals(OpenOutcome.Unreadable, openFailure(KeyStoreException()))
        assertEquals(OpenOutcome.Unreadable, openFailure(ProviderException()))
        assertEquals(OpenOutcome.Unreadable, openFailure(UnrecoverableKeyException()))
    }
}
