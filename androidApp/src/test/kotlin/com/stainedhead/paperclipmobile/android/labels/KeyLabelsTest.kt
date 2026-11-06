package com.stainedhead.paperclipmobile.android.labels

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.diagnostics.GraphOpenOutcome
import com.stainedhead.paperclipmobile.android.diagnostics.KeyErrorClass
import com.stainedhead.paperclipmobile.android.diagnostics.PairingOutcome
import com.stainedhead.paperclipmobile.android.diagnostics.ServerCompatibility
import com.stainedhead.paperclipmobile.android.diagnostics.stringRes
import com.stainedhead.paperclipmobile.application.instance.InstanceNameIssue
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.AddressIssue
import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentGroupUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.ApprovalActionUi
import com.stainedhead.paperclipmobile.presentation.ApprovalStatusUi
import com.stainedhead.paperclipmobile.presentation.ApprovalTypeUi
import com.stainedhead.paperclipmobile.presentation.BudgetChoice
import com.stainedhead.paperclipmobile.presentation.BudgetOutcomeUi
import com.stainedhead.paperclipmobile.presentation.BudgetThresholdUi
import com.stainedhead.paperclipmobile.presentation.CommentAuthorUi
import com.stainedhead.paperclipmobile.presentation.CompanyGate
import com.stainedhead.paperclipmobile.presentation.DecideByKindUi
import com.stainedhead.paperclipmobile.presentation.DecisionFinalKind
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import com.stainedhead.paperclipmobile.presentation.EffectKindUi
import com.stainedhead.paperclipmobile.presentation.EffectStatusUi
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ExecutionStatusUi
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.FeedbackKind
import com.stainedhead.paperclipmobile.presentation.InboxGroupUi
import com.stainedhead.paperclipmobile.presentation.InlineActionUi
import com.stainedhead.paperclipmobile.presentation.InteractionKindUi
import com.stainedhead.paperclipmobile.presentation.InteractionPrimaryUi
import com.stainedhead.paperclipmobile.presentation.InteractionReadOnlyUi
import com.stainedhead.paperclipmobile.presentation.InteractionStatusUi
import com.stainedhead.paperclipmobile.presentation.InvalidKey
import com.stainedhead.paperclipmobile.presentation.IssueFilterUi
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueStatusUi
import com.stainedhead.paperclipmobile.presentation.IssueTab
import com.stainedhead.paperclipmobile.presentation.NotePromptKind
import com.stainedhead.paperclipmobile.presentation.PairedDevicesStatus
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import com.stainedhead.paperclipmobile.presentation.ProbeStatus
import com.stainedhead.paperclipmobile.presentation.RemovalEffect
import com.stainedhead.paperclipmobile.presentation.RoleUi
import com.stainedhead.paperclipmobile.presentation.RunStatusUi
import com.stainedhead.paperclipmobile.presentation.ServerKeyOutcome
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import com.stainedhead.paperclipmobile.presentation.SnoozePresetUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import com.stainedhead.paperclipmobile.presentation.SpendWindowUi
import com.stainedhead.paperclipmobile.presentation.StartupNotice
import com.stainedhead.paperclipmobile.presentation.VerdictUi
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AND-016, the Android twin of the String Catalog test: every entry of every key enum the shared core hands the app has
 * its own non-blank string, named `<namespace>_<entry>`. A new entry fails to compile (the mapping is an exhaustive
 * `when`); a mapping that points at another entry's text fails here.
 */
@RunWith(AndroidJUnit4::class)
class KeyLabelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun <K : Enum<K>> check(
        namespace: String,
        entries: List<K>,
        resource: (K) -> Int,
    ) {
        (entries.isNotEmpty()) shouldBe true
        for (entry in entries) {
            val id = resource(entry)
            val name = context.resources.getResourceEntryName(id)
            name shouldBe "${namespace}_${entry.name.toSnakeCase()}"
            context.getString(id).shouldNotBeBlank()
        }
    }

    @Test
    fun everyErrorKeyHasMessageText() = check("error", ErrorKey.entries, ErrorKey::stringRes)

    @Test
    fun everyActionsDisabledReasonHasText() =
        check("disabled", ActionsDisabledReason.entries, ActionsDisabledReason::stringRes)

    @Test
    fun everyAddressIssueHasText() = check("address", AddressIssue.entries, AddressIssue::stringRes)

    @Test
    fun everyStartupNoticeHasText() = check("startup", StartupNotice.entries, StartupNotice::stringRes)

    @Test
    fun everyDeviceAuthIssueHasText() = check("device_auth", DeviceAuthIssue.entries, DeviceAuthIssue::stringRes)

    @Test
    fun everySeverityHasText() = check("severity", SeverityUi.entries, SeverityUi::stringRes)

    @Test
    fun everyProbeStatusHasText() = check("probe", ProbeStatus.entries, ProbeStatus::stringRes)

    @Test
    fun everyProbeRefusalHasText() = check("refusal", ProbeRefusal.entries, ProbeRefusal::stringRes)

    @Test
    fun everyPairingStatusHasText() = check("pairing", PairingStatus.entries, PairingStatus::stringRes)

    @Test
    fun everyInstanceNameIssueHasText() = check("name_issue", InstanceNameIssue.entries, InstanceNameIssue::stringRes)

    @Test
    fun everyExposureHasText() = check("exposure", Exposure.entries, Exposure::stringRes)

    @Test
    fun everyRoleHasText() = check("role", RoleUi.entries, RoleUi::stringRes)

    @Test
    fun everyFactoryStatusHasText() = check("status", FactoryStatusUi.entries, FactoryStatusUi::stringRes)

    @Test
    fun everyServerKeyOutcomeHasText() = check("key_outcome", ServerKeyOutcome.entries, ServerKeyOutcome::stringRes)

    @Test
    fun everyRemovalEffectHasText() = check("removal", RemovalEffect.entries, RemovalEffect::stringRes)

    @Test
    fun everyIdlePeriodHasText() = check("idle", IdlePeriod.entries, IdlePeriod::stringRes)

    @Test
    fun everyCompanyGateHasTitle() = check("company_gate", CompanyGate.entries, CompanyGate::stringRes)

    @Test
    fun everyCompanyGateHasMessage() = check("company_gate_message", CompanyGate.entries, CompanyGate::messageRes)

    @Test
    fun everyPairedDevicesStatusHasText() =
        check("paired_devices", PairedDevicesStatus.entries, PairedDevicesStatus::stringRes)

    @Test
    fun everyDiagnosticsValueHasText() {
        check("graph_open", GraphOpenOutcome.entries, GraphOpenOutcome::stringRes)
        check("key_error", KeyErrorClass.entries, KeyErrorClass::stringRes)
        check("pairing_outcome", PairingOutcome.entries, PairingOutcome::stringRes)
        check("server_compat", ServerCompatibility.entries, ServerCompatibility::stringRes)
    }

    @Test
    fun everyInboxKeyHasText() {
        check("kind", SourceKindUi.entries, SourceKindUi::stringRes)
        check("kind_plural", SourceKindUi.entries, SourceKindUi::pluralStringRes)
        check("group", InboxGroupUi.entries, InboxGroupUi::stringRes)
        check("inline_action", InlineActionUi.entries, InlineActionUi::stringRes)
        check("snooze", SnoozePresetUi.entries, SnoozePresetUi::stringRes)
    }

    @Test
    fun everyFeedbackKeyHasText() {
        check("feedback", FeedbackKind.entries, FeedbackKind::stringRes)
        check("invalid", InvalidKey.entries, InvalidKey::stringRes)
    }

    @Test
    fun everyDecisionKeyHasText() {
        check("decide_by", DecideByKindUi.entries, DecideByKindUi::stringRes)
        check("effect", EffectKindUi.entries, EffectKindUi::stringRes)
        check("effect_status", EffectStatusUi.entries, EffectStatusUi::stringRes)
        check("execution", ExecutionStatusUi.entries, ExecutionStatusUi::stringRes)
        check("decision_final", DecisionFinalKind.entries, DecisionFinalKind::stringRes)
    }

    @Test
    fun everyApprovalKeyHasText() {
        check("approval_type", ApprovalTypeUi.entries, ApprovalTypeUi::stringRes)
        check("approval_status", ApprovalStatusUi.entries, ApprovalStatusUi::stringRes)
        check("approval_done", ApprovalStatusUi.entries, ApprovalStatusUi::doneStringRes)
        check("approval_action", ApprovalActionUi.entries, ApprovalActionUi::stringRes)
        check("note_prompt", NotePromptKind.entries, NotePromptKind::stringRes)
    }

    @Test
    fun everyBudgetKeyHasText() {
        check("budget_threshold", BudgetThresholdUi.entries, BudgetThresholdUi::stringRes)
        check("budget_choice", BudgetChoice.entries, BudgetChoice::stringRes)
        check("budget_outcome", BudgetOutcomeUi.entries, BudgetOutcomeUi::stringRes)
    }

    @Test
    fun everyOperationsKeyHasText() {
        check("agent_status", AgentStatusUi.entries, AgentStatusUi::stringRes)
        check("run_status", RunStatusUi.entries, RunStatusUi::stringRes)
        check("agent_group", AgentGroupUi.entries, AgentGroupUi::stringRes)
        check("agent_action", AgentActionUi.entries, AgentActionUi::stringRes)
        check("spend_window", SpendWindowUi.entries, SpendWindowUi::stringRes)
    }

    @Test
    fun everyWorkKeyHasText() {
        check("issue_status", IssueStatusUi.entries, IssueStatusUi::stringRes)
        check("issue_priority", IssuePriorityUi.entries, IssuePriorityUi::stringRes)
        check("issue_filter", IssueFilterUi.entries, IssueFilterUi::stringRes)
        check("issue_tab", IssueTab.entries, IssueTab::stringRes)
        check("comment_author", CommentAuthorUi.entries, CommentAuthorUi::stringRes)
        check("interaction_kind", InteractionKindUi.entries, InteractionKindUi::stringRes)
        check("interaction_status", InteractionStatusUi.entries, InteractionStatusUi::stringRes)
        check("interaction_read_only", InteractionReadOnlyUi.entries, InteractionReadOnlyUi::stringRes)
        check("interaction_primary", InteractionPrimaryUi.entries, InteractionPrimaryUi::stringRes)
        check("verdict", VerdictUi.entries, VerdictUi::stringRes)
    }
}
