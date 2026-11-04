package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.ResultCard
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.designsystem.StepIndicator
import com.stainedhead.paperclipmobile.android.designsystem.StepItem
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.ScreenHolder
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.CompanyUi
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.FirstRunState
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import com.stainedhead.paperclipmobile.presentation.ProbeStatus

private val HeaderBox = 48.dp
private val HeaderGlyph = 24.dp

private typealias FirstRunHolder = ScreenHolder<FirstRunState, FirstRunIntent>

/**
 * Screen 15: add the first factory in three steps (Address, Pair, Secure). It renders `FirstRunState` and sends
 * intents; every rule (what is valid, what is enabled, what happened) is shared Kotlin. [onTryDemo] is offered in
 * debug builds only. Typed text lives in this screen's saved state and only change intents go to the holder, because
 * the holder renders asynchronously; nothing from the pairing step is ever saved.
 */
@Composable
fun FirstRunScreen(
    session: GraphSession,
    onTryDemo: (() -> Unit)?,
) {
    val holder = session.firstRun ?: return
    val state by holder.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.page)
            .testTag("first-run"),
        verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
    ) {
        FirstRunHeader()
        StepIndicator(steps(), current = state.step.number())
        state.error?.let { ErrorBanner(it, onDismiss = { holder.send(FirstRunIntent.ErrorDismissed) }) }
        when (state.step) {
            FirstRunStep.Address -> AddressStep(session.id, holder, state, onTryDemo)
            FirstRunStep.Pair -> PairStep(holder, state)
            FirstRunStep.Secure, FirstRunStep.Done -> SecureStep(holder, state)
        }
    }
}

/** 1 Address, 2 Pair, 3 Secure; the end of the flow still shows the last step. */
private fun FirstRunStep.number(): Int = minOf(ordinal, FirstRunStep.Secure.ordinal) + 1

@Composable
private fun steps(): List<StepItem> =
    listOf(
        R.string.step_address,
        R.string.step_pair,
        R.string.step_secure,
    ).mapIndexed { index, title ->
        val name = stringResource(title)
        StepItem(index + 1, name, stringResource(R.string.step_description, (index + 1).toString(), name))
    }

@Composable
private fun FirstRunHeader() {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Box(
            Modifier.size(HeaderBox).clip(RoundedCornerShape(Radius.card)).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_server), null, Modifier.size(HeaderGlyph), tint = colors.onAccent)
        }
        Text(
            stringResource(R.string.first_run_headline),
            Modifier.semantics { heading() }.testTag("first-run-title"),
            style = AppType.largeTitle,
            color = colors.textPrimary,
        )
        Text(stringResource(R.string.first_run_intro), style = AppType.body, color = colors.textSecondary)
    }
}

/** Step 1: the address and name, the check, and the way on. */
@Composable
private fun AddressStep(
    sessionId: Int,
    holder: FirstRunHolder,
    state: FirstRunState,
    onTryDemo: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    CardSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.roomy)) {
            AddressFields(sessionId, holder, state)
            AppButton(
                stringResource(if (state.probe == ProbeStatus.Probing) R.string.checking else R.string.check_address),
                { holder.send(FirstRunIntent.ProbeRequested) },
                Modifier.testTag("check-address-button"),
                kind = ButtonKind.Secondary,
                enabled = state.canContinue && state.probe != ProbeStatus.Probing,
            )
        }
    }
    ProbeResult(state)
    Text(stringResource(R.string.first_run_key_note), style = AppType.secondary, color = colors.textSecondary)
    AppButton(
        stringResource(R.string.first_run_continue_to_pairing),
        { holder.send(FirstRunIntent.ContinueRequested) },
        Modifier.testTag("continue-button"),
        enabled = state.probe == ProbeStatus.Accepted && !state.isBusy,
    )
    if (onTryDemo != null) {
        AppButton(
            stringResource(R.string.first_run_try_demo),
            onTryDemo,
            Modifier.testTag("try-demo-button"),
            kind = ButtonKind.Muted,
        )
    }
}

@Composable
private fun ProbeResult(state: FirstRunState) =
    ProbeResultCard(state.probe, state.probeRefusal, state.exposure, state.serverVersion, "probe-result")

/** Step 2: approve this phone in the browser tab; the shared use case polls until the person decides. */
@Composable
private fun PairStep(
    holder: FirstRunHolder,
    state: FirstRunState,
) {
    val colors = AppTheme.colors
    CardSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
            Text(
                stringResource(R.string.pair_title),
                Modifier.semantics { heading() },
                style = AppType.title,
                color = colors.textPrimary,
            )
            Text(stringResource(R.string.pair_intro), style = AppType.body, color = colors.textSecondary)
            PairingStatusLine(state.pairing)
        }
    }
    if (state.pairing == PairingStatus.Waiting) {
        AppButton(
            stringResource(R.string.pair_cancel),
            { holder.send(FirstRunIntent.PairCancelled) },
            Modifier.testTag("pair-cancel-button"),
            kind = ButtonKind.Secondary,
        )
    } else {
        AppButton(
            stringResource(if (state.pairing == PairingStatus.Idle) R.string.pair_open else R.string.common_retry),
            { holder.send(FirstRunIntent.PairRequested) },
            Modifier.testTag("pair-button"),
            enabled = !state.isBusy,
        )
    }
    AppButton(
        stringResource(R.string.common_back),
        { holder.send(FirstRunIntent.BackRequested) },
        Modifier.testTag("back-button"),
        kind = ButtonKind.Muted,
    )
}

@Composable
private fun PairingStatusLine(pairing: PairingStatus) {
    val colors = AppTheme.colors
    when (pairing) {
        PairingStatus.Waiting -> {
            Text(
                stringResource(pairing.stringRes()),
                Modifier.testTag("pairing-status"),
                style = AppType.body,
                color = colors.textPrimary,
            )
        }

        PairingStatus.Cancelled, PairingStatus.Expired, PairingStatus.Failed -> {
            Text(
                stringResource(pairing.stringRes()),
                Modifier.testTag("pairing-status"),
                style = AppType.body,
                color = colors.warn,
            )
        }

        PairingStatus.Idle -> {}
    }
}

/** Step 3: the key is saved; choose the company that opens first and whether the app locks. */
@Composable
private fun SecureStep(
    holder: FirstRunHolder,
    state: FirstRunState,
) {
    val colors = AppTheme.colors
    Text(
        stringResource(R.string.secure_title),
        Modifier.semantics { heading() },
        style = AppType.title,
        color = colors.textPrimary,
    )
    Text(stringResource(R.string.secure_intro), style = AppType.body, color = colors.textSecondary)
    if (state.companies.isNotEmpty()) {
        SectionHeader(stringResource(R.string.secure_companies))
        CardGroup {
            state.companies.forEachIndexed { index, company ->
                if (index > 0) HairlineDivider()
                CompanyChoiceRow(company, state.defaultCompanyId == company.id) {
                    holder.send(FirstRunIntent.DefaultCompanyChosen(company.id))
                }
            }
        }
    }
    CardGroup {
        CardRow(
            title = stringResource(R.string.secure_app_lock),
            help = stringResource(R.string.secure_app_lock_help),
            modifier = Modifier.testTag("app-lock-row"),
        ) {
            Switch(
                checked = state.appLockEnabled,
                onCheckedChange = { holder.send(FirstRunIntent.AppLockChosen(it)) },
                modifier = Modifier.testTag("app-lock-toggle"),
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
            )
        }
    }
    AppButton(
        stringResource(R.string.secure_finish),
        { holder.send(FirstRunIntent.FinishRequested) },
        Modifier.testTag("finish-button"),
        enabled = !state.isBusy,
        icon = painterResource(R.drawable.ic_lock),
    )
}

/** The address and the name. Typed text lives here; only change intents go to the holder (ADR-108). */
@Composable
private fun AddressFields(
    sessionId: Int,
    holder: FirstRunHolder,
    state: FirstRunState,
) {
    var address by rememberSaveable(sessionId) { mutableStateOf(state.address) }
    var name by rememberSaveable(sessionId) { mutableStateOf(state.name) }
    FormField(
        title = stringResource(R.string.field_address_label),
        value = address,
        onValueChange = {
            address = it
            holder.send(FirstRunIntent.AddressChanged(it))
        },
        tag = "address-field",
        placeholder = stringResource(R.string.field_address_placeholder),
        help =
            state.normalizedAddress?.let { stringResource(R.string.first_run_will_connect, it) }
                ?: stringResource(R.string.field_address_help),
        issue = state.issue?.let { stringResource(it.stringRes()) },
        keyboardType = KeyboardType.Uri,
        capitalization = KeyboardCapitalization.None,
    )
    FormField(
        title = stringResource(R.string.field_name_label),
        value = name,
        onValueChange = {
            name = it
            holder.send(FirstRunIntent.NameChanged(it))
        },
        tag = "name-field",
        placeholder = stringResource(R.string.field_name_placeholder),
        help = stringResource(R.string.field_name_help),
        issue = state.nameIssue?.let { stringResource(it.stringRes()) },
    )
}
