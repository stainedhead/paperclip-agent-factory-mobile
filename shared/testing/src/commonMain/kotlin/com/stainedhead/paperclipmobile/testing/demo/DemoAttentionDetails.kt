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
import kotlin.time.Instant

/** What each Inbox card opens: the decision, approvals and budget incident behind it. */
object DemoAttentionDetails {
    private val now = DemoFactories.now
    private val arrived: Instant = now - 3.days
    private const val NOTE_LIMIT = 500
    private const val BUDGET_CENTS = 20_000L
    private const val HIRE_BUDGET_CENTS = 15_000L
    private val hardStop = Money.usdCents(BUDGET_CENTS)

    /** The effects text of the Decision mock: what each option does to PAP-1182. */
    fun billingDecision(): Decision =
        Decision(
            id = DecisionId(DemoIds.DECISION_BILLING),
            title = "Ship the billing migration to production?",
            body =
                "Staging checks pass. QA has not signed off the refund path. Pick an option; " +
                    "Paperclip applies its effects and logs them on the issue.",
            status = DecisionStatus.Open,
            options =
                listOf(
                    DecisionOption(
                        "ship",
                        "Ship tonight behind a feature flag",
                        null,
                        OptionStyle.Primary,
                        listOf(
                            DecisionEffect.UpdateIssueStatus(
                                DemoIds.ISSUE_BILLING,
                                EffectStaleness.Lenient,
                                "in_progress",
                            ),
                            DecisionEffect.AssignIssue(DemoIds.ISSUE_BILLING, EffectStaleness.Lenient, "DevOps"),
                            DecisionEffect.CommentOnIssue(
                                DemoIds.ISSUE_BILLING,
                                EffectStaleness.Lenient,
                                "Rollout plan: enable the flag for 5% of traffic, then ramp.",
                            ),
                        ),
                    ),
                    DecisionOption(
                        "wait",
                        "Wait for QA sign-off",
                        null,
                        OptionStyle.Default,
                        listOf(
                            DecisionEffect.CreateIssue(
                                DemoIds.ISSUE_BILLING,
                                EffectStaleness.Lenient,
                                "QA regression on refunds",
                            ),
                            DecisionEffect.UpdateIssueStatus(DemoIds.ISSUE_BILLING, EffectStaleness.Lenient, "blocked"),
                        ),
                    ),
                    DecisionOption(
                        "cancel",
                        "Cancel the migration",
                        null,
                        OptionStyle.Destructive,
                        listOf(DecisionEffect.CancelIssueTree(DemoIds.ISSUE_BILLING, "Cancelled by the board.")),
                    ),
                ),
            inputs =
                listOf(
                    DecisionInput("note", "Note to the agent (optional)", "Add context for the CTO", false, NOTE_LIMIT),
                ),
            createdAt = arrived,
            originAgentId = DemoIds.AGENT_CTO,
            originIssueId = DemoIds.ISSUE_BILLING,
            expiresAt = now + 2.days,
        )

    fun decisions(): List<Decision> =
        listOf(
            billingDecision(),
            simpleDecision(DemoIds.DEC_KEYS, "Rotate the signing keys this quarter?"),
            simpleDecision(DemoIds.DEC_EXPORTER, "Archive the legacy invoice exporter?"),
        )

    private fun simpleDecision(
        id: String,
        title: String,
    ) = billingDecision().copy(
        id = DecisionId(id),
        title = title,
        body = "The CTO proposes two options.",
        options =
            listOf(
                DecisionOption("yes", "Yes", null, OptionStyle.Primary, emptyList()),
                DecisionOption("no", "Not now", null, OptionStyle.Default, emptyList()),
            ),
        inputs = emptyList(),
    )

    fun approvals(): List<Approval> =
        listOf(
            Approval(
                id = ApprovalId(DemoIds.APPROVAL_HIRE_QA),
                type = ApprovalType.HireAgent,
                status = ApprovalStatus.Pending,
                payload =
                    ApprovalPayload.HireAgent(
                        name = "QA engineer",
                        role = "qa",
                        title = "QA Engineer",
                        adapterType = "claude_local",
                        reportsToAgentId = DemoIds.AGENT_CTO,
                        monthlyBudget = Money.usdCents(HIRE_BUDGET_CENTS),
                        skills = listOf("regression-testing"),
                        capabilities = null,
                    ),
                createdAt = now - 1.hours,
                requestedByAgentId = DemoIds.AGENT_CEO,
                requestedByAgentName = "CEO",
            ),
            Approval(
                id = ApprovalId(DemoIds.APPROVAL_STRATEGY),
                type = ApprovalType.ApproveCeoStrategy,
                status = ApprovalStatus.Pending,
                payload = ApprovalPayload.CeoStrategy("Q1 strategy", "Focus on billing v2 and webhook reliability."),
                createdAt = now - 2.days,
                requestedByAgentId = DemoIds.AGENT_CEO,
                requestedByAgentName = "CEO",
            ),
        )

    fun linkedIssues() = listOf(LinkedIssue(DemoIds.ISSUE_REFUNDS, "PAP-1190", "Refund path regression", "todo"))

    fun incidents(): List<BudgetIncident> =
        listOf(
            BudgetIncident(
                id = BudgetIncidentId(DemoIds.INCIDENT_ENGINEER_2),
                scopeName = "Engineer 2",
                scopeType = "agent",
                threshold = BudgetThreshold.Hard,
                observed = hardStop,
                limit = hardStop,
                status = IncidentStatus.Open,
            ),
        )
}
