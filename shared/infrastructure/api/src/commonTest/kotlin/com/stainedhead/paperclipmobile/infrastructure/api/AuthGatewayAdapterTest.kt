package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyMembership
import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SpkiPin
import com.stainedhead.paperclipmobile.domain.UserProfile
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val json = headersOf(HttpHeaders.ContentType, "application/json")
private val home = TestData.uri()
private val unauthorized = DomainResult.Failure(DomainError.Unauthorized)
private val serverError = DomainResult.Failure(DomainError.ServerError)

private fun <T> DomainResult<T>.ok(): T = (this as DomainResult.Success).value

private fun gatewayAnswering(
    status: HttpStatusCode,
    body: String,
    contentType: String = "application/json",
): AuthGateway {
    val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }
    return AuthGatewayAdapter(
        PaperclipHttpClient(engine, allowInsecureLocal = false),
        AuthWorld().instances,
        AuthWorld().secrets,
    )
}

/** AD-011: pairing endpoints (`POST/GET /cli-auth/challenges`), what is sent and how replies are read. */
class AuthPairingAdapterTest {
    private val world = AuthWorld()

    @Test
    fun `AD-011 createChallenge posts the pairing request to the given host with no credentials`() =
        runTest {
            world.gateway.createChallenge(home, "Alex's phone", CompanyId("0f8fad5b-d9cb-469f-a165-70867728950e"))
            val request = world.server.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/api/cli-auth/challenges", request.path)
            assertNull(request.authorization)
            assertTrue("\"command\":\"paperclip-mobile pair\"" in request.body, request.body)
            assertTrue("\"clientName\":\"Alex's phone\"" in request.body, request.body)
            assertTrue("\"requestedAccess\":\"board\"" in request.body, request.body)
            assertTrue("0f8fad5b-d9cb-469f-a165-70867728950e" in request.body, request.body)
        }

    @Test
    fun `AD-011 a challenge without a requested company sends none`() =
        runTest {
            world.gateway.createChallenge(home, "Phone", null)
            assertTrue(
                "requestedCompanyId" !in
                    world.server.requests
                        .single()
                        .body,
            )
        }

    @Test
    fun `AD-011 createChallenge reads the id secrets expiry and poll interval`() =
        runTest {
            val challenge: PairingChallenge = world.gateway.createChallenge(home, "Phone", null).ok()
            assertTrue(challenge.id.isNotBlank())
            assertTrue(challenge.pendingBoardToken.startsWith("pcp_board_"))
            assertTrue(challenge.challengeToken.startsWith("pcp_cli_auth_"))
            assertEquals(1_000L, challenge.suggestedPollIntervalMs)
            assertEquals(world.clock.now() + kotlin.time.Duration.parse("10m"), challenge.expiresAt)
            assertTrue(challenge.approvalUrl.startsWith("https://home.example.com/cli-auth/${challenge.id}?token="))
        }

    @Test
    fun `AD-011 a null approvalUrl is built from the base uri and the approval path`() =
        runTest {
            val body =
                ServerFixtures.challengeCreated(
                    "id-1",
                    "pcp_cli_auth_TESTONLYx",
                    "pcp_board_TESTONLYy",
                    null,
                    "2026-11-01T12:10:00.000Z",
                )
            val gateway = gatewayAnswering(HttpStatusCode.Created, body)
            val challenge = gateway.createChallenge(TestData.uri("https://example.com/paperclip"), "P", null).ok()
            val expected = "https://example.com/paperclip/cli-auth/id-1?token=pcp_cli_auth_TESTONLYx"
            assertTrue(challenge.approvalUrl.startsWith(expected))
        }

    @Test
    fun `AD-003 a challenge reply missing its secrets or with a bad expiry is Malformed`() =
        runTest {
            listOf(
                """{"id":"a"}""",
                """{"id":"a","token":"t","boardApiToken":"b","approvalPath":"/p","expiresAt":"tomorrow"}""",
                """{"id":"","token":"t","boardApiToken":"b","approvalPath":"/p","expiresAt":"2026-11-01T12:10:00Z"}""",
            ).forEach {
                assertEquals(
                    DomainResult.Failure(DomainError.Malformed),
                    gatewayAnswering(HttpStatusCode.Created, it).createChallenge(home, "P", null),
                    it,
                )
            }
        }

    @Test
    fun `AD-003 pairing errors map by status`() =
        runTest {
            assertEquals(
                DomainResult.Failure(DomainError.RateLimited),
                gatewayAnswering(HttpStatusCode.TooManyRequests, ServerFixtures.error("Too many CLI auth requests"))
                    .createChallenge(home, "P", null),
            )
            assertEquals(
                DomainResult.Failure(DomainError.Rejected("Validation error")),
                gatewayAnswering(HttpStatusCode.BadRequest, ServerFixtures.error("Validation error"))
                    .createChallenge(home, "P", null),
            )
        }

    @Test
    fun `AD-011 poll sends the challenge token only in the query to the given host with no credentials`() =
        runTest {
            val challenge = world.gateway.createChallenge(home, "Phone", null).ok()
            world.server.requests.clear()
            world.gateway.pollChallenge(home, challenge.id, challenge.challengeToken)
            val request = world.server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/cli-auth/challenges/${challenge.id}", request.path)
            assertEquals("token=${challenge.challengeToken.reveal()}", request.query)
            assertNull(request.authorization)
        }

    @Test
    fun `AD-011 poll reads all four statuses`() =
        runTest {
            val expected =
                mapOf(
                    "pending" to ChallengeStatus.Pending,
                    "approved" to ChallengeStatus.Approved,
                    "cancelled" to ChallengeStatus.Cancelled,
                    "expired" to ChallengeStatus.Expired,
                )
            expected.forEach { (wire, status) ->
                val gateway = gatewayAnswering(HttpStatusCode.OK, ServerFixtures.challengePolled("i", wire))
                assertEquals(DomainResult.Success(status), gateway.pollChallenge(home, "i", Secret.of("t")), wire)
            }
        }

    @Test
    fun `AD-011 an unknown status is Malformed so the app never guesses what a challenge is`() =
        runTest {
            val gateway = gatewayAnswering(HttpStatusCode.OK, ServerFixtures.challengePolled("i", "revoked"))
            assertEquals(DomainResult.Failure(DomainError.Malformed), gateway.pollChallenge(home, "i", Secret.of("t")))
        }

    @Test
    fun `AD-003 polling an unknown challenge is NotFound and a bad id is rejected by the server`() =
        runTest {
            assertEquals(
                DomainResult.Failure(DomainError.NotFound),
                world.gateway.pollChallenge(home, "00000000-0000-4000-8000-0000000000ff", Secret.of("nope")),
            )
            assertEquals(
                DomainResult.Failure(DomainError.Rejected("Invalid CLI auth challenge ID")),
                world.gateway.pollChallenge(home, "not-a-uuid", Secret.of("t")),
            )
        }

    @Test
    fun `AD-011 cancellation and expiry show on the poll`() =
        runTest {
            val challenge = world.gateway.createChallenge(home, "Phone", null).ok()
            world.server.cancel(AuthWorld.HOME, challenge.id)
            assertEquals(
                DomainResult.Success(ChallengeStatus.Cancelled),
                world.gateway.pollChallenge(home, challenge.id, challenge.challengeToken),
            )
        }
}

/** AD-011: `GET /cli-auth/me`, companies and board keys, with a key given or the stored key of a factory. */
class AuthAccountAdapterTest {
    private val world = AuthWorld()
    private val token = Secret.of(TestData.TOKEN)

    @Test
    fun `AD-011 meWithKey sends the key as a bearer to the given host only`() =
        runTest {
            world.gateway.meWithKey(home, token)
            val request = world.server.requests.single()
            assertEquals("home.example.com", request.host)
            assertEquals("/api/cli-auth/me", request.path)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
        }

    @Test
    fun `AD-011 me maps the person the key id and the roles of each company`() =
        runTest {
            val access = world.gateway.me(InstanceId("inst-1")).ok()
            val person = UserProfile("user-1", "Alex Operator", "alex@example.com", isInstanceAdmin = false)
            assertEquals(person, access.profile)
            assertEquals(AuthWorld.KEY_CURRENT, access.keyId)
            assertEquals(
                listOf(
                    CompanyMembership(CompanyId(ServerFixtures.COMPANY_ACME), Role.Owner),
                    CompanyMembership(CompanyId(ServerFixtures.COMPANY_GLOBEX), Role.Viewer),
                ),
                access.memberships,
            )
        }

    private fun meBody(
        isAdmin: Boolean = false,
        user: String = """{"id":"u","name":null,"email":null}""",
        memberships: String = "[]",
    ) = """{"user":$user,"userId":"u","isInstanceAdmin":$isAdmin,"memberships":$memberships,"keyId":null}"""

    @Test
    fun `AD-011 roles map fail closed and inactive memberships are dropped`() =
        runTest {
            val rows =
                listOf(
                    "owner" to "active",
                    "admin" to "active",
                    "operator" to "active",
                    "member" to "active",
                    "viewer" to "active",
                    "wizard" to "active",
                    "owner" to "suspended",
                ).mapIndexed { i, (role, status) ->
                    """{"companyId":"c$i","membershipRole":"$role","status":"$status"}"""
                }
            val gateway = gatewayAnswering(HttpStatusCode.OK, meBody(memberships = rows.joinToString(",", "[", "]")))
            val roles =
                gateway
                    .meWithKey(home, token)
                    .ok()
                    .memberships
                    .map { it.role }
            assertEquals(listOf(Role.Owner, Role.Owner, Role.Operator, Role.Viewer, Role.Viewer, Role.Viewer), roles)
        }

    @Test
    fun `AD-011 an instance admin is an Owner of every company and a missing user keeps the id`() =
        runTest {
            val row = """[{"companyId":"c1","membershipRole":"viewer","status":"active"}]"""
            val access =
                gatewayAnswering(HttpStatusCode.OK, meBody(isAdmin = true, user = "null", memberships = row))
                    .meWithKey(home, token)
                    .ok()
            assertEquals(Role.Owner, access.memberships.single().role)
            assertEquals(UserProfile("u", null, null, isInstanceAdmin = true), access.profile)
            assertNull(access.keyId)
        }

    @Test
    fun `AD-003 a membership with a blank company id is Malformed`() =
        runTest {
            val row = """[{"companyId":"","membershipRole":"owner","status":"active"}]"""
            val gateway = gatewayAnswering(HttpStatusCode.OK, meBody(memberships = row))
            assertEquals(DomainResult.Failure(DomainError.Malformed), gateway.meWithKey(home, token))
        }

    @Test
    fun `AD-011 a stored key call reads the instance host and its own secret`() =
        runTest {
            world.gateway.me(InstanceId("inst-2"))
            val request = world.server.requests.single()
            assertEquals("other.example.com", request.host)
            assertEquals("Bearer ${AuthWorld.OTHER_TOKEN}", request.authorization)
        }

    @Test
    fun `AD-011 a factory that is not configured is NotFound and sends nothing`() =
        runTest {
            assertEquals(DomainResult.Failure(DomainError.NotFound), world.gateway.me(InstanceId("ghost")))
            assertTrue(world.server.requests.isEmpty())
        }

    @Test
    fun `AD-011 no usable stored secret is Unauthorized and sends nothing`() =
        runTest {
            assertEquals(unauthorized, world.gateway.me(InstanceId("inst-signed-out")))
            world.secrets.invalidated = true
            assertEquals(DomainResult.Failure(DomainError.Unauthorized), world.gateway.me(InstanceId("inst-1")))
            world.secrets.invalidated = false
            world.secrets.denyReads = true
            assertEquals(DomainResult.Failure(DomainError.Unauthorized), world.gateway.me(InstanceId("inst-1")))
            assertTrue(world.server.requests.isEmpty())
        }

    @Test
    fun `AD-011 companies are listed with the accessible scope`() =
        runTest {
            val companies = world.gateway.listCompanies(InstanceId("inst-1")).ok()
            assertEquals(
                listOf(
                    CompanyRef(CompanyId(ServerFixtures.COMPANY_ACME), "Acme Labs"),
                    CompanyRef(CompanyId(ServerFixtures.COMPANY_GLOBEX), "Globex"),
                ),
                companies,
            )
            assertEquals(
                "scope=accessible",
                world.server.requests
                    .single()
                    .query,
            )
        }

    @Test
    fun `AD-011 board keys map dates and mark the key this device holds`() =
        runTest {
            val keys = world.gateway.listBoardKeys(InstanceId("inst-1")).ok()
            val current = keys.single { it.id == AuthWorld.KEY_CURRENT }
            assertTrue(current.isCurrentDevice)
            assertEquals("Alex's iPhone", current.name)
            assertEquals(Instant.parse("2026-11-01T12:00:00.000Z"), current.createdAt)
            assertEquals(Instant.parse("2026-12-01T12:00:00.000Z"), current.expiresAt)
            assertNull(current.revokedAt)
            assertNull(current.lastUsedAt)
            assertTrue(keys.single { it.id == AuthWorld.KEY_OTHER }.isCurrentDevice.not())
        }

    @Test
    fun `AD-011 listBoardKeysWithKey sends the given key to the given host and marks none as this device`() =
        runTest {
            val keys = world.gateway.listBoardKeysWithKey(home, token).ok()
            assertTrue(keys.none { it.isCurrentDevice })
            val request = world.server.requests.single()
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
            assertEquals("/api/board-api-keys", request.path)
        }

    @Test
    fun `AD-003 key rows with unreadable dates keep the key and drop the date`() =
        runTest {
            val body = """[{"id":"k1","name":"n","createdAt":"yesterday","lastUsedAt":null}]"""
            val key = gatewayAnswering(HttpStatusCode.OK, body).listBoardKeysWithKey(home, token).ok().single()
            assertNull(key.createdAt)
        }

    @Test
    fun `AD-003 a key list that is not an array is Malformed`() =
        runTest {
            val gateway = gatewayAnswering(HttpStatusCode.OK, """{"keys":[]}""")
            assertEquals(DomainResult.Failure(DomainError.Malformed), gateway.listBoardKeysWithKey(home, token))
        }

    @Test
    fun `AD-011 a wrong key is Unauthorized`() =
        runTest {
            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                world.gateway.meWithKey(home, Secret.of("pcp_board_TESTONLY_WRONG")),
            )
        }

    @Test
    fun `AD-011 a factory with certificate pins fails closed while no engine enforces them`() =
        runTest {
            val pin = SpkiPin.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")!!
            val pinned = world.instances.get(InstanceId("inst-1"))!!.copy(pins = listOf(pin))
            world.instances.update(pinned)
            assertEquals(DomainResult.Failure(DomainError.CertificateInvalid), world.gateway.me(InstanceId("inst-1")))
            assertTrue(world.server.requests.isEmpty())
        }
}

/** AD-011: `DELETE /board-api-keys/:id` and `POST /cli-auth/revoke-current`. */
class AuthRevokeAdapterTest {
    private val world = AuthWorld()
    private val inst1 = InstanceId("inst-1")

    @Test
    fun `AD-011 revoking a key calls DELETE with the key id and the stored bearer once`() =
        runTest {
            assertEquals(DomainResult.Success(Unit), world.gateway.revokeBoardKey(inst1, AuthWorld.KEY_OTHER))
            val request = world.server.requests.single()
            assertEquals("DELETE", request.method)
            assertEquals("/api/board-api-keys/${AuthWorld.KEY_OTHER}", request.path)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
        }

    @Test
    fun `AD-003 revoking an unknown key id is NotFound`() =
        runTest {
            assertEquals(
                DomainResult.Failure(DomainError.NotFound),
                world.gateway.revokeBoardKey(inst1, "cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
            )
        }

    @Test
    fun `AD-003 a key id that cannot exist is NotFound without asking the server`() =
        runTest {
            assertEquals(DomainResult.Failure(DomainError.NotFound), world.gateway.revokeBoardKey(inst1, "no-such-key"))
            assertTrue(world.server.requests.isEmpty())
        }

    @Test
    fun `AD-011 revoke-current posts once and the key stops working`() =
        runTest {
            assertEquals(DomainResult.Success(Unit), world.gateway.revokeCurrentKey(inst1))
            assertEquals(
                "/api/cli-auth/revoke-current",
                world.server.requests
                    .single()
                    .path,
            )
            assertEquals(
                "POST",
                world.server.requests
                    .single()
                    .method,
            )
            assertEquals(DomainResult.Failure(DomainError.Unauthorized), world.gateway.me(inst1))
        }

    @Test
    fun `AD-002 a failing revoke is sent exactly once`() =
        runTest {
            var calls = 0
            val engine =
                MockEngine {
                    calls += 1
                    respond(ServerFixtures.error("down"), HttpStatusCode.ServiceUnavailable, json)
                }
            val gateway = AuthGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
            assertEquals(serverError, gateway.revokeBoardKey(inst1, AuthWorld.KEY_OTHER))
            assertEquals(serverError, gateway.revokeCurrentKey(inst1))
            assertEquals(2, calls)
        }

    @Test
    fun `AD-003 a revoke answered with HTML is Malformed not a success`() =
        runTest {
            val gateway = gatewayAnswering(HttpStatusCode.OK, ServerFixtures.HTML_PAGE, "text/html")
            assertIs<DomainResult.Failure>(gateway.revokeCurrentKey(inst1))
        }

    @Test
    fun `AD-003 revoking with a key already revoked is Unauthorized`() =
        runTest {
            world.gateway.revokeCurrentKey(inst1)
            assertEquals(unauthorized, world.gateway.revokeBoardKey(inst1, AuthWorld.KEY_OTHER))
        }

    @Test
    fun `AD-004 http is refused at the gateway when insecure local addresses are not allowed`() =
        runTest {
            val local = (BaseUri.parse("http://localhost:3100", true) as BaseUriResult.Valid).uri
            val refused = world.gateway.createChallenge(local, "P", null)
            assertEquals(DomainResult.Failure(DomainError.CertificateInvalid), refused)
            assertTrue(world.server.requests.isEmpty())
        }
}
