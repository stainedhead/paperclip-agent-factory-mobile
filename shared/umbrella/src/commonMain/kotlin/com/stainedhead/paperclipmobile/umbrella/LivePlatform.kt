package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.PaperclipHttpClient
import com.stainedhead.paperclipmobile.infrastructure.api.createPaperclipHttpClient
import com.stainedhead.paperclipmobile.infrastructure.persistence.PaperclipPersistence

/**
 * What the live graph cannot create for itself: the device services of one platform and its database (AD-040,
 * ADR-067). The iOS app gets one from `PlatformServices`; tests build one on a JVM database and a stub server.
 *
 * Internal on purpose: it carries database and HTTP types that must not reach the Swift header. The graph owns the
 * HTTP client it asks for and the [persistence]; it hands the platform back through [release].
 */
internal interface LivePlatform {
    val clock: Clock
    val idGenerator: IdGenerator
    val timeZone: DeviceTimeZone
    val secretStore: SecretStore
    val biometrics: Biometrics
    val connectivity: Connectivity
    val persistence: PaperclipPersistence

    /** The client for this graph: the platform's engine unless a test supplies another. */
    fun httpClient(allowInsecureLocal: Boolean): PaperclipHttpClient = createPaperclipHttpClient(allowInsecureLocal)

    /** Lets go of what the platform created besides the database and the HTTP client, such as a network monitor. */
    fun release()
}
