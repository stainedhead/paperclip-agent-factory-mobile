package com.stainedhead.paperclipmobile.android.labels

import androidx.annotation.StringRes
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.presentation.BudgetChoice
import com.stainedhead.paperclipmobile.presentation.BudgetOutcomeUi
import com.stainedhead.paperclipmobile.presentation.BudgetThresholdUi

// The key enums of the Budget incident sheet (AND-016): one exhaustive `when` each.

@StringRes
fun BudgetThresholdUi.stringRes(): Int =
    when (this) {
        BudgetThresholdUi.Hard -> R.string.budget_threshold_hard
        BudgetThresholdUi.Soft -> R.string.budget_threshold_soft
    }

@StringRes
fun BudgetChoice.stringRes(): Int =
    when (this) {
        BudgetChoice.KeepPaused -> R.string.budget_choice_keep_paused
        BudgetChoice.RaiseAndResume -> R.string.budget_choice_raise_and_resume
    }

@StringRes
fun BudgetOutcomeUi.stringRes(): Int =
    when (this) {
        BudgetOutcomeUi.KeptPaused -> R.string.budget_outcome_kept_paused
        BudgetOutcomeUi.Raised -> R.string.budget_outcome_raised
        BudgetOutcomeUi.NoLongerActive -> R.string.budget_outcome_no_longer_active
    }
