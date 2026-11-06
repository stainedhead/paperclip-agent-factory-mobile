package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.PaperclipHttpClient
import com.stainedhead.paperclipmobile.infrastructure.api.StubPaperclipServer
import com.stainedhead.paperclipmobile.infrastructure.persistence.PaperclipPersistence
import com.stainedhead.paperclipmobile.infrastructure.persistence.openJvmPersistence
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeDeviceTimeZone
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.ktor.client.engine.mock.MockEngine
import java.io.File

/**
 * A [LivePlatform] for tests: a real SQLite database (in memory, or the file given), the pinned-server stub behind a
 * Ktor MockEngine, and the device pieces as controllable fakes. [releases] counts how often the graph let go of the
 * platform; [clients] holds every HTTP client the graph asked for.
 */
internal class LiveTestPlatform(
    override val clock: FakeClock = FakeClock(),
    val file: File? = null,
    val stub: StubPaperclipServer = StubPaperclipServer(clock::now),
    override val secretStore: SecretStore = FakeSecretStore(),
    override val biometrics: Biometrics = FakeBiometrics(),
    override val connectivity: Connectivity = FakeConnectivity(),
) : LivePlatform {
    override var keyReadReason: String = DEFAULT_KEY_READ_REASON
    override val idGenerator: IdGenerator = FakeIdGenerator("live-")
    override val timeZone: DeviceTimeZone = FakeDeviceTimeZone()
    override val persistence: PaperclipPersistence = openJvmPersistence(clock, file)

    /** When true [httpClient] throws, as a platform that cannot create its client does. */
    var failHttpClient = false
    val clients = mutableListOf<PaperclipHttpClient>()
    val engines = mutableListOf<MockEngine>()
    var releases = 0
        private set

    override fun httpClient(allowInsecureLocal: Boolean): PaperclipHttpClient =
        stub.newEngine().takeUnless { failHttpClient }.let { engine ->
            check(engine != null) { "no HTTP client" }
            engines += engine
            PaperclipHttpClient(engine, allowInsecureLocal).also { clients += it }
        }

    override fun release() {
        releases++
    }
}
