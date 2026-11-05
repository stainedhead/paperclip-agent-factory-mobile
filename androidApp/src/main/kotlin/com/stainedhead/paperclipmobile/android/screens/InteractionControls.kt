package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.ActionRow
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.ChoiceUi
import com.stainedhead.paperclipmobile.presentation.InteractionUi
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.QuestionUi
import com.stainedhead.paperclipmobile.presentation.VerdictItemUi
import com.stainedhead.paperclipmobile.presentation.VerdictUi

/*
 * The controls of an interaction card. Every tag starts with `interaction-<card id>-`, so several cards on one screen
 * never share a tag. Text the person types stays in the field and only change intents reach the holder, which renders
 * asynchronously: showing its state back into the field would drop characters (ADR-108).
 */

/** The group id of suggested tasks and checkbox options (the shared core's single group). */
private const val SINGLE_GROUP = ""

internal fun InteractionUi.tag(part: String) = "interaction-$id-$part"

/** A text field that keeps what is typed and reports each change. */
@Composable
internal fun DraftField(
    placeholder: String,
    initial: String,
    tag: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var text by rememberSaveable(tag) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minimumTarget)
                .testTag(tag),
        enabled = enabled,
        textStyle = AppType.body.copy(color = colors.textPrimary),
        placeholder = { Text(placeholder, style = AppType.body, color = colors.textSecondary) },
        shape = RoundedCornerShape(Radius.button),
        keyboardOptions =
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.muted,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
            ),
    )
}

/** One choice: a radio for a single answer, a checkbox for several. The whole row is the target. */
@Composable
internal fun ChoiceRow(
    choice: ChoiceUi,
    radio: Boolean,
    enabled: Boolean,
    tag: String,
    onToggle: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .selectable(
                selected = choice.selected,
                enabled = enabled,
                role = if (radio) Role.RadioButton else Role.Checkbox,
                onClick = onToggle,
            ).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (radio) {
            RadioButton(choice.selected, null, enabled = enabled)
        } else {
            Checkbox(choice.selected, null, enabled = enabled)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
            Text(
                choice.label,
                style = AppType.body,
                color = if (enabled) colors.textPrimary else colors.textSecondary,
            )
            choice.summary?.let { Text(it, style = AppType.secondary, color = colors.textSecondary) }
        }
    }
}

@Composable
internal fun ChoiceList(
    card: InteractionUi,
    choices: List<ChoiceUi>,
    group: String,
    radio: Boolean,
    enabled: Boolean,
    send: (IssueIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        for (choice in choices) {
            ChoiceRow(choice, radio, enabled, card.tag("choice-$group-${choice.id}")) {
                send(IssueIntent.InteractionChoiceToggled(card.id, group, choice.id))
            }
        }
    }
}

/** A suggested-task list or a checkbox list: one group, several choices. */
@Composable
internal fun SingleGroupChoices(
    card: InteractionUi,
    enabled: Boolean,
    send: (IssueIntent) -> Unit,
) {
    if (card.tasks.isNotEmpty()) ChoiceList(card, card.tasks, SINGLE_GROUP, radio = false, enabled, send)
    if (card.options.isNotEmpty()) ChoiceList(card, card.options, SINGLE_GROUP, radio = false, enabled, send)
}

@Composable
internal fun QuestionBlock(
    card: InteractionUi,
    question: QuestionUi,
    enabled: Boolean,
    send: (IssueIntent) -> Unit,
) {
    val colors = AppTheme.colors
    val prompt =
        if (question.required) {
            question.prompt
        } else {
            stringResource(R.string.issue_optional_question, question.prompt)
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
            prompt,
            Modifier.semantics { heading() }.testTag(card.tag("question-${question.id}-prompt")),
            style = AppType.cardTitle,
            color = colors.textPrimary,
        )
        question.helpText?.let {
            Text(
                it,
                Modifier.testTag(card.tag("question-${question.id}-help")),
                style = AppType.secondary,
                color = colors.textSecondary,
            )
        }
        if (question.options.isNotEmpty()) {
            ChoiceList(card, question.options, question.id, radio = !question.multi, enabled, send)
        }
        if (question.allowOther || question.options.isEmpty()) {
            val placeholder =
                stringResource(
                    if (question.options.isEmpty()) R.string.issue_answer_placeholder else R.string.issue_other_field,
                )
            DraftField(placeholder, question.otherText, card.tag("text-${question.id}"), enabled) {
                send(IssueIntent.InteractionTextChanged(card.id, question.id, it))
            }
        }
    }
}

@Composable
internal fun VerdictBlock(
    card: InteractionUi,
    item: VerdictItemUi,
    enabled: Boolean,
    send: (IssueIntent) -> Unit,
) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(item.label, style = AppType.cardTitle, color = colors.textPrimary)
        item.summary?.let { Text(it, style = AppType.secondary, color = colors.textSecondary) }
        ActionRow(
            VerdictUi.entries.map { verdict ->
                { modifier: Modifier ->
                    val chosen = item.verdict == verdict
                    AppButton(
                        stringResource(verdict.stringRes()),
                        { send(IssueIntent.InteractionVerdictChosen(card.id, item.id, verdict)) },
                        modifier
                            .testTag(
                                card.tag("verdict-${item.id}-${verdict.name}"),
                            ).semantics { selected = chosen },
                        kind = if (chosen) ButtonKind.Primary else ButtonKind.Secondary,
                        enabled = enabled && verdict in card.enabledVerdicts,
                    )
                }
            },
        )
        DraftField(stringResource(R.string.issue_item_reason), item.reason, card.tag("reason-${item.id}"), enabled) {
            send(IssueIntent.InteractionTextChanged(card.id, item.id, it))
        }
    }
}
