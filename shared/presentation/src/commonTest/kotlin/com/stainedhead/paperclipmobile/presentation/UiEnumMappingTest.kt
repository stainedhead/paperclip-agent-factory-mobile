package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.AgentGroup
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.ApprovalAction
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.DestinationKind
import com.stainedhead.paperclipmobile.domain.EffectStatus
import com.stainedhead.paperclipmobile.domain.ExecutionStatus
import com.stainedhead.paperclipmobile.domain.InboxGroup
import com.stainedhead.paperclipmobile.domain.InlineAction
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.IssueFilter
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.RunEventLevel
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.RunStream
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.domain.SnoozePreset
import com.stainedhead.paperclipmobile.domain.Verdict
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** F-B-11: every domain enum maps to the UI twin of the same name, and the twin maps back where it must. */
class UiEnumMappingTest {
    private fun <D : Enum<D>, U : Enum<U>> names(
        domain: Iterable<D>,
        toUi: (D) -> U,
    ) = domain.map { it.name to toUi(it).name }

    private fun <D : Enum<D>> same(
        domain: Iterable<D>,
        mapped: List<Pair<String, String>>,
    ) {
        mapped shouldBe domain.map { it.name to it.name }
    }

    @Test
    fun `each domain enum maps to the twin with the same name`() {
        same(AgentGroup.entries, names(AgentGroup.entries) { it.toUi() })
        same(AgentStatus.entries, names(AgentStatus.entries) { it.toUi() })
        same(ApprovalAction.entries, names(ApprovalAction.entries) { it.toUi() })
        same(ApprovalStatus.entries, names(ApprovalStatus.entries) { it.toUi() })
        same(BudgetThreshold.entries, names(BudgetThreshold.entries) { it.toUi() })
        same(DestinationKind.entries, names(DestinationKind.entries) { it.toUi() })
        same(EffectStatus.entries, names(EffectStatus.entries) { it.toUi() })
        same(ExecutionStatus.entries, names(ExecutionStatus.entries) { it.toUi() })
        same(InboxGroup.entries, names(InboxGroup.entries) { it.toUi() })
        same(InlineAction.entries, names(InlineAction.entries) { it.toUi() })
        same(InteractionStatus.entries, names(InteractionStatus.entries) { it.toUi() })
        same(IssueFilter.entries, names(IssueFilter.entries) { it.toUi() })
        same(IssuePriority.entries, names(IssuePriority.entries) { it.toUi() })
        same(IssueStatus.entries, names(IssueStatus.entries) { it.toUi() })
        same(ResolverPolicy.entries, names(ResolverPolicy.entries) { it.toUi() })
        same(RunEventLevel.entries, names(RunEventLevel.entries) { it.toUi() })
        same(RunStatus.entries, names(RunStatus.entries) { it.toUi() })
        same(RunStream.entries, names(RunStream.entries) { it.toUi() })
        same(Severity.entries, names(Severity.entries) { it.toUi() })
        same(SnoozePreset.entries, names(SnoozePreset.entries) { it.toUi() })
        same(Verdict.entries, names(Verdict.entries) { it.toUi() })
    }

    @Test
    fun `the twins that map back return the original value`() {
        IssueFilter.entries.forEach { it.toUi().toDomain() shouldBe it }
        IssuePriority.entries.forEach { it.toUi().toDomain() shouldBe it }
        IssueStatus.entries.forEach { it.toUi().toDomain() shouldBe it }
        SnoozePreset.entries.forEach { it.toUi().toDomain() shouldBe it }
        Verdict.entries.forEach { it.toUi().toDomain() shouldBe it }
    }
}
