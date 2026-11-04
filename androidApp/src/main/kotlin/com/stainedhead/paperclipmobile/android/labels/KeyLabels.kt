package com.stainedhead.paperclipmobile.android.labels

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.application.instance.InstanceNameIssue
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.AddressIssue
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import com.stainedhead.paperclipmobile.presentation.ProbeStatus
import com.stainedhead.paperclipmobile.presentation.RoleUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import com.stainedhead.paperclipmobile.presentation.StartupNotice

/*
 * The shared core hands the app keys, never prose (AND-016). Each key enum has one exhaustive `when` here that names a
 * string resource `<namespace>_<entry in snake case>`, so a new entry does not compile until it has a string, and
 * `KeyLabelsTest` checks that every mapped string exists, is not blank and is the entry's own.
 */

/** `ErrorKey.NoPermission` becomes `no_permission`: the entry part of a string resource name. */
internal fun String.toSnakeCase(): String = replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").lowercase()

@StringRes
fun ErrorKey.stringRes(): Int =
    when (this) {
        ErrorKey.Unreachable -> R.string.error_unreachable
        ErrorKey.CertificateInvalid -> R.string.error_certificate_invalid
        ErrorKey.Timeout -> R.string.error_timeout
        ErrorKey.SignedOut -> R.string.error_signed_out
        ErrorKey.NoPermission -> R.string.error_no_permission
        ErrorKey.NotFound -> R.string.error_not_found
        ErrorKey.Conflict -> R.string.error_conflict
        ErrorKey.Rejected -> R.string.error_rejected
        ErrorKey.ServerError -> R.string.error_server_error
        ErrorKey.RateLimited -> R.string.error_rate_limited
        ErrorKey.Malformed -> R.string.error_malformed
        ErrorKey.InvalidKey -> R.string.error_invalid_key
        ErrorKey.StorageFailed -> R.string.error_storage_failed
        ErrorKey.AuthDeclined -> R.string.error_auth_declined
        ErrorKey.KeyInvalidated -> R.string.error_key_invalidated
        ErrorKey.CurrentKeyUnknown -> R.string.error_current_key_unknown
    }

@StringRes
fun ActionsDisabledReason.stringRes(): Int =
    when (this) {
        ActionsDisabledReason.Offline -> R.string.disabled_offline
        ActionsDisabledReason.ViewerRole -> R.string.disabled_viewer_role
        ActionsDisabledReason.OwnerRole -> R.string.disabled_owner_role
    }

@StringRes
fun AddressIssue.stringRes(): Int =
    when (this) {
        AddressIssue.MissingScheme -> R.string.address_missing_scheme
        AddressIssue.MustUseHttps -> R.string.address_must_use_https
        AddressIssue.MissingHost -> R.string.address_missing_host
        AddressIssue.InvalidHost -> R.string.address_invalid_host
        AddressIssue.InvalidPort -> R.string.address_invalid_port
        AddressIssue.InvalidPath -> R.string.address_invalid_path
        AddressIssue.RemoveCredentials -> R.string.address_remove_credentials
        AddressIssue.RemoveQueryOrFragment -> R.string.address_remove_query_or_fragment
    }

@StringRes
fun StartupNotice.stringRes(): Int =
    when (this) {
        StartupNotice.LiveGraphUnavailable -> R.string.startup_live_graph_unavailable
    }

@StringRes
fun DeviceAuthIssue.stringRes(): Int =
    when (this) {
        DeviceAuthIssue.Cancelled -> R.string.device_auth_cancelled
        DeviceAuthIssue.Failed -> R.string.device_auth_failed
        DeviceAuthIssue.LockedOut -> R.string.device_auth_locked_out
        DeviceAuthIssue.Unavailable -> R.string.device_auth_unavailable
    }

@StringRes
fun SeverityUi.stringRes(): Int =
    when (this) {
        SeverityUi.Critical -> R.string.severity_critical
        SeverityUi.High -> R.string.severity_high
        SeverityUi.Medium -> R.string.severity_medium
        SeverityUi.Low -> R.string.severity_low
    }

@StringRes
fun ProbeStatus.stringRes(): Int =
    when (this) {
        ProbeStatus.Idle -> R.string.probe_idle
        ProbeStatus.Probing -> R.string.probe_probing
        ProbeStatus.Accepted -> R.string.probe_accepted
        ProbeStatus.Refused -> R.string.probe_refused
        ProbeStatus.Failed -> R.string.probe_failed
    }

@StringRes
fun ProbeRefusal.stringRes(): Int =
    when (this) {
        ProbeRefusal.LocalTrusted -> R.string.refusal_local_trusted
        ProbeRefusal.UnknownDeploymentMode -> R.string.refusal_unknown_deployment_mode
        ProbeRefusal.BootstrapPending -> R.string.refusal_bootstrap_pending
        ProbeRefusal.NotReady -> R.string.refusal_not_ready
    }

@StringRes
fun PairingStatus.stringRes(): Int =
    when (this) {
        PairingStatus.Idle -> R.string.pairing_idle
        PairingStatus.Waiting -> R.string.pairing_waiting
        PairingStatus.Cancelled -> R.string.pairing_cancelled
        PairingStatus.Expired -> R.string.pairing_expired
        PairingStatus.Failed -> R.string.pairing_failed
    }

@StringRes
fun InstanceNameIssue.stringRes(): Int =
    when (this) {
        InstanceNameIssue.Empty -> R.string.name_issue_empty
        InstanceNameIssue.TooLong -> R.string.name_issue_too_long
        InstanceNameIssue.Taken -> R.string.name_issue_taken
    }

@StringRes
fun Exposure.stringRes(): Int =
    when (this) {
        Exposure.Private -> R.string.exposure_private
        Exposure.Public -> R.string.exposure_public
        Exposure.Unknown -> R.string.exposure_unknown
    }

@StringRes
fun RoleUi.stringRes(): Int =
    when (this) {
        RoleUi.Owner -> R.string.role_owner
        RoleUi.Operator -> R.string.role_operator
        RoleUi.Viewer -> R.string.role_viewer
    }

@StringRes
fun FactoryStatusUi.stringRes(): Int =
    when (this) {
        FactoryStatusUi.SignedIn -> R.string.status_signed_in
        FactoryStatusUi.SignedOut -> R.string.status_signed_out
        FactoryStatusUi.NeedsRePairing -> R.string.status_needs_re_pairing
    }

/** The message for an error: its key's text, followed by the server's own reason for a rejection (never raw JSON). */
@Composable
fun ErrorUi.message(): String {
    val base = stringResource(key.stringRes())
    return if (key == ErrorKey.Rejected && !detail.isNullOrEmpty()) "$base $detail" else base
}
