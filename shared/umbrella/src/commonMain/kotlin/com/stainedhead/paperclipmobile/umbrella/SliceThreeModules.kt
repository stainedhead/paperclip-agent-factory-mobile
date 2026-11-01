package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.agent.InvokeHeartbeat
import com.stainedhead.paperclipmobile.application.agent.ListAgents
import com.stainedhead.paperclipmobile.application.agent.LoadAgent
import com.stainedhead.paperclipmobile.application.agent.PauseAgent
import com.stainedhead.paperclipmobile.application.agent.ResumeAgent
import com.stainedhead.paperclipmobile.application.agent.SetAgentBudget
import com.stainedhead.paperclipmobile.application.dashboard.LoadDashboard
import com.stainedhead.paperclipmobile.application.dashboard.LoadWindowSpend
import com.stainedhead.paperclipmobile.application.interaction.LoadInteractions
import com.stainedhead.paperclipmobile.application.interaction.RespondToInteraction
import com.stainedhead.paperclipmobile.application.issue.AssignIssue
import com.stainedhead.paperclipmobile.application.issue.ChangeIssueStatus
import com.stainedhead.paperclipmobile.application.issue.CommentOnIssue
import com.stainedhead.paperclipmobile.application.issue.CreateIssue
import com.stainedhead.paperclipmobile.application.issue.ListIssueAttachments
import com.stainedhead.paperclipmobile.application.issue.ListIssues
import com.stainedhead.paperclipmobile.application.issue.LoadIssue
import com.stainedhead.paperclipmobile.application.issue.LoadIssuePlan
import com.stainedhead.paperclipmobile.application.issue.SearchIssues
import com.stainedhead.paperclipmobile.application.refresh.RefreshVisibleScreen
import com.stainedhead.paperclipmobile.application.run.CancelRun
import com.stainedhead.paperclipmobile.application.run.ListIssueRuns
import com.stainedhead.paperclipmobile.application.run.LoadRun
import com.stainedhead.paperclipmobile.application.run.TailRunLog
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.AppLifecycle
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.presentation.AgentDetailStateHolder
import com.stainedhead.paperclipmobile.presentation.AgentDetailUseCases
import com.stainedhead.paperclipmobile.presentation.AgentsStateHolder
import com.stainedhead.paperclipmobile.presentation.AgentsUseCases
import com.stainedhead.paperclipmobile.presentation.CompanyTarget
import com.stainedhead.paperclipmobile.presentation.FactoryHomeStateHolder
import com.stainedhead.paperclipmobile.presentation.FactoryHomeUseCases
import com.stainedhead.paperclipmobile.presentation.InteractionUseCases
import com.stainedhead.paperclipmobile.presentation.IssueEditUseCases
import com.stainedhead.paperclipmobile.presentation.IssueStateHolder
import com.stainedhead.paperclipmobile.presentation.IssueUseCases
import com.stainedhead.paperclipmobile.presentation.RunViewerStateHolder
import com.stainedhead.paperclipmobile.presentation.RunViewerUseCases
import com.stainedhead.paperclipmobile.presentation.WorkListStateHolder
import com.stainedhead.paperclipmobile.presentation.WorkListUseCases
import com.stainedhead.paperclipmobile.testing.fakes.FakeAgentGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDashboardGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeInteractionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIssueGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeLiveEvents
import com.stainedhead.paperclipmobile.testing.fakes.FakeRunGateway
import org.koin.core.module.Module

/** The slice 1a-3 gateways, backed by the shared fakes until the Phase 1b adapters exist. */
internal fun Module.sliceThreePorts() {
    single<IssueGateway> { FakeIssueGateway() }
    single<InteractionGateway> { FakeInteractionGateway() }
    single<AgentGateway> { FakeAgentGateway() }
    single<RunGateway> { FakeRunGateway() }
    single<DashboardGateway> { FakeDashboardGateway() }
}

/** Live events and the app lifecycle (slice 1a-4). The WebSocket has no real adapter: board keys fail on it. */
internal fun Module.signalPorts() {
    single<LiveEvents> { FakeLiveEvents() }
    single { AppLifecycleBridge() }
    single<AppLifecycle> { get<AppLifecycleBridge>() }
    single { RefreshVisibleScreen(get(), get(), get()) }
}

internal fun Module.sliceThreeUseCases() {
    workUseCases()
    agentUseCases()
    runUseCases()
}

private fun Module.workUseCases() {
    single { ListIssues(get()) }
    single { SearchIssues(get()) }
    single { LoadIssue(get(), get()) }
    single { LoadIssuePlan(get()) }
    single { ListIssueAttachments(get()) }
    single { CommentOnIssue(get()) }
    single { ChangeIssueStatus(get()) }
    single { AssignIssue(get()) }
    single { CreateIssue(get()) }
    single { LoadInteractions(get()) }
}

private fun Module.agentUseCases() {
    single { RespondToInteraction(get()) }
    single { ListAgents(get()) }
    single { LoadAgent(get(), get(), get()) }
    single { PauseAgent(get(), get()) }
    single { ResumeAgent(get()) }
    single { InvokeHeartbeat(get(), get()) }
    single { SetAgentBudget(get()) }
}

private fun Module.runUseCases() {
    single { LoadRun(get()) }
    single { TailRunLog(get()) }
    single { CancelRun(get(), get()) }
    single { ListIssueRuns(get()) }
    single { LoadDashboard(get(), get()) }
    single { LoadWindowSpend(get()) }
}

/** State holders are factories: each screen gets its own, built with the scope the graph passes in. */
internal fun Module.sliceThreeHolders() {
    factory { params ->
        WorkListStateHolder(
            WorkListUseCases(get(), get(), get(), get(), get()),
            get(),
            get(),
            params.get(),
            params.get<CompanyTarget>(),
        )
    }
    factory { params ->
        IssueStateHolder(
            IssueUseCases(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                IssueEditUseCases(get(), get(), get(), get()),
                InteractionUseCases(get(), get()),
            ),
            get(),
            get(),
            params.get(),
            params.get<CompanyTarget>(),
            params.get<String>(),
        )
    }
    agentHolders()
    factory { params ->
        RunViewerStateHolder(
            RunViewerUseCases(get(), get(), get(), get()),
            get(),
            get(),
            params.get(),
            params.get<CompanyTarget>(),
            params.get<String>(),
        )
    }
    factory { params ->
        FactoryHomeStateHolder(
            FactoryHomeUseCases(get(), get(), get()),
            get(),
            get(),
            params.get(),
            params.get<CompanyTarget>(),
        )
    }
}

private fun Module.agentHolders() {
    factory { params ->
        AgentsStateHolder(
            AgentsUseCases(get(), get(), get(), get(), get()),
            get(),
            get(),
            params.get(),
            params.get<CompanyTarget>(),
        )
    }
    factory { params ->
        AgentDetailStateHolder(
            AgentDetailUseCases(get(), get(), get(), get(), get(), get()),
            get(),
            get(),
            params.get(),
            params.get<CompanyTarget>(),
            params.get<String>(),
        )
    }
}
