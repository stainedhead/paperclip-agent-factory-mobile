package com.stainedhead.paperclipmobile.infrastructure.persistence.payload

import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.AttentionDetail
import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.AttentionSubject
import com.stainedhead.paperclipmobile.domain.BudgetAlertInfo
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DecisionVerb
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.DismissalKind
import com.stainedhead.paperclipmobile.domain.InteractionKind
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolverAudience
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.ResolverPolicySource
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.domain.SubjectKind
import kotlin.time.Instant

/**
 * Stored layout to domain. Any value that no longer fits throws [IllegalArgumentException] (a damaged `Instant`, a
 * `Money` that is not valid); the caller treats that as a cache miss.
 */
internal fun CachedFeed.toDomain() =
    AttentionFeed(
        generatedAt = Instant.parse(generatedAt),
        totalCount = totalCount,
        deskBadgeCount = deskBadgeCount,
        nextCursor = nextCursor,
        countsBySourceKind = countsBySourceKind.associate { AttentionSourceKind.fromServer(it.kind) to it.count },
        items = items.map { it.toDomain() },
    )

private fun CachedItem.toDomain() =
    AttentionItem(
        id = id,
        sourceKind = AttentionSourceKind.fromServer(sourceKind),
        subject = subject.toDomain(),
        whyNow = whyNow,
        severity = named(severity, Severity.Medium),
        dismissalKey = dismissalKey,
        activityAt = Instant.parse(activityAt),
        createdAt = Instant.parse(createdAt),
        decideBy = DecideBy.parse(decideBy),
        decisionVerbs = decisionVerbs.map { DecisionVerb(it.id, it.label, it.description) },
        inlineResolvable = inlineResolvable,
        dismissal = dismissal?.toDomain(),
        snoozedUntil = snoozedUntil?.let { Instant.parse(it) },
        originAgentName = originAgentName,
        relatedIssue = relatedIssue?.toDomain(),
        detail = detail?.toDomain(),
        resolverAudience = resolverAudience?.toDomain(),
    )

private fun CachedSubject.toDomain() =
    AttentionSubject(
        kind = named(kind, SubjectKind.Unknown),
        id = id,
        title = title,
        identifier = identifier,
        status = status,
        href = href,
        issueId = issueId,
        approvalType = approvalType?.let { ApprovalType.fromServer(it) },
        interactionKind = interactionKind?.let { InteractionKind.fromServer(it) },
        budget = budget?.toDomain(),
    )

private fun CachedBudget.toDomain() =
    BudgetAlertInfo(named(threshold, BudgetThreshold.Hard), observed.toDomain(), limit.toDomain(), scopeType)

private fun CachedMoney.toDomain() = Money(minorUnits, currency)

private fun CachedDismissal.toDomain() =
    Dismissal(
        kind = named(kind, DismissalKind.Dismiss),
        dismissedAt = Instant.parse(dismissedAt),
        snoozedUntil = snoozedUntil?.let { Instant.parse(it) },
        isActive = isActive,
    )

/** Unknown policy names fail closed to the narrowest audience, like the server mapper. */
private fun CachedAudience.toDomain() =
    ResolverAudience(
        requestedPolicy = named(requestedPolicy, ResolverPolicy.HumanOnly),
        effectivePolicy = named(effectivePolicy, ResolverPolicy.HumanOnly),
        policySource = named(policySource, ResolverPolicySource.Requested),
        addresseeAgentId = addresseeAgentId,
        addresseeUserId = addresseeUserId,
        addresseeName = addresseeName,
        createdByAgentId = createdByAgentId,
        createdByAgentName = createdByAgentName,
    )

private fun CachedDetail.toDomain(): AttentionDetail =
    when (this) {
        is CachedDetail.Approval -> AttentionDetail.ApprovalSummary(approvalType, summary)
        is CachedDetail.Plan -> AttentionDetail.PlanApproval(issueTitle, planTitle, summary)
        is CachedDetail.Confirmation -> AttentionDetail.Confirmation(prompt)
        is CachedDetail.Questions -> AttentionDetail.Questions(questionCount, firstQuestion)
        is CachedDetail.Tasks -> AttentionDetail.SuggestedTasks(taskCount, firstTaskTitle)
        is CachedDetail.Checkbox -> AttentionDetail.CheckboxConfirmation(optionCount, prompt)
        is CachedDetail.Verdicts -> AttentionDetail.ItemVerdicts(itemCount, prompt)
        is CachedDetail.FailedRun -> AttentionDetail.FailedRun(agentName, failureReason)
        is CachedDetail.Blocker -> AttentionDetail.Blocker(blockingIdentifier, blockingTitle, blockedTaskCount)
        is CachedDetail.Budget -> AttentionDetail.Budget(observedPercent, observed.toDomain(), limit.toDomain())
        is CachedDetail.AgentError -> AttentionDetail.AgentError(agentName, failureReason)
        is CachedDetail.Generic -> AttentionDetail.Generic(summary)
        is CachedDetail.Unsupported -> AttentionDetail.Unsupported(raw)
    }

private inline fun <reified E : Enum<E>> named(
    name: String,
    default: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: default
