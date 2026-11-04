package com.stainedhead.paperclipmobile.android.diagnostics

import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Keeps the last of each diagnostic fact for the life of the process, as typed values (AND-018). It lives in the app
 * environment, so it outlasts a replaced graph; nothing is written to disk and nothing is sent anywhere.
 */
class DiagnosticsRecorder(
    appVersion: String,
) {
    private val mutableReport = MutableStateFlow(DiagnosticsReport(appVersion))

    val report: StateFlow<DiagnosticsReport> = mutableReport.asStateFlow()

    fun recordGraphOpen(outcome: GraphOpenOutcome) =
        mutableReport.update { it.copy(graphOpen = Recorded.Value(outcome)) }

    /** The graph could not be opened: only the class of [cause] is kept. */
    fun recordGraphUnavailable(cause: Throwable) = recordGraphOpen(cause.toGraphOpenOutcome())

    fun recordKeyError(error: KeyErrorClass) = mutableReport.update { it.copy(keystoreError = Recorded.Value(error)) }

    fun recordBiometric(issue: DeviceAuthIssue) =
        mutableReport.update { it.copy(biometricResult = Recorded.Value(issue)) }

    fun recordPairing(outcome: PairingOutcome) = mutableReport.update { it.copy(pairing = Recorded.Value(outcome)) }
}
