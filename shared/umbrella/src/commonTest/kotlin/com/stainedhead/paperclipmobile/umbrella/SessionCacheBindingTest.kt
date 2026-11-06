package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.auth.SessionCachingSecretStore
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

/** ADR-145: every graph hands the app the session cache; the device store is only ever reached through it. */
class SessionCacheBindingTest {
    @Test
    fun `the demo graph and the default graph put the session cache above the secret store`() {
        listOf(PaperclipCore.createDemoGraph(), PaperclipCore.createGraph(allowInsecureLocal = false)).forEach {
            it.resolve<SecretStore>().shouldBeInstanceOf<SessionCachingSecretStore>()
            it.resolveRaw<SecretStore>().shouldBeInstanceOf<FakeSecretStore>()
            (it.resolve<SecretStore>() === it.resolveRaw<SecretStore>()) shouldBe false
            it.close()
        }
    }
}
