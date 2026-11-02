package com.stainedhead.paperclipmobile.application.dashboard

import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DashboardSummary
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.SpendWindow
import com.stainedhead.paperclipmobile.domain.SpendWindows
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.domain.valueOrNull

/** Factory home: the tiles and the runs in progress (PRD FR-078). The runs are best effort. */
data class FactoryHome(
    val summary: DashboardSummary,
    val liveRuns: List<HeartbeatRun>,
)

class LoadDashboard(
    private val dashboard: DashboardGateway,
    private val runs: RunGateway,
) {
    suspend operator fun invoke(scope: CompanyScope): DomainResult<FactoryHome> =
        when (val summary = retryingRead { dashboard.dashboard(scope) }) {
            is DomainResult.Failure -> {
                summary
            }

            is DomainResult.Success -> {
                DomainResult.Success(FactoryHome(summary.value, runs.live(scope).valueOrNull().orEmpty()))
            }
        }
}

/** Rolling spend for the last 5 hours, 24 hours and 7 days (PRD FR-078). */
class LoadWindowSpend(
    private val dashboard: DashboardGateway,
) {
    suspend operator fun invoke(scope: CompanyScope): DomainResult<List<SpendWindow>> =
        when (val rows = retryingRead { dashboard.windowSpend(scope) }) {
            is DomainResult.Failure -> rows
            is DomainResult.Success -> DomainResult.Success(SpendWindows.fromRows(rows.value))
        }
}
