package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleStartEffect
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.EmptyState
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.LoadingState
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.labels.message
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.FeedbackUi

/** The outcome of the last action in words: the role refused it, the device owner declined, the server rejected it. */
@Composable
fun FeedbackBanner(
    feedback: FeedbackUi,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.warnSoft)
            .padding(start = Spacing.card)
            .testTag("feedback-banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(
            feedback.message(),
            Modifier.weight(1f).padding(vertical = Spacing.medium).testTag("feedback-message"),
            style = AppType.body,
            color = colors.warn,
        )
        TextButton(onDismiss, Modifier.heightIn(min = Spacing.minimumTarget).testTag("feedback-dismiss")) {
            Text(stringResource(R.string.common_dismiss), style = AppType.button, color = colors.accent)
        }
    }
}

/**
 * What a detail screen shows when it has no content: the spinner while it loads, the error with its way forward, or the
 * note that it cannot be read while offline.
 */
@Composable
fun DetailUnavailable(
    phase: DetailPhase,
    error: ErrorUi?,
    isOffline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSignInAgain: (() -> Unit)? = null,
) {
    when (phase) {
        DetailPhase.Loading -> {
            LoadingState(stringResource(R.string.common_loading), modifier.testTag("detail-loading"))
        }

        DetailPhase.Failed -> {
            Column(modifier.testTag("detail-failed"), verticalArrangement = Arrangement.spacedBy(Spacing.roomy)) {
                when {
                    isOffline -> {
                        DetailMessage(R.string.detail_offline_title, R.string.detail_offline_message)
                    }

                    error != null -> {
                        ErrorBanner(error, onRetry = onRetry, onSignInAgain = onSignInAgain)
                    }

                    else -> {
                        DetailMessage(R.string.detail_failed_title, null)
                        AppButton(
                            stringResource(R.string.common_retry),
                            onRetry,
                            Modifier.testTag("detail-retry"),
                            kind = ButtonKind.Secondary,
                        )
                    }
                }
            }
        }

        DetailPhase.Content -> {
            Unit
        }
    }
}

@Composable
private fun DetailMessage(
    title: Int,
    message: Int?,
) {
    EmptyState(
        icon = painterResource(R.drawable.ic_warning),
        title = stringResource(title),
        message = message?.let { stringResource(it) },
        modifier = Modifier.heightIn(min = DETAIL_MESSAGE_HEIGHT).semantics(mergeDescendants = true) {},
    )
}

/**
 * Runs the shared refresh policy for a detail screen while it is on screen: started when the screen starts, stopped
 * when it stops. The interval (15 seconds while the item can change, none once it is final) is the state's own; no
 * timer lives here (PRD FR-090). A session that has refresh switched off (tests) starts nothing.
 */
@Composable
fun RefreshWhileVisible(
    session: GraphSession,
    target: RefreshTarget?,
) {
    val graph = session.graph
    if (graph == null || target == null || !session.autoRefresh) return
    LifecycleStartEffect(session.id, target) {
        val running = graph.refresh.start(target)
        onStopOrDispose { running.stop() }
    }
}

private val DETAIL_MESSAGE_HEIGHT = Spacing.minimumTarget * 5
