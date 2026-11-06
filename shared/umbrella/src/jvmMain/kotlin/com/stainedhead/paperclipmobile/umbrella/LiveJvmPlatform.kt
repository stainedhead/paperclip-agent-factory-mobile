package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.persistence.PaperclipPersistence
import com.stainedhead.paperclipmobile.infrastructure.persistence.openJvmPersistence
import com.stainedhead.paperclipmobile.infrastructure.platform.OptimisticConnectivity
import com.stainedhead.paperclipmobile.infrastructure.platform.SystemClock
import com.stainedhead.paperclipmobile.infrastructure.platform.SystemDeviceTimeZone
import com.stainedhead.paperclipmobile.infrastructure.platform.UnavailableBiometrics
import com.stainedhead.paperclipmobile.infrastructure.platform.UuidIdGenerator
import com.stainedhead.paperclipmobile.infrastructure.securestore.UnavailableSecretStore

/**
 * A JVM host: no secret store and no biometrics (both refuse, so pairing and every destructive action fail closed), an
 * always-online connectivity and a database that lives only as long as the process (ADR-069).
 */
internal class LiveJvmPlatform : LivePlatform {
    override val clock: Clock = SystemClock()
    override val idGenerator: IdGenerator = UuidIdGenerator()
    override val timeZone: DeviceTimeZone = SystemDeviceTimeZone()
    override val secretStore: SecretStore = UnavailableSecretStore()
    override val biometrics: Biometrics = UnavailableBiometrics()
    override val connectivity: Connectivity = OptimisticConnectivity()
    override val persistence: PaperclipPersistence = openJvmPersistence(clock)

    override fun release() = Unit
}

internal actual fun defaultLivePlatform(): LivePlatform = LiveJvmPlatform()
