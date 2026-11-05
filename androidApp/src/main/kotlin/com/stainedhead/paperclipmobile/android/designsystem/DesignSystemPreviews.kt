package com.stainedhead.paperclipmobile.android.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi

private const val SAMPLE_UPDATED_AT = 1_000_000L
private const val SAMPLE_NOW = 1_012_000L

@Composable
private fun PreviewSurface(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    PaperclipTheme(darkTheme = darkTheme) {
        Column(
            Modifier.background(AppTheme.colors.background).padding(Spacing.page),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) { content() }
    }
}

@Composable
private fun ButtonsAndPills() {
    for (kind in ButtonKind.entries) AppButton(kind.name, {}, kind = kind)
    AppButton("Disabled", {}, enabled = false)
    for (tone in StatusTone.entries) StatusPill(tone.name, tone)
    CompositionLocalProvider(LocalReferenceMillis provides SAMPLE_NOW) { UpdatedLabel(SAMPLE_UPDATED_AT) }
    LoadingState(stringResource(R.string.common_loading))
}

@Composable
private fun Cards() {
    AttentionCard(
        severity = SeverityUi.Critical,
        kind = "Decision · Plans queue",
        age = { "8m" },
        title = "Ship the billing migration to production?",
        subtitle = "CTO proposes 3 options · PAP-1182 · blocks 4 tasks",
        onOpen = {},
    ) { AppButton("Review options", {}) }
    ErrorBanner(ErrorUi(ErrorKey.Rejected, "Budget must be higher."), onRetry = {})
    DisabledReason(ActionsDisabledReason.Offline)
    OfflineBanner(SAMPLE_UPDATED_AT)
}

@Preview(name = "Cards, light", showBackground = true)
@Composable
fun CardsLightPreview() = PreviewSurface(darkTheme = false) { Cards() }

@Preview(name = "Cards, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun CardsDarkPreview() = PreviewSurface(darkTheme = true) { Cards() }

@Preview(name = "Buttons and pills, light", showBackground = true)
@Composable
fun ButtonsAndPillsLightPreview() = PreviewSurface(darkTheme = false) { ButtonsAndPills() }

@Preview(
    name = "Buttons and pills, dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
fun ButtonsAndPillsDarkPreview() = PreviewSurface(darkTheme = true) { ButtonsAndPills() }

@Preview(name = "Buttons and pills, font scale 2.0", fontScale = 2f, showBackground = true)
@Composable
fun ButtonsAndPillsLargeFontPreview() = PreviewSurface(darkTheme = false) { ButtonsAndPills() }
