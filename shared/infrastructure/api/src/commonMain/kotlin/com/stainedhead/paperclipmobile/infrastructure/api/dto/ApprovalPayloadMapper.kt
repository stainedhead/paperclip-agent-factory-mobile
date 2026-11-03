package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.ApprovalPayload
import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.Money
import kotlinx.serialization.json.JsonObject

/**
 * The payload of each approval type, field by field (`ui/src/components/ApprovalPayload.tsx:89-184`). Every field may
 * be missing or of another type, because the server redacts payloads; such a field is null, not an error.
 */
internal fun approvalPayload(
    rawType: String,
    payload: JsonObject,
): ApprovalPayload =
    when (ApprovalType.fromServer(rawType)) {
        ApprovalType.HireAgent -> hire(payload)
        ApprovalType.ApproveCeoStrategy -> ceoStrategy(payload)
        ApprovalType.BudgetOverrideRequired -> budgetOverride(payload)
        ApprovalType.RequestBoardApproval -> boardRequest(payload)
        is ApprovalType.Unsupported -> ApprovalPayload.Unsupported(rawType)
    }

private fun hire(p: JsonObject) =
    ApprovalPayload.HireAgent(
        name = p.text("name"),
        role = p.text("role"),
        title = p.text("title"),
        adapterType = p.text("adapterType"),
        reportsToAgentId = p.text("reportsTo"),
        // 0 means "no budget" on the server (agent budgets, research A2), so it is not shown as a spend limit.
        monthlyBudget = p.cents("budgetMonthlyCents")?.takeIf { it > 0 }?.let(Money::usdCents),
        skills = p.texts("desiredSkills"),
        capabilities = p.text("capabilities"),
    )

private fun ceoStrategy(p: JsonObject) =
    ApprovalPayload.CeoStrategy(
        title = p.text("title"),
        plan = p.text("plan") ?: p.text("description") ?: p.text("strategy") ?: p.text("text"),
    )

private fun budgetOverride(p: JsonObject) =
    ApprovalPayload.BudgetOverride(
        scopeName = p.text("scopeName") ?: p.text("scopeType"),
        windowKind = p.text("windowKind"),
        limit = p.cents("budgetAmount")?.let(Money::usdCents),
        observed = p.cents("observedAmount")?.let(Money::usdCents),
        guidance = p.text("guidance"),
    )

private fun boardRequest(p: JsonObject) =
    ApprovalPayload.BoardRequest(
        title = p.text("title"),
        summary = p.text("summary"),
        recommendedAction = p.text("recommendedAction"),
        nextActionOnApproval = p.text("nextActionOnApproval"),
        risks = p.texts("risks"),
        proposedComment = p.text("proposedComment"),
    )
