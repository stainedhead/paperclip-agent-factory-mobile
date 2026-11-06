package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import io.kotest.matchers.shouldBe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Security.errSecParam
import kotlin.test.Test

/**
 * Review1b F-C-1: when the access control for a protected item cannot be built, the real Security backend must refuse
 * before `SecItemAdd`. Without it the add succeeds with the default (backup-eligible) accessibility and the store
 * would report Stored. A bare Kotlin/Native test has no Keychain (`errSecNotAvailable`), so the exact `errSecParam`
 * shows that the refusal came from the backend, not from the Keychain.
 */
@OptIn(ExperimentalForeignApi::class)
class AccessControlFailureTest {
    private val unbuildable = SecurityKeychain("pafm.test.accesscontrol") { null }
    private val data = "pcp_board_TESTONLY".encodeToByteArray()

    @Test
    fun `F-C-1 a biometry protected add is refused when the access control cannot be created`() {
        unbuildable.add("instance/a/boardToken", data.copyOf(), Protection.BiometryCurrentSet) shouldBe errSecParam
    }

    @Test
    fun `F-C-1 a user presence protected add is refused when the access control cannot be created`() {
        unbuildable.add("instance/a/boardToken", data.copyOf(), Protection.UserPresence) shouldBe errSecParam
    }

    @Test
    fun `F-C-1 a protected put through the store is Failed and nothing is marked`() =
        runTest {
            val store = KeychainSecretStore(unbuildable)
            val key = SecretKey(InstanceId("a"), SecretName.BoardToken)
            store.put(key, Secret.of("pcp_board_TESTONLY"), SecretPolicy(requireUserPresence = true)) shouldBe
                SecretWriteResult.Failed
        }
}
