package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.port.HealthGateway

/**
 * Behaviour every [HealthGateway] must have (research, `GET /api/health`: `server/src/routes/health.ts`). The subclass
 * provides one address per situation; a Phase 1b adapter subclass points them at a recorded or throwaway server.
 */
abstract class HealthContract {
    abstract fun create(): HealthGateway

    /** A ready `authenticated` server. */
    abstract val authenticated: BaseUri

    /** A `local_trusted` server, which the app refuses (PRD FR-005). */
    abstract val localTrusted: BaseUri

    /** An `authenticated` server whose bootstrap is still pending. */
    abstract val bootstrapping: BaseUri

    /** An address nothing answers on. */
    abstract val unreachable: BaseUri

    suspend fun runAll() {
        readyServerIsAccepted()
        localTrustedServerIsRefused()
        bootstrappingServerIsRefused()
        unreachableServerIsAFailureNotAReport()
    }

    private suspend fun readyServerIsAccepted() {
        val report = create().probe(authenticated).mustSucceed("probing a ready authenticated server")
        check(report.deploymentMode == DeploymentMode.Authenticated) { "a ready server reports authenticated" }
        check(report.refusal() == null) { "a ready authenticated server must not be refused: ${report.refusal()}" }
    }

    private suspend fun localTrustedServerIsRefused() {
        val report = create().probe(localTrusted).mustSucceed("probing a local_trusted server")
        check(report.refusal() == ProbeRefusal.LocalTrusted) { "local_trusted must be refused, not worked around" }
    }

    private suspend fun bootstrappingServerIsRefused() {
        val report = create().probe(bootstrapping).mustSucceed("probing a bootstrapping server")
        check(report.refusal() == ProbeRefusal.BootstrapPending) { "a pending bootstrap must be refused" }
    }

    private suspend fun unreachableServerIsAFailureNotAReport() {
        val result = create().probe(unreachable)
        val error = (result as? DomainResult.Failure)?.error
        check(error == DomainError.Unreachable || error == DomainError.Timeout) {
            "an address nobody answers on must fail as Unreachable or Timeout but was $result"
        }
    }
}
