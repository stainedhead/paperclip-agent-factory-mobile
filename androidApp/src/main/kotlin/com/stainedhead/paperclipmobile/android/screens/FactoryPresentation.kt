package com.stainedhead.paperclipmobile.android.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.ExpiryUi
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.KeyExpiryUi

/** The words a factory's status can have. An expired key outranks the status: it is what the person must fix. */
enum class FactoryStatusLabel {
    Connected,
    KeyExpired,
    SignedOut,
    NeedsRePairing,
}

@StringRes
fun FactoryStatusLabel.stringRes(): Int =
    when (this) {
        FactoryStatusLabel.Connected -> FactoryStatusUi.SignedIn.stringRes()
        FactoryStatusLabel.KeyExpired -> R.string.status_key_expired
        FactoryStatusLabel.SignedOut -> FactoryStatusUi.SignedOut.stringRes()
        FactoryStatusLabel.NeedsRePairing -> FactoryStatusUi.NeedsRePairing.stringRes()
    }

/**
 * Which words and colour a factory's status gets, from the two facts the shared state gives (status and key expiry).
 * A lookup, not a rule: whether a key is expired is decided in shared Kotlin. "Pair again" is offered whenever this
 * phone has no working key for the factory.
 */
data class FactoryStatusPresentation(
    val label: FactoryStatusLabel,
    val tone: StatusTone,
    val offersPairing: Boolean,
) {
    companion object {
        fun of(
            status: FactoryStatusUi,
            expiry: ExpiryUi,
        ): FactoryStatusPresentation {
            val expired = expiry.state == KeyExpiryUi.Expired
            val (label, tone) =
                when {
                    expired -> FactoryStatusLabel.KeyExpired to StatusTone.Warn
                    status == FactoryStatusUi.SignedIn -> FactoryStatusLabel.Connected to StatusTone.Success
                    status == FactoryStatusUi.SignedOut -> FactoryStatusLabel.SignedOut to StatusTone.Neutral
                    else -> FactoryStatusLabel.NeedsRePairing to StatusTone.Warn
                }
            return FactoryStatusPresentation(label, tone, offersPairing = status != FactoryStatusUi.SignedIn || expired)
        }
    }
}

/** "Board key expires in 23 days": the text and colour for a key's expiry; [None] when the key never expires. */
sealed interface ExpiryPresentation {
    val tone: StatusTone

    data object None : ExpiryPresentation {
        override val tone = StatusTone.Neutral
    }

    data object Expired : ExpiryPresentation {
        override val tone = StatusTone.Warn
    }

    data class Days(
        val days: Int,
        override val tone: StatusTone,
    ) : ExpiryPresentation

    companion object {
        fun of(expiry: ExpiryUi): ExpiryPresentation =
            when (expiry.state) {
                KeyExpiryUi.NoExpiry -> None
                KeyExpiryUi.Expired -> Expired
                KeyExpiryUi.Valid -> Days(expiry.daysLeft ?: 0, StatusTone.Neutral)
                KeyExpiryUi.ExpiringSoon -> Days(expiry.daysLeft ?: 0, StatusTone.Warn)
            }
    }
}

/** The sentence for an expiry, or null when the key never expires. */
@Composable
fun ExpiryPresentation.text(): String? =
    when (this) {
        ExpiryPresentation.None -> null
        ExpiryPresentation.Expired -> stringResource(R.string.expiry_expired)
        is ExpiryPresentation.Days -> pluralStringResource(R.plurals.expiry_days, days, days)
    }
