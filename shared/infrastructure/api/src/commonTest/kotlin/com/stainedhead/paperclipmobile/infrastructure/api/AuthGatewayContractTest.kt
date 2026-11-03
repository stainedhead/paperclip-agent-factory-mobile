package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.contract.AuthAccountContract
import com.stainedhead.paperclipmobile.testing.contract.AuthPairingContract
import com.stainedhead.paperclipmobile.testing.contract.AuthWrongServerContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-011, AD-014: the shared pairing contract, run against the real adapter and the stub server. */
class AuthPairingGatewayContractTest : AuthPairingContract() {
    private var world = AuthWorld()

    override val server: BaseUri = TestData.uri()
    override val validKey: Secret = Secret.of(TestData.TOKEN)
    override val invalidKey: Secret = Secret.of("pcp_board_TESTONLY_WRONG")

    override fun create(): AuthGateway = AuthWorld().also { world = it }.gateway

    override suspend fun approveInBrowser(challenge: PairingChallenge) =
        world.server.approve(AuthWorld.HOME, challenge.id)

    override suspend fun letExpire(challenge: PairingChallenge) = world.server.expire(AuthWorld.HOME, challenge.id)

    @Test
    fun `AD-011 the real adapter passes the pairing contract`() = runTest { runAll() }
}

class AuthWrongServerGatewayContractTest : AuthWrongServerContract() {
    override val server: BaseUri = TestData.uri()
    override val otherServer: BaseUri = TestData.uri("https://other.example.com")

    override fun create(): AuthGateway = AuthWorld().gateway

    @Test
    fun `AD-011 the real adapter passes the wrong server contract`() = runTest { runAll() }
}

class AuthAccountGatewayContractTest : AuthAccountContract() {
    override val instance = InstanceId("inst-1")
    override val signedOutInstance = InstanceId("inst-signed-out")

    override fun create(): AuthGateway = AuthWorld().gateway

    @Test
    fun `AD-011 the real adapter passes the account contract`() = runTest { runAll() }
}
