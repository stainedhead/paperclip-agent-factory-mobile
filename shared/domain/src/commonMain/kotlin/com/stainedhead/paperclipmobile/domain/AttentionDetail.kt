package com.stainedhead.paperclipmobile.domain

/** The one-line summary the server attaches to a card, by kind. Images are not carried in v1. */
sealed interface AttentionDetail {
    data class ApprovalSummary(
        val approvalType: String,
        val summary: String?,
    ) : AttentionDetail

    data class PlanApproval(
        val issueTitle: String?,
        val planTitle: String?,
        val summary: String?,
    ) : AttentionDetail

    data class Confirmation(
        val prompt: String?,
    ) : AttentionDetail

    data class Questions(
        val questionCount: Int,
        val firstQuestion: String?,
    ) : AttentionDetail

    data class SuggestedTasks(
        val taskCount: Int,
        val firstTaskTitle: String?,
    ) : AttentionDetail

    data class CheckboxConfirmation(
        val optionCount: Int,
        val prompt: String?,
    ) : AttentionDetail

    data class ItemVerdicts(
        val itemCount: Int,
        val prompt: String?,
    ) : AttentionDetail

    data class FailedRun(
        val agentName: String?,
        val failureReason: String?,
    ) : AttentionDetail

    data class Blocker(
        val blockingIdentifier: String?,
        val blockingTitle: String?,
        val blockedTaskCount: Int?,
    ) : AttentionDetail

    /** [observedPercent] is whole percent, rounded down by the mapper. */
    data class Budget(
        val observedPercent: Int,
        val observed: Money,
        val limit: Money,
    ) : AttentionDetail

    data class AgentError(
        val agentName: String?,
        val failureReason: String?,
    ) : AttentionDetail

    data class Generic(
        val summary: String?,
    ) : AttentionDetail

    /** A detail kind from a newer server. */
    data class Unsupported(
        val raw: String,
    ) : AttentionDetail
}
