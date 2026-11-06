package com.stainedhead.paperclipmobile.android.labels

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.presentation.FeedbackKind
import com.stainedhead.paperclipmobile.presentation.FeedbackUi
import com.stainedhead.paperclipmobile.presentation.InboxGroupUi
import com.stainedhead.paperclipmobile.presentation.InlineActionUi
import com.stainedhead.paperclipmobile.presentation.InvalidKey
import com.stainedhead.paperclipmobile.presentation.SnoozePresetUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi

/*
 * The key enums of the Inbox and of what the last action said (AND-016): one exhaustive `when` each, so a new entry
 * does not compile until it has a string. `KeyLabelsTest` checks each mapped string exists and is the entry's own.
 */

/** The kind as a card names it ("Agent question"). */
@StringRes
fun SourceKindUi.stringRes(): Int =
    when (this) {
        SourceKindUi.Approval -> R.string.kind_approval
        SourceKindUi.Decision -> R.string.kind_decision
        SourceKindUi.IssueThreadInteraction -> R.string.kind_issue_thread_interaction
        SourceKindUi.JoinRequest -> R.string.kind_join_request
        SourceKindUi.RecoveryAction -> R.string.kind_recovery_action
        SourceKindUi.ProductivityReview -> R.string.kind_productivity_review
        SourceKindUi.BlockerAttention -> R.string.kind_blocker_attention
        SourceKindUi.Review -> R.string.kind_review
        SourceKindUi.FailedRun -> R.string.kind_failed_run
        SourceKindUi.BudgetAlert -> R.string.kind_budget_alert
        SourceKindUi.AgentErrorAlert -> R.string.kind_agent_error_alert
        SourceKindUi.Unsupported -> R.string.kind_unsupported
    }

/** The kind as a filter chip names it, in the plural ("Questions"). */
@StringRes
fun SourceKindUi.pluralStringRes(): Int =
    when (this) {
        SourceKindUi.Approval -> R.string.kind_plural_approval
        SourceKindUi.Decision -> R.string.kind_plural_decision
        SourceKindUi.IssueThreadInteraction -> R.string.kind_plural_issue_thread_interaction
        SourceKindUi.JoinRequest -> R.string.kind_plural_join_request
        SourceKindUi.RecoveryAction -> R.string.kind_plural_recovery_action
        SourceKindUi.ProductivityReview -> R.string.kind_plural_productivity_review
        SourceKindUi.BlockerAttention -> R.string.kind_plural_blocker_attention
        SourceKindUi.Review -> R.string.kind_plural_review
        SourceKindUi.FailedRun -> R.string.kind_plural_failed_run
        SourceKindUi.BudgetAlert -> R.string.kind_plural_budget_alert
        SourceKindUi.AgentErrorAlert -> R.string.kind_plural_agent_error_alert
        SourceKindUi.Unsupported -> R.string.kind_plural_unsupported
    }

@StringRes
fun InboxGroupUi.stringRes(): Int =
    when (this) {
        InboxGroupUi.DecideNow -> R.string.group_decide_now
        InboxGroupUi.ThisWeek -> R.string.group_this_week
        InboxGroupUi.Whenever -> R.string.group_whenever
    }

@StringRes
fun InlineActionUi.stringRes(): Int =
    when (this) {
        InlineActionUi.Approve -> R.string.inline_action_approve
        InlineActionUi.Reject -> R.string.inline_action_reject
        InlineActionUi.KeepPaused -> R.string.inline_action_keep_paused
        InlineActionUi.RaiseAndResume -> R.string.inline_action_raise_and_resume
    }

@StringRes
fun SnoozePresetUi.stringRes(): Int =
    when (this) {
        SnoozePresetUi.OneHour -> R.string.snooze_one_hour
        SnoozePresetUi.Tonight -> R.string.snooze_tonight
        SnoozePresetUi.Tomorrow -> R.string.snooze_tomorrow
        SnoozePresetUi.NextWeek -> R.string.snooze_next_week
    }

@StringRes
fun FeedbackKind.stringRes(): Int =
    when (this) {
        FeedbackKind.NotAllowed -> R.string.feedback_not_allowed
        FeedbackKind.Invalid -> R.string.feedback_invalid
        FeedbackKind.DeviceAuth -> R.string.feedback_device_auth
        FeedbackKind.Failed -> R.string.feedback_failed
        FeedbackKind.Reconciled -> R.string.feedback_reconciled
        FeedbackKind.Unknown -> R.string.feedback_unknown
    }

@StringRes
fun InvalidKey.stringRes(): Int =
    when (this) {
        InvalidKey.NoteRequired -> R.string.invalid_note_required
        InvalidKey.BodyRequired -> R.string.invalid_body_required
        InvalidKey.WrongStatus -> R.string.invalid_wrong_status
        InvalidKey.UnknownOption -> R.string.invalid_unknown_option
        InvalidKey.SnoozeNotInFuture -> R.string.invalid_snooze_not_in_future
        InvalidKey.UndoWindowClosed -> R.string.invalid_undo_window_closed
        InvalidKey.InputRequired -> R.string.invalid_input_required
        InvalidKey.InputTooLong -> R.string.invalid_input_too_long
        InvalidKey.AmountMustExceedObserved -> R.string.invalid_amount_must_exceed_observed
        InvalidKey.WrongCurrency -> R.string.invalid_wrong_currency
        InvalidKey.AmountNotNumeric -> R.string.invalid_amount_not_numeric
        InvalidKey.AmountNotPositive -> R.string.invalid_amount_not_positive
        InvalidKey.AmountTooLarge -> R.string.invalid_amount_too_large
        InvalidKey.AmountTooManyDecimals -> R.string.invalid_amount_too_many_decimals
        InvalidKey.TitleRequired -> R.string.invalid_title_required
        InvalidKey.NotAddressedToYou -> R.string.invalid_not_addressed_to_you
        InvalidKey.AnswerUnsupported -> R.string.invalid_answer_unsupported
        InvalidKey.AnswerBlocked -> R.string.invalid_answer_blocked
        InvalidKey.AnswerWrongKind -> R.string.invalid_answer_wrong_kind
        InvalidKey.AnswerNothingSelected -> R.string.invalid_answer_nothing_selected
        InvalidKey.AnswerUnknownChoice -> R.string.invalid_answer_unknown_choice
        InvalidKey.AnswerTooFewSelected -> R.string.invalid_answer_too_few_selected
        InvalidKey.AnswerTooManySelected -> R.string.invalid_answer_too_many_selected
        InvalidKey.AnswerQuestionRequired -> R.string.invalid_answer_question_required
        InvalidKey.AnswerVerdictNotEnabled -> R.string.invalid_answer_verdict_not_enabled
        InvalidKey.AnswerReasonRequired -> R.string.invalid_answer_reason_required
    }

/**
 * What the last action said, in words: the device-owner issue, the invalid field, or the kind followed by the error
 * (with the server's own reason for a rejection), so a person sees why and not only that it failed.
 */
@Composable
fun FeedbackUi.message(): String {
    deviceAuth?.let { return stringResource(it.stringRes()) }
    invalid?.let { return stringResource(it.stringRes()) }
    val base = stringResource(kind.stringRes())
    val failure = error ?: return base
    return "$base ${failure.message()}"
}
