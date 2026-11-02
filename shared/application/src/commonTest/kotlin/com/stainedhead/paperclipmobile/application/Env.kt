package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.application.auth.KeyEnrollment
import com.stainedhead.paperclipmobile.application.auth.KeyRevoker
import com.stainedhead.paperclipmobile.application.auth.SignedOutMarker
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeAgentGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAppSettingsStore
import com.stainedhead.paperclipmobile.testing.fakes.FakeApprovalGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionCache
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAuthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeBudgetGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeDashboardGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDecisionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDeviceTimeZone
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository
import com.stainedhead.paperclipmobile.testing.fakes.FakeInteractionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIssueGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeRunGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore

/** Every fake and the shared collaborators, so each test builds only the use case it checks. */
class Env(
    instances: List<Instance> = emptyList(),
    clockElapsed: () -> Long = { 0L },
) {
    val clock = FakeClock(elapsedMillis = clockElapsed)
    val connectivity = FakeConnectivity()
    val repository = FakeInstanceRepository(instances)
    val secrets = FakeSecretStore()
    val settings = FakeAppSettingsStore(AppSettings(deviceName = "Alex's iPhone"))
    val auth = FakeAuthGateway()
    val health = FakeHealthGateway()
    val biometrics = FakeBiometrics()
    val browser = FakePairingBrowser()
    val ids = FakeIdGenerator("inst-")
    val attention = FakeAttentionGateway(clock)
    val attentionCache = FakeAttentionCache()
    val decisions = FakeDecisionGateway()
    val approvals = FakeApprovalGateway()
    val budgets = FakeBudgetGateway()
    val timeZone = FakeDeviceTimeZone()
    val issues = FakeIssueGateway()
    val interactions = FakeInteractionGateway()
    val agents = FakeAgentGateway()
    val runs = FakeRunGateway()
    val dashboard = FakeDashboardGateway()
    val scope = CompanyScope(InstanceId("inst-1"), TestData.acme)
    val validate = ValidateInstanceAddress(allowInsecureLocal = false)
    val deviceAuth = DeviceAuthGuard(biometrics)
    val revoker = KeyRevoker(auth)
    val locks = InstanceLocks()
    val signedOut = SignedOutMarker(repository, locks)
    val enrollment = KeyEnrollment(auth, secrets, repository, clock, settings, locks)

    init {
        auth.hostOf = { id -> repository.current.firstOrNull { it.id == id }?.baseUri }
        auth.tokenOf = { id -> secrets.peek(SecretKey(id, SecretName.BoardToken)) }
    }

    fun context(
        role: Role = Role.Owner,
        userId: String? = "user-1",
    ) = CompanyContext(scope, role, userId)

    suspend fun storeToken(instance: Instance = TestData.instance()) {
        secrets.put(SecretKey(instance.id, SecretName.BoardToken), Secret.of(TestData.TOKEN), SecretPolicy(false))
    }
}
