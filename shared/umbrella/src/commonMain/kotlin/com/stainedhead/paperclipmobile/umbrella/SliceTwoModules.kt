package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.application.approval.ApproveApproval
import com.stainedhead.paperclipmobile.application.approval.CommentOnApproval
import com.stainedhead.paperclipmobile.application.approval.LoadApproval
import com.stainedhead.paperclipmobile.application.approval.RejectApproval
import com.stainedhead.paperclipmobile.application.approval.RequestRevision
import com.stainedhead.paperclipmobile.application.attention.AvailableSnoozePresets
import com.stainedhead.paperclipmobile.application.attention.DismissItem
import com.stainedhead.paperclipmobile.application.attention.LoadInbox
import com.stainedhead.paperclipmobile.application.attention.LoadMoreInbox
import com.stainedhead.paperclipmobile.application.attention.ReadCachedInbox
import com.stainedhead.paperclipmobile.application.attention.RefreshInbox
import com.stainedhead.paperclipmobile.application.attention.SetDecideBy
import com.stainedhead.paperclipmobile.application.attention.SnoozeItem
import com.stainedhead.paperclipmobile.application.attention.UndoDismiss
import com.stainedhead.paperclipmobile.application.auth.CheckCredentials
import com.stainedhead.paperclipmobile.application.auth.KeyEnrollment
import com.stainedhead.paperclipmobile.application.auth.KeyExpiryStatus
import com.stainedhead.paperclipmobile.application.auth.KeyRevoker
import com.stainedhead.paperclipmobile.application.auth.ListBoardKeys
import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.application.auth.PairInstance
import com.stainedhead.paperclipmobile.application.auth.RevokeBoardKey
import com.stainedhead.paperclipmobile.application.auth.SaveManualBoardKey
import com.stainedhead.paperclipmobile.application.auth.SignOutEverywhere
import com.stainedhead.paperclipmobile.application.auth.SignOutInstance
import com.stainedhead.paperclipmobile.application.auth.SignedOutMarker
import com.stainedhead.paperclipmobile.application.budget.LoadBudgetIncident
import com.stainedhead.paperclipmobile.application.budget.ResolveBudgetIncident
import com.stainedhead.paperclipmobile.application.decision.DecideDecision
import com.stainedhead.paperclipmobile.application.decision.DismissDecision
import com.stainedhead.paperclipmobile.application.decision.LoadDecision
import com.stainedhead.paperclipmobile.application.decision.PollDecisionExecution
import com.stainedhead.paperclipmobile.application.instance.AddInstance
import com.stainedhead.paperclipmobile.application.instance.BuildInstanceLink
import com.stainedhead.paperclipmobile.application.instance.GetInstance
import com.stainedhead.paperclipmobile.application.instance.ObserveInstances
import com.stainedhead.paperclipmobile.application.instance.ParseAddLink
import com.stainedhead.paperclipmobile.application.instance.ProbeInstance
import com.stainedhead.paperclipmobile.application.instance.RemoveInstance
import com.stainedhead.paperclipmobile.application.instance.ReorderInstances
import com.stainedhead.paperclipmobile.application.instance.SwitchCompany
import com.stainedhead.paperclipmobile.application.instance.SwitchInstance
import com.stainedhead.paperclipmobile.application.instance.UpdateInstance
import com.stainedhead.paperclipmobile.application.settings.ObserveAppSettings
import com.stainedhead.paperclipmobile.application.settings.ShouldLockApp
import com.stainedhead.paperclipmobile.application.settings.UnlockApp
import com.stainedhead.paperclipmobile.application.settings.UpdateAppSettings
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.AttentionCache
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.HealthGateway
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.presentation.AppStateHolder
import com.stainedhead.paperclipmobile.presentation.AppUseCases
import com.stainedhead.paperclipmobile.presentation.ApprovalStateHolder
import com.stainedhead.paperclipmobile.presentation.ApprovalUseCases
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentStateHolder
import com.stainedhead.paperclipmobile.presentation.BudgetUseCases
import com.stainedhead.paperclipmobile.presentation.CompanyTarget
import com.stainedhead.paperclipmobile.presentation.DecisionStateHolder
import com.stainedhead.paperclipmobile.presentation.DecisionTarget
import com.stainedhead.paperclipmobile.presentation.DecisionUseCases
import com.stainedhead.paperclipmobile.presentation.EditInstanceStateHolder
import com.stainedhead.paperclipmobile.presentation.EditInstanceUseCases
import com.stainedhead.paperclipmobile.presentation.FactoriesStateHolder
import com.stainedhead.paperclipmobile.presentation.FactoriesUseCases
import com.stainedhead.paperclipmobile.presentation.FirstRunStateHolder
import com.stainedhead.paperclipmobile.presentation.FirstRunUseCases
import com.stainedhead.paperclipmobile.presentation.InboxStateHolder
import com.stainedhead.paperclipmobile.presentation.InboxUseCases
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesStateHolder
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesUseCases
import com.stainedhead.paperclipmobile.presentation.SettingsStateHolder
import com.stainedhead.paperclipmobile.presentation.SettingsUseCases
import com.stainedhead.paperclipmobile.testing.fakes.FakeAppSettingsStore
import com.stainedhead.paperclipmobile.testing.fakes.FakeApprovalGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionCache
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAuthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeBudgetGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeDecisionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDeviceTimeZone
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import org.koin.dsl.module

internal fun org.koin.core.module.Module.sliceTwoUseCases() {
    attentionUseCases()
    decisionUseCases()
    approvalUseCases()
    budgetUseCases()
}

private fun org.koin.core.module.Module.attentionUseCases() {
    single { ResolveCompanyContext(get()) }
    single { RefreshInbox(get(), get()) }
    single { LoadInbox(get(), get()) }
    single { ReadCachedInbox(get()) }
    single { LoadMoreInbox(get()) }
    single { SnoozeItem(get(), get(), get()) }
    single { DismissItem(get()) }
    single { UndoDismiss(get(), get()) }
    single { SetDecideBy(get()) }
    single { AvailableSnoozePresets(get(), get()) }
}

private fun org.koin.core.module.Module.decisionUseCases() {
    single { LoadDecision(get()) }
    single { DecideDecision(get(), get(), get()) }
    single { DismissDecision(get()) }
    single { PollDecisionExecution(get()) }
}

private fun org.koin.core.module.Module.approvalUseCases() {
    single { LoadApproval(get()) }
    single { ApproveApproval(get()) }
    single { RejectApproval(get(), get()) }
    single { RequestRevision(get()) }
    single { CommentOnApproval(get()) }
}

private fun org.koin.core.module.Module.budgetUseCases() {
    single { LoadBudgetIncident(get()) }
    single { ResolveBudgetIncident(get(), get()) }
}

internal fun org.koin.core.module.Module.sliceTwoHolders() {
    factory { params ->
        InboxStateHolder(
            InboxUseCases(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            ),
            get(),
            get(),
            params.get(),
            params.get<CompanyTarget>(),
        )
    }
    factory { params ->
        DecisionStateHolder(
            DecisionUseCases(get(), get(), get(), get(), get(), get(), get()),
            get(),
            get(),
            params.get(),
            params.get<DecisionTarget>(),
        )
    }
    factory { params ->
        ApprovalStateHolder(
            ApprovalUseCases(get(), get(), get(), get(), get(), get()),
            get(),
            get(),
            params.get(),
            params.get<CompanyTarget>(),
            params.get<String>(),
        )
    }
    factory { params ->
        BudgetIncidentStateHolder(
            BudgetUseCases(get(), get(), get()),
            get(),
            get(),
            params.get(),
            params.get<CompanyTarget>(),
            params.get<String>(),
        )
    }
}
