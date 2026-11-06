package com.stainedhead.paperclipmobile.android.diagnostics

import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The recorder keeps the last of each kind, as typed values, and nothing else. */
class DiagnosticsRecorderTest {
    @Test
    fun `it starts with the app version and nothing recorded`() {
        DiagnosticsRecorder("1.2.3").report.value shouldBe DiagnosticsReport("1.2.3")
    }

    @Test
    fun `the last value of each kind wins and the others are kept`() {
        val recorder = DiagnosticsRecorder("1.2.3")

        recorder.recordGraphOpen(GraphOpenOutcome.UnavailableStorage)
        recorder.recordGraphOpen(GraphOpenOutcome.Opened)
        recorder.recordKeyError(KeyErrorClass.StorageFailed)
        recorder.recordBiometric(DeviceAuthIssue.LockedOut)
        recorder.recordPairing(PairingOutcome.Cancelled)
        recorder.recordPairing(PairingOutcome.Approved)

        recorder.report.value shouldBe
            DiagnosticsReport(
                "1.2.3",
                graphOpen = Recorded.Value(GraphOpenOutcome.Opened),
                keystoreError = Recorded.Value(KeyErrorClass.StorageFailed),
                biometricResult = Recorded.Value(DeviceAuthIssue.LockedOut),
                pairing = Recorded.Value(PairingOutcome.Approved),
            )
    }

    @Test
    fun `a failed open is recorded as the class of its cause and never as its text`() {
        val recorder = DiagnosticsRecorder("1.2.3")

        recorder.recordGraphUnavailable(leakyFailure())

        recorder.report.value.graphOpen shouldBe Recorded.Value(GraphOpenOutcome.UnavailableOther)
    }
}
