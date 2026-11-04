package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.labels.message
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi

private val SymbolSize = 16.dp
private val HairlineWidth = 1.dp
private const val STACK_FONT_SCALE = 1.3f

/** "CRITICAL", "HIGH": the word carries the meaning, the colour only reinforces it. */
@Composable
fun SeverityLabel(
    severity: SeverityUi,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val word = stringResource(severity.stringRes())
    val color: Color =
        when (severity) {
            SeverityUi.Critical -> colors.destructive
            SeverityUi.High -> colors.warn
            SeverityUi.Medium, SeverityUi.Low -> colors.textSecondary
        }
    Text(
        text = word.uppercase(),
        modifier = modifier.testTag("severity-label").semantics { contentDescription = word },
        style = AppType.label,
        color = color,
    )
}

/** Why the action buttons next to it are disabled, in words (PRD FR-095). */
@Composable
fun DisabledReason(
    reason: ActionsDisabledReason,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().testTag("disabled-reason").semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (reason == ActionsDisabledReason.Offline) R.drawable.ic_offline else R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(SymbolSize),
            tint = AppTheme.colors.textSecondary,
        )
        Text(stringResource(reason.stringRes()), style = AppType.secondary, color = AppTheme.colors.textSecondary)
    }
}

/** The one button a banner offers: sign in again when signed out, otherwise try again, otherwise just close it. */
enum class ErrorBannerButton {
    SignIn,
    Retry,
    Dismiss,
    ;

    companion object {
        fun choose(
            signedOut: Boolean,
            signIn: Boolean,
            retry: Boolean,
            dismiss: Boolean,
        ): ErrorBannerButton? =
            when {
                signedOut && signIn -> SignIn
                retry -> Retry
                dismiss -> Dismiss
                else -> null
            }
    }
}

/**
 * One message per `ErrorKey` (PRD FR-097), with the server's own reason for a rejection and the way forward: sign in
 * again when signed out, otherwise try again.
 */
@Composable
fun ErrorBanner(
    error: ErrorUi,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onSignInAgain: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val button =
        ErrorBannerButton.choose(
            signedOut = error.key == ErrorKey.SignedOut,
            signIn = onSignInAgain != null,
            retry = onRetry != null,
            dismiss = onDismiss != null,
        )
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.destructiveSoft)
            .padding(Spacing.card)
            .testTag("error-banner"),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium), verticalAlignment = Alignment.Top) {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.size(SymbolSize),
                tint = colors.destructive,
            )
            Text(error.message(), Modifier.testTag("error-message"), style = AppType.body, color = colors.destructive)
        }
        when (button) {
            ErrorBannerButton.SignIn -> {
                AppButton(stringResource(R.string.common_sign_in_again), { onSignInAgain?.invoke() })
            }

            ErrorBannerButton.Retry -> {
                AppButton(stringResource(R.string.common_retry), { onRetry?.invoke() }, kind = ButtonKind.Secondary)
            }

            ErrorBannerButton.Dismiss -> {
                AppButton(stringResource(R.string.common_dismiss), { onDismiss?.invoke() }, kind = ButtonKind.Secondary)
            }

            null -> {
                Unit
            }
        }
    }
}

/** Shown above cached content while the phone is offline, with the age of what is shown. */
@Composable
fun OfflineBanner(
    updatedAtEpochMillis: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.warnSoft)
            .padding(Spacing.card)
            .testTag("offline-banner")
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_offline),
                contentDescription = null,
                modifier = Modifier.size(SymbolSize),
                tint = colors.warn,
            )
            Text(stringResource(R.string.common_offline), style = AppType.body, color = colors.warn)
        }
        UpdatedLabel(updatedAtEpochMillis)
    }
}

/**
 * The card pattern of the Inbox and every "needs you" list: severity, kind, age, title, one line of context, then the
 * actions. It renders what it is given; it knows no rules.
 *
 * With [onOpen] the text block is one button that opens the card: TalkBack reads it as [combinedLabel] (one sentence),
 * and the action buttons below it stay separate elements, so each can be found and tapped on its own.
 */
@Composable
fun AttentionCard(
    severity: SeverityUi?,
    kind: String,
    age: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    combinedLabel: String? = null,
    onOpen: (() -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(HairlineWidth, colors.hairline, shape)
            .padding(Spacing.card)
            .testTag("attention-card"),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        if (onOpen != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minimumTarget)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .testTag("attention-card-open")
                    .semantics { if (combinedLabel != null) contentDescription = combinedLabel },
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) { CardText(severity, kind, age, title, subtitle, isHeading = false) }
        } else {
            CardText(severity, kind, age, title, subtitle, isHeading = true)
        }
        Column(Modifier.padding(top = Spacing.extraSmall), content = actions)
    }
}

@Composable
private fun CardText(
    severity: SeverityUi?,
    kind: String,
    age: String,
    title: String,
    subtitle: String?,
    isHeading: Boolean,
) {
    val colors = AppTheme.colors
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    val tag: @Composable () -> Unit = {
        if (severity != null) SeverityLabel(severity)
        Text("· $kind", style = AppType.label, color = colors.textSecondary)
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            tag()
            Text(age, style = AppType.label, color = colors.textSecondary)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) { tag() }
            Text(age, style = AppType.label, color = colors.textSecondary)
        }
    }
    Text(
        title,
        Modifier.then(if (isHeading) Modifier.semantics { heading() } else Modifier),
        style = AppType.cardTitle,
        color = colors.textPrimary,
    )
    if (subtitle != null) Text(subtitle, style = AppType.secondary, color = colors.textSecondary)
}
