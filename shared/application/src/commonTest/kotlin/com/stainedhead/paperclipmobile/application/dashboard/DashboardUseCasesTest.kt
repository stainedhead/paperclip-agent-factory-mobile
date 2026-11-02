package com.stainedhead.paperclipmobile.application.dashboard

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.SpendWindowKind
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DashboardUseCasesTest {
    @Test
    fun `FR-078 factory home has the tiles and the runs in progress`() =
        runTest {
            val env = Env()

            val home = (LoadDashboard(env.dashboard, env.runs)(env.scope) as DomainResult.Success).value

            home.summary.pendingApprovals shouldBe 2
            home.liveRuns.map { it.id.value } shouldBe listOf("run-live")
        }

    @Test
    fun `the runs in progress are best effort but the tiles are required`() =
        runTest {
            val env = Env()
            env.runs.failures["live"] = DomainError.ServerError
            val home = (LoadDashboard(env.dashboard, env.runs)(env.scope) as DomainResult.Success).value
            home.liveRuns shouldBe emptyList()

            env.dashboard.failures["dashboard"] = DomainError.Forbidden
            (LoadDashboard(env.dashboard, env.runs)(env.scope) as DomainResult.Failure).error shouldBe
                DomainError.Forbidden
            env.dashboard.count("dashboard") shouldBe 2 // one call per load: Forbidden is not retried
        }

    @Test
    fun `FR-078 rolling spend is summed per window`() =
        runTest {
            val env = Env()

            val windows = (LoadWindowSpend(env.dashboard)(env.scope) as DomainResult.Success).value

            windows.map { it.window } shouldBe SpendWindowKind.entries
            windows.map { it.cost.minorUnits } shouldBe listOf(1_200L, 9_000L, 61_000L)
        }
}
