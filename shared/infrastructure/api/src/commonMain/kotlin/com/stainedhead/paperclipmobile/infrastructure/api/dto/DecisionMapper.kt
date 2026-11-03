package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.DecisionEffect
import com.stainedhead.paperclipmobile.domain.DecisionInput
import com.stainedhead.paperclipmobile.domain.DecisionOption
import com.stainedhead.paperclipmobile.domain.DecisionOutcome
import com.stainedhead.paperclipmobile.domain.EffectResult
import com.stainedhead.paperclipmobile.domain.EffectStaleness
import com.stainedhead.paperclipmobile.domain.EffectStatus
import com.stainedhead.paperclipmobile.domain.ExecutionStatus
import com.stainedhead.paperclipmobile.domain.OptionStyle
import kotlinx.serialization.json.JsonObject

// Option, effect, input and execution shapes: packages/shared/src/types/decision.ts:6-78,
// packages/shared/src/validators/decision.ts:89-139, packages/db/src/schema/decisions.ts:100-114.

/** An option needs an id and a label; its effects are read leniently. Null when the option cannot be shown. */
internal fun decisionOption(raw: JsonObject): DecisionOption? {
    val id = raw.text("id")
    val label = raw.text("label")
    if (id == null || label == null) return null
    return DecisionOption(
        id = id,
        label = label,
        description = raw.text("description"),
        style = OptionStyle.fromServer(raw.text("style")),
        effects = raw.objects("effects").map { decisionEffect(it) },
    )
}

internal fun decisionInput(raw: JsonObject): DecisionInput? {
    val id = raw.text("id")
    val label = raw.text("label")
    if (id == null || label == null) return null
    return DecisionInput(
        id = id,
        label = label,
        placeholder = raw.text("placeholder"),
        required = raw.flag("required") == true,
        maxLength = raw.whole("maxLength"),
    )
}

/**
 * An effect by its `type`. A known type is always mapped, even with fields missing, so a `cancel_issue_tree` can never
 * be hidden as an unknown effect and escape the destructive check. Staleness defaults to strict.
 */
internal fun decisionEffect(raw: JsonObject): DecisionEffect {
    val type = raw.text("type").orEmpty()
    val target = raw.text("targetIssueId").orEmpty()
    val staleness = if (raw.text("staleness") == "lenient") EffectStaleness.Lenient else EffectStaleness.Strict
    return when (type) {
        "comment_on_issue" -> {
            DecisionEffect.CommentOnIssue(target, staleness, raw.text("bodyMarkdown").orEmpty())
        }

        "create_issue" -> {
            DecisionEffect.CreateIssue(target, staleness, raw.obj("draft")?.text("title").orEmpty())
        }

        "update_issue_status" -> {
            DecisionEffect.UpdateIssueStatus(target, staleness, raw.text("status").orEmpty())
        }

        // The effect names the assignee by id only; resolving a name needs the agent list (deferred, ADR-058).
        "assign_issue" -> {
            DecisionEffect.AssignIssue(target, staleness, null)
        }

        "cancel_issue_tree" -> {
            DecisionEffect.CancelIssueTree(target, raw.text("reasonComment").orEmpty())
        }

        "resolve_blocker" -> {
            DecisionEffect.ResolveBlocker(target, staleness, raw.texts("removeBlockedByIssueIds").size)
        }

        else -> {
            DecisionEffect.Unsupported(type, target)
        }
    }
}

/** Null while the decision is open; otherwise the execution status and one result per effect row. */
internal fun decisionOutcome(
    executionStatus: String?,
    rows: List<JsonObject>,
): DecisionOutcome? {
    val status = ExecutionStatus.fromServer(executionStatus)
    if (status == null && rows.isEmpty()) return null
    val results =
        rows.mapIndexed { position, row ->
            EffectResult(
                index = row.whole("effectIndex") ?: position,
                effectType = row.text("effectType").orEmpty(),
                status = EffectStatus.fromServer(row.text("status").orEmpty()),
                // The reason code is the row's `error` (services/decisions.ts:441-445), for example target_changed.
                reason = row.text("error") ?: row.obj("result")?.text("reason"),
            )
        }
    return DecisionOutcome(status, results)
}
