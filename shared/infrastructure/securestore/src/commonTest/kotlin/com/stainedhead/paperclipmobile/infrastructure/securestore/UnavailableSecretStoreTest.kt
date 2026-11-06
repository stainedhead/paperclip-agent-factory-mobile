package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-033: where no device secret store is bridged yet, nothing is stored and nothing can be read back. */
class UnavailableSecretStoreTest {
    private val key = SecretKey(InstanceId("a"), SecretName.BoardToken)

    @Test
    fun `AD-033 a write fails for both policies and keeps nothing`() =
        runTest {
            val store = UnavailableSecretStore()
            listOf(false, true).forEach { protected ->
                store.put(key, Secret.of(TestData.TOKEN), SecretPolicy(requireUserPresence = protected)) shouldBe
                    SecretWriteResult.Failed
                store.get(key, "check") shouldBe SecretReadResult.Missing
            }
        }

    @Test
    fun `AD-033 a second store instance finds nothing either so nothing was retained anywhere`() =
        runTest {
            UnavailableSecretStore().put(key, Secret.of(TestData.TOKEN), SecretPolicy(requireUserPresence = false))
            UnavailableSecretStore().get(key, "check") shouldBe SecretReadResult.Missing
        }

    @Test
    fun `AD-033 delete and deleteNamespace are harmless`() =
        runTest {
            val store = UnavailableSecretStore()
            store.delete(key)
            store.deleteNamespace(InstanceId("a"))
            store.get(key, "check") shouldBe SecretReadResult.Missing
        }
}
