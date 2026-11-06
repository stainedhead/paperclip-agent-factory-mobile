package com.stainedhead.paperclipmobile.infrastructure.persistence.payload

import com.stainedhead.paperclipmobile.domain.AttentionDetail
import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.AttentionSubject
import com.stainedhead.paperclipmobile.domain.BudgetAlertInfo
import com.stainedhead.paperclipmobile.domain.DecisionVerb
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolverAudience

/** Domain to stored layout. */
internal fun AttentionFeed.toCached() =
    CachedFeed(
        generatedAt = generatedAt.toString(),
        totalCount = totalCount,
        deskBadgeCount = deskBadgeCount,
        nextCursor = nextCursor,
        countsBySourceKind = countsBySourceKind.map { (kind, count) -> CachedCount(kind.serverName, count) },
        items = items.map { it.toCached() },
    )

private fun AttentionItem.toCached() =
    CachedItem(
        id = id,
        sourceKind = sourceKind.serverName,
        subject = subject.toCached(),
        whyNow = whyNow,
        severity = severity.name,
        dismissalKey = dismissalKey,
        activityAt = activityAt.toString(),
        createdAt = createdAt.toString(),
        decideBy = decideBy.toServer(),
        decisionVerbs = decisionVerbs.map { it.toCached() },
        inlineResolvable = inlineResolvable,
        dismissal = dismissal?.toCached(),
        snoozedUntil = snoozedUntil?.toString(),
        originAgentName = originAgentName,
        relatedIssue = relatedIssue?.toCached(),
        detail = detail?.toCached(),
        resolverAudience = resolverAudience?.toCached(),
    )

private fun AttentionSubject.toCached() =
    CachedSubject(
        kind = kind.name,
        id = id,
        title = title,
        identifier = identifier,
        status = status,
        href = href,
        issueId = issueId,
        approvalType = approvalType?.serverName,
        interactionKind = interactionKind?.serverName,
        budget = budget?.toCached(),
    )

private fun BudgetAlertInfo.toCached() = CachedBudget(threshold.name, observed.toCached(), limit.toCached(), scopeType)

private fun Money.toCached() = CachedMoney(minorUnits, currency)

private fun DecisionVerb.toCached() = CachedVerb(id, label, description)

private fun Dismissal.toCached() =
    CachedDismissal(kind.name, dismissedAt.toString(), snoozedUntil?.toString(), isActive)

private fun ResolverAudience.toCached() =
    CachedAudience(
        requestedPolicy = requestedPolicy.name,
        effectivePolicy = effectivePolicy.name,
        policySource = policySource.name,
        addresseeAgentId = addresseeAgentId,
        addresseeUserId = addresseeUserId,
        addresseeName = addresseeName,
        createdByAgentId = createdByAgentId,
        createdByAgentName = createdByAgentName,
    )

private fun AttentionDetail.toCached(): CachedDetail =
    when (this) {
        is AttentionDetail.ApprovalSummary -> CachedDetail.Approval(approvalType, summary)
        is AttentionDetail.PlanApproval -> CachedDetail.Plan(issueTitle, planTitle, summary)
        is AttentionDetail.Confirmation -> CachedDetail.Confirmation(prompt)
        is AttentionDetail.Questions -> CachedDetail.Questions(questionCount, firstQuestion)
        is AttentionDetail.SuggestedTasks -> CachedDetail.Tasks(taskCount, firstTaskTitle)
        is AttentionDetail.CheckboxConfirmation -> CachedDetail.Checkbox(optionCount, prompt)
        is AttentionDetail.ItemVerdicts -> CachedDetail.Verdicts(itemCount, prompt)
        is AttentionDetail.FailedRun -> CachedDetail.FailedRun(agentName, failureReason)
        is AttentionDetail.Blocker -> CachedDetail.Blocker(blockingIdentifier, blockingTitle, blockedTaskCount)
        is AttentionDetail.Budget -> CachedDetail.Budget(observedPercent, observed.toCached(), limit.toCached())
        is AttentionDetail.AgentError -> CachedDetail.AgentError(agentName, failureReason)
        is AttentionDetail.Generic -> CachedDetail.Generic(summary)
        is AttentionDetail.Unsupported -> CachedDetail.Unsupported(raw)
    }
