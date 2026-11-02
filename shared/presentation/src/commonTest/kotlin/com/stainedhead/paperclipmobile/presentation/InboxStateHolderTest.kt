@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.AttentionSubject
import com.stainedhead.paperclipmobile.domain.BudgetAlertInfo
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolverAudience
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.ResolverPolicySource
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.domain.SubjectKind
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private fun TestScope.testScope() = CoroutineScope(StandardTestDispatcher(testScheduler))

private val home = TestData.instance()

private fun TestScope.env(role: Role = Role.Owner): Env {
    val env = Env(listOf(home)) { testScheduler.currentTime }
    env.auth.meResult = DomainResult.Success(TestData.access(role))
    env.clock.advance(3.days) // 2026-11-04, a Wednesday, so this_week is not due
    return env
}

private fun TestScope.holder(env: Env) =
    InboxStateHolder(env.inbox, env.connectivity, env.clock, testScope(), CompanyTarget("inst-1", TestData.acme.value))

private fun InboxState.items() = sections.flatMap { it.items }

private fun InboxState.item(id: String) = items().first { it.id.endsWith(":$id") }

class InboxLoadingTest {
    private val mixed =
        listOf(
            AttentionFixtures.approvalItem("w", DecideBy.Whenever),
            AttentionFixtures.approvalItem("t", DecideBy.Today),
            AttentionFixtures.approvalItem("n", DecideBy.None),
            AttentionFixtures.decisionItem("wk", DecideBy.ThisWeek),
        )

    @Test
    fun `FR-030 FR-095 the feed loads grouped with the server's badge and the feed's own timestamp`() =
        runTest {
            val env = env()
            env.attention.items = mixed
            val holder = holder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe InboxPhase.Content
            state.sections.map { it.group } shouldBe
                listOf(InboxGroupUi.DecideNow, InboxGroupUi.ThisWeek, InboxGroupUi.Whenever)
            state.sections[0].items.map { it.id } shouldBe listOf("approval:t")
            state.sections[2].items.map { it.id } shouldBe listOf("approval:w", "approval:n")
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
            state.isFromCache shouldBe false
            state.role shouldBe RoleUi.Owner
            state.isRefreshing shouldBe false
        }

    @Test
    fun `FR-032 the badge is the server's deskBadgeCount whatever the screen shows`() =
        runTest {
            val env = env()
            env.attentionCache.write(env.scope, AttentionFixtures.feed(mixed, badge = 9, total = 40))
            env.attention.failures["feed"] = DomainError.Forbidden
            val holder = holder(env)
            advanceUntilIdle()

            holder.state.value.badgeCount shouldBe 9
            holder.state.value.totalCount shouldBe 40
        }

    @Test
    fun `FR-095 the cache is shown first and replaced by the fresh feed`() =
        runTest {
            val env = env()
            env.attentionCache.write(
                env.scope,
                AttentionFixtures.feed(listOf(AttentionFixtures.approvalItem("old")), badge = 9),
            )
            env.attention.feedLatencyMillis = 100
            val seen = mutableListOf<InboxState>()
            val holder = holder(env)
            seen += holder.state.value
            val job = launch(start = CoroutineStart.UNDISPATCHED) { holder.state.collect { seen += it } }

            advanceUntilIdle()

            seen.any { it.isFromCache && it.items().map { i -> i.id } == listOf("approval:old") } shouldBe true
            holder.state.value.isFromCache shouldBe false
            holder.state.value
                .items()
                .size shouldBe 3
            job.cancel()
        }

    @Test
    fun `FR-095 when the refresh fails the cached feed stays with its time and the error is a key`() =
        runTest {
            val env = env()
            env.attentionCache.write(env.scope, AttentionFixtures.feed(mixed, badge = 4))
            env.attention.failures["feed"] = DomainError.Unreachable
            val holder = holder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe InboxPhase.Content
            state.isFromCache shouldBe true
            state.error shouldBe ErrorUi(ErrorKey.Unreachable)
            state.updatedAtEpochMillis shouldBe AttentionFixtures.generatedAt.toEpochMilliseconds()
            state.items().size shouldBe 4
        }

    @Test
    fun `an empty feed is an empty state and a failure with no cache is a failed state`() =
        runTest {
            val empty = env()
            empty.attention.items = emptyList()
            val emptyHolder = holder(empty)
            advanceUntilIdle()
            emptyHolder.state.value.phase shouldBe InboxPhase.Empty

            val failed = env()
            failed.attention.failures["feed"] = DomainError.ServerError
            val failedHolder = holder(failed)
            advanceUntilIdle()
            failedHolder.state.value.phase shouldBe InboxPhase.Failed
            failedHolder.state.value.error shouldBe ErrorUi(ErrorKey.ServerError)
        }

    @Test
    fun `FR-095 offline with an empty cache explains instead of showing a blank screen`() =
        runTest {
            val env = env()
            env.connectivity.setOnline(false)
            val holder = holder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe InboxPhase.OfflineEmpty
            holder.state.value.isOffline shouldBe true
            env.attention.count("feed") shouldBe 0
        }

    @Test
    fun `FR-035 refresh reloads the first page and foregrounding does the same`() =
        runTest {
            val env = env()
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(InboxIntent.Refresh)
            advanceUntilIdle()

            env.attention.count("feed") shouldBe 2
            holder.state.value.isRefreshing shouldBe false
        }

    @Test
    fun `refresh while offline makes no call`() =
        runTest {
            val env = env()
            val holder = holder(env)
            advanceUntilIdle()
            env.connectivity.setOnline(false)
            advanceUntilIdle()

            holder.onIntent(InboxIntent.Refresh)
            advanceUntilIdle()

            env.attention.count("feed") shouldBe 1
            holder.state.value.isOffline shouldBe true
        }

    @Test
    fun `FR-030 more pages append without duplicates and a stale cursor reloads the first page`() =
        runTest {
            val env = env()
            env.attention.items = (1..60).map { AttentionFixtures.approvalItem("a$it") }
            val holder = holder(env)
            advanceUntilIdle()
            holder.state.value.canLoadMore shouldBe true
            holder.state.value
                .items()
                .size shouldBe 50

            holder.onIntent(InboxIntent.LoadMore)
            advanceUntilIdle()

            holder.state.value
                .items()
                .size shouldBe 60
            holder.state.value
                .items()
                .map { it.id }
                .toSet()
                .size shouldBe 60
            holder.state.value.canLoadMore shouldBe false
        }

    @Test
    fun `a rejected cursor reloads from the first page`() =
        runTest {
            val env = env()
            env.attention.items = (1..60).map { AttentionFixtures.approvalItem("a$it") }
            val holder = holder(env)
            advanceUntilIdle()
            env.attention.items = (1..60).map { AttentionFixtures.approvalItem("b$it") } // the cursor item is gone

            holder.onIntent(InboxIntent.LoadMore)
            advanceUntilIdle()

            holder.state.value
                .items()
                .first()
                .id shouldBe "approval:b1"
            holder.state.value
                .items()
                .size shouldBe 50
        }
}

class InboxCardsTest {
    @Test
    fun `FR-031 FR-033 cards show kind severity title why now and the origin agent`() =
        runTest {
            val env = env()
            val holder = holder(env)
            advanceUntilIdle()

            val card = holder.state.value.item("ap-1")
            card.kind shouldBe SourceKindUi.Approval
            card.severity shouldBe SeverityUi.Medium
            card.title shouldBe "Hire a Designer"
            card.whyNow shouldBe "Why now for ap-1"
            card.originAgentName shouldBe "CEO"
            card.decideBy shouldBe DecideByUi(DecideByKindUi.Today, null)
            card.open shouldBe OpenTargetUi(OpenKindUi.Approval, "ap-1", null)
            card.webUrl shouldBe "https://home.example.com/PAP/approvals/ap-1"
            holder.state.value
                .item("dec-1")
                .identifier shouldBe "PAP-1182"
        }

    @Test
    fun `FR-031 filter chips come from the server's counts and filter the cards`() =
        runTest {
            val env = env()
            val holder = holder(env)
            advanceUntilIdle()

            holder.state.value.filters shouldBe
                listOf(
                    FilterChipUi(SourceKindUi.Approval, 1),
                    FilterChipUi(SourceKindUi.Decision, 1),
                    FilterChipUi(SourceKindUi.BudgetAlert, 1),
                )
            holder.onIntent(InboxIntent.FilterSelected(SourceKindUi.Decision))
            advanceUntilIdle()
            holder.state.value
                .items()
                .map { it.kind } shouldBe listOf(SourceKindUi.Decision)
            holder.state.value.selectedFilter shouldBe SourceKindUi.Decision

            holder.onIntent(InboxIntent.FilterSelected(null))
            advanceUntilIdle()
            holder.state.value
                .items()
                .size shouldBe 3
        }

    @Test
    fun `FR-033 inline actions need the server flag a supported kind and the role`() =
        runTest {
            val owner = env(Role.Owner)
            owner.attention.items =
                listOf(
                    AttentionFixtures.approvalItem("ap"),
                    AttentionFixtures.approvalItem("board", inlineResolvable = false),
                    AttentionFixtures.budgetItem("hard"),
                    AttentionFixtures.budgetItem("soft", BudgetThreshold.Soft),
                    AttentionFixtures.decisionItem("dec"),
                )
            val holder = holder(owner)
            advanceUntilIdle()

            val state = holder.state.value
            state.item("ap").inlineActions shouldBe listOf(InlineActionUi.Approve, InlineActionUi.Reject)
            state.item("board").inlineActions shouldBe emptyList()
            state.item("hard").inlineActions shouldBe listOf(InlineActionUi.KeepPaused, InlineActionUi.RaiseAndResume)
            state.item("soft").inlineActions shouldBe emptyList()
            state.item("dec").inlineActions shouldBe emptyList()
            state.item("dec").open shouldBe OpenTargetUi(OpenKindUi.Decision, "dec", null)
        }

    @Test
    fun `an operator cannot raise a budget and a viewer has no inline actions`() =
        runTest {
            val operator = env(Role.Operator)
            operator.attention.items =
                listOf(AttentionFixtures.budgetItem("hard"), AttentionFixtures.approvalItem("ap"))
            val operatorHolder = holder(operator)
            advanceUntilIdle()
            operatorHolder.state.value
                .item("hard")
                .inlineActions shouldBe listOf(InlineActionUi.KeepPaused)

            val viewer = env(Role.Viewer)
            viewer.attention.items = operator.attention.items
            val viewerHolder = holder(viewer)
            advanceUntilIdle()
            viewerHolder.state.value
                .items()
                .all { it.inlineActions.isEmpty() } shouldBe true
            viewerHolder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `unsupported and web-only kinds open the web app with the card's path`() =
        runTest {
            val env = env()
            env.attention.items =
                listOf(
                    AttentionFixtures.item(
                        "new",
                        AttentionSourceKind.Unsupported("quantum_review"),
                        AttentionSubject(SubjectKind.Unknown, "new", title = "Quantum", href = "/PAP/quantum"),
                    ),
                    AttentionFixtures.item(
                        "join",
                        AttentionSourceKind.JoinRequest,
                        AttentionSubject(SubjectKind.JoinRequest, "join", href = null),
                    ),
                )
            val holder = holder(env)
            advanceUntilIdle()

            val unsupported =
                holder.state.value
                    .items()
                    .first { it.kind == SourceKindUi.Unsupported }
            unsupported.rawKind shouldBe "quantum_review"
            unsupported.open.kind shouldBe OpenKindUi.WebOnly
            unsupported.webUrl shouldBe "https://home.example.com/PAP/quantum"
            val join =
                holder.state.value
                    .items()
                    .first { it.kind == SourceKindUi.JoinRequest }
            join.open.kind shouldBe OpenKindUi.WebOnly
            join.webUrl shouldBe null
        }

    @Test
    fun `an interaction addressed to another user is read-only and shows its audience`() =
        runTest {
            val env = env()
            val audience =
                ResolverAudience(
                    ResolverPolicy.Anyone,
                    ResolverPolicy.Anyone,
                    ResolverPolicySource.Requested,
                    addresseeUserId = "user-2",
                    addresseeName = "Sam",
                )
            val interaction =
                AttentionFixtures
                    .item(
                        "i1",
                        AttentionSourceKind.IssueThreadInteraction,
                        AttentionSubject(SubjectKind.Interaction, "i1", issueId = "iss-1"),
                    ).copy(resolverAudience = audience)
            env.attention.items =
                listOf(interaction, interaction.copy(id = "issue_thread_interaction:i2", resolverAudience = null))
            val holder = holder(env)
            advanceUntilIdle()

            val card =
                holder.state.value
                    .items()
                    .first { it.id.endsWith(":i1") }
            card.isReadOnly shouldBe true
            card.audience shouldBe AudienceUi(ResolverPolicyUi.Anyone, "Sam", true, null)
            holder.state.value
                .items()
                .first { it.id.endsWith(":i2") }
                .isReadOnly shouldBe false
        }

    @Test
    fun `severity orders cards inside a group`() =
        runTest {
            val env = env()
            env.attention.items =
                listOf(
                    AttentionFixtures.approvalItem("low", DecideBy.Today).copy(severity = Severity.Low),
                    AttentionFixtures.approvalItem("crit", DecideBy.Today).copy(severity = Severity.Critical),
                )
            val holder = holder(env)
            advanceUntilIdle()

            holder.state.value
                .items()
                .map { it.id } shouldBe listOf("approval:crit", "approval:low")
        }
}

class InboxActionsTest {
    private suspend fun TestScope.loaded(
        env: Env,
        items: List<com.stainedhead.paperclipmobile.domain.AttentionItem> =
            listOf(AttentionFixtures.approvalItem("ap-1")),
    ): InboxStateHolder {
        env.attention.items = items
        val holder = holder(env)
        advanceUntilIdle()
        return holder
    }

    @Test
    fun `FR-033 approve sends one approval call without device authentication and refreshes`() =
        runTest {
            val env = env(Role.Operator)
            val holder = loaded(env)

            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 1
            env.biometrics.calls shouldBe 0
            env.attention.count("feed") shouldBe 2
            holder.state.value.isMutating shouldBe false
        }

    @Test
    fun `FR-051 reject asks for a note then the device and sends once`() =
        runTest {
            val env = env()
            val holder = loaded(env)

            holder.onIntent(InboxIntent.RejectTapped("approval:ap-1"))
            advanceUntilIdle()
            holder.state.value.rejectPrompt shouldBe RejectPromptUi("approval:ap-1")

            holder.onIntent(InboxIntent.RejectConfirmed("  "))
            advanceUntilIdle()
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.NoteRequired)
            env.biometrics.calls shouldBe 0
            holder.state.value.rejectPrompt shouldBe RejectPromptUi("approval:ap-1")

            holder.onIntent(InboxIntent.RejectConfirmed("too expensive"))
            advanceUntilIdle()
            env.biometrics.calls shouldBe 1
            env.approvals.count("reject") shouldBe 1
            holder.state.value.rejectPrompt shouldBe null
            holder.state.value.feedback shouldBe null
        }

    @Test
    fun `FR-014 a cancelled device prompt makes no reject call and keeps the note prompt open`() =
        runTest {
            val env = env()
            env.biometrics.result = BiometricResult.Cancelled
            val holder = loaded(env)

            holder.onIntent(InboxIntent.RejectTapped("approval:ap-1"))
            holder.onIntent(InboxIntent.RejectConfirmed("no"))
            advanceUntilIdle()

            env.approvals.count("reject") shouldBe 0
            holder.state.value.feedback shouldBe
                FeedbackUi(FeedbackKind.DeviceAuth, deviceAuth = DeviceAuthIssue.Cancelled)
            holder.state.value.rejectPrompt shouldBe RejectPromptUi("approval:ap-1")

            holder.onIntent(InboxIntent.RejectCancelled)
            advanceUntilIdle()
            holder.state.value.rejectPrompt shouldBe null
        }

    @Test
    fun `FR-033 keep paused on a hard stop resolves the incident without device authentication`() =
        runTest {
            val env = env(Role.Operator)
            val holder = loaded(env, listOf(AttentionFixtures.budgetItem("inc-1")))

            holder.onIntent(InboxIntent.KeepPausedTapped("budget_alert:inc-1"))
            advanceUntilIdle()

            env.budgets.count("resolve") shouldBe 1
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `FR-034 snoozing hides the card sends the key and offers undo for five seconds`() =
        runTest {
            val env = env()
            val holder = loaded(env)
            val key = "attention:approval:ap-1"

            holder.onIntent(InboxIntent.SnoozeRequested("approval:ap-1", SnoozePresetUi.OneHour))
            runCurrent()

            env.attention.snoozes
                .single()
                .first shouldBe key
            holder.state.value.items() shouldBe emptyList()
            holder.state.value.undo
                ?.itemId shouldBe "approval:ap-1"

            advanceTimeBy(5_001)
            holder.state.value.undo shouldBe null
        }

    @Test
    fun `FR-034 dismiss then undo restores the card exactly once even across a refresh`() =
        runTest {
            val env = env()
            val holder = loaded(env)

            holder.onIntent(InboxIntent.DismissTapped("approval:ap-1"))
            runCurrent()
            holder.state.value.items() shouldBe emptyList()
            env.attention.count("dismiss") shouldBe 1

            holder.onIntent(InboxIntent.UndoTapped)
            runCurrent()
            env.attention.count("restore") shouldBe 1
            holder.onIntent(InboxIntent.Refresh)
            advanceUntilIdle()

            holder.state.value
                .items()
                .map { it.id } shouldBe listOf("approval:ap-1")
            holder.state.value.undo shouldBe null
        }

    @Test
    fun `a refresh that was computed before the dismissal does not bring the card back until the server agrees`() =
        runTest {
            val env = env()
            val holder = loaded(env)

            holder.onIntent(InboxIntent.DismissTapped("approval:ap-1"))
            env.attention.servesStaleFeed = true
            advanceUntilIdle()
            holder.onIntent(InboxIntent.Refresh)
            advanceUntilIdle()
            holder.state.value.items() shouldBe emptyList()

            env.attention.servesStaleFeed = false
            holder.onIntent(InboxIntent.Refresh)
            advanceUntilIdle()
            holder.state.value.items() shouldBe emptyList()

            env.attention.items = listOf(AttentionFixtures.approvalItem("ap-1")) // the item came back with new activity
            env.attention.restore(env.scope, "attention:approval:ap-1")
            holder.onIntent(InboxIntent.Refresh)
            advanceUntilIdle()
            holder.state.value
                .items()
                .map { it.id } shouldBe listOf("approval:ap-1")
        }

    @Test
    fun `FR-034 setting decide-by sends the source and moves the card to its new group`() =
        runTest {
            val env = env()
            val holder = loaded(env, listOf(AttentionFixtures.decisionItem("dec-1", DecideBy.Whenever)))
            holder.state.value.sections
                .single()
                .group shouldBe InboxGroupUi.Whenever

            holder.onIntent(InboxIntent.DecideBySet("decision:dec-1", DecideByKindUi.Today, null))
            advanceUntilIdle()

            env.attention.decideByCalls
                .single()
                .third shouldBe DecideBy.Today
            holder.state.value.sections
                .single()
                .group shouldBe InboxGroupUi.DecideNow
        }

    @Test
    fun `a viewer's action intents never reach the gateway`() =
        runTest {
            val env = env(Role.Viewer)
            val holder = loaded(env)

            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            holder.onIntent(InboxIntent.DismissTapped("approval:ap-1"))
            holder.onIntent(InboxIntent.SnoozeRequested("approval:ap-1", SnoozePresetUi.OneHour))
            holder.onIntent(InboxIntent.RejectTapped("approval:ap-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 0
            env.attention.count("dismiss") shouldBe 0
            env.attention.count("snooze") shouldBe 0
            holder.state.value.rejectPrompt shouldBe null
            holder.state.value
                .items()
                .size shouldBe 1
        }

    @Test
    fun `FR-095 offline every action intent is ignored and the reason is a key`() =
        runTest {
            val env = env()
            val holder = loaded(env)
            env.connectivity.setOnline(false)
            advanceUntilIdle()

            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            holder.onIntent(InboxIntent.DismissTapped("approval:ap-1"))
            holder.onIntent(InboxIntent.RejectTapped("approval:ap-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 0
            env.attention.count("dismiss") shouldBe 0
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.Offline
            holder.state.value
                .items()
                .size shouldBe 1
        }

    @Test
    fun `a second tap while an action is in flight is ignored`() =
        runTest {
            val env = env()
            val holder = loaded(env)

            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 1
        }

    @Test
    fun `acceptance 10 a timeout is shown as an error key and the feed is read again`() =
        runTest {
            val env = env()
            env.approvals.failures["approve"] = DomainError.Timeout
            val holder = loaded(env)

            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 1
            env.attention.count("feed") shouldBe 2
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.Reconciled
            holder.state.value.feedback
                ?.error shouldBe ErrorUi(ErrorKey.Timeout)

            holder.onIntent(InboxIntent.MessageDismissed)
            advanceUntilIdle()
            holder.state.value.feedback shouldBe null
        }

    @Test
    fun `snooze options leave out tonight after the cut-off`() =
        runTest {
            val env = env()
            env.clock.advance(8.hours) // 20:00Z, tonight in UTC has passed
            val holder = loaded(env)

            holder.state.value.snoozeOptions shouldBe
                listOf(SnoozePresetUi.OneHour, SnoozePresetUi.Tomorrow, SnoozePresetUi.NextWeek)
        }

    @Test
    fun `closing the holder stops it`() =
        runTest {
            val env = env()
            val holder = loaded(env)

            holder.close()
            holder.onIntent(InboxIntent.Refresh)
            advanceUntilIdle()

            env.attention.count("feed") shouldBe 1
        }
}

/** F-B-4: an inline action intent is honoured only for a card that offers that action to this role. */
class InboxInlineActionGuardTest {
    private suspend fun TestScope.loaded(
        env: Env,
        items: List<com.stainedhead.paperclipmobile.domain.AttentionItem>,
    ): InboxStateHolder {
        env.attention.items = items
        val holder = holder(env)
        advanceUntilIdle()
        return holder
    }

    @Test
    fun `approveTappedOnDecisionCardIsIgnored`() =
        runTest {
            val env = env()
            val holder = loaded(env, listOf(AttentionFixtures.decisionItem("dec-1")))

            holder.onIntent(InboxIntent.ApproveTapped("decision:dec-1"))
            holder.onIntent(InboxIntent.RejectTapped("decision:dec-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 0
            holder.state.value.rejectPrompt shouldBe null
        }

    @Test
    fun `approveTappedOnNonInlineApprovalIsIgnored`() =
        runTest {
            val env = env()
            val holder = loaded(env, listOf(AttentionFixtures.approvalItem("ap-1", inlineResolvable = false)))

            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            holder.onIntent(InboxIntent.RejectTapped("approval:ap-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 0
            holder.state.value.rejectPrompt shouldBe null
        }

    @Test
    fun `approveTappedForViewerRoleMakesNoGatewayCall`() =
        runTest {
            val env = env(Role.Viewer)
            val holder = loaded(env, listOf(AttentionFixtures.approvalItem("ap-1")))

            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 0
            holder.state.value.feedback shouldBe null
        }

    @Test
    fun `approveWithoutSubjectStatusIsIgnored`() =
        runTest {
            val env = env()
            val noStatus = AttentionFixtures.approvalItem("ap-1")
            val holder = loaded(env, listOf(noStatus.copy(subject = noStatus.subject.copy(status = null))))

            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 0
        }

    @Test
    fun `keepPausedTappedOnAnApprovalCardIsIgnored`() =
        runTest {
            val env = env()
            val holder = loaded(env, listOf(AttentionFixtures.approvalItem("ap-1")))

            holder.onIntent(InboxIntent.KeepPausedTapped("approval:ap-1"))
            advanceUntilIdle()

            env.budgets.count("resolve") shouldBe 0
        }

    @Test
    fun `forbiddenMutationReResolvesTheRole in Inbox`() =
        runTest {
            val env = env(Role.Operator)
            val holder = loaded(env, listOf(AttentionFixtures.approvalItem("ap-1")))
            holder.state.value
                .items()
                .first()
                .inlineActions
                .isNotEmpty() shouldBe true
            env.auth.meResult = DomainResult.Success(TestData.access(Role.Viewer))
            env.approvals.failures["approve"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(InboxIntent.ApproveTapped("approval:ap-1"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 1
            env.auth.count("me") shouldBe resolved + 1
            holder.state.value
                .items()
                .first()
                .inlineActions shouldBe emptyList()
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenReadReResolvesTheRole in Inbox`() =
        runTest {
            val env = env(Role.Operator)
            val holder = loaded(env, listOf(AttentionFixtures.approvalItem("ap-1")))
            env.auth.meResult = DomainResult.Success(TestData.access(Role.Viewer))
            env.attention.failures["feed"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(InboxIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }
}
