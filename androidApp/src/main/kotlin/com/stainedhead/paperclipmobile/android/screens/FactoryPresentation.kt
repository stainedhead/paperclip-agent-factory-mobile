package com.stainedhead.paperclipmobile.android.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.ExpiryDecision
import com.stainedhead.paperclipmobile.presentation.ExpiryKindUi
import com.stainedhead.paperclipmobile.presentation.FactoryStatusLabelUi
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi

// The words for what shared presentation decided about a factory (FactoryStatusDecision, ExpiryDecision, R-019).

@StringRes
fun FactoryStatusLabelUi.stringRes(): Int =
    when (this) {
        FactoryStatusLabelUi.Connected -> FactoryStatusUi.SignedIn.stringRes()
        FactoryStatusLabelUi.KeyExpired -> R.string.status_key_expired
        FactoryStatusLabelUi.SignedOut -> FactoryStatusUi.SignedOut.stringRes()
        FactoryStatusLabelUi.NeedsRePairing -> FactoryStatusUi.NeedsRePairing.stringRes()
    }

/** The sentence for an expiry, or null when the key never expires. */
@Composable
fun ExpiryDecision.text(): String? =
    when (kind) {
        ExpiryKindUi.None -> null
        ExpiryKindUi.Expired -> stringResource(R.string.expiry_expired)
        ExpiryKindUi.Days -> (days ?: 0).let { pluralStringResource(R.plurals.expiry_days, it, it) }
    }
