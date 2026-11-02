package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SpkiPin
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Review1b F-A-1: pairing, pending-key and health calls carry the pins of the factory at that address. */
class PairingPinsTest {
    private val pin = SpkiPin.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")!!
    private val home = TestData.uri()
    private val requests = mutableListOf<String>()
    private val world = AuthWorld()
    private val engine =
        MockEngine {
            requests += it.url.encodedPath
            respond("{}", HttpStatusCode.OK)
        }
    private val client = PaperclipHttpClient(engine, allowInsecureLocal = false, pinsEnforced = false)

    private suspend fun pinnedFactory() {
        world.instances.update(world.instances.get(InstanceId("inst-1"))!!.copy(pins = listOf(pin)))
    }

    private fun auth() = AuthGatewayAdapter(client, world.instances, world.secrets)

    private fun failedClosed(result: DomainResult<*>) {
        assertEquals(DomainResult.Failure(DomainError.CertificateInvalid), result)
    }

    @Test
    fun `F-A-1 pairing calls for a pinned factory fail closed while pins are not enforced`() =
        runTest {
            pinnedFactory()
            val key = Secret.of("pcp_board_TESTONLY0000000000000009")
            failedClosed(auth().createChallenge(home, "Phone", null))
            failedClosed(auth().pollChallenge(home, "challenge-1", Secret.of("tok")))
            failedClosed(auth().meWithKey(home, key))
            failedClosed(auth().listBoardKeysWithKey(home, key))
            failedClosed(HealthGatewayAdapter(client, world.instances).probe(home))
            assertEquals(emptyList(), requests)
        }

    @Test
    fun `F-A-1 pairing calls for an unpinned factory are unchanged`() =
        runTest {
            val result = HealthGatewayAdapter(client, world.instances).probe(home)
            assertIs<DomainResult<*>>(result)
            assertEquals(listOf("/api/health"), requests)
        }
}
