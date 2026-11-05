package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.DisabledReason
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.ResultCard
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusPill
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.BudgetChoice
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentState
import com.stainedhead.paperclipmobile.presentation.BudgetIntent
import com.stainedhead.paperclipmobile.presentation.BudgetOutcomeUi
import com.stainedhead.paperclipmobile.presentation.BudgetThresholdUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.MoneyUi

private const val PERCENT_FULL = 100f
private val RadioGlyph = 24.dp
private val LockGlyph = 16.dp
private val SelectedWidth = 2.dp
private val HairlineWidth = 1.dp
private val GaugeHeight = 8.dp

/** The share of the bar to fill: the state's whole percent, held between empty and full. */
fun gauge(percent: Int): Float = (percent / PERCENT_FULL).coerceIn(0f, 1f)

@Composable
fun BudgetBody(
    state: BudgetIncidentState,
    send: (BudgetIntent) -> Unit,
) {
    BudgetHeader(state)
    state.feedback?.let { FeedbackBanner(it, { send(BudgetIntent.MessageDismissed) }) }
    state.error?.let {
        ErrorBanner(it, onRetry = { send(BudgetIntent.Refresh) })
    }
    val outcome = state.outcome
    if (outcome != null) {
        ResultCard(
            stringResource(outcome.stringRes()),
            if (outcome == BudgetOutcomeUi.NoLongerActive) StatusTone.Neutral else StatusTone.Success,
            Modifier.testTag("budget-outcome"),
        ) {}
    } else {
        BudgetChoices(state, send)
        if (state.choice == BudgetChoice.RaiseAndResume) AmountField(state, send)
    }
}

/** The threshold, the scope, how much of the budget is used and how old the read is. */
@Composable
private fun BudgetHeader(state: BudgetIncidentState) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        state.threshold?.let {
            StatusPill(
                stringResource(it.stringRes()),
                if (it == BudgetThresholdUi.Hard) StatusTone.Critical else StatusTone.Warn,
            )
        }
        Text(
            state.scopeName,
            Modifier.semantics { heading() }.testTag("budget-scope"),
            style = AppType.title,
            color = colors.textPrimary,
        )
        if (state.scopeType.isNotEmpty()) {
            Text(
                prettify(state.scopeType).replaceFirstChar { it.uppercase() },
                style = AppType.secondary,
                color = colors.textSecondary,
            )
        }
        val observed = state.observed
        val limit = state.limit
        if (observed != null && limit != null) Usage(state, observed, limit)
        UpdatedLabel(state.updatedAtEpochMillis)
    }
}

@Composable
private fun Usage(
    state: BudgetIncidentState,
    observed: MoneyUi,
    limit: MoneyUi,
) {
    val colors = AppTheme.colors
    LinearProgressIndicator(
        progress = { gauge(state.observedPercent) },
        modifier = Modifier.fillMaxWidth().heightIn(min = GaugeHeight).clearAndSetSemantics {},
        color = if (state.observedPercent >= PERCENT_FULL.toInt()) colors.destructive else colors.accent,
        trackColor = colors.muted,
    )
    Text(
        stringResource(
            R.string.budget_usage,
            observed.text(),
            limit.text(),
            stringResource(R.string.budget_percent, state.observedPercent),
        ),
        Modifier.testTag("budget-usage"),
        style = AppType.body,
        color = colors.textPrimary,
    )
}

/** Keep paused or Raise and resume, each with what it does; Raise carries the lock glyph. */
@Composable
private fun BudgetChoices(
    state: BudgetIncidentState,
    send: (BudgetIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        SectionHeader(stringResource(R.string.budget_choice_header))
        ChoiceCard(
            BudgetChoice.KeepPaused,
            stringResource(R.string.budget_keep_paused_help),
            state.choice == BudgetChoice.KeepPaused,
            state.canKeepPaused,
            locked = false,
        ) { send(BudgetIntent.ChoiceSelected(BudgetChoice.KeepPaused)) }
        ChoiceCard(
            BudgetChoice.RaiseAndResume,
            stringResource(R.string.budget_raise_help),
            state.choice == BudgetChoice.RaiseAndResume,
            state.canRaise,
            locked = true,
        ) { send(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume)) }
        val reason = state.raiseDisabledReason
        if (reason != null && !state.canRaise) DisabledReason(reason)
    }
}

@Composable
private fun ChoiceCard(
    choice: BudgetChoice,
    help: String,
    isSelected: Boolean,
    enabled: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val title = stringResource(choice.stringRes())
    val shape = RoundedCornerShape(Radius.card)
    val tag = if (choice == BudgetChoice.KeepPaused) "budget-choice-keep" else "budget-choice-raise"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .clip(shape)
            .background(colors.surface)
            .border(
                if (isSelected) SelectedWidth else HairlineWidth,
                if (isSelected) colors.accent else colors.hairline,
                shape,
            ).selectable(isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(Spacing.card)
            .semantics { contentDescription = "$title. $help" }
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
    ) {
        Icon(
            painterResource(if (isSelected) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked),
            contentDescription = null,
            Modifier.size(RadioGlyph),
            tint = if (isSelected) colors.accent else colors.textSecondary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                Text(
                    title,
                    style = AppType.cardTitle,
                    color = if (enabled) colors.textPrimary else colors.textSecondary,
                )
                if (locked) {
                    Icon(
                        painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        Modifier.size(LockGlyph),
                        tint = colors.textSecondary,
                    )
                }
            }
            Text(help, style = AppType.secondary, color = colors.textSecondary)
        }
    }
}

/** The new monthly budget; shared code reads the text with the locale's separator and the currency's digits. */
@Composable
private fun AmountField(
    state: BudgetIncidentState,
    send: (BudgetIntent) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val observed = state.observed
    val format = MoneyInputFormat.of(observed?.currency ?: DEFAULT_CURRENCY)
    // After a process death the text is restored but the new holder has no amount: send it once so both agree.
    LaunchedEffect(Unit) {
        if (text.isNotEmpty() && state.amountMinorUnits == null && state.amountIssue == null) {
            send(BudgetIntent.AmountTyped(text, format.decimalSeparator, format.fractionDigits))
        }
    }
    FormField(
        title = stringResource(R.string.budget_amount_label),
        value = text,
        onValueChange = {
            text = it
            send(BudgetIntent.AmountTyped(it, format.decimalSeparator, format.fractionDigits))
        },
        tag = "budget-amount",
        placeholder = stringResource(R.string.budget_amount_placeholder),
        help = observed?.let { stringResource(R.string.budget_amount_help, it.text()) },
        issue = state.amountIssue?.let { stringResource(it.stringRes()) },
        keyboardType = KeyboardType.Decimal,
        capitalization = KeyboardCapitalization.None,
    )
}

private const val DEFAULT_CURRENCY = "USD"

/** What the submit button says: working, the amount that will be sent (and the lock), keep paused, or to choose. */
@Composable
private fun submitText(state: BudgetIncidentState): String {
    val minor = state.amountMinorUnits
    val currency = state.observed?.currency
    return when {
        state.isSubmitting -> {
            stringResource(R.string.budget_working)
        }

        state.submitNeedsDeviceAuth && minor != null && currency != null -> {
            stringResource(R.string.budget_submit_raise_amount, formatMoney(minor, currency))
        }

        state.submitNeedsDeviceAuth -> {
            stringResource(R.string.budget_submit_raise)
        }

        state.choice == null -> {
            stringResource(R.string.budget_submit_choose)
        }

        else -> {
            stringResource(R.string.budget_submit_keep)
        }
    }
}

/** The bar under the content: why actions are off, then the one button the choice makes of itself. */
@Composable
fun BudgetSubmitBar(
    state: BudgetIncidentState,
    send: (BudgetIntent) -> Unit,
) {
    if (state.phase != DetailPhase.Content || state.outcome != null) return
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface).testTag("budget-submit-bar")) {
        HairlineDivider()
        Column(
            Modifier
                .padding(start = Spacing.page, end = Spacing.page, top = Spacing.extraLarge, bottom = Spacing.medium)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            state.actionsDisabledReason?.let { DisabledReason(it) }
            AppButton(
                submitText(state),
                { send(BudgetIntent.SubmitTapped) },
                Modifier.testTag("budget-submit"),
                enabled = state.canSubmit,
                icon =
                    if (state.submitNeedsDeviceAuth &&
                        !state.isSubmitting
                    ) {
                        painterResource(R.drawable.ic_lock)
                    } else {
                        null
                    },
            )
        }
    }
}
