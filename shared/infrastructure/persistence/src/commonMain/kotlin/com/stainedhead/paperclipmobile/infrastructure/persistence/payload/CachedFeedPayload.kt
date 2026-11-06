package com.stainedhead.paperclipmobile.infrastructure.persistence.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The stored layout of one cached Inbox page (payload format 1). It mirrors the domain on purpose, in plain strings and
 * numbers, so the domain carries no serialization annotations and a domain change cannot silently change what an old
 * row means. Nothing here can hold a secret: the feed is the server's own, already shown on screen.
 */
@Serializable
internal data class CachedFeed(
    val generatedAt: String,
    val totalCount: Int,
    val deskBadgeCount: Int,
    val nextCursor: String?,
    val countsBySourceKind: List<CachedCount>,
    val items: List<CachedItem>,
)

@Serializable
internal data class CachedCount(
    val kind: String,
    val count: Int,
)

@Serializable
internal data class CachedItem(
    val id: String,
    val sourceKind: String,
    val subject: CachedSubject,
    val whyNow: String,
    val severity: String,
    val dismissalKey: String,
    val activityAt: String,
    val createdAt: String,
    val decideBy: String?,
    val decisionVerbs: List<CachedVerb>,
    val inlineResolvable: Boolean,
    val dismissal: CachedDismissal?,
    val snoozedUntil: String?,
    val originAgentName: String?,
    val relatedIssue: CachedSubject?,
    val detail: CachedDetail?,
    val resolverAudience: CachedAudience?,
)

@Serializable
internal data class CachedSubject(
    val kind: String,
    val id: String,
    val title: String?,
    val identifier: String?,
    val status: String?,
    val href: String?,
    val issueId: String?,
    val approvalType: String?,
    val interactionKind: String?,
    val budget: CachedBudget?,
)

@Serializable
internal data class CachedBudget(
    val threshold: String,
    val observed: CachedMoney,
    val limit: CachedMoney,
    val scopeType: String?,
)

@Serializable
internal data class CachedMoney(
    val minorUnits: Long,
    val currency: String,
)

@Serializable
internal data class CachedVerb(
    val id: String,
    val label: String,
    val description: String?,
)

@Serializable
internal data class CachedDismissal(
    val kind: String,
    val dismissedAt: String,
    val snoozedUntil: String?,
    val isActive: Boolean,
)

@Serializable
internal data class CachedAudience(
    val requestedPolicy: String,
    val effectivePolicy: String,
    val policySource: String,
    val addresseeAgentId: String?,
    val addresseeUserId: String?,
    val addresseeName: String?,
    val createdByAgentId: String?,
    val createdByAgentName: String?,
)

@Serializable
internal sealed interface CachedDetail {
    @Serializable
    @SerialName("approval")
    class Approval(
        val approvalType: String,
        val summary: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("plan")
    class Plan(
        val issueTitle: String?,
        val planTitle: String?,
        val summary: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("confirmation")
    class Confirmation(
        val prompt: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("questions")
    class Questions(
        val questionCount: Int,
        val firstQuestion: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("tasks")
    class Tasks(
        val taskCount: Int,
        val firstTaskTitle: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("checkbox")
    class Checkbox(
        val optionCount: Int,
        val prompt: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("verdicts")
    class Verdicts(
        val itemCount: Int,
        val prompt: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("failed_run")
    class FailedRun(
        val agentName: String?,
        val failureReason: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("blocker")
    class Blocker(
        val blockingIdentifier: String?,
        val blockingTitle: String?,
        val blockedTaskCount: Int?,
    ) : CachedDetail

    @Serializable
    @SerialName("budget")
    class Budget(
        val observedPercent: Int,
        val observed: CachedMoney,
        val limit: CachedMoney,
    ) : CachedDetail

    @Serializable
    @SerialName("agent_error")
    class AgentError(
        val agentName: String?,
        val failureReason: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("generic")
    class Generic(
        val summary: String?,
    ) : CachedDetail

    @Serializable
    @SerialName("unsupported")
    class Unsupported(
        val raw: String,
    ) : CachedDetail
}
