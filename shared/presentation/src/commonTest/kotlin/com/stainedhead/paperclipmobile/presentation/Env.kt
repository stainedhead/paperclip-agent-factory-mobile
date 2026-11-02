package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.InstanceLocks
import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.application.agent.InvokeHeartbeat
import com.stainedhead.paperclipmobile.application.agent.ListAgents
import com.stainedhead.paperclipmobile.application.agent.LoadAgent
import com.stainedhead.paperclipmobile.application.agent.PauseAgent
import com.stainedhead.paperclipmobile.application.agent.ResumeAgent
import com.stainedhead.paperclipmobile.application.agent.SetAgentBudget
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
import com.stainedhead.paperclipmobile.application.dashboard.LoadDashboard
import com.stainedhead.paperclipmobile.application.dashboard.LoadWindowSpend
import com.stainedhead.paperclipmobile.application.decision.DecideDecision
import com.stainedhead.paperclipmobile.application.decision.DismissDecision
import com.stainedhead.paperclipmobile.application.decision.LoadDecision
import com.stainedhead.paperclipmobile.application.decision.PollDecisionExecution
import com.stainedhead.paperclipmobile.application.instance.AddInstance
import com.stainedhead.paperclipmobile.application.instance.GetInstance
import com.stainedhead.paperclipmobile.application.instance.ObserveInstances
import com.stainedhead.paperclipmobile.application.instance.ProbeInstance
import com.stainedhead.paperclipmobile.application.instance.RemoveInstance
import com.stainedhead.paperclipmobile.application.instance.ReorderInstances
import com.stainedhead.paperclipmobile.application.instance.SwitchCompany
import com.stainedhead.paperclipmobile.application.instance.SwitchInstance
import com.stainedhead.paperclipmobile.application.instance.UpdateInstance
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
import com.stainedhead.paperclipmobile.application.run.CancelRun
import com.stainedhead.paperclipmobile.application.run.ListIssueRuns
import com.stainedhead.paperclipmobile.application.run.LoadRun
import com.stainedhead.paperclipmobile.application.run.TailRunLog
import com.stainedhead.paperclipmobile.application.settings.ObserveAppSettings
import com.stainedhead.paperclipmobile.application.settings.ShouldLockApp
import com.stainedhead.paperclipmobile.application.settings.UnlockApp
import com.stainedhead.paperclipmobile.application.settings.UpdateAppSettings
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
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

/** All fakes and every slice 1a-1 use case, wired the way the umbrella wires them. */
class Env(
    instances: List<Instance> = emptyList(),
    clockElapsed: () -> Long = { 0L },
) {
    val clock = FakeClock(elapsedMillis = clockElapsed)
    val repository = FakeInstanceRepository(instances)
    val secrets = FakeSecretStore()
    val settings = FakeAppSettingsStore(AppSettings(deviceName = "Alex's iPhone"))
    val auth = FakeAuthGateway()
    val health = FakeHealthGateway()
    val biometrics = FakeBiometrics()
    val browser = FakePairingBrowser()
    val connectivity = FakeConnectivity()
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
    private val ids = FakeIdGenerator("inst-")
    private val validate = ValidateInstanceAddress(allowInsecureLocal = false)
    private val deviceAuth = DeviceAuthGuard(biometrics)
    private val revoker = KeyRevoker(auth)
    val locks = InstanceLocks()
    private val signedOutMarker = SignedOutMarker(repository, locks)
    private val enrollment = KeyEnrollment(auth, secrets, repository, clock, settings, locks)

    init {
        auth.hostOf = { id -> repository.current.firstOrNull { it.id == id }?.baseUri }
        auth.tokenOf = { id -> secrets.peek(SecretKey(id, SecretName.BoardToken)) }
    }

    val probe = ProbeInstance(health)
    val add = AddInstance(repository, clock, ids, validate)
    val update = UpdateInstance(repository, secrets, validate, locks)
    val get = GetInstance(repository)
    val pair = PairInstance(repository, auth, browser, clock, settings, enrollment)
    val saveKey = SaveManualBoardKey(repository, auth, enrollment)
    val loadAccess = LoadInstanceAccess(repository, auth, signedOutMarker, clock, connectivity, locks)
    val updateSettings = UpdateAppSettings(settings)
    val switchInstance = SwitchInstance(repository, settings)
    val switchCompany = SwitchCompany(repository, locks)
    val observeInstances = ObserveInstances(repository)
    val observeSettings = ObserveAppSettings(settings)
    val reorder = ReorderInstances(repository)
    val remove = RemoveInstance(repository, secrets, revoker, deviceAuth, locks)
    val signOut = SignOutInstance(repository, secrets, revoker, locks)
    val signOutEverywhere = SignOutEverywhere(repository, signOut)
    val listKeys = ListBoardKeys(repository, auth, signedOutMarker)
    val revokeKey = RevokeBoardKey(repository, auth, signOut, signedOutMarker)
    val expiry = KeyExpiryStatus(clock)
    val checkCredentials = CheckCredentials(repository, secrets, locks)
    val unlock = UnlockApp(biometrics)
    val shouldLock = ShouldLockApp(clock)
    val validateAddress = validate

    private val resolveContext = ResolveCompanyContext(loadAccess)
    private val refreshInbox = RefreshInbox(attention, attentionCache)

    val inbox =
        InboxUseCases(
            resolveContext,
            get,
            refreshInbox,
            LoadInbox(attentionCache, refreshInbox),
            ReadCachedInbox(attentionCache),
            LoadMoreInbox(attention),
            SnoozeItem(attention, clock, timeZone),
            DismissItem(attention),
            UndoDismiss(attention, clock),
            SetDecideBy(attention),
            ApproveApproval(approvals),
            RejectApproval(approvals, deviceAuth),
            ResolveBudgetIncident(budgets, deviceAuth),
            AvailableSnoozePresets(clock, timeZone),
        )

    val decision =
        DecisionUseCases(
            resolveContext,
            LoadDecision(decisions),
            DecideDecision(decisions, deviceAuth, ids),
            DismissDecision(decisions),
            PollDecisionExecution(decisions),
            SnoozeItem(attention, clock, timeZone),
            AvailableSnoozePresets(clock, timeZone),
        )

    val approval =
        ApprovalUseCases(
            resolveContext,
            LoadApproval(approvals, agents, connectivity),
            ApproveApproval(approvals),
            RejectApproval(approvals, deviceAuth),
            RequestRevision(approvals),
            CommentOnApproval(approvals),
        )

    val budget =
        BudgetUseCases(
            resolveContext,
            LoadBudgetIncident(budgets),
            ResolveBudgetIncident(budgets, deviceAuth),
        )

    private val listAgents = ListAgents(agents)
    private val pauseAgent = PauseAgent(agents, deviceAuth)
    private val resumeAgent = ResumeAgent(agents)
    private val invoke = InvokeHeartbeat(agents, runs)

    val factory = FactoryHomeUseCases(resolveContext, LoadDashboard(dashboard, runs), LoadWindowSpend(dashboard))

    val agentList = AgentsUseCases(resolveContext, listAgents, pauseAgent, resumeAgent, invoke)

    val agentDetail =
        AgentDetailUseCases(
            resolveContext,
            LoadAgent(agents, runs, issues),
            pauseAgent,
            resumeAgent,
            invoke,
            SetAgentBudget(agents),
        )

    val runViewer = RunViewerUseCases(resolveContext, LoadRun(runs), TailRunLog(runs), CancelRun(runs, deviceAuth))

    val workList =
        WorkListUseCases(
            resolveContext,
            ListIssues(issues),
            SearchIssues(ListIssues(issues)),
            CreateIssue(issues),
            listAgents,
        )

    val issue =
        IssueUseCases(
            resolveContext,
            get,
            LoadIssue(issues, agents, connectivity),
            LoadIssuePlan(issues),
            ListIssueRuns(runs),
            ListIssueAttachments(issues),
            IssueEditUseCases(
                CommentOnIssue(issues),
                ChangeIssueStatus(issues),
                AssignIssue(issues),
                CreateIssue(issues),
            ),
            InteractionUseCases(LoadInteractions(interactions), RespondToInteraction(interactions)),
        )

    val firstRun =
        FirstRunUseCases(validate, probe, add, update, get, pair, saveKey, loadAccess, updateSettings, switchInstance)

    suspend fun storeToken(instance: Instance = TestData.instance()) {
        secrets.put(SecretKey(instance.id, SecretName.BoardToken), Secret.of(TestData.TOKEN), SecretPolicy(false))
    }
}
