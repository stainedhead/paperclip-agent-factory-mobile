package com.stainedhead.paperclipmobile.android.diagnostics

import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.GeneralSecurityException

/** The things a real report must never carry: addresses, hosts, tokens, key ids, companies and the person. */
internal val SENSITIVE_STRINGS =
    listOf(
        "https://paperclip.tail1234.ts.net",
        "paperclip.tail1234.ts.net",
        "tail1234",
        "paperclip.example.com",
        "http://localhost:3100",
        "pcp_board_TESTONLY0000000000DEMOHOME",
        "bk-demo-a91f",
        "demo-home",
        "company-acme",
        "Acme Labs",
        "Side Project Co",
        "Alex Rivera",
        "alex@example.com",
        "user-1",
    )

/** A failure whose text carries all of it, as real exceptions do. */
internal fun leakyFailure(): Throwable =
    IllegalStateException(
        "GET ${SENSITIVE_STRINGS.joinToString(" ")} failed",
        IllegalArgumentException(SENSITIVE_STRINGS.joinToString(", ")),
    )

private const val LABELS = "App version|Server compatibility|Graph open|Key store error|Biometric result|Pairing"
private val LINE = Regex("^($LABELS): [A-Za-z0-9 .+\\-]{1,40}$")

/** AND-018: the report is typed values only; it is redacted by construction, and this proves it. */
class DiagnosticsReportTest {
    private val full =
        DiagnosticsReport(
            appVersion = "0.1.0",
            serverCompatibility = Recorded.Value(ServerCompatibility.Compatible),
            graphOpen = Recorded.Value(GraphOpenOutcome.UnavailableStorage),
            keystoreError = Recorded.Value(KeyErrorClass.KeyInvalidated),
            biometricResult = Recorded.Value(DeviceAuthIssue.Cancelled),
            pairing = Recorded.Value(PairingOutcome.Expired),
        )

    @Test
    fun `a new report has recorded nothing but the app version`() {
        val report = DiagnosticsReport("0.1.0")

        report.toReportText().lines() shouldBe
            listOf(
                "Paperclip Mobile diagnostics",
                "App version: 0.1.0",
                "Server compatibility: not recorded",
                "Graph open: not recorded",
                "Key store error: not recorded",
                "Biometric result: not recorded",
                "Pairing: not recorded",
            )
    }

    @Test
    fun `every recorded value is said by its own name and nothing else`() {
        full.toReportText().lines().drop(1) shouldBe
            listOf(
                "App version: 0.1.0",
                "Server compatibility: Compatible",
                "Graph open: UnavailableStorage",
                "Key store error: KeyInvalidated",
                "Biometric result: Cancelled",
                "Pairing: Expired",
            )
    }

    @Test
    fun `every line of a report is a fixed label and a short plain value`() {
        for (line in full.toReportText().lines().drop(1)) {
            (LINE.matches(line)) shouldBe true
        }
    }

    @Test
    fun `a version that is not a version is refused`() {
        shouldThrow<IllegalArgumentException> { DiagnosticsReport("https://paperclip.example.com") }
        shouldThrow<IllegalArgumentException> { DiagnosticsReport("0.1.0 alex@example.com") }
        shouldThrow<IllegalArgumentException> { DiagnosticsReport("") }
    }

    @Test
    fun `a failure that carries addresses, tokens and names reaches the report as a class only`() {
        val outcome = leakyFailure().toGraphOpenOutcome()
        val report = DiagnosticsReport("0.1.0", graphOpen = Recorded.Value(outcome))

        outcome shouldBe GraphOpenOutcome.UnavailableOther
        for (sensitive in SENSITIVE_STRINGS) report.toReportText() shouldNotContain sensitive
    }

    @Test
    fun `the cause of a failed open is classified from its type, found anywhere in the chain`() {
        GeneralSecurityException("x").toGraphOpenOutcome() shouldBe GraphOpenOutcome.UnavailableKeystore
        IOException("x").toGraphOpenOutcome() shouldBe GraphOpenOutcome.UnavailableStorage
        RuntimeException("wrapped", GeneralSecurityException("x")).toGraphOpenOutcome() shouldBe
            GraphOpenOutcome.UnavailableKeystore
        StorageUnavailableException(IllegalStateException("locked")).toGraphOpenOutcome() shouldBe
            GraphOpenOutcome.UnavailableStorage
        IllegalStateException("x").toGraphOpenOutcome() shouldBe GraphOpenOutcome.UnavailableOther
    }

    @Test
    fun `only the keystore related error keys are recorded as a key error`() {
        ErrorKey.StorageFailed.toKeyErrorClass() shouldBe KeyErrorClass.StorageFailed
        ErrorKey.KeyInvalidated.toKeyErrorClass() shouldBe KeyErrorClass.KeyInvalidated
        ErrorKey.AuthDeclined.toKeyErrorClass() shouldBe KeyErrorClass.AuthDeclined
        ErrorKey.CurrentKeyUnknown.toKeyErrorClass() shouldBe KeyErrorClass.CurrentKeyUnknown
        (ErrorKey.entries - KeyErrorClass.entries.map { ErrorKey.valueOf(it.name) }.toSet())
            .all { it.toKeyErrorClass() == null } shouldBe true
    }

    @Test
    fun `only a pairing that ended is recorded`() {
        PairingStatus.Idle.toPairingOutcome() shouldBe null
        PairingStatus.Waiting.toPairingOutcome() shouldBe null
        PairingStatus.Cancelled.toPairingOutcome() shouldBe PairingOutcome.Cancelled
        PairingStatus.Expired.toPairingOutcome() shouldBe PairingOutcome.Expired
        PairingStatus.Failed.toPairingOutcome() shouldBe PairingOutcome.Failed
    }
}

/** Stands in for the persistence module's exception of the same name, which the app cannot import. */
internal class StorageUnavailableException(
    cause: Throwable,
) : Exception("The factory database is not available right now", cause)
