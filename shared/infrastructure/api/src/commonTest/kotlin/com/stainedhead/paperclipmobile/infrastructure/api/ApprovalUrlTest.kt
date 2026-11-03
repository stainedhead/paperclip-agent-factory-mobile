package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Review1b F-A-3: the server's `approvalUrl` is opened only when it is the instance's own origin. */
class ApprovalUrlTest {
    private val world = AuthWorld()

    private suspend fun challengeFor(
        base: String,
        origin: String?,
        id: String = "id-1",
        token: String = "pcp_cli_auth_TESTONLYx",
    ): PairingChallenge {
        val body =
            ServerFixtures.challengeCreated(
                id,
                token,
                "pcp_board_TESTONLYy",
                origin?.replace("\\", "\\\\"),
                "2026-11-01T12:10:00.000Z",
            )
        val engine =
            MockEngine { respond(body, HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json")) }
        val gateway =
            AuthGatewayAdapter(PaperclipHttpClient(engine, allowInsecureLocal = true), world.instances, world.secrets)
        val uri = (BaseUri.parse(base, allowInsecureLocal = true) as BaseUriResult.Valid).uri
        return (gateway.createChallenge(uri, "P", null) as DomainResult.Success).value
    }

    private val own = "https://home.example.com/cli-auth/id-1?token=pcp_cli_auth_TESTONLYx"

    @Test
    fun `F-A-3 an approvalUrl on the instance origin is used as given`() =
        runTest {
            assertEquals(own, challengeFor("https://home.example.com", "https://home.example.com").approvalUrl.reveal())
            val explicitPort = "https://HOME.example.com:443/cli-auth/id-1?token=pcp_cli_auth_TESTONLYx"
            assertEquals(
                explicitPort,
                challengeFor("https://home.example.com", "https://HOME.example.com:443").approvalUrl.reveal(),
            )
        }

    @Test
    fun `F-A-3 an approvalUrl that is not the instance origin is replaced by the one built from the base uri`() =
        runTest {
            listOf(
                "http://home.example.com",
                "https://evil.example.com",
                "https://home.example.com:8443",
                "https://user:pw@home.example.com",
                "https://home.example.com@evil.example.com",
                "https://evil.example.com\\@home.example.com",
                "https://home.example.com.evil.example.com",
                "javascript:alert(1)//",
                "file:///etc/passwd?",
                "intent://home.example.com#Intent;scheme=https;end;",
            ).forEach { origin ->
                assertEquals(own, challengeFor("https://home.example.com", origin).approvalUrl.reveal(), origin)
            }
        }

    @Test
    fun `F-A-3 a local http instance accepts only its own http origin`() =
        runTest {
            val local = "http://localhost:3100/cli-auth/id-1?token=pcp_cli_auth_TESTONLYx"
            assertEquals(local, challengeFor("http://localhost:3100", "http://localhost:3100").approvalUrl.reveal())
            assertEquals(local, challengeFor("http://localhost:3100", "http://localhost:9999").approvalUrl.reveal())
            assertEquals(local, challengeFor("http://localhost:3100", "https://localhost:3100").approvalUrl.reveal())
        }

    @Test
    fun `F-A-3 the built approval url percent-encodes the id and the token`() =
        runTest {
            val challenge = challengeFor("https://home.example.com", null, id = "a b/c", token = "t&x=y#z")
            assertEquals(
                "https://home.example.com/cli-auth/a%20b%2Fc?token=t%26x%3Dy%23z",
                challenge.approvalUrl.reveal(),
            )
        }
}
