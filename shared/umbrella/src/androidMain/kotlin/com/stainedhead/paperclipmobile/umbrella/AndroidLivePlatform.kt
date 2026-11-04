package com.stainedhead.paperclipmobile.umbrella

import android.content.Context
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.persistence.PaperclipPersistence
import com.stainedhead.paperclipmobile.infrastructure.persistence.openAndroidPersistence
import com.stainedhead.paperclipmobile.infrastructure.platform.AuthHost
import com.stainedhead.paperclipmobile.infrastructure.platform.BiometricPromptBiometrics
import com.stainedhead.paperclipmobile.infrastructure.platform.ConnectivityManagerConnectivity
import com.stainedhead.paperclipmobile.infrastructure.platform.ReasonTexts
import com.stainedhead.paperclipmobile.infrastructure.platform.SystemClock
import com.stainedhead.paperclipmobile.infrastructure.platform.SystemDeviceTimeZone
import com.stainedhead.paperclipmobile.infrastructure.platform.UuidIdGenerator
import com.stainedhead.paperclipmobile.infrastructure.securestore.KeystoreSecretStore

/**
 * The phone's device services: the Keystore secret store, the system authentication prompt, the system network monitor,
 * the system clock and the SQLite database in `noBackupFilesDir`. The HTTP client is the shared default, which is
 * OkHttp on Android.
 *
 * The database is opened first and the network monitor last, so a failure while opening the database leaves no monitor
 * registered (the same order as the iOS platform). [openPersistence] and [openConnectivity] are the seams the real
 * constructors plug into; `androidLivePlatform` supplies them.
 */
internal class AndroidLivePlatform(
    override val secretStore: SecretStore,
    override val biometrics: Biometrics,
    openPersistence: (Clock) -> PaperclipPersistence,
    openConnectivity: () -> Connectivity,
) : LivePlatform {
    override val clock: Clock = SystemClock()
    override val idGenerator: IdGenerator = UuidIdGenerator()
    override val timeZone: DeviceTimeZone = SystemDeviceTimeZone()
    override val persistence: PaperclipPersistence = openPersistence(clock)
    override val connectivity: Connectivity = openConnectivity()

    /** Unregisters the network callback; the graph closes the database and the HTTP client itself. */
    override fun release() {
        (connectivity as? AutoCloseable)?.close()
    }
}

/**
 * The real Android platform. The Keystore store asks for the device owner through the same [BiometricPromptBiometrics]
 * the destructive actions use, so every prompt of the app is shown and mapped in one place.
 */
internal fun androidLivePlatform(
    context: Context,
    settings: AndroidLiveSettings,
    authHost: AuthHost,
): AndroidLivePlatform {
    val prompts = BiometricPromptBiometrics(authHost, ReasonTexts(settings.unlockReason, settings.confirmReason))
    val secrets =
        KeystoreSecretStore(
            context,
            authenticate = prompts::authenticateWithText,
            writeReason = settings.writeReason,
            keyAliasPrefix = settings.keyAliasPrefix,
        )
    return AndroidLivePlatform(
        secretStore = secrets,
        biometrics = prompts,
        openPersistence = { clock -> openAndroidPersistence(context, clock) },
        openConnectivity = { ConnectivityManagerConnectivity(context) },
    )
}

/**
 * The context free entry has no `Context` for the database and the Keystore, so it stays refused rather than run on a
 * pretend secret store (ADR-069, INTENT principle 1). The Android app opens the live graph with
 * `AndroidLiveGraph.openLiveGraph`.
 */
internal actual fun defaultLivePlatform(): LivePlatform =
    error(
        "The live graph needs a Context on Android: use AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, " +
            "pairingBrowser, settings)",
    )
