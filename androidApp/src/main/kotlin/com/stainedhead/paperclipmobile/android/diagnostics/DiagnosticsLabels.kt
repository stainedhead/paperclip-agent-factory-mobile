package com.stainedhead.paperclipmobile.android.diagnostics

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R

// One exhaustive `when` per diagnostics enum, like the key labels (AND-016): a new entry needs its string to compile.

@StringRes
fun GraphOpenOutcome.stringRes(): Int =
    when (this) {
        GraphOpenOutcome.Opened -> R.string.graph_open_opened
        GraphOpenOutcome.UnavailableStorage -> R.string.graph_open_unavailable_storage
        GraphOpenOutcome.UnavailableKeystore -> R.string.graph_open_unavailable_keystore
        GraphOpenOutcome.UnavailableOther -> R.string.graph_open_unavailable_other
    }

@StringRes
fun KeyErrorClass.stringRes(): Int =
    when (this) {
        KeyErrorClass.StorageFailed -> R.string.key_error_storage_failed
        KeyErrorClass.KeyInvalidated -> R.string.key_error_key_invalidated
        KeyErrorClass.AuthDeclined -> R.string.key_error_auth_declined
        KeyErrorClass.CurrentKeyUnknown -> R.string.key_error_current_key_unknown
    }

@StringRes
fun PairingOutcome.stringRes(): Int =
    when (this) {
        PairingOutcome.Approved -> R.string.pairing_outcome_approved
        PairingOutcome.Cancelled -> R.string.pairing_outcome_cancelled
        PairingOutcome.Expired -> R.string.pairing_outcome_expired
        PairingOutcome.Failed -> R.string.pairing_outcome_failed
    }

@StringRes
fun ServerCompatibility.stringRes(): Int =
    when (this) {
        ServerCompatibility.Compatible -> R.string.server_compat_compatible
        ServerCompatibility.Incompatible -> R.string.server_compat_incompatible
    }

/** The words for a recorded value, or "Not recorded". */
@Composable
fun <T> Recorded<T>.label(text: (T) -> Int): String =
    when (this) {
        Recorded.NotRecorded -> stringResource(R.string.diagnostics_not_recorded)
        is Recorded.Value -> stringResource(text(value))
    }
