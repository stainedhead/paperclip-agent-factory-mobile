package com.stainedhead.paperclipmobile.android.labels

import androidx.annotation.StringRes
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.presentation.DecideByKindUi
import com.stainedhead.paperclipmobile.presentation.DecisionFinalKind
import com.stainedhead.paperclipmobile.presentation.EffectKindUi
import com.stainedhead.paperclipmobile.presentation.EffectStatusUi
import com.stainedhead.paperclipmobile.presentation.ExecutionStatusUi

/*
 * The key enums of the Decision screen (AND-016): one exhaustive `when` each. The effect lines take the server's own
 * detail (a comment excerpt, an issue title, a status, a name) as their one argument.
 */

@StringRes
fun DecideByKindUi.stringRes(): Int =
    when (this) {
        DecideByKindUi.Today -> R.string.decide_by_today
        DecideByKindUi.ThisWeek -> R.string.decide_by_this_week
        DecideByKindUi.Whenever -> R.string.decide_by_whenever
        DecideByKindUi.OnDate -> R.string.decide_by_on_date
        DecideByKindUi.None -> R.string.decide_by_none
    }

@StringRes
fun EffectKindUi.stringRes(): Int =
    when (this) {
        EffectKindUi.CommentOnIssue -> R.string.effect_comment_on_issue
        EffectKindUi.CreateIssue -> R.string.effect_create_issue
        EffectKindUi.UpdateIssueStatus -> R.string.effect_update_issue_status
        EffectKindUi.AssignIssue -> R.string.effect_assign_issue
        EffectKindUi.CancelIssueTree -> R.string.effect_cancel_issue_tree
        EffectKindUi.ResolveBlocker -> R.string.effect_resolve_blocker
        EffectKindUi.Unsupported -> R.string.effect_unsupported
    }

@StringRes
fun EffectStatusUi.stringRes(): Int =
    when (this) {
        EffectStatusUi.Claimed -> R.string.effect_status_claimed
        EffectStatusUi.Executed -> R.string.effect_status_executed
        EffectStatusUi.Skipped -> R.string.effect_status_skipped
        EffectStatusUi.Failed -> R.string.effect_status_failed
        EffectStatusUi.Unknown -> R.string.effect_status_unknown
    }

@StringRes
fun ExecutionStatusUi.stringRes(): Int =
    when (this) {
        ExecutionStatusUi.Running -> R.string.execution_running
        ExecutionStatusUi.Succeeded -> R.string.execution_succeeded
        ExecutionStatusUi.Partial -> R.string.execution_partial
        ExecutionStatusUi.Failed -> R.string.execution_failed
        ExecutionStatusUi.Unknown -> R.string.execution_unknown
    }

@StringRes
fun DecisionFinalKind.stringRes(): Int =
    when (this) {
        DecisionFinalKind.Decided -> R.string.decision_final_decided
        DecisionFinalKind.Dismissed -> R.string.decision_final_dismissed
        DecisionFinalKind.Expired -> R.string.decision_final_expired
        DecisionFinalKind.Cancelled -> R.string.decision_final_cancelled
        DecisionFinalKind.Unknown -> R.string.decision_final_unknown
    }
