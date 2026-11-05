package com.stainedhead.paperclipmobile.android.labels

import androidx.annotation.StringRes
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.presentation.CommentAuthorUi
import com.stainedhead.paperclipmobile.presentation.InteractionKindUi
import com.stainedhead.paperclipmobile.presentation.InteractionPrimaryUi
import com.stainedhead.paperclipmobile.presentation.InteractionReadOnlyUi
import com.stainedhead.paperclipmobile.presentation.InteractionStatusUi
import com.stainedhead.paperclipmobile.presentation.IssueFilterUi
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueStatusUi
import com.stainedhead.paperclipmobile.presentation.IssueTab
import com.stainedhead.paperclipmobile.presentation.VerdictUi

// The key enums of the Work list and the Issue screen (AND-016): one exhaustive `when` each, checked by
// `KeyLabelsTest`.

@StringRes
fun IssueStatusUi.stringRes(): Int =
    when (this) {
        IssueStatusUi.Backlog -> R.string.issue_status_backlog
        IssueStatusUi.Todo -> R.string.issue_status_todo
        IssueStatusUi.InProgress -> R.string.issue_status_in_progress
        IssueStatusUi.InReview -> R.string.issue_status_in_review
        IssueStatusUi.Done -> R.string.issue_status_done
        IssueStatusUi.Blocked -> R.string.issue_status_blocked
        IssueStatusUi.Cancelled -> R.string.issue_status_cancelled
        IssueStatusUi.Unknown -> R.string.issue_status_unknown
    }

@StringRes
fun IssuePriorityUi.stringRes(): Int =
    when (this) {
        IssuePriorityUi.Critical -> R.string.issue_priority_critical
        IssuePriorityUi.High -> R.string.issue_priority_high
        IssuePriorityUi.Medium -> R.string.issue_priority_medium
        IssuePriorityUi.Low -> R.string.issue_priority_low
        IssuePriorityUi.Unknown -> R.string.issue_priority_unknown
    }

@StringRes
fun IssueFilterUi.stringRes(): Int =
    when (this) {
        IssueFilterUi.AllOpen -> R.string.issue_filter_all_open
        IssueFilterUi.Blocked -> R.string.issue_filter_blocked
        IssueFilterUi.CriticalAndHigh -> R.string.issue_filter_critical_and_high
        IssueFilterUi.InReview -> R.string.issue_filter_in_review
        IssueFilterUi.AssignedToMe -> R.string.issue_filter_assigned_to_me
    }

@StringRes
fun IssueTab.stringRes(): Int =
    when (this) {
        IssueTab.Thread -> R.string.issue_tab_thread
        IssueTab.Plan -> R.string.issue_tab_plan
        IssueTab.Runs -> R.string.issue_tab_runs
        IssueTab.Files -> R.string.issue_tab_files
    }

@StringRes
fun CommentAuthorUi.stringRes(): Int =
    when (this) {
        CommentAuthorUi.User -> R.string.comment_author_user
        CommentAuthorUi.Agent -> R.string.comment_author_agent
        CommentAuthorUi.System -> R.string.comment_author_system
    }

@StringRes
fun InteractionKindUi.stringRes(): Int =
    when (this) {
        InteractionKindUi.SuggestTasks -> R.string.interaction_kind_suggest_tasks
        InteractionKindUi.AskUserQuestions -> R.string.interaction_kind_ask_user_questions
        InteractionKindUi.RequestConfirmation -> R.string.interaction_kind_request_confirmation
        InteractionKindUi.RequestCheckboxConfirmation -> R.string.interaction_kind_request_checkbox_confirmation
        InteractionKindUi.RequestItemVerdicts -> R.string.interaction_kind_request_item_verdicts
        InteractionKindUi.Unsupported -> R.string.interaction_kind_unsupported
    }

@StringRes
fun InteractionStatusUi.stringRes(): Int =
    when (this) {
        InteractionStatusUi.Pending -> R.string.interaction_status_pending
        InteractionStatusUi.Accepted -> R.string.interaction_status_accepted
        InteractionStatusUi.Rejected -> R.string.interaction_status_rejected
        InteractionStatusUi.Answered -> R.string.interaction_status_answered
        InteractionStatusUi.Cancelled -> R.string.interaction_status_cancelled
        InteractionStatusUi.Expired -> R.string.interaction_status_expired
        InteractionStatusUi.Failed -> R.string.interaction_status_failed
        InteractionStatusUi.Unknown -> R.string.interaction_status_unknown
    }

@StringRes
fun InteractionReadOnlyUi.stringRes(): Int =
    when (this) {
        InteractionReadOnlyUi.NotPending -> R.string.interaction_read_only_not_pending
        InteractionReadOnlyUi.NotForYou -> R.string.interaction_read_only_not_for_you
        InteractionReadOnlyUi.Blocked -> R.string.interaction_read_only_blocked
        InteractionReadOnlyUi.ViewerRole -> R.string.interaction_read_only_viewer_role
        InteractionReadOnlyUi.Offline -> R.string.interaction_read_only_offline
        InteractionReadOnlyUi.Unsupported -> R.string.interaction_read_only_unsupported
    }

@StringRes
fun InteractionPrimaryUi.stringRes(): Int =
    when (this) {
        InteractionPrimaryUi.Accept -> R.string.interaction_primary_accept
        InteractionPrimaryUi.Respond -> R.string.interaction_primary_respond
        InteractionPrimaryUi.SubmitVerdicts -> R.string.interaction_primary_submit_verdicts
    }

@StringRes
fun VerdictUi.stringRes(): Int =
    when (this) {
        VerdictUi.Approve -> R.string.verdict_approve
        VerdictUi.Reject -> R.string.verdict_reject
        VerdictUi.Defer -> R.string.verdict_defer
    }
