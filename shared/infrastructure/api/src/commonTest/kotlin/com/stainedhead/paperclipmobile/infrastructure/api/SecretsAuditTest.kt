package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Principle 1 and the security rules: where a key may go, and what must never contain one. */
class SecretsAuditTest {
    private val world = AuthWorld()
    private val home = TestData.uri()
    private val other = TestData.uri("https://other.example.com")

    private suspend fun pairedChallenge(): PairingChallenge {
        val challenge = (world.gateway.createChallenge(home, "Phone", null) as DomainResult.Success).value
        world.server.approve(AuthWorld.HOME, challenge.id)
        world.gateway.pollChallenge(home, challenge.id, challenge.challengeToken)
        return challenge
    }

    @Test
    fun `a pending key is only ever sent to the host that issued the challenge`() =
        runTest {
            val challenge = pairedChallenge()
            val pending = challenge.pendingBoardToken
            world.gateway.meWithKey(home, pending)
            world.gateway.listBoardKeysWithKey(home, pending)
            val carrying = world.server.requests.filter { it.authorization == "Bearer ${pending.reveal()}" }
            assertEquals(2, carrying.size)
            assertTrue(carrying.all { it.host == AuthWorld.HOME }, carrying.toString())
        }

    @Test
    fun `a challenge token only ever reaches the host that issued it and only in the poll query`() =
        runTest {
            val challenge = pairedChallenge()
            val secret = challenge.challengeToken.reveal()
            val carrying = world.server.requests.filter { secret in it.path + it.query + it.body }
            assertTrue(carrying.isNotEmpty())
            assertTrue(carrying.all { it.host == AuthWorld.HOME && it.method == "GET" && secret in it.query })
            assertTrue(carrying.none { secret in it.path || secret in it.body || it.authorization != null })
        }

    @Test
    fun `a stored key goes to its own factory and no other`() =
        runTest {
            world.gateway.me(InstanceId("inst-1"))
            world.gateway.listBoardKeys(InstanceId("inst-1"))
            world.gateway.me(InstanceId("inst-2"))
            world.gateway.listCompanies(InstanceId("inst-2"))
            val byToken = world.server.requests.groupBy({ it.authorization }, { it.host })
            assertEquals(setOf(AuthWorld.HOME), byToken.getValue("Bearer ${TestData.TOKEN}").toSet())
            assertEquals(setOf(AuthWorld.OTHER), byToken.getValue("Bearer ${AuthWorld.OTHER_TOKEN}").toSet())
        }

    @Test
    fun `a key given for one host is not retried on another when it fails`() =
        runTest {
            val challenge = pairedChallenge()
            world.server.requests.clear()
            val result = world.gateway.meWithKey(other, challenge.pendingBoardToken)
            assertEquals(DomainResult.Failure(DomainError.Unauthorized), result)
            assertEquals(listOf(AuthWorld.OTHER), world.server.requests.map { it.host })
        }

    @Test
    fun `no board key is ever part of a URL`() =
        runTest {
            val challenge = pairedChallenge()
            world.gateway.meWithKey(home, challenge.pendingBoardToken)
            world.gateway.me(InstanceId("inst-1"))
            world.gateway.listBoardKeys(InstanceId("inst-2"))
            world.gateway.revokeBoardKey(InstanceId("inst-1"), AuthWorld.KEY_OTHER)
            val keys = listOf(TestData.TOKEN, AuthWorld.OTHER_TOKEN, challenge.pendingBoardToken.reveal())
            world.server.requests.forEach { request ->
                keys.forEach { key -> assertTrue(key !in request.path + request.query, request.toString()) }
            }
        }

    @Test
    fun `no secret appears in any error trace line or request line across success and failure paths`() =
        runTest {
            val challenge = pairedChallenge()
            val everySecret =
                listOf(
                    TestData.TOKEN,
                    AuthWorld.OTHER_TOKEN,
                    challenge.pendingBoardToken.reveal(),
                    challenge.challengeToken.reveal(),
                    challenge.approvalUrl.reveal(),
                )
            val results = mutableListOf<Any?>()
            results += world.gateway.meWithKey(home, Secret.of("pcp_board_TESTONLY_WRONG"))
            results += world.gateway.pollChallenge(other, challenge.id, challenge.challengeToken)
            results += world.gateway.me(InstanceId("inst-signed-out"))
            results += world.gateway.revokeBoardKey(InstanceId("inst-1"), "cccccccc-cccc-4ccc-8ccc-cccccccccccc")
            results += failingGateway(everySecret).pollChallenge(home, challenge.id, challenge.challengeToken)
            results += failingGateway(everySecret).meWithKey(home, challenge.pendingBoardToken)
            val sink = results.joinToString("\n") { it.toString() } + world.trace.joinToString("\n") + challenge
            everySecret.forEach { secret -> assertTrue(secret !in sink, "a secret leaked into: $sink") }
        }

    /** An engine whose exceptions and error bodies are stuffed with every secret, as a hostile stack could be. */
    private fun failingGateway(secrets: List<String>): AuthGatewayAdapter {
        var round = 0
        val engine =
            MockEngine {
                round += 1
                if (round % 2 ==
                    1
                ) {
                    throw IOException("failed GET https://home.example.com/x?token=${secrets.joinToString()}")
                }
                respond(
                    """{"error":"${secrets.first().take(0)}Server error"}""",
                    HttpStatusCode.InternalServerError,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = PaperclipHttpClient(engine, allowInsecureLocal = false) { world.trace += it }
        return AuthGatewayAdapter(client, world.instances, world.secrets)
    }
}
