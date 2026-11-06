package com.stainedhead.paperclipmobile.android.labels

import androidx.annotation.StringRes
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.presentation.ApprovalActionUi
import com.stainedhead.paperclipmobile.presentation.ApprovalStatusUi
import com.stainedhead.paperclipmobile.presentation.ApprovalTypeUi
import com.stainedhead.paperclipmobile.presentation.NotePromptKind

// The key enums of the Approval screen (AND-016): one exhaustive `when` each.

@StringRes
fun ApprovalTypeUi.stringRes(): Int =
    when (this) {
        ApprovalTypeUi.HireAgent -> R.string.approval_type_hire_agent
        ApprovalTypeUi.ApproveCeoStrategy -> R.string.approval_type_approve_ceo_strategy
        ApprovalTypeUi.BudgetOverrideRequired -> R.string.approval_type_budget_override_required
        ApprovalTypeUi.RequestBoardApproval -> R.string.approval_type_request_board_approval
        ApprovalTypeUi.Unsupported -> R.string.approval_type_unsupported
    }

@StringRes
fun ApprovalStatusUi.stringRes(): Int =
    when (this) {
        ApprovalStatusUi.Pending -> R.string.approval_status_pending
        ApprovalStatusUi.RevisionRequested -> R.string.approval_status_revision_requested
        ApprovalStatusUi.Approved -> R.string.approval_status_approved
        ApprovalStatusUi.Rejected -> R.string.approval_status_rejected
        ApprovalStatusUi.Cancelled -> R.string.approval_status_cancelled
        ApprovalStatusUi.Unknown -> R.string.approval_status_unknown
    }

/** What a resolved or waiting approval says about itself. */
@StringRes
fun ApprovalStatusUi.doneStringRes(): Int =
    when (this) {
        ApprovalStatusUi.Pending -> R.string.approval_done_pending
        ApprovalStatusUi.RevisionRequested -> R.string.approval_done_revision_requested
        ApprovalStatusUi.Approved -> R.string.approval_done_approved
        ApprovalStatusUi.Rejected -> R.string.approval_done_rejected
        ApprovalStatusUi.Cancelled -> R.string.approval_done_cancelled
        ApprovalStatusUi.Unknown -> R.string.approval_done_unknown
    }

@StringRes
fun ApprovalActionUi.stringRes(): Int =
    when (this) {
        ApprovalActionUi.Approve -> R.string.approval_action_approve
        ApprovalActionUi.Reject -> R.string.approval_action_reject
        ApprovalActionUi.RequestRevision -> R.string.approval_action_request_revision
        ApprovalActionUi.Comment -> R.string.approval_action_comment
    }

@StringRes
fun NotePromptKind.stringRes(): Int =
    when (this) {
        NotePromptKind.Reject -> R.string.note_prompt_reject
        NotePromptKind.RequestRevision -> R.string.note_prompt_request_revision
    }
