package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.AgentGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.ApprovalGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.AttentionGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.AuthGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.DashboardGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.HealthGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.InteractionGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.IssueGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.RunGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.createPaperclipHttpClient
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions

/** What the bootstrap and the first pairing produced. */
internal class Established(
    val base: BaseUri,
    val raw: RawServer,
    val session: BoardSession,
    val preBootstrapHealth: HealthReport,
    val paired: PairedKey,
)

/**
 * The world of one contract run: the production adapters on a real OkHttp engine, a test-only secret store, one paired
 * device (its key came out of the real pairing flow) and one seeded company. Built once, on first use, and shared by
 * the test classes; a failure to build it is remembered, so every test reports the same cause.
 */
internal class LiveContext private constructor(
    private val established: Established,
    val instanceId: InstanceId,
    val scope: CompanyScope,
) {
    val base: BaseUri get() = established.base
    val raw: RawServer get() = established.raw
    val session: BoardSession get() = established.session
    val preBootstrapHealth: HealthReport get() = established.preBootstrapHealth
    val paired: PairedKey get() = established.paired
    val seeder: Seeder = Seeder(established.raw, established.paired.key)
    val key: String get() = established.paired.key

    private val secrets: SecretStore = world.secrets
    val client = world.client
    val health = HealthGatewayAdapter(client)
    val auth = AuthGatewayAdapter(client, world.instances, secrets)
    val attention = AttentionGatewayAdapter(client, world.instances, secrets)
    val approvals = ApprovalGatewayAdapter(client, world.instances, secrets)
    val dashboard = DashboardGatewayAdapter(client, world.instances, secrets)
    val issues = IssueGatewayAdapter(client, world.instances, secrets)
    val agents = AgentGatewayAdapter(client, world.instances, secrets)
    val runs = RunGatewayAdapter(client, world.instances, secrets)
    val interactions = InteractionGatewayAdapter(client, world.instances, secrets)

    /** Configures one more paired device (a second factory entry with its own key) for the stored-key calls. */
    suspend fun registerDevice(
        id: String,
        deviceKey: String,
        keyId: String?,
    ): InstanceId {
        val instanceId = InstanceId(id)
        world.instances.add(
            TestData.instance(id = id, uri = base.value).copy(boardKeyId = keyId, defaultCompanyId = scope.companyId),
        )
        world.secrets.put(
            SecretKey(instanceId, SecretName.BoardToken),
            Secret.of(deviceKey),
            SecretPolicy(requireUserPresence = false),
        )
        return instanceId
    }

    /** A repository and store shared by every adapter above. */
    private class World {
        val client = createPaperclipHttpClient(allowInsecureLocal = true)
        val instances: InstanceRepository = FakeInstanceRepository()
        val secrets: SecretStore = MemorySecretStore()
    }

    companion object {
        private val world = World()
        private val built: Result<LiveContext> by lazy { runCatching { runBlocking { build() } } }

        /** The context, or the test is skipped (no server configured). In CI a missing server is a failure. */
        fun get(): LiveContext {
            if (System.getenv("PAPERCLIP_CONTRACT_BASE_URL").isNullOrBlank()) {
                check(System.getenv("PAPERCLIP_CONTRACT_REQUIRED") != "true") {
                    "PAPERCLIP_CONTRACT_BASE_URL is not set but PAPERCLIP_CONTRACT_REQUIRED=true"
                }
                Assumptions.assumeTrue(
                    false,
                    "PAPERCLIP_CONTRACT_BASE_URL is not set: start a server with scripts/paperclip-test-server.sh up",
                )
            }
            return built.getOrThrow()
        }

        private suspend fun build(): LiveContext {
            val baseText = ContractTarget.adapterBase(requireNotNull(System.getenv("PAPERCLIP_CONTRACT_BASE_URL")))
            val base = (BaseUri.parse(baseText, allowInsecureLocal = true) as BaseUriResult.Valid).uri
            val raw = RawServer(baseText)
            val health = HealthGatewayAdapter(world.client)
            val auth = AuthGatewayAdapter(world.client, world.instances, world.secrets)
            val before = Pairing.value(health.probe(base), "probe before bootstrap")
            val session = ServerBootstrap.establishSession(raw, System.getenv("PAPERCLIP_CONTRACT_BOOTSTRAP_INVITE"))
            val paired = Pairing.pair(auth, raw, session, base, "pafm contract test")
            val company = Seeder(raw, paired.key).company("Contract Co")
            val instanceId = InstanceId("contract-instance")
            val instance =
                TestData.instance(id = instanceId.value, uri = base.value).copy(
                    boardKeyId = null,
                    defaultCompanyId = CompanyId(company.id),
                )
            world.instances.add(instance)
            world.secrets.put(
                SecretKey(instanceId, SecretName.BoardToken),
                Secret.of(paired.key),
                SecretPolicy(requireUserPresence = false),
            )
            return LiveContext(
                Established(base, raw, session, before, paired),
                instanceId,
                CompanyScope(instanceId, CompanyId(company.id)),
            )
        }
    }
}
