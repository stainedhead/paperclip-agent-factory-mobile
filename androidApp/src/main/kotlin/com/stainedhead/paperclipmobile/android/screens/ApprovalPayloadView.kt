package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.CardGroup
import com.stainedhead.paperclipmobile.android.designsystem.CardRow
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.presentation.ApprovalPayloadUi

/** A titled value of a payload; a missing value is left out of the screen, never drawn as a blank. */
private class PayloadField(
    val title: String,
    val value: String?,
    val key: String,
)

/**
 * The payload of an approval as the mock shows it: label and value rows in one grouped card, then any long text below.
 * Which fields exist for which type is the shared state's; this only lays them out.
 */
@Composable
fun ApprovalPayloadView(
    payload: ApprovalPayloadUi?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.roomy)) {
        when (payload) {
            is ApprovalPayloadUi.HireAgent -> {
                HirePayload(payload)
            }

            is ApprovalPayloadUi.CeoStrategy -> {
                Blocks(listOf(PayloadField(stringResource(R.string.approval_plan), payload.plan, "plan")))
            }

            is ApprovalPayloadUi.BudgetOverride -> {
                BudgetPayload(payload)
            }

            is ApprovalPayloadUi.BoardRequest -> {
                BoardPayload(payload)
            }

            is ApprovalPayloadUi.Unsupported -> {
                CardSurface(Modifier.testTag("approval-unsupported")) {
                    Text(
                        stringResource(R.string.approval_unsupported),
                        style = AppType.body,
                        color = AppTheme.colors.textPrimary,
                    )
                }
            }

            null -> {
                Unit
            }
        }
    }
}

@Composable
private fun HirePayload(hire: ApprovalPayloadUi.HireAgent) {
    Rows(
        listOf(
            PayloadField(stringResource(R.string.approval_name), hire.name, "name"),
            PayloadField(stringResource(R.string.approval_role), roleName(hire.role), "role"),
            PayloadField(stringResource(R.string.approval_job_title), hire.title, "title"),
            PayloadField(
                stringResource(R.string.approval_reports_to),
                hire.reportsToName ?: hire.reportsToAgentId,
                "reports-to",
            ),
            PayloadField(stringResource(R.string.approval_adapter), hire.adapterType, "adapter"),
            PayloadField(stringResource(R.string.approval_monthly_budget), hire.monthlyBudget?.text(), "budget"),
            PayloadField(
                stringResource(R.string.approval_skills),
                hire.skills.takeIf { it.isNotEmpty() }?.joinToString(", "),
                "skills",
            ),
        ),
    )
    Blocks(listOf(PayloadField(stringResource(R.string.approval_capabilities), hire.capabilities, "capabilities")))
}

@Composable
private fun BudgetPayload(budget: ApprovalPayloadUi.BudgetOverride) {
    Rows(
        listOf(
            PayloadField(stringResource(R.string.approval_scope), budget.scopeName, "scope"),
            PayloadField(stringResource(R.string.approval_window), budget.windowKind?.let(::prettify), "window"),
            PayloadField(stringResource(R.string.approval_limit), budget.limit?.text(), "limit"),
            PayloadField(stringResource(R.string.approval_observed), budget.observed?.text(), "observed"),
        ),
    )
    Blocks(listOf(PayloadField(stringResource(R.string.approval_guidance), budget.guidance, "guidance")))
}

@Composable
private fun BoardPayload(board: ApprovalPayloadUi.BoardRequest) {
    Blocks(
        listOf(
            PayloadField(stringResource(R.string.approval_summary), board.summary, "summary"),
            PayloadField(stringResource(R.string.approval_recommended), board.recommendedAction, "recommended"),
            PayloadField(stringResource(R.string.approval_next_on_approval), board.nextActionOnApproval, "next"),
            PayloadField(stringResource(R.string.approval_risks), bullets(board.risks), "risks"),
            PayloadField(stringResource(R.string.approval_proposed_comment), board.proposedComment, "proposed-comment"),
        ),
    )
}

/** Rows of one grouped card, separated by hairlines. */
@Composable
private fun Rows(items: List<PayloadField>) {
    val present = items.filter { it.value != null }
    if (present.isEmpty()) return
    CardGroup {
        present.forEachIndexed { index, item ->
            if (index > 0) HairlineDivider()
            CardRow(item.title, Modifier.testTag("approval-row-${item.key}").semantics(mergeDescendants = true) {}) {
                Text(
                    item.value.orEmpty(),
                    style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = AppTheme.colors.textPrimary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Long text (a plan, a summary) under its heading. */
@Composable
private fun Blocks(items: List<PayloadField>) {
    for (item in items) {
        val text = item.value
        if (text.isNullOrEmpty()) continue
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            SectionHeader(item.title)
            CardSurface(Modifier.testTag("approval-block-${item.key}")) {
                Text(text, style = AppType.body, color = AppTheme.colors.textPrimary)
            }
        }
    }
}
