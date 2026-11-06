package com.stainedhead.paperclipmobile.android.labels

import androidx.annotation.StringRes
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentGroupUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.RunStatusUi
import com.stainedhead.paperclipmobile.presentation.SpendWindowUi

// The key enums of agents, runs and spend (AND-016): one exhaustive `when` each, checked by `KeyLabelsTest`.

@StringRes
fun AgentStatusUi.stringRes(): Int =
    when (this) {
        AgentStatusUi.Active -> R.string.agent_status_active
        AgentStatusUi.Paused -> R.string.agent_status_paused
        AgentStatusUi.Idle -> R.string.agent_status_idle
        AgentStatusUi.Running -> R.string.agent_status_running
        AgentStatusUi.Error -> R.string.agent_status_error
        AgentStatusUi.PendingApproval -> R.string.agent_status_pending_approval
        AgentStatusUi.Terminated -> R.string.agent_status_terminated
        AgentStatusUi.Unknown -> R.string.agent_status_unknown
    }

@StringRes
fun RunStatusUi.stringRes(): Int =
    when (this) {
        RunStatusUi.Queued -> R.string.run_status_queued
        RunStatusUi.ScheduledRetry -> R.string.run_status_scheduled_retry
        RunStatusUi.Running -> R.string.run_status_running
        RunStatusUi.Succeeded -> R.string.run_status_succeeded
        RunStatusUi.Interrupted -> R.string.run_status_interrupted
        RunStatusUi.Failed -> R.string.run_status_failed
        RunStatusUi.Cancelled -> R.string.run_status_cancelled
        RunStatusUi.TimedOut -> R.string.run_status_timed_out
        RunStatusUi.Unknown -> R.string.run_status_unknown
    }

@StringRes
fun AgentGroupUi.stringRes(): Int =
    when (this) {
        AgentGroupUi.NeedsAttention -> R.string.agent_group_needs_attention
        AgentGroupUi.Running -> R.string.agent_group_running
        AgentGroupUi.Paused -> R.string.agent_group_paused
        AgentGroupUi.Idle -> R.string.agent_group_idle
    }

@StringRes
fun AgentActionUi.stringRes(): Int =
    when (this) {
        AgentActionUi.Pause -> R.string.agent_action_pause
        AgentActionUi.Resume -> R.string.agent_action_resume
        AgentActionUi.RunNow -> R.string.agent_action_run_now
    }

@StringRes
fun SpendWindowUi.stringRes(): Int =
    when (this) {
        SpendWindowUi.Last5Hours -> R.string.spend_window_last5_hours
        SpendWindowUi.Last24Hours -> R.string.spend_window_last24_hours
        SpendWindowUi.Last7Days -> R.string.spend_window_last7_days
    }
