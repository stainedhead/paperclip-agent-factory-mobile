package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.ChoiceChip
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.RelativeAge
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusPill
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.designsystem.compactText
import com.stainedhead.paperclipmobile.android.designsystem.rememberNowMillis
import com.stainedhead.paperclipmobile.android.designsystem.spokenText
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberSettingsHolder
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.DeviceKeyUi
import com.stainedhead.paperclipmobile.presentation.PairedDevicesStatus
import com.stainedhead.paperclipmobile.presentation.PairedDevicesUi
import com.stainedhead.paperclipmobile.presentation.SettingsIntent

private val Spinner = 24.dp
private const val MILLIS_PER_SECOND = 1000L

/**
 * "Paired devices and keys" (AND-024): the board keys of the person's account on one factory, this phone's marked, each
 * with Revoke. The shared holder owns the list, the load (the latest request wins) and the revoke confirmation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PairedDevicesScreen(
    session: GraphSession,
    navigator: TabNavigator,
) {
    val holder = rememberSettingsHolder(session, "devices")
    val state by holder.state.collectAsStateWithLifecycle()
    val send = holder::onIntent
    var chosen by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    val factoryId = chosen ?: state.factories.firstOrNull()?.id
    LaunchedEffect(factoryId) { factoryId?.let { send(SettingsIntent.PairedDevicesRequested(it)) } }
    Column(Modifier.fillMaxSize().testTag("paired-devices")) {
        ScreenTitleBar(
            stringResource(R.string.devices_title),
            Modifier.padding(horizontal = Spacing.page),
            onBack = navigator.pop,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.page),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        ) {
            state.error?.let { ErrorBanner(it, onDismiss = { send(SettingsIntent.MessageDismissed) }) }
            if (state.factories.size > 1) {
                Text(
                    stringResource(R.string.devices_choose_factory),
                    style = AppType.button,
                    color = AppTheme.colors.textPrimary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    for (factory in state.factories) {
                        ChoiceChip(factory.name, factoryId == factory.id, "devices-factory-${factory.id}") {
                            chosen =
                                factory.id
                        }
                    }
                }
            }
            state.profileName?.let {
                Text(
                    stringResource(R.string.settings_signed_in_as, it),
                    Modifier.testTag("devices-profile"),
                    style = AppType.body,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Devices(
                state.pairedDevices,
            ) { instanceId, keyId -> send(SettingsIntent.RevokeKeyRequested(instanceId, keyId)) }
        }
    }
    SettingsConfirmationSheet(state.confirmation, state.isBusy, send)
}

@Composable
private fun Devices(
    devices: PairedDevicesUi?,
    onRevoke: (instanceId: String, keyId: String) -> Unit,
) {
    when (devices?.status) {
        PairedDevicesStatus.Loading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            ) {
                CircularProgressIndicator(Modifier.size(Spinner), color = AppTheme.colors.accent, strokeWidth = 2.dp)
                Text(stringResource(PairedDevicesStatus.Loading.stringRes()), style = AppType.body)
            }
        }

        PairedDevicesStatus.Failed -> {
            Text(
                stringResource(PairedDevicesStatus.Failed.stringRes()),
                Modifier.testTag("devices-failed"),
                style = AppType.body,
                color = AppTheme.colors.destructive,
            )
        }

        PairedDevicesStatus.Loaded -> {
            if (devices.keys.isEmpty()) {
                Text(stringResource(R.string.devices_empty), style = AppType.body)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
                    for (key in devices.keys) DeviceKeyCard(key) { onRevoke(devices.instanceId, key.id) }
                }
            }
        }

        PairedDevicesStatus.Idle, null -> {}
    }
}

@Composable
private fun DeviceKeyCard(
    key: DeviceKeyUi,
    onRevoke: () -> Unit,
) {
    CardSurface(Modifier.testTag("device-key-${key.id}")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Text(key.name, style = AppType.cardTitle, color = AppTheme.colors.textPrimary)
            if (key.isCurrentDevice) {
                StatusPill(
                    stringResource(R.string.devices_this_phone),
                    StatusTone.Success,
                    Modifier.testTag("device-this-phone-${key.id}"),
                )
            }
            LastUsed(key.lastUsedAtEpochMillis)
            val revokeName = stringResource(R.string.devices_revoke_named, key.name)
            AppButton(
                stringResource(R.string.devices_revoke),
                onRevoke,
                Modifier.semantics { contentDescription = revokeName }.testTag("revoke-${key.id}"),
                kind = ButtonKind.Destructive,
            )
        }
    }
}

@Composable
private fun LastUsed(epochMillis: Long?) {
    val colors = AppTheme.colors
    if (epochMillis == null) {
        Text(stringResource(R.string.devices_never_used), style = AppType.secondary, color = colors.textSecondary)
        return
    }
    val now by rememberNowMillis()
    val age = RelativeAge.of((now - epochMillis) / MILLIS_PER_SECOND)
    val spoken = stringResource(R.string.devices_last_used, age.spokenText())
    Text(
        stringResource(R.string.devices_last_used, age.compactText()),
        Modifier.semantics { contentDescription = spoken },
        style = AppType.secondary,
        color = colors.textSecondary,
    )
}
