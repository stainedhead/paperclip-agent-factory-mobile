package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

/**
 * AND-004: the Android live platform. A working database needs the Android SQLite classes, so the paths that open one
 * (every port resolved as real, `release()` unregistering the network callback) run on the emulator, see
 * `AndroidLiveGraphDeviceTest`.
 */
class AndroidLivePlatformTest {
    @Test
    fun `a database that cannot be opened stops the platform before a network monitor is registered`() {
        var monitors = 0

        val failure =
            runCatching {
                AndroidLivePlatform(
                    secretStore = FakeSecretStore(),
                    biometrics = FakeBiometrics(),
                    openPersistence = { error("the database is locked") },
                    openConnectivity = { FakeConnectivity().also { monitors++ } },
                )
            }.exceptionOrNull()

        failure.shouldBeInstanceOf<IllegalStateException>()
        monitors shouldBe 0
    }
}
