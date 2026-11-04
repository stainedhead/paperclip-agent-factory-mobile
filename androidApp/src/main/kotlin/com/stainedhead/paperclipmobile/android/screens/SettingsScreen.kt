package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.CardGroup
import com.stainedhead.paperclipmobile.android.designsystem.CardRow
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.foreground
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberSettingsHolder
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.SettingsFactoryUi
import com.stainedhead.paperclipmobile.presentation.SettingsIntent
import com.stainedhead.paperclipmobile.presentation.SettingsState

private val Chevron = 20.dp
private const val DOWN_ARROW = 90f

/**
 * Screen 13 (AND-024): this phone (device name, app lock, lock after, the destructive-action note), each factory, the
 * doors to paired devices and diagnostics, and signing out everywhere. It renders `SettingsState`; every change is an
 * intent, and the questions before signing out are the confirmations the shared state holds.
 */
@Composable
fun SettingsScreen(
    session: GraphSession,
    navigator: TabNavigator,
) {
    val holder = rememberSettingsHolder(session, "settings")
    val state by holder.state.collectAsStateWithLifecycle()
    val send = holder::onIntent
    Column(Modifier.fillMaxSize().testTag("settings")) {
        ScreenTitleBar(
            stringResource(R.string.settings_title),
            Modifier.padding(horizontal = Spacing.page),
            onBack = navigator.pop,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.page),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        ) {
            state.error?.let { ErrorBanner(it, onDismiss = { send(SettingsIntent.MessageDismissed) }) }
            Notices(state)
            ThisPhone(session.id, state, send)
            SectionHeader(stringResource(R.string.settings_each_factory))
            Factories(state.factories) { navigator.push(AppRoute.EditFactory(it)) }
            SectionHeader(stringResource(R.string.settings_keys_and_devices))
            Doors(navigator)
            Text(
                stringResource(R.string.settings_account_help),
                style = AppType.secondary,
                color = AppTheme.colors.textSecondary,
            )
            AppButton(
                stringResource(R.string.settings_sign_out_everywhere),
                { send(SettingsIntent.SignOutEverywhereRequested) },
                Modifier.testTag("settings-sign-out-everywhere"),
                kind = ButtonKind.Destructive,
                enabled = !state.isBusy,
                icon = painterResource(R.drawable.ic_logout),
            )
        }
    }
    SettingsConfirmationSheet(state.confirmation, state.isBusy, send)
}

/** What the last sign out or revoke could not do, said before anything else. */
@Composable
private fun Notices(state: SettingsState) {
    val colors = AppTheme.colors
    state.lastSignOutKey?.let {
        Text(
            stringResource(it.stringRes()),
            Modifier.testTag("settings-key-outcome"),
            style = AppType.secondary,
            color = colors.textSecondary,
        )
    }
    if (state.unconfirmedRevokes > 0) {
        Text(
            pluralStringResource(
                R.plurals.settings_unconfirmed_revokes,
                state.unconfirmedRevokes,
                state.unconfirmedRevokes,
            ),
            Modifier.testTag("settings-unconfirmed"),
            style = AppType.secondary,
            color = colors.warn,
        )
    }
    if (state.signOutFailures > 0) {
        Text(
            pluralStringResource(R.plurals.settings_sign_out_failures, state.signOutFailures, state.signOutFailures),
            Modifier.testTag("settings-signout-failures"),
            style = AppType.secondary,
            color = colors.destructive,
        )
    }
}

@Composable
private fun ThisPhone(
    sessionId: Int,
    state: SettingsState,
    send: (SettingsIntent) -> Unit,
) {
    val colors = AppTheme.colors
    SectionHeader(stringResource(R.string.settings_this_phone))
    CardSurface { DeviceName(sessionId, state, send) }
    CardGroup {
        CardRow(title = stringResource(R.string.settings_app_lock), modifier = Modifier.testTag("settings-app-lock")) {
            Switch(
                checked = state.appLockEnabled,
                onCheckedChange = { send(SettingsIntent.AppLockToggled(it)) },
                modifier = Modifier.testTag("settings-app-lock-toggle"),
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
            )
        }
    }
    CardGroup { IdlePeriodRow(state, send) }
    CardGroup {
        CardRow(
            title = stringResource(R.string.settings_destructive),
            help = stringResource(R.string.settings_destructive_help),
        ) {
            Switch(
                checked = state.destructiveActionAuthAlwaysOn,
                onCheckedChange = {},
                enabled = false,
                modifier = Modifier.testTag("settings-destructive-toggle"),
            )
        }
    }
}

/**
 * The device name. Typed text lives here so the cursor never waits for the holder; the name is saved when the keyboard
 * says Done, when the field loses focus and when the screen goes, and only when it was edited.
 */
@Composable
private fun DeviceName(
    sessionId: Int,
    state: SettingsState,
    send: (SettingsIntent) -> Unit,
) {
    var draft by rememberSaveable(sessionId) { mutableStateOf(state.deviceName) }
    var edited by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val saved by rememberUpdatedState(state.deviceName)
    val current by rememberUpdatedState(draft)
    val commit = {
        if (edited && current != saved) send(SettingsIntent.DeviceNameCommitted(current))
        edited = false
    }
    val commitLatest by rememberUpdatedState(commit)
    LaunchedEffect(state.deviceName) { if (!edited) draft = state.deviceName }
    DisposableEffect(Unit) { onDispose { commitLatest() } }
    FormField(
        title = stringResource(R.string.settings_device_name),
        value = draft,
        onValueChange = {
            draft = it
            edited = true
        },
        tag = "settings-device-name",
        help = stringResource(R.string.settings_device_name_help),
        issue = if (state.deviceNameInvalid) stringResource(R.string.settings_device_name_invalid) else null,
        onDone = { commit() },
        onFocusChanged = { focused ->
            if (focused) {
                hadFocus = true
            } else if (hadFocus) {
                hadFocus = false
                commit()
            }
        },
    )
}

/** "Lock after": the periods the core offers, in a menu. */
@Composable
private fun IdlePeriodRow(
    state: SettingsState,
    send: (SettingsIntent) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        CardRow(
            title = stringResource(R.string.settings_idle_period),
            modifier =
                Modifier
                    .clickable(role = Role.DropdownList) { open = true }
                    .semantics(mergeDescendants = true) {}
                    .testTag("settings-idle-period"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(state.idlePeriod.stringRes()), style = AppType.body, color = AppTheme.colors.accent)
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    null,
                    Modifier.size(Chevron).rotate(DOWN_ARROW),
                    tint = AppTheme.colors.accent,
                )
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            for (period in state.idleOptions) {
                DropdownMenuItem(
                    text = { Text(stringResource(period.stringRes())) },
                    onClick = {
                        send(SettingsIntent.IdlePeriodChosen(period))
                        open = false
                    },
                    modifier = Modifier.testTag("idle-option-${period.name}"),
                )
            }
        }
    }
}

@Composable
private fun Factories(
    factories: List<SettingsFactoryUi>,
    onOpen: (String) -> Unit,
) {
    CardGroup {
        factories.forEachIndexed { index, factory ->
            if (index > 0) HairlineDivider()
            val status = FactoryStatusPresentation.of(factory.status, factory.expiry)
            CardRow(
                title = factory.name,
                modifier = Modifier.clickable { onOpen(factory.id) }.testTag("settings-factory-${factory.id}"),
            ) {
                Text(
                    stringResource(status.label.stringRes()),
                    Modifier.testTag("settings-factory-status-${factory.id}"),
                    style = AppType.body,
                    color = status.tone.foreground(AppTheme.colors),
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    null,
                    Modifier.size(Chevron),
                    tint = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Doors(navigator: TabNavigator) {
    CardGroup {
        Door(R.string.settings_paired_devices, "settings-paired-devices") { navigator.push(AppRoute.PairedDevices) }
        HairlineDivider()
        Door(R.string.settings_diagnostics, "settings-diagnostics", R.string.settings_diagnostics_help) {
            navigator.push(AppRoute.Diagnostics)
        }
    }
}

@Composable
private fun Door(
    title: Int,
    tag: String,
    help: Int? = null,
    onClick: () -> Unit,
) {
    CardRow(
        title = stringResource(title),
        help = help?.let { stringResource(it) },
        modifier = Modifier.heightIn(min = Spacing.minimumTarget).clickable(onClick = onClick).testTag(tag),
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            null,
            Modifier.size(Chevron),
            tint = AppTheme.colors.textSecondary,
        )
    }
}
