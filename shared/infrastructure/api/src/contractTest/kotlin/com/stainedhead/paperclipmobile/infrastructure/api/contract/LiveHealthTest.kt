package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.ServerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** AD-050 (a): the health probe the add-factory flow relies on, against the pinned server. */
class LiveHealthTest {
    @Test
    fun `the live server reports authenticated, private and ok and ProbeInstance would accept it`() =
        live {
            val report = health.probe(base).verified("HealthGateway.probe", "(${base.value})")
            assertEquals(DeploymentMode.Authenticated, report.deploymentMode)
            assertEquals(Exposure.Private, report.exposure)
            assertEquals(ServerStatus.Ok, report.status)
            assertEquals(false, report.bootstrapPending)
            assertNull(report.refusal())
            LiveReport.note("health serverVersion", report.serverVersion ?: "(not reported)")
        }

    @Test
    fun `a fresh server is refused as bootstrap pending until its first admin exists`() =
        live {
            // Captured before the sign-up and invite acceptance of this run. A re-run on the same container has
            // no fresh state to observe, so the check only applies when the server really was pending.
            val before = preBootstrapHealth
            if (before.bootstrapPending) {
                assertEquals(ProbeRefusal.BootstrapPending, before.refusal())
                LiveReport.note("health before bootstrap", "bootstrap_pending, refused as BootstrapPending")
            } else {
                LiveReport.note(
                    "health before bootstrap",
                    "server was already bootstrapped (re-run on the same container)",
                )
            }
        }
}
