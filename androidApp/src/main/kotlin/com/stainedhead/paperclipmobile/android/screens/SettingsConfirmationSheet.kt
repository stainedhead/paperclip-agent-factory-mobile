package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.shell.ConfirmSheet
import com.stainedhead.paperclipmobile.presentation.SettingsConfirmation
import com.stainedhead.paperclipmobile.presentation.SettingsIntent

/**
 * The question before signing out or revoking a key, worded from the confirmation the shared state holds. The device
 * owner is not asked here: a failed key read comes back as a "here only" confirmation from the shared use case.
 */
@Composable
fun SettingsConfirmationSheet(
    confirmation: SettingsConfirmation?,
    isWorking: Boolean,
    send: (SettingsIntent) -> Unit,
) {
    val onConfirm = { send(SettingsIntent.ConfirmationAccepted) }
    val onCancel = { send(SettingsIntent.ConfirmationDismissed) }
    when (confirmation) {
        null -> {}

        is SettingsConfirmation.SignOut -> {
            ConfirmSheet(
                stringResource(R.string.edit_sign_out_title, confirmation.name),
                stringResource(R.string.edit_sign_out_message),
                stringResource(R.string.edit_sign_out_confirm),
                onConfirm,
                onCancel,
                isWorking,
            )
        }

        is SettingsConfirmation.SignOutHereOnly -> {
            ConfirmSheet(
                stringResource(R.string.edit_sign_out_here_only_title, confirmation.name),
                stringResource(R.string.edit_sign_out_here_only_message),
                stringResource(R.string.edit_sign_out_here_only_confirm),
                onConfirm,
                onCancel,
                isWorking,
            )
        }

        SettingsConfirmation.SignOutEverywhere -> {
            ConfirmSheet(
                stringResource(R.string.settings_sign_out_everywhere_title),
                stringResource(R.string.settings_sign_out_everywhere_message),
                stringResource(R.string.settings_sign_out_everywhere_confirm),
                onConfirm,
                onCancel,
                isWorking,
            )
        }

        is SettingsConfirmation.RevokeKey -> {
            ConfirmSheet(
                stringResource(R.string.revoke_title, confirmation.name),
                stringResource(
                    if (confirmation.isCurrentDevice) R.string.revoke_this_phone_message else R.string.revoke_message,
                ),
                stringResource(R.string.revoke_confirm),
                onConfirm,
                onCancel,
                isWorking,
            )
        }
    }
}
