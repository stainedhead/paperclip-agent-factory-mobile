package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class HealthReportTest {
    private fun report(
        mode: DeploymentMode = DeploymentMode.Authenticated,
        status: ServerStatus = ServerStatus.Ok,
        bootstrapPending: Boolean = false,
    ) = HealthReport(status, mode, Exposure.Private, serverVersion = null, bootstrapPending = bootstrapPending)

    @Test
    fun `FR-005 an authenticated ready server is accepted`() {
        report().refusal() shouldBe null
    }

    @Test
    fun `FR-005 local trusted is refused`() {
        report(DeploymentMode.LocalTrusted).refusal() shouldBe ProbeRefusal.LocalTrusted
    }

    @Test
    fun `the probe fails closed on an unknown mode a pending bootstrap and a server that is not ready`() {
        report(DeploymentMode.Unknown).refusal() shouldBe ProbeRefusal.UnknownDeploymentMode
        report(bootstrapPending = true).refusal() shouldBe ProbeRefusal.BootstrapPending
        report(status = ServerStatus.Starting).refusal() shouldBe ProbeRefusal.NotReady
        report(status = ServerStatus.Unhealthy).refusal() shouldBe ProbeRefusal.NotReady
    }

    @Test
    fun `an instance falls back to its default company and a result exposes its value`() {
        val instance =
            Instance(
                id = InstanceId("i"),
                displayName = "Home",
                baseUri = (BaseUri.parse("https://p.example.com") as BaseUriResult.Valid).uri,
                deploymentMode = DeploymentMode.Authenticated,
                exposure = Exposure.Private,
                authMethod = AuthMethod.PairedBoardKey,
                boardKeyId = null,
                keyExpiresAt = null,
                defaultCompanyId = CompanyId("c1"),
                lastCompanyId = null,
                pins = emptyList(),
                createdAt = Instant.fromEpochSeconds(0),
                lastConnectedAt = null,
                sortOrder = 0,
                status = InstanceStatus.SignedOut,
            )

        instance.activeCompanyId shouldBe CompanyId("c1")
        instance.copy(lastCompanyId = CompanyId("c2")).activeCompanyId shouldBe CompanyId("c2")
        DomainResult.Success(3).valueOrNull() shouldBe 3
        DomainResult.Failure(DomainError.Timeout).valueOrNull() shouldBe null
    }
}
