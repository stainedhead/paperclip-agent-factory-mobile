package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R

/** The mocks' four button looks. Every one is at least 48 dp tall. A disabled button is muted grey, not dimmed. */
enum class ButtonKind {
    Primary,
    Secondary,
    Destructive,
    Muted,
}

private const val STACK_FONT_SCALE = 1.3f
private val HairlineWidth = 1.dp
private val StepBadge = 24.dp
private val LargeIcon = 40.dp
private val SmallIcon = 16.dp

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    val colors = AppTheme.colors
    val container =
        when (kind) {
            ButtonKind.Primary -> colors.accent
            ButtonKind.Muted -> colors.muted
            ButtonKind.Secondary, ButtonKind.Destructive -> colors.surface
        }
    val content =
        when (kind) {
            ButtonKind.Primary -> colors.onAccent
            ButtonKind.Destructive -> colors.destructive
            ButtonKind.Secondary, ButtonKind.Muted -> colors.textPrimary
        }
    val outlined = kind == ButtonKind.Secondary || kind == ButtonKind.Destructive
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.minimumTarget),
        enabled = enabled,
        shape = RoundedCornerShape(Radius.button),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = colors.muted,
                disabledContentColor = colors.textSecondary,
            ),
        border = if (outlined && enabled) BorderStroke(HairlineWidth, colors.border) else null,
        contentPadding = PaddingValues(horizontal = Spacing.roomy, vertical = Spacing.medium),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(SmallIcon))
            Box(Modifier.size(Spacing.medium))
        }
        Text(text, style = AppType.button, textAlign = TextAlign.Center)
    }
}

/** The white card of the mocks: surface fill, hairline border, 14 dp radius. */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(HairlineWidth, colors.hairline, shape)
                .padding(Spacing.card),
        content = content,
    )
}

/** Rows separated by hairlines inside one card. */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(HairlineWidth, colors.hairline, shape)
                .padding(horizontal = Spacing.card),
        content = content,
    )
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = HairlineWidth, color = AppTheme.colors.hairline)
}

/** Side by side at normal sizes; stacked when the font is scaled up, so nothing is squeezed to a letter per line. */
@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.extraLarge,
    content: @Composable () -> Unit,
) {
    if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) { content() }
    } else {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

/** A row of a grouped card: leading text (title and optional help), trailing content. */
@Composable
fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    help: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = AppTheme.colors
    val text: @Composable (Modifier) -> Unit = { textModifier ->
        Column(textModifier, verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
            Text(title, style = AppType.cardTitle, color = colors.textPrimary)
            if (help != null) Text(help, style = AppType.secondary, color = colors.textSecondary)
        }
    }
    val rowModifier = modifier.fillMaxWidth().heightIn(min = Spacing.minimumTarget).padding(vertical = Spacing.small)
    if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            text(Modifier)
            trailing()
        }
    } else {
        Row(
            rowModifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            text(Modifier.weight(1f))
            trailing()
        }
    }
}

/** A small uppercase section title above a grouped card. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title.uppercase(),
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.extraSmall).semantics { heading() },
        style = AppType.label,
        color = AppTheme.colors.textSecondary,
    )
}

enum class StatusTone {
    Success,
    Warn,
    Critical,
    Neutral,
}

internal fun StatusTone.foreground(colors: AppColors): Color =
    when (this) {
        StatusTone.Success -> colors.success
        StatusTone.Warn -> colors.warn
        StatusTone.Critical -> colors.destructive
        StatusTone.Neutral -> colors.textSecondary
    }

internal fun StatusTone.background(colors: AppColors): Color =
    when (this) {
        StatusTone.Success -> colors.successSoft
        StatusTone.Warn -> colors.warnSoft
        StatusTone.Critical -> colors.destructiveSoft
        StatusTone.Neutral -> colors.muted
    }

private fun StatusTone.symbol(): Int =
    when (this) {
        StatusTone.Success -> R.drawable.ic_check_circle
        StatusTone.Warn -> R.drawable.ic_warning
        StatusTone.Critical -> R.drawable.ic_error
        StatusTone.Neutral -> R.drawable.ic_radio_unchecked
    }

/** A small status chip. The symbol and the text say what it is; colour only reinforces it. */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(tone.background(colors))
                .padding(horizontal = Spacing.large, vertical = Spacing.extraSmall)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Icon(
            painterResource(tone.symbol()),
            contentDescription = null,
            Modifier.size(SmallIcon),
            tint = tone.foreground(colors),
        )
        Text(text, style = AppType.label, color = tone.foreground(colors))
    }
}

/** A coloured result box ("Server found"): title, detail lines, and a tone that backs the words up. */
@Composable
fun ResultCard(
    title: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(tone.background(colors))
                .padding(Spacing.card)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Text(title, style = AppType.button, color = tone.foreground(colors))
        content()
    }
}

/** One step of [StepIndicator]; [description] is the spoken form ("Step 2, Pair, current"). */
class StepItem(
    val number: Int,
    val title: String,
    val description: String,
)

/** "1 Address  2 Pair  3 Secure": the current step is filled, the others muted. The step is spoken, not coloured. */
@Composable
fun StepIndicator(
    steps: List<StepItem>,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    AdaptiveRow(modifier.fillMaxWidth(), spacing = Spacing.roomy) {
        for (step in steps) {
            val isCurrent = step.number == current
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = step.description
                        selected = isCurrent
                    },
            ) {
                Box(
                    Modifier
                        .size(
                            StepBadge,
                        ).clip(CircleShape)
                        .background(if (isCurrent) colors.accent else colors.muted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        step.number.toString(),
                        style = AppType.label,
                        color = if (isCurrent) colors.onAccent else colors.textSecondary,
                    )
                }
                Text(
                    step.title,
                    style = if (isCurrent) AppType.button else AppType.secondary,
                    color = if (isCurrent) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

/**
 * A labelled single-line text field with help or an issue underneath, as in the mocks. The text is the caller's state,
 * edited through [onValueChange]; the field owns nothing.
 */
@Composable
fun FormField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    help: String? = null,
    issue: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(title, style = AppType.button, color = colors.textPrimary)
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.replace("\n", "")) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minimumTarget)
                    .onFocusChanged { onFocusChanged?.invoke(it.isFocused) }
                    .testTag(tag),
            enabled = enabled,
            singleLine = true,
            isError = issue != null,
            textStyle = AppType.body.copy(color = colors.textPrimary),
            placeholder = placeholder?.let { { Text(it, style = AppType.body, color = colors.textSecondary) } },
            shape = RoundedCornerShape(Radius.button),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboardType,
                    capitalization = capitalization,
                    autoCorrectEnabled = false,
                    imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
                ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    disabledContainerColor = colors.muted,
                    errorContainerColor = colors.surface,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    errorBorderColor = colors.destructive,
                ),
        )
        if (issue != null) {
            Text(issue, Modifier.testTag("$tag-issue"), style = AppType.secondary, color = colors.destructive)
        } else if (help != null) {
            Text(help, Modifier.testTag("$tag-help"), style = AppType.secondary, color = colors.textSecondary)
        }
    }
}

@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Column(
        modifier.fillMaxSize().padding(Spacing.page),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(LargeIcon), tint = colors.textSecondary)
        Text(title, style = AppType.title, color = colors.textPrimary, textAlign = TextAlign.Center)
        if (message != null) {
            Text(message, style = AppType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
        if (actionTitle != null && onAction != null) {
            AppButton(actionTitle, onAction, Modifier.testTag("empty-state-action"))
        }
    }
}

@Composable
fun LoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = AppTheme.colors.accent)
        Text(label, style = AppType.body, color = AppTheme.colors.textSecondary)
    }
}
