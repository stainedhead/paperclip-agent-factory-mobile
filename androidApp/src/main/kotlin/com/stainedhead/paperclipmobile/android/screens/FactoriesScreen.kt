package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.BarIconButton
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.ChoiceChip
import com.stainedhead.paperclipmobile.android.designsystem.EmptyState
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.designsystem.foreground
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberFactoriesHolder
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.FactoriesIntent
import com.stainedhead.paperclipmobile.presentation.FactoriesState
import com.stainedhead.paperclipmobile.presentation.FactoryRowUi

private val StatusDot = 10.dp
private val SelectedBorder = 2.dp

/**
 * Screen 8, the root of the Factories tab (AND-021): every factory with its status, key expiry and companies, the way
 * to add one, and the doors to Manage factories and Settings. It renders `FactoriesState`; choosing a factory or a
 * company is an intent, and what each status means is the shared core's.
 */
@Composable
fun FactoriesScreen(
    session: GraphSession,
    navigator: TabNavigator,
) {
    val holder = rememberFactoriesHolder(session)
    val state by holder.state.collectAsStateWithLifecycle()
    LaunchedEffect(holder) { holder.onIntent(FactoriesIntent.Refresh) }
    val addFactory = { navigator.push(AppRoute.EditFactory(null)) }
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.page).testTag("factories-root")) {
        ScreenTitleBar(stringResource(R.string.tab_factories), isLarge = true) {
            BarIconButton(
                painterResource(R.drawable.ic_edit),
                stringResource(R.string.factories_manage),
                { navigator.push(AppRoute.ManageFactories) },
                "manage-factories-button",
            )
            BarIconButton(
                painterResource(R.drawable.ic_settings),
                stringResource(R.string.factories_settings),
                { navigator.push(AppRoute.Settings) },
                "settings-button",
            )
        }
        if (state.isEmpty) {
            EmptyState(
                icon = painterResource(R.drawable.ic_server),
                title = stringResource(R.string.factories_empty_title),
                message = stringResource(R.string.factories_empty_message),
                actionTitle = stringResource(R.string.factories_add),
                onAction = addFactory,
                modifier = Modifier.weight(1f),
            )
        } else {
            FactoryList(state, holder::onIntent, navigator, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FactoryList(
    state: FactoriesState,
    onIntent: (FactoriesIntent) -> Unit,
    navigator: TabNavigator,
    modifier: Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onIntent(FactoriesIntent.Refresh) },
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.page),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
        ) {
            if (state.isOffline) {
                OfflineBanner(state.updatedAtEpochMillis)
            } else {
                UpdatedLabel(state.updatedAtEpochMillis)
            }
            for (row in state.rows) {
                FactoryCard(
                    row,
                    onSwitch = { onIntent(FactoriesIntent.InstanceSelected(row.id)) },
                    onChooseCompany = { onIntent(FactoriesIntent.CompanySelected(row.id, it)) },
                    onDetails = { navigator.push(AppRoute.EditFactory(row.id)) },
                )
            }
            AppButton(
                stringResource(R.string.factories_add),
                { navigator.push(AppRoute.EditFactory(null)) },
                Modifier.testTag("add-factory-button"),
                kind = ButtonKind.Secondary,
                icon = painterResource(R.drawable.ic_add),
            )
        }
    }
}

/**
 * One factory of the list, as in the mock: status dot and name, status words, address, key expiry, company chips, and
 * "Pair again" when this phone has no working key. The selected factory has an accent border.
 */
@Composable
private fun FactoryCard(
    row: FactoryRowUi,
    onSwitch: () -> Unit,
    onChooseCompany: (String) -> Unit,
    onDetails: () -> Unit,
) {
    val colors = AppTheme.colors
    val status = FactoryStatusPresentation.of(row.status, row.expiry)
    val expiry = ExpiryPresentation.of(row.expiry)
    val shape = RoundedCornerShape(Radius.card)
    val selectedFrame =
        if (row.isSelected) {
            Modifier.border(
                BorderStroke(SelectedBorder, colors.accent),
                shape,
            )
        } else {
            Modifier
        }
    CardSurface(selectedFrame.testTag("factory-card-${row.id}")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
            FactoryHeader(row, status, onSwitch, onDetails)
            Text(row.address, style = AppType.secondary, color = colors.textSecondary)
            expiry.text()?.let {
                Text(
                    it,
                    Modifier.testTag("factory-expiry-${row.id}"),
                    style = AppType.secondary,
                    color = expiry.tone.foreground(colors),
                )
            }
            row.error?.let { ErrorBanner(it) }
            if (row.isLoading) {
                Text(stringResource(R.string.factories_loading_companies), style = AppType.secondary)
            }
            Companies(row, onChooseCompany)
            if (status.offersPairing) {
                AppButton(
                    stringResource(R.string.factories_pair_again),
                    onDetails,
                    Modifier.testTag("pair-again-${row.id}"),
                    kind = ButtonKind.Secondary,
                )
            }
        }
    }
}

/** The name and status of a factory, which switches to it when tapped, and the way to its details. */
@Composable
private fun FactoryHeader(
    row: FactoryRowUi,
    status: FactoryStatusPresentation,
    onSwitch: () -> Unit,
    onDetails: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minimumTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = Spacing.minimumTarget)
                .clickable(
                    enabled = !row.isSelected,
                    onClickLabel = stringResource(R.string.factories_switch_to, row.name),
                    role = Role.Button,
                    onClick = onSwitch,
                ).testTag("factory-header-${row.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Box(Modifier.size(StatusDot).clip(CircleShape).background(status.tone.foreground(colors)))
            Text(
                row.name,
                Modifier.weight(1f, fill = false).semantics { heading() }.testTag("factory-name-${row.id}"),
                style = AppType.cardTitle,
                color = colors.textPrimary,
            )
            Text(
                stringResource(status.label.stringRes()),
                Modifier.testTag("factory-status-${row.id}"),
                style = AppType.label,
                color = status.tone.foreground(colors),
            )
        }
        BarIconButton(
            painterResource(R.drawable.ic_more),
            stringResource(R.string.factories_details),
            onDetails,
            "factory-details-${row.id}",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Companies(
    row: FactoryRowUi,
    onChoose: (String) -> Unit,
) {
    if (row.companies.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        for (company in row.companies) {
            ChoiceChip(company.name, row.selectedCompanyId == company.id, "company-chip-${row.id}-${company.id}") {
                onChoose(company.id)
            }
        }
    }
}
