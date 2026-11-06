package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.infrastructure.api.ServerFixtures
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.CompanyGate
import com.stainedhead.paperclipmobile.presentation.LockState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * F-1 on the live graph with the pinned-server stub: a factory paired while it has no company used to leave the
 * company tabs on "Loading..." for good, and the app made no request after the pairing calls (ADR-150).
 */
class LiveNoCompanyTest {
    private val acmeOnly =
        """[{"id":"${ServerFixtures.COMPANY_ACME}","name":"Acme Labs","status":"active","issuePrefix":"ACM",""" +
            """"budgetMonthlyCents":0}]"""

    private fun LiveTestPlatform.companyRequests() = stub.requests.count { it.path.endsWith("/companies") }

    @Test
    fun `a factory with no company reads its companies after the unlock and reads again on a recheck`() =
        runTest {
            val platform = LiveTestPlatform()
            platform.stub.host(LIVE_HOST).companiesJson = "[]"
            val graph = PaperclipCore.createLiveGraph(false, ApprovingBrowser(platform.stub), platform)
            graph.createFirstRunStateHolder().completeFirstRun(company = null)
            val pairingReads = platform.companyRequests()
            val app = graph.createAppStateHolder()
            app.state.awaitLive { it.isLoaded }
            if (app.state.value.lock == LockState.Locked) app.onIntent(AppIntent.UnlockRequested)

            val none = app.state.awaitLive { it.companyGate == CompanyGate.NoCompany }

            none.hasFactories shouldBe true
            none.selectedCompanyId shouldBe null
            (platform.companyRequests() > pairingReads) shouldBe true

            platform.stub.host(LIVE_HOST).companiesJson = acmeOnly
            app.onIntent(AppIntent.RecheckCompanies)

            val ready = app.state.awaitLive { it.companyGate == CompanyGate.Ready }
            ready.selectedCompanyId shouldBe ServerFixtures.COMPANY_ACME
            app.close()
            graph.close()
        }
}
