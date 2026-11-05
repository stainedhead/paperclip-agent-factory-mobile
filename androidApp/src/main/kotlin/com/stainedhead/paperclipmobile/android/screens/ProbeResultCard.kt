package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ResultCard
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.presentation.ProbeStatus

/**
 * What the check of an address found, as first run and the edit screen both show it: "Server found" with how the
 * server is reached and its version, or "This server cannot be used" with the reason. Nothing for the other states.
 */
@Composable
fun ProbeResultCard(
    probe: ProbeStatus,
    refusal: ProbeRefusal?,
    exposure: Exposure?,
    serverVersion: String?,
    tag: String,
) {
    val colors = AppTheme.colors
    when (probe) {
        ProbeStatus.Accepted -> {
            ResultCard(stringResource(ProbeStatus.Accepted.stringRes()), StatusTone.Success, Modifier.testTag(tag)) {
                val details =
                    listOfNotNull(
                        exposure?.let { stringResource(it.stringRes()) },
                        serverVersion?.let { stringResource(R.string.probe_version, it) },
                    )
                Text(details.joinToString(" · "), style = AppType.body, color = colors.textPrimary)
            }
        }

        ProbeStatus.Refused -> {
            ResultCard(stringResource(ProbeStatus.Refused.stringRes()), StatusTone.Critical, Modifier.testTag(tag)) {
                refusal?.let { Text(stringResource(it.stringRes()), style = AppType.body, color = colors.textPrimary) }
            }
        }

        ProbeStatus.Idle, ProbeStatus.Probing, ProbeStatus.Failed -> {}
    }
}
