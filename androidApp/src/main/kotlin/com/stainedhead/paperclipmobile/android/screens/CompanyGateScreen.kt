package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.LoadingState
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.labels.message
import com.stainedhead.paperclipmobile.android.labels.messageRes
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.AppTab
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.AppState
import com.stainedhead.paperclipmobile.presentation.CompanyGate
import com.stainedhead.paperclipmobile.presentation.ErrorUi

private val GateIconSize = 40.dp

/**
 * What a company tab shows while the selected factory has no company to show (F-1, ADR-150): the app holder's
 * [AppState.companyGate] decides, this only draws it and forwards the re-check.
 */
@Composable
fun CompanyGateScreen(
    session: GraphSession,
    navigator: TabNavigator,
    app: AppState,
) {
    val holder = checkNotNull(session.app) { "The company gate needs the app holder" }
    CompanyGateContent(
        gate = app.companyGate,
        isChecking = app.isCheckingCompanies,
        error = app.companyError,
        onRecheck = { holder.send(AppIntent.RecheckCompanies) },
        onOpenFactories = { navigator.select(AppTab.Factories) },
    )
}

/** What the gate shows for a state. Stateless: tests and screenshots give it any state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyGateContent(
    gate: CompanyGate,
    isChecking: Boolean,
    error: ErrorUi?,
    onRecheck: () -> Unit,
    onOpenFactories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (gate == CompanyGate.Checking || gate == CompanyGate.Ready) {
        LoadingState(stringResource(CompanyGate.Checking.stringRes()), modifier.testTag("company-gate-checking"))
        return
    }
    val colors = AppTheme.colors
    PullToRefreshBox(isRefreshing = isChecking, onRefresh = onRecheck, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.page)
                .testTag("company-gate"),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(if (gate == CompanyGate.Failed) R.drawable.ic_error else R.drawable.ic_tab_factories),
                contentDescription = null,
                modifier = Modifier.size(GateIconSize),
                tint = colors.textSecondary,
            )
            Text(
                stringResource(gate.stringRes()),
                modifier = Modifier.semantics { heading() },
                style = AppType.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(gate.messageRes()),
                style = AppType.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (gate == CompanyGate.Failed && error != null) {
                Text(error.message(), style = AppType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
            GateActions(gate, onRecheck, onOpenFactories)
        }
    }
}

@Composable
private fun GateActions(
    gate: CompanyGate,
    onRecheck: () -> Unit,
    onOpenFactories: () -> Unit,
) {
    if (gate == CompanyGate.ChooseCompany) {
        AppButton(
            stringResource(R.string.company_gate_open_factories),
            onOpenFactories,
            Modifier.testTag("company-gate-open-factories"),
        )
    }
    val retry = if (gate == CompanyGate.Failed) R.string.common_retry else R.string.company_gate_recheck
    AppButton(
        stringResource(retry),
        onRecheck,
        Modifier.testTag("company-gate-recheck"),
        kind = if (gate == CompanyGate.ChooseCompany) ButtonKind.Secondary else ButtonKind.Primary,
    )
}
