package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.ActionRow
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AttentionCard
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.RelativeAge
import com.stainedhead.paperclipmobile.android.designsystem.compactText
import com.stainedhead.paperclipmobile.android.designsystem.rememberNowMillis
import com.stainedhead.paperclipmobile.android.designsystem.spokenText
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import com.stainedhead.paperclipmobile.presentation.InlineActionUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi

private const val MILLIS_PER_SECOND = 1_000L

/** What a card can do; each is a plain forward of an intent or a navigation. */
class InboxCardActions(
    val onOpen: () -> Unit,
    val onApprove: () -> Unit,
    val onReject: () -> Unit,
    val onKeepPaused: () -> Unit,
    val onSnooze: (() -> Unit)?,
    val onDismiss: (() -> Unit)?,
)

/**
 * One Inbox card: the shared [AttentionCard] plus the buttons the state offers. Buttons are disabled while the phone is
 * offline or another action runs ([isDisabled]); opening a card only reads, so it stays available. Snooze and Dismiss
 * are TalkBack actions of the card as well as swipes (a swipe is never the only way).
 */
@Composable
fun InboxCard(
    item: InboxItemUi,
    isDisabled: Boolean,
    actions: InboxCardActions,
    modifier: Modifier = Modifier,
) {
    val kind = stringResource(item.kind.stringRes())
    val kindName = if (item.kind == SourceKindUi.Unsupported) item.rawKind ?: kind else kind
    val title = item.title ?: kindName
    val context = cardContext(item.whyNow, item.originAgentName)
    val line = kindLine(kindName, item.identifier)
    val now by rememberNowMillis()
    val age = RelativeAge.of((now - item.activityAtEpochMillis) / MILLIS_PER_SECOND)
    val spoken =
        listOf(
            stringResource(item.severity.stringRes()),
            line,
            title,
            context,
            stringResource(R.string.inbox_age_ago, age.spokenText()),
        ).joinToString(". ")
    val snoozeLabel = stringResource(R.string.inbox_swipe_snooze)
    val dismissLabel = stringResource(R.string.inbox_swipe_dismiss)
    val custom =
        listOfNotNull(
            actions.onSnooze?.let { run ->
                CustomAccessibilityAction(snoozeLabel) {
                    run()
                    true
                }
            },
            actions.onDismiss?.let { run ->
                CustomAccessibilityAction(dismissLabel) {
                    run()
                    true
                }
            },
        )
    AttentionCard(
        severity = item.severity,
        kind = line,
        age = age.compactText(),
        title = title,
        modifier = modifier.testTag("inbox-card-${item.id}"),
        subtitle = context,
        combinedLabel = spoken,
        onOpen = actions.onOpen,
        customActions = custom,
    ) { CardButtons(item, isDisabled, actions) }
}

@Composable
private fun CardButtons(
    item: InboxItemUi,
    isDisabled: Boolean,
    actions: InboxCardActions,
) {
    val offered = item.inlineActions
    when (InboxCardLink.of(item)) {
        InboxCardLink.ReviewOptions -> {
            AppButton(
                stringResource(R.string.inbox_review_options),
                actions.onOpen,
                Modifier.testTag("inbox-review-${item.id}"),
            )
        }

        InboxCardLink.Details -> {
            ActionRow(
                buildList<@Composable (Modifier) -> Unit> {
                    if (InlineActionUi.Reject in offered) {
                        add { m ->
                            AppButton(
                                stringResource(R.string.inline_action_reject),
                                actions.onReject,
                                m.testTag("inbox-reject-${item.id}"),
                                kind = ButtonKind.Destructive,
                                enabled = !isDisabled,
                            )
                        }
                    }
                    add { m ->
                        AppButton(
                            stringResource(R.string.inbox_details),
                            actions.onOpen,
                            m.testTag("inbox-details-${item.id}"),
                            kind = ButtonKind.Muted,
                        )
                    }
                    if (InlineActionUi.Approve in offered) {
                        add { m ->
                            AppButton(
                                stringResource(R.string.inline_action_approve),
                                actions.onApprove,
                                m.testTag("inbox-approve-${item.id}"),
                                enabled = !isDisabled,
                            )
                        }
                    }
                },
            )
        }

        InboxCardLink.OpenInWeb -> {
            AppButton(
                stringResource(R.string.inbox_open_in_web),
                actions.onOpen,
                Modifier.testTag("inbox-web-${item.id}"),
                kind = ButtonKind.Secondary,
            )
        }

        InboxCardLink.Chevron -> {
            BudgetButtons(item, isDisabled, actions)
        }
    }
}

/** Keep paused and Raise and resume on a hard-stop budget card; Raise opens the sheet where the amount is typed. */
@Composable
private fun BudgetButtons(
    item: InboxItemUi,
    isDisabled: Boolean,
    actions: InboxCardActions,
) {
    ActionRow(
        buildList<@Composable (Modifier) -> Unit> {
            if (InlineActionUi.KeepPaused in item.inlineActions) {
                add { m ->
                    AppButton(
                        stringResource(R.string.inline_action_keep_paused),
                        actions.onKeepPaused,
                        m.testTag("inbox-keep-paused-${item.id}"),
                        kind = ButtonKind.Secondary,
                        enabled = !isDisabled,
                    )
                }
            }
            if (InlineActionUi.RaiseAndResume in item.inlineActions) {
                add { m ->
                    AppButton(
                        stringResource(R.string.inline_action_raise_and_resume),
                        actions.onOpen,
                        m.testTag("inbox-raise-${item.id}"),
                        enabled = !isDisabled,
                    )
                }
            }
        },
    )
}
