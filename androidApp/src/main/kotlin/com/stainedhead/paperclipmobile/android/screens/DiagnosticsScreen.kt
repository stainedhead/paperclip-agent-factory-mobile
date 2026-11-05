package com.stainedhead.paperclipmobile.android.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.CardGroup
import com.stainedhead.paperclipmobile.android.designsystem.CardRow
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.diagnostics.DiagnosticsReport
import com.stainedhead.paperclipmobile.android.diagnostics.label
import com.stainedhead.paperclipmobile.android.diagnostics.stringRes
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.TabNavigator

/**
 * Diagnostics (AND-018): the app version, the server-compatibility result, the last graph-open outcome, the last key
 * storage and unlock problems and the last pairing outcome, as typed values only. "Copy report" puts the redacted text
 * on the clipboard for the person to paste where they choose; nothing is sent anywhere and nothing here can hold an
 * address, a name, a key or a token.
 */
@Composable
fun DiagnosticsScreen(
    session: GraphSession,
    navigator: TabNavigator,
) {
    val report by session.diagnostics.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.diagnostics_title)
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("diagnostics")) {
        ScreenTitleBar(
            stringResource(R.string.diagnostics_title),
            Modifier.padding(horizontal = Spacing.page),
            onBack = navigator.pop,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.page),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        ) {
            Text(
                stringResource(R.string.diagnostics_intro),
                style = AppType.body,
                color = AppTheme.colors.textSecondary,
            )
            Values(report)
            AppButton(
                stringResource(R.string.diagnostics_copy),
                {
                    copyToClipboard(context, clipLabel, report.toReportText())
                    copied = true
                },
                Modifier.testTag("diagnostics-copy"),
            )
            if (copied) {
                Text(
                    stringResource(R.string.diagnostics_copied),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("diagnostics-copied"),
                    style = AppType.secondary,
                    color = AppTheme.colors.success,
                )
            }
        }
    }
}

@Composable
private fun Values(report: DiagnosticsReport) {
    CardGroup {
        Row(R.string.diagnostics_app_version, "diagnostics-app-version", report.appVersion)
        HairlineDivider()
        Row(
            R.string.diagnostics_server_compatibility,
            "diagnostics-server-compatibility",
            report.serverCompatibility.label { it.stringRes() },
        )
        HairlineDivider()
        Row(R.string.diagnostics_graph_open, "diagnostics-graph-open", report.graphOpen.label { it.stringRes() })
        HairlineDivider()
        Row(R.string.diagnostics_key_error, "diagnostics-key-error", report.keystoreError.label { it.stringRes() })
        HairlineDivider()
        Row(R.string.diagnostics_biometric, "diagnostics-biometric", report.biometricResult.label { it.stringRes() })
        HairlineDivider()
        Row(R.string.diagnostics_pairing, "diagnostics-pairing", report.pairing.label { it.stringRes() })
    }
}

@Composable
private fun Row(
    title: Int,
    tag: String,
    value: String,
) {
    CardRow(
        title = stringResource(title),
        modifier = Modifier.testTag(tag).semantics(mergeDescendants = true) {},
    ) { Text(value, style = AppType.body, color = AppTheme.colors.textSecondary) }
}

private fun copyToClipboard(
    context: Context,
    label: String,
    text: String,
) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}
