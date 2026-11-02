package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.ServerStatus
import com.stainedhead.paperclipmobile.domain.port.HealthGateway
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.contract.HealthContract
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun stub(): StubPaperclipServer = StubPaperclipServer(FakeClock()::now)

/** AD-014: the shared health contract, run against the real adapter and the stub server. */
class HealthGatewayContractTest : HealthContract() {
    private val server = stub()

    override val authenticated: BaseUri = TestData.uri("https://ready.example.com")
    override val localTrusted: BaseUri = TestData.uri("https://trusted.example.com")
    override val bootstrapping: BaseUri = TestData.uri("https://bootstrap.example.com")
    override val unreachable: BaseUri = TestData.uri("https://nobody.example.com")

    override fun create(): HealthGateway {
        server.host("ready.example.com")
        server.host("trusted.example.com").health = HealthMode.LocalTrusted
        server.host("bootstrap.example.com").health = HealthMode.Bootstrapping
        return HealthGatewayAdapter(PaperclipHttpClient(server.engine, allowInsecureLocal = false))
    }

    @Test
    fun `AD-010 the real adapter passes the health contract`() = runTest { runAll() }
}

/** AD-010: `GET /api/health`, what is read from it and what is refused. */
class HealthGatewayAdapterTest {
    private val server = stub()
    private val home = TestData.uri()

    private fun gateway(engine: MockEngine = server.engine) =
        HealthGatewayAdapter(PaperclipHttpClient(engine, allowInsecureLocal = false))

    private suspend fun report(mode: HealthMode) =
        server.host("home.example.com").also { it.health = mode }.let {
            (gateway().probe(home) as DomainResult.Success).value
        }

    @Test
    fun `AD-010 an anonymous probe calls GET api health once with no credentials`() =
        runTest {
            report(HealthMode.Ready)
            val request = server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/health", request.path)
            assertEquals("", request.query)
            assertNull(request.authorization)
        }

    @Test
    fun `AD-010 a redacted authenticated body reads mode exposure and status with no version`() =
        runTest {
            val report = report(HealthMode.Ready)
            assertEquals(ServerStatus.Ok, report.status)
            assertEquals(DeploymentMode.Authenticated, report.deploymentMode)
            assertEquals(Exposure.Private, report.exposure)
            assertNull(report.serverVersion)
            assertEquals(false, report.bootstrapPending)
            assertNull(report.refusal())
        }

    @Test
    fun `AD-010 a full body carries the version and local_trusted is refused`() =
        runTest {
            val report = report(HealthMode.LocalTrusted)
            assertEquals("2026.1023.0", report.serverVersion)
            assertEquals(ProbeRefusal.LocalTrusted, report.refusal())
        }

    @Test
    fun `AD-010 a bootstrap pending server is refused`() =
        runTest {
            val report = report(HealthMode.Bootstrapping)
            assertTrue(report.bootstrapPending)
            assertEquals(ProbeRefusal.BootstrapPending, report.refusal())
        }

    @Test
    fun `AD-010 a starting server is a report that is not ready`() =
        runTest {
            val report = report(HealthMode.Starting)
            assertEquals(ServerStatus.Starting, report.status)
            assertEquals(ProbeRefusal.NotReady, report.refusal())
        }

    @Test
    fun `AD-003 the 503 unhealthy answer is a ServerError`() =
        runTest {
            server.host("home.example.com").health = HealthMode.Unhealthy
            assertEquals(DomainResult.Failure(DomainError.ServerError), gateway().probe(home))
        }

    @Test
    fun `AD-003 a 200 HTML page from a proxy is Malformed`() =
        runTest {
            server.host("home.example.com").health = HealthMode.HtmlPage
            assertEquals(DomainResult.Failure(DomainError.Malformed), gateway().probe(home))
        }

    private fun probeBody(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = gateway(MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) })

    @Test
    fun `AD-003 an empty or non-object 200 body is Malformed`() =
        runTest {
            listOf("", "[]", "null", "not json", "\"ok\"").forEach {
                assertEquals(DomainResult.Failure(DomainError.Malformed), probeBody(it).probe(home), it)
            }
        }

    @Test
    fun `AD-010 a JSON object that is not a Paperclip health body is refused not trusted`() =
        runTest {
            val report = (probeBody("{}").probe(home) as DomainResult.Success).value
            assertEquals(DeploymentMode.Unknown, report.deploymentMode)
            assertEquals(ProbeRefusal.UnknownDeploymentMode, report.refusal())
        }

    @Test
    fun `AD-010 unknown enum values and extra fields do not crash`() =
        runTest {
            val body = """{"status":"hibernating","deploymentMode":"quantum","deploymentExposure":"orbital","x":[1]}"""
            val report = (probeBody(body).probe(home) as DomainResult.Success).value
            assertEquals(ServerStatus.Unhealthy, report.status)
            assertEquals(DeploymentMode.Unknown, report.deploymentMode)
            assertEquals(Exposure.Unknown, report.exposure)
        }

    @Test
    fun `AD-010 fields of the wrong type are Malformed`() =
        runTest {
            assertEquals(DomainResult.Failure(DomainError.Malformed), probeBody("""{"status":5}""").probe(home))
        }

    @Test
    fun `AD-003 an address nobody answers on is Unreachable`() =
        runTest {
            val result = gateway().probe(TestData.uri("https://nobody.example.com"))
            assertEquals(DomainResult.Failure(DomainError.Unreachable), result)
        }
}
