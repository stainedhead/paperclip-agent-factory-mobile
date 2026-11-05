package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.asksDeviceOwner
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.AgentDetailIntent
import com.stainedhead.paperclipmobile.presentation.AgentDetailState

private const val DEFAULT_CURRENCY = "USD"

/**
 * "Set monthly budget" for an owner: one amount in the budget's currency. The text is read, validated and sent by the
 * shared holder (with the locale's separator and the currency's digits); the sheet closes only when the holder says
 * the budget was saved, so nothing typed is lost on a failure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSheet(
    state: AgentDetailState,
    send: (AgentDetailIntent) -> Unit,
    onClose: () -> Unit,
) {
    val format = MoneyInputFormat.of(state.monthlyBudget?.currency ?: DEFAULT_CURRENCY)
    val saved = remember { state.budgetSavedCount }
    LaunchedEffect(state.budgetSavedCount) { if (state.budgetSavedCount != saved) onClose() }
    LaunchedEffect(
        Unit,
    ) { send(AgentDetailIntent.BudgetAmountTyped("", format.decimalSeparator, format.fractionDigits)) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        BudgetForm(state, send, onClose, format)
    }
}

@Composable
private fun BudgetForm(
    state: AgentDetailState,
    send: (AgentDetailIntent) -> Unit,
    onClose: () -> Unit,
    format: MoneyInputFormat,
) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier
            .padding(Spacing.page)
            .navigationBarsPadding()
            .imePadding()
            .testTag("agent-budget-form"),
        verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
    ) {
        Text(
            stringResource(R.string.agent_detail_budget_title, state.agent?.name.orEmpty()),
            Modifier.semantics { heading() },
            style = AppType.title,
            color = AppTheme.colors.textPrimary,
        )
        FormField(
            title = stringResource(R.string.agent_detail_budget_field),
            value = text,
            onValueChange = {
                text = it
                send(AgentDetailIntent.BudgetAmountTyped(it, format.decimalSeparator, format.fractionDigits))
            },
            tag = "agent-budget-amount",
            placeholder = stringResource(R.string.agent_detail_budget_placeholder),
            help =
                stringResource(
                    R.string.agent_detail_budget_help,
                    state.monthlyBudget?.currency ?: DEFAULT_CURRENCY,
                ),
            issue = state.budgetAmountIssue?.let { stringResource(it.stringRes()) },
            keyboardType = KeyboardType.Decimal,
            capitalization = KeyboardCapitalization.None,
        )
        val save = stringResource(R.string.agent_detail_budget_save)
        AppButton(
            save,
            { send(AgentDetailIntent.BudgetSubmitted) },
            Modifier.asksDeviceOwner(save, state.saveBudgetNeedsDeviceAuth).testTag("agent-budget-save"),
            enabled = state.canSaveBudget,
            icon = if (state.saveBudgetNeedsDeviceAuth) painterResource(R.drawable.ic_lock) else null,
        )
        AppButton(
            stringResource(R.string.common_cancel),
            onClose,
            Modifier.testTag("agent-budget-cancel"),
            kind = ButtonKind.Secondary,
        )
    }
}
