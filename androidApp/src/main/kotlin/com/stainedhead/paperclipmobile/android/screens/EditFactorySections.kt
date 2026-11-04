package com.stainedhead.paperclipmobile.android.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.BarIconButton
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.CardGroup
import com.stainedhead.paperclipmobile.android.designsystem.CardRow
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusPill
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.EditInstanceIntent
import com.stainedhead.paperclipmobile.presentation.EditInstanceState
import com.stainedhead.paperclipmobile.presentation.ExpiryUi
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.KeyExpiryUi
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import com.stainedhead.paperclipmobile.presentation.ProbeStatus

private val NoteGlyph = 16.dp
private val Progress = 20.dp

@Composable
internal fun SectionTitle(
    @StringRes title: Int,
) = SectionHeader(stringResource(title))

@Composable
internal fun OfflineNote() {
    val colors = AppTheme.colors
    Row(
        Modifier.testTag("edit-offline"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Icon(painterResource(R.drawable.ic_offline), null, Modifier.size(NoteGlyph), tint = colors.warn)
        Text(stringResource(R.string.edit_offline_note), style = AppType.secondary, color = colors.warn)
    }
}

@Composable
internal fun StatusLine(
    status: FactoryStatusUi,
    expiry: ExpiryUi?,
) {
    val presentation = FactoryStatusPresentation.of(status, expiry ?: ExpiryUi(KeyExpiryUi.NoExpiry, null))
    StatusPill(stringResource(presentation.label.stringRes()), presentation.tone, Modifier.testTag("edit-status"))
}

/**
 * The name and the address. Typed text lives here, so the cursor never waits for the holder; only change intents go
 * to it. The fields are filled from the holder once the factory is loaded, and typed text that came back from a saved
 * state (process death) is told to the holder again (AND-019).
 */
@Composable
internal fun EditFactoryFields(
    fieldsKey: String,
    state: EditInstanceState,
    send: (EditInstanceIntent) -> Unit,
) {
    var name by rememberSaveable(fieldsKey) { mutableStateOf("") }
    var address by rememberSaveable(fieldsKey) { mutableStateOf("") }
    var seeded by rememberSaveable(fieldsKey) { mutableStateOf(false) }
    val loaded = state.instanceId == null || state.savedAddress.isNotEmpty()
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        if (seeded) {
            send(EditInstanceIntent.NameChanged(name))
            send(EditInstanceIntent.AddressChanged(address))
        } else {
            name = state.name
            address = state.address
            seeded = true
        }
    }
    CardSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.roomy)) {
            FormField(
                title = stringResource(R.string.field_name_label),
                value = name,
                onValueChange = {
                    name = it
                    send(EditInstanceIntent.NameChanged(it))
                },
                tag = "edit-name",
                placeholder = stringResource(R.string.field_name_placeholder),
                help = stringResource(R.string.field_name_help),
                issue = state.nameIssue?.let { stringResource(it.stringRes()) },
            )
            FormField(
                title = stringResource(R.string.field_address_label),
                value = address,
                onValueChange = {
                    address = it
                    send(EditInstanceIntent.AddressChanged(it))
                },
                tag = "edit-address",
                placeholder = stringResource(R.string.field_address_placeholder),
                help = stringResource(R.string.field_address_help),
                issue = state.addressIssue?.let { stringResource(it.stringRes()) },
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
            )
            AddressCheck(state, send)
        }
    }
}

/** The warning that a new address drops the key, the check, and what the check found. */
@Composable
private fun AddressCheck(
    state: EditInstanceState,
    send: (EditInstanceIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.roomy)) {
        if (state.addressChangeClearsKey) {
            Text(
                stringResource(R.string.edit_address_change_warning),
                Modifier.testTag("edit-address-warning"),
                style = AppType.secondary,
                color = AppTheme.colors.warn,
            )
        }
        AppButton(
            stringResource(if (state.probe == ProbeStatus.Probing) R.string.checking else R.string.check_address),
            { send(EditInstanceIntent.ProbeRequested) },
            Modifier.testTag("edit-check-address"),
            kind = ButtonKind.Secondary,
            enabled = state.canCheckAddress,
        )
        ProbeResultCard(state.probe, state.probeRefusal, state.exposure, state.serverVersion, "edit-probe-result")
    }
}

/** The company that opens first, the person's role in each company, and how long the board key lasts. */
@Composable
internal fun EditCompanies(
    state: EditInstanceState,
    send: (EditInstanceIntent) -> Unit,
) {
    SectionTitle(R.string.edit_open_on_launch)
    val chosen = state.defaultCompanyId ?: state.companies.first().id
    CardGroup(Modifier.testTag("edit-default-company")) {
        state.companies.forEachIndexed { index, company ->
            if (index > 0) HairlineDivider()
            CompanyChoiceRow(
                company,
                chosen == company.id,
            ) { send(EditInstanceIntent.DefaultCompanyChosen(company.id)) }
        }
    }
    SectionHeader(
        state.profileName?.let { stringResource(R.string.edit_section_access, it) }
            ?: stringResource(R.string.edit_section_access_anonymous),
    )
    CardGroup {
        for (company in state.companies) {
            CardRow(
                title = company.name,
                modifier = Modifier.testTag("edit-access-company-${company.id}").semantics(mergeDescendants = true) {},
            ) {
                Text(
                    stringResource(company.role.stringRes()),
                    style = AppType.body,
                    color = AppTheme.colors.textSecondary,
                )
            }
            HairlineDivider()
        }
        CardRow(
            title = stringResource(R.string.edit_board_key),
            modifier = Modifier.testTag("edit-board-key").semantics(mergeDescendants = true) {},
        ) {
            val expiry = ExpiryPresentation.of(state.expiry ?: ExpiryUi(KeyExpiryUi.NoExpiry, null))
            Text(expiry.text().orEmpty(), style = AppType.body, color = AppTheme.colors.textSecondary)
        }
    }
}

/** Certificate pinning: a switch, the pins, and a field to add one. */
@Composable
internal fun EditPins(
    fieldsKey: String,
    state: EditInstanceState,
    send: (EditInstanceIntent) -> Unit,
) {
    var pin by rememberSaveable(fieldsKey) { mutableStateOf("") }
    LaunchedEffect(state.pins) { pin = "" }
    val colors = AppTheme.colors
    CardGroup {
        CardRow(title = stringResource(R.string.edit_pin_title), help = stringResource(R.string.edit_pin_help)) {
            Switch(
                checked = state.pinningEnabled,
                onCheckedChange = { send(EditInstanceIntent.PinningToggled(it)) },
                modifier = Modifier.testTag("edit-pin-toggle"),
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
            )
        }
        if (state.pinningEnabled) {
            for ((index, existing) in state.pins.withIndex()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        existing,
                        Modifier.weight(1f),
                        style = AppType.secondary,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                    BarIconButton(
                        painterResource(R.drawable.ic_remove_circle),
                        stringResource(R.string.edit_pin_remove, existing),
                        { send(EditInstanceIntent.PinRemoved(existing)) },
                        "edit-pin-remove-$index",
                        tint = colors.destructive,
                    )
                }
            }
        }
        if (state.pinEntryOpen) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                FormField(
                    title = stringResource(R.string.edit_pin_title),
                    value = pin,
                    onValueChange = { pin = it },
                    tag = "edit-pin-field",
                    placeholder = stringResource(R.string.edit_pin_placeholder),
                    issue = if (state.pinInvalid) stringResource(R.string.edit_pin_invalid) else null,
                    capitalization = KeyboardCapitalization.None,
                )
                AppButton(
                    stringResource(R.string.edit_pin_add),
                    { send(EditInstanceIntent.PinAdded(pin)) },
                    Modifier.testTag("edit-pin-add"),
                    kind = ButtonKind.Secondary,
                )
            }
        }
    }
}

/** Pair again, sign out and remove, for a factory that already exists. */
@Composable
internal fun EditActions(
    state: EditInstanceState,
    send: (EditInstanceIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        if (state.pairing == PairingStatus.Waiting) {
            Row(
                Modifier.testTag("edit-pairing-status").semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            ) {
                CircularProgressIndicator(Modifier.size(Progress), color = AppTheme.colors.accent, strokeWidth = 2.dp)
                Text(stringResource(PairingStatus.Waiting.stringRes()), style = AppType.body)
            }
            AppButton(
                stringResource(R.string.pair_cancel),
                { send(EditInstanceIntent.PairCancelled) },
                Modifier.testTag("edit-pair-cancel"),
                kind = ButtonKind.Secondary,
            )
        } else {
            if (state.pairing != PairingStatus.Idle) {
                Text(
                    stringResource(state.pairing.stringRes()),
                    Modifier.testTag("edit-pairing-status"),
                    style = AppType.body,
                    color = AppTheme.colors.warn,
                )
            }
            AppButton(
                stringResource(R.string.edit_pair_again),
                { send(EditInstanceIntent.RePairRequested) },
                Modifier.testTag("edit-pair-again"),
                kind = ButtonKind.Secondary,
                enabled = !state.isOffline && !state.isBusy,
            )
            AppButton(
                stringResource(R.string.edit_sign_out),
                { send(EditInstanceIntent.SignOutRequested) },
                Modifier.testTag("edit-sign-out"),
                kind = ButtonKind.Secondary,
                enabled = !state.isBusy,
            )
        }
        AppButton(
            stringResource(R.string.edit_remove),
            { send(EditInstanceIntent.RemoveRequested) },
            Modifier.testTag("edit-remove"),
            kind = ButtonKind.Destructive,
            enabled = !state.isBusy,
            icon = painterResource(R.drawable.ic_lock),
        )
    }
}
