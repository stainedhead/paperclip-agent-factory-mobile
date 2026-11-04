package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.EffectSummaryUi
import com.stainedhead.paperclipmobile.presentation.OptionUi

private val RadioGlyph = 24.dp
private val LockSize = 16.dp
private val SelectedWidth = 2.dp
private val HairlineWidth = 1.dp

/** What one effect will do, in plain words: the line the option shows before anything is chosen. */
@Composable
private fun EffectSummaryUi.line(): String = stringResource(kind.stringRes(), effectDetail(this))

/**
 * One option of a decision as a selectable card: the label, then every effect in plain words before anything is
 * chosen. A destructive option is red with a lock glyph (the device owner is asked after the confirm, inside the
 * shared use case, never here). The card reads as one radio button, with its effects in the sentence.
 */
@Composable
fun DecisionOptionCard(
    option: OptionUi,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    val effects = option.effects.map { it.line() }
    val strict = option.effects.any { it.isStrict }
    val strictLine = stringResource(R.string.decision_effect_strict)
    val noEffects = stringResource(R.string.decision_no_effects)
    val lines = listOfNotNull(option.summary) + effects + listOfNotNull(strictLine.takeIf { strict })
    val shown = lines.ifEmpty { listOf(noEffects) }
    val spoken =
        buildList {
            add(option.label)
            if (option.isDestructive) add(stringResource(R.string.decision_destructive))
            if (option.needsDeviceAuth) add(stringResource(R.string.decision_asks_auth))
            addAll(shown)
        }.joinToString(". ")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .clip(shape)
            .background(colors.surface)
            .border(
                if (isSelected) SelectedWidth else HairlineWidth,
                if (isSelected) colors.accent else colors.hairline,
                shape,
            ).selectable(isSelected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(Spacing.card)
            .semantics { contentDescription = spoken }
            .testTag("decision-option-${option.id}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
    ) {
        Icon(
            painterResource(if (isSelected) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked),
            contentDescription = null,
            Modifier.size(RadioGlyph),
            tint = if (isSelected) colors.accent else colors.textSecondary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            OptionTitle(option)
            for (line in shown) Text(line, style = AppType.secondary, color = colors.textSecondary)
        }
    }
}

@Composable
private fun OptionTitle(option: OptionUi) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
            option.label,
            Modifier.weight(1f, fill = false),
            style = AppType.cardTitle,
            color = if (option.isDestructive) colors.destructive else colors.textPrimary,
        )
        if (option.needsDeviceAuth) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = null,
                Modifier.size(LockSize).padding(top = Spacing.hairline).testTag("decision-option-lock-${option.id}"),
                tint = colors.destructive,
            )
        }
    }
}
