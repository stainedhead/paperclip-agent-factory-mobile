package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.ApprovalAction
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.DestinationKind
import com.stainedhead.paperclipmobile.domain.EffectStatus
import com.stainedhead.paperclipmobile.domain.ExecutionStatus
import com.stainedhead.paperclipmobile.domain.RunEventLevel
import com.stainedhead.paperclipmobile.domain.RunStream

/*
 * Domain enum to UI twin as an exhaustive `when` with no `else`, so adding or reordering a domain value fails to
 * compile here instead of silently mapping to the wrong twin (F-B-11). UiEnumMappingTest checks the names agree.
 */

internal fun ApprovalAction.toUi(): ApprovalActionUi =
    when (this) {
        ApprovalAction.Approve -> ApprovalActionUi.Approve
        ApprovalAction.Reject -> ApprovalActionUi.Reject
        ApprovalAction.RequestRevision -> ApprovalActionUi.RequestRevision
        ApprovalAction.Comment -> ApprovalActionUi.Comment
    }

internal fun BudgetThreshold.toUi(): BudgetThresholdUi =
    when (this) {
        BudgetThreshold.Hard -> BudgetThresholdUi.Hard
        BudgetThreshold.Soft -> BudgetThresholdUi.Soft
    }

internal fun DestinationKind.toUi(): OpenKindUi =
    when (this) {
        DestinationKind.Decision -> OpenKindUi.Decision
        DestinationKind.Approval -> OpenKindUi.Approval
        DestinationKind.BudgetIncident -> OpenKindUi.BudgetIncident
        DestinationKind.Interaction -> OpenKindUi.Interaction
        DestinationKind.Issue -> OpenKindUi.Issue
        DestinationKind.Run -> OpenKindUi.Run
        DestinationKind.Agent -> OpenKindUi.Agent
        DestinationKind.WebOnly -> OpenKindUi.WebOnly
    }

internal fun ExecutionStatus.toUi(): ExecutionStatusUi =
    when (this) {
        ExecutionStatus.Running -> ExecutionStatusUi.Running
        ExecutionStatus.Succeeded -> ExecutionStatusUi.Succeeded
        ExecutionStatus.Partial -> ExecutionStatusUi.Partial
        ExecutionStatus.Failed -> ExecutionStatusUi.Failed
        ExecutionStatus.Unknown -> ExecutionStatusUi.Unknown
    }

internal fun EffectStatus.toUi(): EffectStatusUi =
    when (this) {
        EffectStatus.Claimed -> EffectStatusUi.Claimed
        EffectStatus.Executed -> EffectStatusUi.Executed
        EffectStatus.Skipped -> EffectStatusUi.Skipped
        EffectStatus.Failed -> EffectStatusUi.Failed
        EffectStatus.Unknown -> EffectStatusUi.Unknown
    }

internal fun RunStream.toUi(): RunStreamUi =
    when (this) {
        RunStream.System -> RunStreamUi.System
        RunStream.Stdout -> RunStreamUi.Stdout
        RunStream.Stderr -> RunStreamUi.Stderr
    }

internal fun RunEventLevel.toUi(): RunLevelUi =
    when (this) {
        RunEventLevel.Info -> RunLevelUi.Info
        RunEventLevel.Warn -> RunLevelUi.Warn
        RunEventLevel.Error -> RunLevelUi.Error
    }
