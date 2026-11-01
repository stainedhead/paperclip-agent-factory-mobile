package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalPayload
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.AttentionDetail
import com.stainedhead.paperclipmobile.domain.AttentionFeed
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
import com.stainedhead.paperclipmobile.domain.LinkedIssue
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.OptionStyle
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.domain.SubjectKind
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Attention, decision, approval and budget fixtures that match the design mocks (Acme Labs, PAP-1182). */
object AttentionFixtures {
    val generatedAt: Instant = TestData.epoch
    private val arrivedAt: Instant = TestData.epoch - 2.hours
    private const val FULL_PERCENT = 100
    private const val HARD_STOP_CENTS = 10_000L
    private val hardStopSpend = Money.usdCents(HARD_STOP_CENTS)
    private val decisionAge = 3.hours
    private val decisionLifetime = 48.hours
    private val approvalAge = 4.hours
    private val commentAge = 1.hours
    private val hireBudget = Money.usdCents(20_000)

    fun item(
        id: String,
        sourceKind: AttentionSourceKind = AttentionSourceKind.Approval,
        subject: AttentionSubject = AttentionSubject(SubjectKind.Approval, id, title = "Item $id"),
    ) = AttentionItem(
        id = "${sourceKind.serverName}:$id",
        sourceKind = sourceKind,
        subject = subject,
        whyNow = "Why now for $id",
        severity = Severity.Medium,
        dismissalKey = "attention:${sourceKind.serverName}:$id",
        activityAt = arrivedAt,
        createdAt = arrivedAt,
        decisionVerbs = listOf(DecisionVerb("decide", "Review")),
    )

    fun approvalItem(
        id: String = "ap-1",
        decideBy: DecideBy = DecideBy.Today,
        inlineResolvable: Boolean = true,
        type: ApprovalType = ApprovalType.HireAgent,
    ) = item(
        id,
        AttentionSourceKind.Approval,
        AttentionSubject(
            SubjectKind.Approval,
            id,
            title = "Hire a Designer",
            href = "/PAP/approvals/$id",
            approvalType = type,
        ),
    ).copy(
        decideBy = decideBy,
        inlineResolvable = inlineResolvable,
        originAgentName = "CEO",
        detail = AttentionDetail.ApprovalSummary(type.serverName, "Hire a Designer"),
    )

    fun decisionItem(
        id: String = "dec-1",
        decideBy: DecideBy = DecideBy.ThisWeek,
    ) = item(
        id,
        AttentionSourceKind.Decision,
        AttentionSubject(SubjectKind.Decision, id, title = "Ship the billing migration?", identifier = "PAP-1182"),
    ).copy(
        severity = Severity.High,
        decideBy = decideBy,
        inlineResolvable = true,
        originAgentName = "Engineer 1",
    )

    fun budgetItem(
        id: String = "inc-1",
        threshold: BudgetThreshold = BudgetThreshold.Hard,
        decideBy: DecideBy = DecideBy.Today,
    ) = item(
        id,
        AttentionSourceKind.BudgetAlert,
        AttentionSubject(
            SubjectKind.BudgetIncident,
            id,
            title = "Engineer 2 budget hard stop",
            href = "/PAP/costs",
            budget = BudgetAlertInfo(threshold, hardStopSpend, hardStopSpend, "agent"),
        ),
    ).copy(
        severity = Severity.High,
        decideBy = decideBy,
        inlineResolvable = true,
        detail = AttentionDetail.Budget(FULL_PERCENT, hardStopSpend, hardStopSpend),
    )

    fun feed(
        items: List<AttentionItem> = listOf(approvalItem(), decisionItem(), budgetItem()),
        badge: Int = items.size,
        next: String? = null,
        total: Int = items.size,
    ) = AttentionFeed(
        generatedAt = generatedAt,
        totalCount = total,
        deskBadgeCount = badge,
        nextCursor = next,
        countsBySourceKind = items.groupingBy { it.sourceKind }.eachCount(),
        items = items,
    )

    private val comment =
        DecisionEffect.CommentOnIssue(
            "iss-1182",
            EffectStaleness.Lenient,
            "Go ahead with the migration.",
        )
    private val cancel = DecisionEffect.CancelIssueTree("iss-1182", "Stopped by the board.")

    fun decision(
        id: String = "dec-1",
        status: DecisionStatus = DecisionStatus.Open,
        inputs: List<DecisionInput> = emptyList(),
    ) = Decision(
        id = DecisionId(id),
        title = "Ship the billing migration?",
        body = "Engineer 1 finished PAP-1182. Ship tonight or hold for the review.",
        status = status,
        options =
            listOf(
                DecisionOption("ship", "Ship it", "Comment and continue", OptionStyle.Primary, listOf(comment)),
                DecisionOption("hold", "Hold", null, OptionStyle.Default, emptyList()),
                DecisionOption(
                    "cancel",
                    "Cancel the work",
                    "Stops the whole tree",
                    OptionStyle.Destructive,
                    listOf(cancel),
                ),
            ),
        inputs = inputs,
        createdAt = TestData.epoch - decisionAge,
        originAgentId = "agent-eng-1",
        originAgentName = "Engineer 1",
        originIssueId = "iss-1182",
        expiresAt = TestData.epoch + decisionLifetime,
    )

    fun approval(
        id: String = "ap-1",
        status: ApprovalStatus = ApprovalStatus.Pending,
        type: ApprovalType = ApprovalType.HireAgent,
        payload: ApprovalPayload =
            ApprovalPayload.HireAgent(
                name = "Designer",
                role = "designer",
                title = "Product Designer",
                adapterType = "claude_local",
                reportsToAgentId = "agent-cto",
                monthlyBudget = hireBudget,
                skills = listOf("figma"),
                capabilities = null,
            ),
    ) = Approval(
        id = ApprovalId(id),
        type = type,
        status = status,
        payload = payload,
        createdAt = TestData.epoch - approvalAge,
        requestedByAgentId = "agent-ceo",
        requestedByAgentName = "CEO",
    )

    fun approvalComment(id: String = "c-1") =
        ApprovalComment(id, "Looks fine to me.", TestData.epoch - commentAge, authorAgentId = "agent-cto")

    fun linkedIssue() = LinkedIssue("iss-1182", "PAP-1182", "Ship the billing migration", "in_review")

    fun incident(
        id: String = "inc-1",
        observed: Long = HARD_STOP_CENTS,
        limit: Long = HARD_STOP_CENTS,
        status: IncidentStatus = IncidentStatus.Open,
    ) = BudgetIncident(
        id = BudgetIncidentId(id),
        scopeName = "Engineer 2",
        scopeType = "agent",
        threshold = BudgetThreshold.Hard,
        observed = Money.usdCents(observed),
        limit = Money.usdCents(limit),
        status = status,
    )
}
