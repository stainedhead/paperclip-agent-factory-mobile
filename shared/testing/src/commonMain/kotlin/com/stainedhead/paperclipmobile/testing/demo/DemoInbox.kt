package com.stainedhead.paperclipmobile.testing.demo

import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalPayload
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.AttentionDetail
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.AttentionSubject
import com.stainedhead.paperclipmobile.domain.BudgetAlertInfo
import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionEffect
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DecisionInput
import com.stainedhead.paperclipmobile.domain.DecisionOption
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.domain.DecisionVerb
import com.stainedhead.paperclipmobile.domain.EffectStaleness
import com.stainedhead.paperclipmobile.domain.IncidentStatus
import com.stainedhead.paperclipmobile.domain.InteractionKind
import com.stainedhead.paperclipmobile.domain.LinkedIssue
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.OptionStyle
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.domain.SubjectKind
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The Inbox of the design mocks (Acme Labs). Nine cards: three due today (the badge), then the week and the rest. The
 * counts the mock shows (All 9, Decisions 3, Approvals 2, Questions 2) follow from the items; nothing is hard-coded.
 */
object DemoInbox {
    private val now = DemoFactories.now
    private val arrived: Instant = now - 3.days

    /** The mock reads "Updated 12s ago" on first sight; a refresh then reads current. */
    val FIRST_READ_AGE = 12.seconds
    private const val BUDGET_CENTS = 20_000L
    private const val FULL_PERCENT = 100
    private val hardStop = Money.usdCents(BUDGET_CENTS)

    fun items(): List<AttentionItem> = decideNow() + thisWeek() + whenever()

    /** How urgent a card is and how long ago it last changed. */
    private data class Triage(
        val severity: Severity,
        val decideBy: DecideBy,
        val age: kotlin.time.Duration,
    )

    private fun card(
        sourceKind: AttentionSourceKind,
        subject: AttentionSubject,
        triage: Triage,
        whyNow: String,
    ) = AttentionItem(
        id = "${sourceKind.serverName}:${subject.id}",
        sourceKind = sourceKind,
        subject = subject,
        whyNow = whyNow,
        severity = triage.severity,
        dismissalKey = "attention:${sourceKind.serverName}:${subject.id}",
        activityAt = now - triage.age,
        createdAt = arrived,
        decideBy = triage.decideBy,
        decisionVerbs = listOf(DecisionVerb("review", "Review")),
        inlineResolvable = true,
    )

    private fun decideNow(): List<AttentionItem> =
        listOf(
            card(
                AttentionSourceKind.Decision,
                AttentionSubject(
                    SubjectKind.Decision,
                    DemoIds.DECISION_BILLING,
                    title = "Ship the billing migration to production?",
                    identifier = "PAP-1182",
                ),
                Triage(Severity.Critical, DecideBy.Today, 8.minutes),
                "CTO proposes 3 options, PAP-1182, blocks 4 tasks",
            ).copy(originAgentName = "CTO"),
            card(
                AttentionSourceKind.Approval,
                AttentionSubject(
                    SubjectKind.Approval,
                    DemoIds.APPROVAL_HIRE_QA,
                    title = "Hire a QA engineer agent",
                    approvalType = ApprovalType.HireAgent,
                    status = "pending",
                ),
                Triage(Severity.High, DecideBy.Today, 1.hours),
                "Requested by CEO, reports to CTO, \$150/mo",
            ).copy(
                originAgentName = "CEO",
                detail = AttentionDetail.ApprovalSummary(ApprovalType.HireAgent.serverName, "Hire a QA engineer agent"),
            ),
            card(
                AttentionSourceKind.BudgetAlert,
                AttentionSubject(
                    SubjectKind.BudgetIncident,
                    DemoIds.INCIDENT_ENGINEER_2,
                    title = "Engineer 2 hit its monthly budget",
                    budget = BudgetAlertInfo(BudgetThreshold.Hard, hardStop, hardStop, "agent"),
                ),
                Triage(Severity.Critical, DecideBy.Today, 2.hours),
                "Hard stop paused the agent and cancelled 1 run",
            ).copy(detail = AttentionDetail.Budget(FULL_PERCENT, hardStop, hardStop)),
        )

    private fun thisWeek(): List<AttentionItem> =
        listOf(
            card(
                AttentionSourceKind.IssueThreadInteraction,
                AttentionSubject(
                    SubjectKind.Interaction,
                    "int-1204-questions",
                    title = "Engineer 1 asked 2 questions",
                    identifier = "PAP-1204",
                    issueId = DemoIds.ISSUE_RETRIES,
                    interactionKind = InteractionKind.AskUserQuestions,
                ),
                Triage(Severity.Medium, DecideBy.ThisWeek, 3.hours),
                "Agent question, PAP-1204",
            ).copy(
                originAgentName = "Engineer 1",
                detail = AttentionDetail.Questions(2, "Which retry policy should webhook ingest use?"),
            ),
            card(
                AttentionSourceKind.FailedRun,
                AttentionSubject(
                    SubjectKind.Run,
                    DemoIds.RUN_QA_TIMEOUT,
                    title = "QA agent: run timed out on PAP-1190",
                    issueId = DemoIds.ISSUE_REFUNDS,
                ),
                Triage(Severity.Medium, DecideBy.ThisWeek, 5.hours),
                "Failed run, QA agent",
            ).copy(detail = AttentionDetail.FailedRun("QA agent", "timed_out")),
        )

    private fun whenever(): List<AttentionItem> =
        listOf(
            card(
                AttentionSourceKind.Decision,
                AttentionSubject(
                    SubjectKind.Decision,
                    DemoIds.DEC_KEYS,
                    title = "Rotate the signing keys this quarter?",
                ),
                Triage(Severity.Medium, DecideBy.Whenever, 1.days),
                "CTO proposes 2 options",
            ),
            card(
                AttentionSourceKind.Decision,
                AttentionSubject(
                    SubjectKind.Decision,
                    DemoIds.DEC_EXPORTER,
                    title = "Archive the legacy invoice exporter?",
                ),
                Triage(Severity.Low, DecideBy.None, 2.days),
                "CTO proposes 2 options",
            ),
            card(
                AttentionSourceKind.Approval,
                AttentionSubject(
                    SubjectKind.Approval,
                    DemoIds.APPROVAL_STRATEGY,
                    title = "Approve the Q1 strategy",
                    approvalType = ApprovalType.ApproveCeoStrategy,
                    status = "pending",
                ),
                Triage(Severity.Low, DecideBy.Whenever, 2.days),
                "Requested by CEO",
            ).copy(originAgentName = "CEO"),
            card(
                AttentionSourceKind.IssueThreadInteraction,
                AttentionSubject(
                    SubjectKind.Interaction,
                    "int-1182-confirm",
                    title = "Switch traffic to the new billing service?",
                    identifier = "PAP-1182",
                    issueId = DemoIds.ISSUE_BILLING,
                    interactionKind = InteractionKind.RequestConfirmation,
                ),
                Triage(Severity.Low, DecideBy.None, 2.days),
                "Confirmation, PAP-1182",
            ).copy(originAgentName = "Engineer 1"),
        )
}
