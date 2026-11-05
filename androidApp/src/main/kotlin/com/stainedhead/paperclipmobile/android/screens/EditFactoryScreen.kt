package com.stainedhead.paperclipmobile.android.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.diagnostics.PairingOutcome
import com.stainedhead.paperclipmobile.android.diagnostics.toKeyErrorClass
import com.stainedhead.paperclipmobile.android.diagnostics.toPairingOutcome
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberEditHolder
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.ConfirmDestructiveSheet
import com.stainedhead.paperclipmobile.android.shell.ConfirmSheet
import com.stainedhead.paperclipmobile.android.shell.ReportPairingWait
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.EditConfirmation
import com.stainedhead.paperclipmobile.presentation.EditInstanceIntent
import com.stainedhead.paperclipmobile.presentation.EditInstanceState
import com.stainedhead.paperclipmobile.presentation.EditMode
import com.stainedhead.paperclipmobile.presentation.PairingStatus

/**
 * Screen 9 (AND-022): add a factory, or edit one. Name, address and its check, default company, the person's access
 * and roles, the certificate pin, and pair again, sign out and remove. It renders `EditInstanceState`: Save is on when
 * the state says so, a changed address cannot be saved before it is checked, and removing asks the device owner inside
 * the shared use case. The approval page of a re-pair opens in a Custom Tab through the pairing browser port.
 */
@Composable
fun EditFactoryScreen(
    session: GraphSession,
    instanceId: String?,
    navigator: TabNavigator,
) {
    val holder = rememberEditHolder(session, instanceId)
    val state by holder.state.collectAsStateWithLifecycle()
    val send = holder::onIntent
    var showsDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.hasUnsavedChanges) showsDiscard = true else navigator.pop() }
    BackHandler(enabled = state.hasUnsavedChanges) { showsDiscard = true }
    ReportPairingWait(state.pairing == PairingStatus.Waiting)
    DisposableEffect(holder) {
        val stopListening = session.onPairingCancelled { send(EditInstanceIntent.PairCancelled) }
        onDispose { stopListening() }
    }
    RecordDiagnostics(session, state)
    LaunchedEffect(state.saved) { if (state.saved) navigator.pop() }
    LaunchedEffect(state.removed) { if (state.removed) navigator.popToRoot() }
    Column(Modifier.fillMaxSize().testTag("edit-factory")) {
        ScreenTitleBar(
            stringResource(if (state.mode == EditMode.Add) R.string.edit_title_add else R.string.edit_title_edit),
            Modifier.padding(horizontal = Spacing.page),
            onBack = leave,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.page),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        ) {
            EditFactoryBody("${session.id}-${instanceId ?: "new"}", state, send)
            Spacer(Modifier.height(Spacing.page))
        }
        BottomBar(state, leave) { send(EditInstanceIntent.SaveRequested) }
    }
    if (showsDiscard) {
        DiscardDialog(onKeep = { showsDiscard = false }, onDiscard = { navigator.pop() })
    }
    state.confirmation?.let { Confirmation(it, state, send) }
}

/** Tells the diagnostics how a re-pair ended and which key problem was seen: classes and outcomes, never details. */
@Composable
private fun RecordDiagnostics(
    session: GraphSession,
    state: EditInstanceState,
) {
    var wasWaiting by remember { mutableStateOf(false) }
    LaunchedEffect(state.error?.key) {
        state.error
            ?.key
            ?.toKeyErrorClass()
            ?.let(session.diagnostics::recordKeyError)
    }
    LaunchedEffect(state.pairing) {
        state.pairing.toPairingOutcome()?.let(session.diagnostics::recordPairing)
        if (wasWaiting && state.pairing == PairingStatus.Idle && state.error == null) {
            session.diagnostics.recordPairing(PairingOutcome.Approved)
        }
        wasWaiting = state.pairing == PairingStatus.Waiting
    }
}

@Composable
private fun BottomBar(
    state: EditInstanceState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        HairlineDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    AppTheme.colors.surface,
                ).padding(horizontal = Spacing.page, vertical = Spacing.large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            AppButton(
                stringResource(R.string.common_cancel),
                onCancel,
                Modifier.weight(1f).testTag("edit-cancel-button"),
                kind = ButtonKind.Secondary,
            )
            AppButton(
                stringResource(R.string.common_save),
                onSave,
                Modifier.weight(1f).testTag("edit-save-button"),
                enabled = state.canSave && !state.isBusy,
            )
        }
    }
}

@Composable
private fun DiscardDialog(
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.edit_discard_title)) },
        confirmButton = { TextButton(onDiscard) { Text(stringResource(R.string.edit_discard_confirm)) } },
        dismissButton = { TextButton(onKeep) { Text(stringResource(R.string.edit_discard_keep)) } },
        containerColor = AppTheme.colors.surface,
    )
}

@Composable
private fun EditFactoryBody(
    fieldsKey: String,
    state: EditInstanceState,
    send: (EditInstanceIntent) -> Unit,
) {
    state.error?.let { ErrorBanner(it, onDismiss = { send(EditInstanceIntent.MessageDismissed) }) }
    if (state.isOffline) OfflineNote()
    state.status?.let { StatusLine(it, state.expiry) }
    SectionTitle(R.string.edit_section_factory)
    EditFactoryFields(fieldsKey, state, send)
    if (state.companies.isNotEmpty()) EditCompanies(state, send)
    SectionTitle(R.string.edit_section_security)
    EditPins(fieldsKey, state, send)
    if (state.instanceId != null) EditActions(state, send)
    state.serverKeyOutcome?.let {
        Text(
            stringResource(it.stringRes()),
            Modifier.testTag("edit-key-outcome"),
            style = AppType.secondary,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/** The question before signing out or removing, worded from the confirmation the shared state holds. */
@Composable
private fun Confirmation(
    which: EditConfirmation,
    state: EditInstanceState,
    send: (EditInstanceIntent) -> Unit,
) {
    val name = state.name
    val onConfirm = { send(EditInstanceIntent.ConfirmationAccepted) }
    val onCancel = { send(EditInstanceIntent.ConfirmationDismissed) }
    when (which) {
        EditConfirmation.SignOut -> {
            ConfirmSheet(
                stringResource(R.string.edit_sign_out_title, name),
                stringResource(R.string.edit_sign_out_message),
                stringResource(R.string.edit_sign_out_confirm),
                onConfirm,
                onCancel,
                isWorking = state.isBusy,
            )
        }

        EditConfirmation.SignOutHereOnly -> {
            ConfirmSheet(
                stringResource(R.string.edit_sign_out_here_only_title, name),
                stringResource(R.string.edit_sign_out_here_only_message),
                stringResource(R.string.edit_sign_out_here_only_confirm),
                onConfirm,
                onCancel,
                isWorking = state.isBusy,
            )
        }

        EditConfirmation.Remove -> {
            ConfirmDestructiveSheet(
                stringResource(R.string.remove_title, name),
                stringResource(R.string.edit_remove_message),
                stringResource(R.string.remove_confirm),
                onConfirm,
                onCancel,
                issue = state.deviceAuthIssue,
                isWorking = state.isBusy,
            )
        }

        EditConfirmation.RemoveHereOnly -> {
            ConfirmDestructiveSheet(
                stringResource(R.string.remove_title, name),
                stringResource(R.string.remove_here_only_message),
                stringResource(R.string.remove_here_only_confirm),
                onConfirm,
                onCancel,
                issue = state.deviceAuthIssue,
                isWorking = state.isBusy,
            )
        }
    }
}
