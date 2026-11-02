package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.AttentionSubject
import com.stainedhead.paperclipmobile.domain.BudgetAlertInfo
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.InteractionKind
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.SubjectKind
import kotlinx.serialization.json.JsonObject

/** Null (so Malformed) when the subject has no id: a card must say what it is about. */
internal fun AttentionSubjectDto.toDomain(): AttentionSubject? {
    val subjectId = id?.takeIf { it.isNotBlank() } ?: return null
    val extras = metadata ?: JsonObject(emptyMap())
    val kind = subjectKind(kind)
    return AttentionSubject(
        kind = kind,
        id = subjectId,
        title = title?.takeIf { it.isNotBlank() },
        identifier = identifier?.takeIf { it.isNotBlank() },
        status = status?.takeIf { it.isNotBlank() },
        href = href?.takeIf { it.isNotBlank() },
        issueId = extras.text("issueId").takeIf { kind == SubjectKind.Approval || kind == SubjectKind.Interaction },
        approvalType = extras.text("type")?.takeIf { kind == SubjectKind.Approval }?.let(ApprovalType::fromServer),
        interactionKind =
            extras.text("kind")?.takeIf { kind == SubjectKind.Interaction }?.let(InteractionKind::fromServer),
        budget = extras.takeIf { kind == SubjectKind.BudgetIncident }?.let(::budgetInfo),
    )
}

private fun subjectKind(raw: String?): SubjectKind =
    when (raw) {
        "approval" -> SubjectKind.Approval
        "decision" -> SubjectKind.Decision
        "issue" -> SubjectKind.Issue
        "interaction" -> SubjectKind.Interaction
        "join_request" -> SubjectKind.JoinRequest
        "recovery_action" -> SubjectKind.RecoveryAction
        "run" -> SubjectKind.Run
        "budget_incident" -> SubjectKind.BudgetIncident
        "agent" -> SubjectKind.Agent
        else -> SubjectKind.Unknown
    }

/**
 * The typed view of a budget alert's metadata (services/attention.ts:1777-1788): `thresholdType`, `amountObserved` and
 * `amountLimit` in cents, the observed one possibly fractional and rounded up (ADR-016). Null when any is unreadable,
 * which leaves the card without inline budget actions.
 */
private fun budgetInfo(extras: JsonObject): BudgetAlertInfo? {
    val observed = extras.cents("amountObserved") ?: return null
    val limit = extras.cents("amountLimit") ?: return null
    val threshold =
        when (extras.text("thresholdType")) {
            "hard" -> BudgetThreshold.Hard
            "soft" -> BudgetThreshold.Soft
            else -> return null
        }
    return BudgetAlertInfo(threshold, Money.usdCents(observed), Money.usdCents(limit), extras.text("scopeType"))
}
