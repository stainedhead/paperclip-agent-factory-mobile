package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** R-001: a protected secret is opened by an authentication made for that use, never by one made earlier. */
class KeystoreSecretStorePerUseTest {
    private val f = StoreFixture()

    @Test
    fun `R-001 an authentication another app just made does not satisfy a protected read`() =
        runTest {
            f.store.put(f.key, Secret.of("one"), f.protected) shouldBe SecretWriteResult.Stored
            f.prompts.clear()
            f.backend.authenticated = true // the person unlocked the phone or another app a moment ago
            f.answer = BiometricResult.Cancelled
            f.read() shouldBe SecretReadResult.Denied
            f.prompts.size shouldBe 1
        }

    @Test
    fun `R-001 two consecutive protected reads prompt twice`() =
        runTest {
            f.store.put(f.key, Secret.of("one"), f.protected) shouldBe SecretWriteResult.Stored
            f.prompts.clear()
            repeat(2) { (f.read() as SecretReadResult.Found).secret shouldBe Secret.of("one") }
            f.prompts.size shouldBe 2
        }

    @Test
    fun `R-001 a cipher the person did not authorise opens nothing even if the prompt handed it back`() =
        runTest {
            f.store.put(f.key, Secret.of("one"), f.protected) shouldBe SecretWriteResult.Stored
            f.backend.authenticated = false
            val handsBack = KeystoreSecretStore(f.backend, RecordFiles(f.dir), { _, cipher -> cipher }, "protect")
            handsBack.get(f.key, "test") shouldBe SecretReadResult.Denied
        }
}
