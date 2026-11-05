package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.ActionRow
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.DisabledReason
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.NOTE_FIELD_LINES
import com.stainedhead.paperclipmobile.android.designsystem.ResultCard
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.DecideByKindUi
import com.stainedhead.paperclipmobile.presentation.DecideByUi
import com.stainedhead.paperclipmobile.presentation.DecisionFinalKind
import com.stainedhead.paperclipmobile.presentation.DecisionFinalUi
import com.stainedhead.paperclipmobile.presentation.DecisionIntent
import com.stainedhead.paperclipmobile.presentation.DecisionState
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.EffectResultUi
import com.stainedhead.paperclipmobile.presentation.EffectStatusUi
import com.stainedhead.paperclipmobile.presentation.ExecutionUi

/** "Decide by today", "Decide by Nov 20, 2026"; none for a decision without a deadline. */
@Composable
private fun DecideByUi.text(): String? =
    when (kind) {
        DecideByKindUi.None -> null
        DecideByKindUi.OnDate -> isoDate?.let { stringResource(kind.stringRes(), decideByDate(it)) }
        else -> stringResource(kind.stringRes())
    }

/** The kind and the deadline, the title, who proposed it, when it lapses and the body, then how old the read is. */
@Composable
fun DecisionHeader(
    state: DecisionState,
    identifier: String?,
) {
    val colors = AppTheme.colors
    val kind = stringResource(R.string.kind_decision)
    val separator = stringResource(R.string.meta_separator)
    val kindLine = listOfNotNull(kind, state.decideBy.text()).joinToString(separator).uppercase()
    val proposed = state.originAgentName?.let { stringResource(R.string.decision_proposed_by, it) }
    val meta = listOfNotNull(proposed, identifier).joinToString(separator).ifEmpty { null }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(kindLine, Modifier.testTag("decision-kind-line"), style = AppType.label, color = colors.destructive)
        Text(
            state.title,
            Modifier.semantics { heading() }.testTag("decision-title"),
            style = AppType.title,
            color = colors.textPrimary,
        )
        meta?.let {
            Text(it, Modifier.testTag("decision-meta"), style = AppType.secondary, color = colors.textSecondary)
        }
        val expires = state.expiresAtEpochMillis
        if (expires != null && state.finalState == null) {
            Text(
                stringResource(R.string.decision_expires, expiryDateTime(expires)),
                Modifier.testTag("decision-expires"),
                style = AppType.secondary,
                color = colors.warn,
            )
        }
        if (state.body.isNotEmpty()) {
            Text(
                state.body,
                Modifier.padding(top = Spacing.small).testTag("decision-body"),
                style = AppType.body,
                color = colors.textPrimary,
            )
        }
        UpdatedLabel(state.updatedAtEpochMillis)
    }
}

/** The option cards, each with every effect in words before the tap. */
@Composable
fun DecisionOptions(
    state: DecisionState,
    send: (DecisionIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        SectionHeader(stringResource(R.string.decision_options))
        for (option in state.options) {
            DecisionOptionCard(
                option,
                isSelected = state.selectedOptionId == option.id,
                enabled = !state.isSubmitting,
                onSelect = { send(DecisionIntent.OptionSelected(option.id)) },
            )
        }
    }
}

/**
 * The inputs the options ask for. The text lives here while it is typed, so a keystroke never waits for the shared
 * state to come round; the limit is never enforced here: the counter shows it and shared code answers `InputTooLong`.
 */
@Composable
fun DecisionInputs(
    state: DecisionState,
    send: (DecisionIntent) -> Unit,
) {
    val typed = remember { mutableStateMapOf<String, String>() }
    val feedback = state.feedback
    for (input in state.inputs) {
        val text = typed[input.id] ?: input.value
        val counter = inputCounter(text, input.maxLength)
        FormField(
            title = input.label,
            value = text,
            onValueChange = {
                typed[input.id] = it
                send(DecisionIntent.InputChanged(input.id, it))
            },
            tag = "decision-input-${input.id}",
            placeholder = input.placeholder,
            help = counter?.let { stringResource(R.string.decision_counter, it.typed, it.limit) },
            issue =
                feedback?.invalid?.takeIf { feedback.inputId == input.id }?.let { stringResource(it.stringRes()) },
            capitalization = KeyboardCapitalization.Sentences,
            maxLines = NOTE_FIELD_LINES,
            enabled = !state.isSubmitting && state.actionsDisabledReason == null,
        )
    }
}

/** Shown instead of the options once the decision is no longer open (decided elsewhere, expired, cancelled). */
@Composable
fun DecisionFinal(final: DecisionFinalUi) {
    val text =
        if (final.kind == DecisionFinalKind.Decided) {
            final.chosenOptionLabel?.let { stringResource(final.kind.stringRes(), it) }
                ?: stringResource(R.string.decision_final_decided_no_option)
        } else {
            stringResource(final.kind.stringRes())
        }
    ResultCard(
        stringResource(R.string.decision_chosen),
        if (final.kind == DecisionFinalKind.Decided) StatusTone.Success else StatusTone.Neutral,
        Modifier.testTag("decision-final"),
    ) { Text(text, style = AppType.body, color = AppTheme.colors.textPrimary) }
}

private fun EffectStatusUi.tone(): StatusTone =
    when (this) {
        EffectStatusUi.Executed -> StatusTone.Success
        EffectStatusUi.Skipped -> StatusTone.Warn
        EffectStatusUi.Failed -> StatusTone.Critical
        EffectStatusUi.Claimed, EffectStatusUi.Unknown -> StatusTone.Neutral
    }

/** What each effect did once the decision was confirmed: done, skipped (with why) or failed. */
@Composable
fun DecisionExecution(execution: ExecutionUi) {
    CardSurface(Modifier.testTag("decision-execution")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
            SectionHeader(stringResource(R.string.decision_results))
            Text(
                stringResource(execution.status.stringRes()),
                Modifier.testTag("decision-execution-status"),
                style = AppType.button,
                color = AppTheme.colors.textPrimary,
            )
            for (result in execution.results) EffectResultRow(result)
        }
    }
}

@Composable
private fun EffectResultRow(result: EffectResultUi) {
    val reason =
        when (val why = ResultReason.of(result.reason)) {
            ResultReason.None -> null
            ResultReason.TargetChanged -> stringResource(R.string.decision_reason_target_changed)
            is ResultReason.Other -> why.text
        }
    OutcomeRow(
        resultTitle(result.effectType),
        stringResource(result.status.stringRes()),
        result.status.tone(),
        "decision-result-${result.index}",
        detail = reason,
    )
}

/** What the confirm button says: working, or the choice, with the lock when the device owner is asked. */
@Composable
private fun confirmText(state: DecisionState): String =
    stringResource(if (state.isSubmitting) R.string.decision_working else R.string.decision_confirm)

/** The bar under the content: why actions are off, the chosen option restated, then Dismiss and Confirm. */
@Composable
fun DecisionActionBar(
    state: DecisionState,
    send: (DecisionIntent) -> Unit,
) {
    if (state.phase != DetailPhase.Content || state.finalState != null) return
    val chosen = state.options.firstOrNull { it.id == state.selectedOptionId }
    val spoken =
        if (state.confirmNeedsDeviceAuth && !state.isSubmitting) {
            stringResource(
                R.string.decision_confirm_spoken,
                confirmText(state),
                stringResource(R.string.decision_asks_auth),
            )
        } else {
            confirmText(state)
        }
    Column(
        Modifier.fillMaxWidth().background(AppTheme.colors.surface).testTag("decision-action-bar"),
    ) {
        HairlineDivider()
        Column(
            Modifier
                .padding(
                    start = Spacing.page,
                    end = Spacing.page,
                    top = Spacing.extraLarge,
                    bottom = Spacing.medium,
                ).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            state.actionsDisabledReason?.let { DisabledReason(it) }
            chosen?.let {
                val line = stringResource(R.string.decision_selected_option, it.label)
                Text(
                    line,
                    Modifier.testTag("decision-chosen"),
                    style = AppType.secondary,
                    color = AppTheme.colors.textSecondary,
                )
            }
            ActionRow(
                buildList<@Composable (Modifier) -> Unit> {
                    add { m ->
                        AppButton(
                            stringResource(R.string.decision_dismiss),
                            { send(DecisionIntent.DismissTapped) },
                            m.testTag("decision-dismiss"),
                            kind = ButtonKind.Secondary,
                            enabled = state.canDismiss,
                        )
                    }
                    add { m ->
                        AppButton(
                            confirmText(state),
                            { send(DecisionIntent.ConfirmTapped) },
                            m.testTag("decision-confirm").semantics { contentDescription = spoken },
                            enabled = state.canConfirm,
                            icon = if (state.confirmNeedsDeviceAuth) painterResource(R.drawable.ic_lock) else null,
                        )
                    }
                },
            )
        }
    }
}
