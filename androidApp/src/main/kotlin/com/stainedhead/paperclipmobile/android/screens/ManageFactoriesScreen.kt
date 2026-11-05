package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.BarIconButton
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.CardGroup
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.ReorderableColumn
import com.stainedhead.paperclipmobile.android.designsystem.RowControls
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.foreground
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberManageHolder
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.ConfirmDestructiveSheet
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.ExpiryUi
import com.stainedhead.paperclipmobile.presentation.KeyExpiryUi
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesIntent
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesState
import com.stainedhead.paperclipmobile.presentation.ManageRowUi
import com.stainedhead.paperclipmobile.presentation.RemovalConfirmationUi

private val HandleSize = 48.dp

/**
 * Screen 16 (AND-023): reorder and remove factories. A row is dragged by its handle (or moved with its accessibility
 * actions); removing asks for confirmation, which says what will be deleted (from state), and then the device owner,
 * inside the shared use case: no factory leaves the list until that approves, and this screen never deletes anything.
 */
@Composable
fun ManageFactoriesScreen(
    session: GraphSession,
    navigator: TabNavigator,
) {
    val holder = rememberManageHolder(session)
    val state by holder.state.collectAsStateWithLifecycle()
    val send = holder::onIntent
    Column(Modifier.fillMaxSize().testTag("manage-factories")) {
        ScreenTitleBar(
            stringResource(R.string.manage_title),
            Modifier.padding(horizontal = Spacing.page),
            onBack = navigator.pop,
        ) {
            BarIconButton(
                painterResource(R.drawable.ic_check),
                stringResource(R.string.common_done),
                navigator.popToRoot,
                "manage-done-button",
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.page),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
        ) {
            state.error?.let { ErrorBanner(it, onDismiss = { send(ManageFactoriesIntent.MessageDismissed) }) }
            Text(
                pluralStringResource(R.plurals.manage_count, state.rows.size, state.rows.size),
                Modifier.testTag("manage-count"),
                style = AppType.secondary,
                color = AppTheme.colors.textSecondary,
            )
            FactoryRows(state, send, navigator)
            Notes(state)
            AppButton(
                stringResource(R.string.factories_add),
                { navigator.push(AppRoute.EditFactory(null)) },
                Modifier.testTag("add-factory-button"),
                kind = ButtonKind.Secondary,
                icon = painterResource(R.drawable.ic_add),
            )
        }
    }
    state.confirmation?.let { RemovalSheet(it, state, send) }
}

/** What removing deletes, said from the state before the person confirms; the device owner is asked after the tap. */
@Composable
private fun RemovalSheet(
    confirmation: RemovalConfirmationUi,
    state: ManageFactoriesState,
    send: (ManageFactoriesIntent) -> Unit,
) {
    val lines = confirmation.effects.map { stringResource(it.stringRes()) }
    ConfirmDestructiveSheet(
        title = stringResource(R.string.remove_title, confirmation.name),
        message =
            if (confirmation.hereOnly) {
                stringResource(R.string.remove_here_only_message)
            } else {
                (lines + stringResource(R.string.remove_footer)).joinToString("\n")
            },
        confirmTitle =
            stringResource(if (confirmation.hereOnly) R.string.remove_here_only_confirm else R.string.remove_confirm),
        onConfirm = { send(ManageFactoriesIntent.RemoveConfirmed) },
        onCancel = { send(ManageFactoriesIntent.RemoveCancelled) },
        issue = state.deviceAuthIssue,
        isWorking = state.isRemoving,
    )
}

@Composable
private fun FactoryRows(
    state: ManageFactoriesState,
    send: (ManageFactoriesIntent) -> Unit,
    navigator: TabNavigator,
) {
    CardGroup {
        ReorderableColumn(
            items = state.rows,
            keyOf = { it.id },
            onReordered = { rows -> send(ManageFactoriesIntent.Reordered(rows.map { it.id })) },
            reorderRejected = state.reorderRejected,
        ) { row, controls ->
            ManageRow(
                row,
                controls,
                onRemove = { send(ManageFactoriesIntent.RemoveRequested(row.id)) },
                onOpen = { navigator.push(AppRoute.EditFactory(row.id)) },
            )
        }
    }
}

/** What follows the list: how to reorder, why a reorder did not stick, and what happened to a removed key. */
@Composable
private fun Notes(state: ManageFactoriesState) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(stringResource(R.string.manage_hint), style = AppType.secondary, color = colors.textSecondary)
        if (state.reorderRejected) {
            Text(
                stringResource(R.string.manage_reorder_rejected),
                Modifier.testTag("manage-reorder-rejected"),
                style = AppType.secondary,
                color = colors.destructive,
            )
        }
        state.lastRemovedKey?.let {
            Text(
                stringResource(it.stringRes()),
                Modifier.testTag("manage-key-outcome"),
                style = AppType.secondary,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun RowText(
    row: ManageRowUi,
    status: FactoryStatusPresentation,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier
            .heightIn(min = Spacing.minimumTarget)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(vertical = Spacing.medium)
            .testTag("manage-open-${row.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.hairline),
    ) {
        Text(row.name, Modifier.testTag("manage-name-${row.id}"), style = AppType.cardTitle, color = colors.textPrimary)
        Text(
            row.address,
            Modifier.testTag("manage-address-${row.id}"),
            style = AppType.secondary,
            color = colors.textSecondary,
        )
        Text(
            stringResource(status.label.stringRes()),
            Modifier.testTag("manage-status-${row.id}"),
            style = AppType.label,
            color = status.tone.foreground(colors),
        )
    }
}

@Composable
private fun ManageRow(
    row: ManageRowUi,
    controls: RowControls,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = AppTheme.colors
    val status = FactoryStatusPresentation.of(row.status, ExpiryUi(KeyExpiryUi.NoExpiry, null))
    val up = stringResource(R.string.manage_move_up)
    val down = stringResource(R.string.manage_move_down)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minimumTarget)
                .semantics {
                    customActions =
                        listOfNotNull(
                            controls.moveUp?.let { move ->
                                CustomAccessibilityAction(up) {
                                    move()
                                    true
                                }
                            },
                            controls.moveDown?.let { move ->
                                CustomAccessibilityAction(down) {
                                    move()
                                    true
                                }
                            },
                        )
                }.testTag("manage-row-${row.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            BarIconButton(
                painterResource(R.drawable.ic_remove_circle),
                stringResource(R.string.manage_remove_action, row.name),
                onRemove,
                "manage-remove-${row.id}",
                tint = colors.destructive,
            )
            RowText(row, status, onOpen, Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_drag_handle),
                stringResource(R.string.manage_reorder_handle, row.name),
                controls.handle
                    .size(HandleSize)
                    .padding(Spacing.large)
                    .testTag("manage-handle-${row.id}"),
                tint = colors.textSecondary,
            )
        }
        if (controls.moveDown != null) HairlineDivider()
    }
}
