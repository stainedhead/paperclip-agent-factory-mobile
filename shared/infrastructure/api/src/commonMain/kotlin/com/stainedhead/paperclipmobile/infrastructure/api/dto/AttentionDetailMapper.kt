package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.AttentionDetail
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolverAudience
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.ResolverPolicySource
import kotlinx.serialization.json.JsonObject

/**
 * The one-line summary of a card by its `kind` (types/attention.ts:110-187): twelve kinds, and `Unsupported` for any
 * other. A count that is missing reads as zero and a text as absent; images are not carried (v1).
 */
internal fun attentionDetail(raw: JsonObject): AttentionDetail {
    val kind = raw.text("kind").orEmpty()
    return summaryDetail(kind, raw) ?: countedDetail(kind, raw) ?: when (kind) {
        "blocker" -> blocker(raw)
        "budget" -> budget(raw) ?: AttentionDetail.Unsupported(kind)
        else -> AttentionDetail.Unsupported(kind)
    }
}

/** The kinds that carry texts only. */
private fun summaryDetail(
    kind: String,
    raw: JsonObject,
): AttentionDetail? =
    when (kind) {
        "approval" -> {
            AttentionDetail.ApprovalSummary(raw.text("approvalType").orEmpty(), raw.text("summaryExcerpt"))
        }

        "plan_approval" -> {
            AttentionDetail.PlanApproval(raw.text("issueTitle"), raw.text("planTitle"), raw.text("summaryExcerpt"))
        }

        "confirmation" -> {
            AttentionDetail.Confirmation(raw.text("promptExcerpt"))
        }

        "failed_run" -> {
            AttentionDetail.FailedRun(raw.text("agentName"), raw.text("failureReasonExcerpt"))
        }

        "agent_error" -> {
            AttentionDetail.AgentError(raw.text("agentName"), raw.text("failureReasonExcerpt"))
        }

        "generic" -> {
            AttentionDetail.Generic(raw.text("summaryExcerpt"))
        }

        else -> {
            null
        }
    }

/** The kinds that count something (questions, tasks, options, items) and show the first one. */
private fun countedDetail(
    kind: String,
    raw: JsonObject,
): AttentionDetail? =
    when (kind) {
        "questions" -> {
            AttentionDetail.Questions(raw.whole("questionCount") ?: 0, raw.text("firstQuestionText"))
        }

        "suggested_tasks" -> {
            AttentionDetail.SuggestedTasks(raw.whole("taskCount") ?: 0, raw.text("firstTaskTitle"))
        }

        "checkbox_confirmation" -> {
            AttentionDetail.CheckboxConfirmation(raw.whole("optionCount") ?: 0, raw.text("promptExcerpt"))
        }

        "item_verdicts" -> {
            AttentionDetail.ItemVerdicts(raw.whole("itemCount") ?: 0, raw.text("promptExcerpt"))
        }

        else -> {
            null
        }
    }

private fun blocker(raw: JsonObject): AttentionDetail.Blocker {
    val issue = raw.obj("blockingIssue")
    return AttentionDetail.Blocker(issue?.text("identifier"), issue?.text("title"), raw.whole("blockedTaskCount"))
}

private fun budget(raw: JsonObject): AttentionDetail.Budget? {
    val observed = raw.cents("amountObserved") ?: return null
    val limit = raw.cents("amountLimit") ?: return null
    val percent = raw.wholePart("observedPercent") ?: return null
    return AttentionDetail.Budget(percent, Money.usdCents(observed), Money.usdCents(limit))
}

/**
 * Who may resolve an interaction card (types/attention.ts:189-219). Unknown policies fail closed to human-only (the
 * domain does that); an unknown or absent source reads as requested, or as a company cap when it is a value this app
 * does not know, because both explain a narrower audience than asked for.
 */
internal fun resolverAudienceOf(raw: JsonObject): ResolverAudience =
    ResolverAudience(
        requestedPolicy = ResolverPolicy.fromServer(raw.text("requestedResolverPolicy")),
        effectivePolicy = ResolverPolicy.fromServer(raw.text("effectiveResolverPolicy")),
        policySource =
            when (raw.text("effectiveResolverPolicySource")) {
                null, "requested" -> ResolverPolicySource.Requested
                "governed_action" -> ResolverPolicySource.GovernedAction
                else -> ResolverPolicySource.CompanyCap
            },
        addresseeAgentId = raw.text("addresseeAgentId"),
        addresseeUserId = raw.text("addresseeUserId"),
        addresseeName = raw.text("addresseeName"),
        createdByAgentId = raw.text("createdByAgentId"),
        createdByAgentName = raw.text("createdByAgentName"),
    )
