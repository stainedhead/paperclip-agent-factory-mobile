package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.BarIconButton
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.WithSheetHolders
import com.stainedhead.paperclipmobile.android.env.rememberBudgetHolder
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentState
import com.stainedhead.paperclipmobile.presentation.BudgetIntent
import com.stainedhead.paperclipmobile.presentation.DetailPhase

/**
 * Screen 12, shown over the Inbox as a sheet (AND-042). It renders `BudgetIncidentState` and forwards intents. Who may
 * keep paused or raise, the amount rule (it must exceed the observed spend), the device-owner prompt for a raise (asked
 * inside the shared use case) and the server's own refusal reason are the shared holder's. Its holder lives as long as
 * the sheet does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetIncidentSheet(
    session: GraphSession,
    incidentId: String,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.background,
    ) {
        WithSheetHolders {
            val holder = rememberBudgetHolder(session, incidentId)
            val state by holder.state.collectAsStateWithLifecycle()
            RefreshWhileVisible(session, holder.holder.refreshTarget)
            BudgetContent(state, holder::onIntent, onClose)
        }
    }
}

/** What the sheet shows for a state. Stateless: tests and screenshots give it any state, including a viewer's. */
@Composable
fun BudgetContent(
    state: BudgetIncidentState,
    send: (BudgetIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().imePadding().testTag("budget-sheet")) {
        ScreenTitleBar(stringResource(R.string.budget_nav_title), Modifier.padding(start = Spacing.page)) {
            BarIconButton(
                painterResource(R.drawable.ic_dismiss),
                stringResource(R.string.budget_close),
                onClose,
                "budget-close",
                tint = AppTheme.colors.textPrimary,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page, vertical = Spacing.roomy)
                .testTag("budget-screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        ) {
            if (state.isOffline && state.phase == DetailPhase.Content) OfflineBanner(state.updatedAtEpochMillis)
            if (state.phase == DetailPhase.Content) {
                BudgetBody(state, send)
            } else {
                DetailUnavailable(state.phase, state.error, state.isOffline, { send(BudgetIntent.Refresh) })
            }
        }
        BudgetSubmitBar(state, send)
    }
}
