package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.PaperclipHttpClient
import com.stainedhead.paperclipmobile.infrastructure.persistence.PaperclipPersistence
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeDeviceTimeZone
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.test.Test

/**
 * A platform whose database cannot be opened right now, as when the Android database file is locked. It counts what the
 * graph lets go of. (A working database needs the Android SQLite classes, so the success path runs on the emulator, see
 * `AndroidLiveGraphDeviceTest`.)
 */
private class BrokenDatabasePlatform : LivePlatform {
    override val clock: Clock = FakeClock()
    override val idGenerator: IdGenerator = FakeIdGenerator("android-")
    override val timeZone: DeviceTimeZone = FakeDeviceTimeZone()
    override val secretStore: SecretStore = FakeSecretStore()
    override val biometrics: Biometrics = FakeBiometrics()
    override val connectivity: Connectivity = FakeConnectivity()
    override val persistence: PaperclipPersistence get() = error("the database is locked")

    val clients = mutableListOf<PaperclipHttpClient>()
    var releases = 0
        private set

    override fun httpClient(allowInsecureLocal: Boolean): PaperclipHttpClient =
        PaperclipHttpClient(MockEngine { respondOk() }, allowInsecureLocal).also { clients += it }

    override fun release() {
        releases++
    }
}

/** AND-004: opening the live graph on Android reports a failure instead of crashing, and leaves nothing open. */
class AndroidLiveGraphTest {
    @Test
    fun `a platform that cannot be built is Unavailable with the cause`() {
        val cause = IllegalStateException("no keystore")

        val result = AndroidLiveGraph.open(false, FakePairingBrowser()) { throw cause }

        result.shouldBeInstanceOf<AndroidGraphOpen.Unavailable>().cause shouldBe cause
    }

    @Test
    fun `a graph that cannot be built is Unavailable and releases the platform and its HTTP client`() {
        val platform = BrokenDatabasePlatform()

        val result = AndroidLiveGraph.open(false, FakePairingBrowser()) { platform }

        result.shouldBeInstanceOf<AndroidGraphOpen.Unavailable>()
        platform.releases shouldBe 1
        platform.clients.size shouldBe 1
    }
}
