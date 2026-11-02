package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.persistence.PaperclipPersistence
import com.stainedhead.paperclipmobile.infrastructure.persistence.openIosPersistence
import com.stainedhead.paperclipmobile.infrastructure.platform.LocalAuthenticationBiometrics
import com.stainedhead.paperclipmobile.infrastructure.platform.NwConnectivity
import com.stainedhead.paperclipmobile.infrastructure.platform.ReasonTexts
import com.stainedhead.paperclipmobile.infrastructure.platform.SystemClock
import com.stainedhead.paperclipmobile.infrastructure.platform.SystemDeviceTimeZone
import com.stainedhead.paperclipmobile.infrastructure.platform.UuidIdGenerator
import com.stainedhead.paperclipmobile.infrastructure.securestore.KeychainSecretStore

private const val KEYCHAIN_SERVICE = "com.stainedhead.paperclipmobile.board-keys"

/**
 * The iPhone's device services: the Keychain, LocalAuthentication, the system network monitor, the system clock and the
 * SQLite database in `Application Support` (or [dataDirectory], which tests give a temporary folder).
 */
internal class IosLivePlatform(
    keychainService: String,
    dataDirectory: String?,
    reasons: ReasonTexts,
    monitorFactory: () -> NwConnectivity = ::NwConnectivity,
) : LivePlatform {
    override val clock: Clock = SystemClock()
    override val idGenerator: IdGenerator = UuidIdGenerator()
    override val timeZone: DeviceTimeZone = SystemDeviceTimeZone()
    override val secretStore: SecretStore = KeychainSecretStore(keychainService)
    override val biometrics: Biometrics = LocalAuthenticationBiometrics(reasons)

    // The only member that can throw comes first, and the monitor last: a failure while opening the database must
    // not leave a running network monitor behind (review1b F-B-4).
    override val persistence: PaperclipPersistence =
        if (dataDirectory == null) openIosPersistence(clock) else openIosPersistence(clock, dataDirectory)
    private val monitor = monitorFactory()
    override val connectivity: Connectivity = monitor

    override fun release() = monitor.close()
}

internal actual fun defaultLivePlatform(): LivePlatform =
    IosLivePlatform(KEYCHAIN_SERVICE, dataDirectory = null, ReasonTexts.default)

/**
 * Where an iOS app or test puts things. [keychainService] is the Keychain `kSecAttrService` of the board keys,
 * [dataDirectory] holds the database (the app passes a folder in `Application Support`), and the two reasons are the
 * localized sentences the system shows when unlocking the app and before a destructive action.
 */
data class IosLiveSettings(
    val keychainService: String,
    val dataDirectory: String,
    val unlockReason: String,
    val confirmReason: String,
)

/**
 * The iOS entry for apps and tests that choose where things live. `PaperclipCore.createLiveGraph(allowInsecureLocal,
 * pairingBrowser)` is the same graph with the app's Keychain service, the default data folder and English prompt texts.
 */
object LiveIosGraph {
    fun createLiveGraph(
        allowInsecureLocal: Boolean,
        pairingBrowser: PairingBrowser,
        settings: IosLiveSettings,
    ): PaperclipGraph =
        PaperclipCore.createLiveGraph(
            allowInsecureLocal,
            pairingBrowser,
            IosLivePlatform(
                settings.keychainService,
                settings.dataDirectory,
                ReasonTexts(settings.unlockReason, settings.confirmReason),
            ),
        )
}
