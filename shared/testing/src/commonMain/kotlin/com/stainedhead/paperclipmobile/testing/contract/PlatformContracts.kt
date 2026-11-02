package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.LiveEvent
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * [Biometrics] reports exactly what the device owner did, once per prompt. [willAnswer] scripts the platform prompt
 * (a fake answers it; a platform bridge test taps the simulator prompt). The obligations a real adapter could break:
 * a result is never turned into another one (lockout is not failure, cancel is not approval), and an earlier
 * approval is never reused for a later prompt, whatever the reason.
 */
abstract class BiometricsContract {
    abstract fun create(): Biometrics

    abstract fun willAnswer(
        biometrics: Biometrics,
        result: BiometricResult,
    )

    suspend fun runAll() {
        everyResultIsReportedUnchanged()
        anEarlierApprovalIsNeverReused()
    }

    private suspend fun everyResultIsReportedUnchanged() {
        for (result in BiometricResult.entries) {
            for (reason in AuthReason.entries) {
                val biometrics = create()
                willAnswer(biometrics, result)
                check(biometrics.authenticate(reason) == result) { "$reason must report $result unchanged" }
            }
        }
    }

    private suspend fun anEarlierApprovalIsNeverReused() {
        val biometrics = create()
        val refusals = BiometricResult.entries.filter { it != BiometricResult.Approved }
        for (refusal in refusals) {
            willAnswer(biometrics, BiometricResult.Approved)
            check(biometrics.authenticate(AuthReason.ConfirmDestructiveAction) == BiometricResult.Approved) {
                "the prompt is approved"
            }
            willAnswer(biometrics, refusal)
            val next = biometrics.authenticate(AuthReason.ConfirmDestructiveAction)
            check(next == refusal && next != BiometricResult.Approved) {
                "after an approval the next prompt must report $refusal, not reuse the approval, but was $next"
            }
        }
    }
}

/**
 * [Clock] never runs backwards and moves on when time passes. [advance] moves time on (a fake advances; a system clock
 * waits).
 */
abstract class ClockContract {
    abstract fun create(): Clock

    abstract suspend fun advance(clock: Clock)

    suspend fun runAll() {
        val clock = create()
        val first = clock.now()
        advance(clock)
        val second = clock.now()
        check(second > first) { "time moves on when it passes: $first then $second" }
        var previous = second
        repeat(READS) {
            val next = clock.now()
            check(next >= previous) { "a clock never goes backwards between reads: $previous then $next" }
            previous = next
        }
    }

    private companion object {
        const val READS = 100
    }
}

/** [IdGenerator] hands out ids that are never blank and never repeat. */
abstract class IdGeneratorContract {
    abstract fun create(): IdGenerator

    fun runAll() {
        val generator = create()
        val ids = List(SAMPLE) { generator.generate() }
        check(ids.all { it.isNotBlank() }) { "an id is not blank" }
        check(ids.toSet().size == ids.size) { "ids never repeat" }
    }

    private companion object {
        const val SAMPLE = 200
    }
}

/** [DeviceTimeZone] answers with a real zone that converts times and does not change between reads. */
abstract class DeviceTimeZoneContract {
    abstract fun create(): DeviceTimeZone

    fun runAll() {
        val timeZone = create()
        val zone = timeZone.current()
        check(zone.id.isNotBlank()) { "a zone has an id" }
        check(TimeZone.of(zone.id) == zone) { "the zone id names a zone that can be looked up again" }
        check(Instant.fromEpochSeconds(0).toLocalDateTime(zone).year in EPOCH_YEARS) { "a time converts in the zone" }
        check(timeZone.current() == zone) { "the zone is stable between reads" }
    }

    private companion object {
        val EPOCH_YEARS = 1969..1970
    }
}

/**
 * [PairingBrowser] opens exactly the approval link it is given, once per call, and leaves the link usable (the caller
 * keeps polling with the same challenge). [openedUrls] reads back what the system browser was asked to load (a fake
 * records it; a platform bridge test intercepts the intent or the web authentication session).
 */
abstract class PairingBrowserContract {
    abstract fun create(): PairingBrowser

    abstract fun openedUrls(browser: PairingBrowser): List<String>

    suspend fun runAll() {
        val browser = create()
        val url = "https://home.example.com/cli-auth/challenge-TESTONLY?token=challenge-TESTONLY-secret"
        val link = Secret.of(url)
        browser.open(link)
        check(openedUrls(browser) == listOf(url)) { "the browser loads exactly the approval link" }
        check(link.reveal() == url) { "opening the link leaves it usable" }
        check(!link.toString().contains("challenge-TESTONLY")) { "the approval link redacts itself when printed" }
        browser.open(link)
        check(openedUrls(browser) == listOf(url, url)) { "every call opens the page once" }
    }
}

/**
 * [LiveEvents] delivers events to a collector in the order they happened. [emit] makes the server (or the fake) send
 * one. Events sent before anyone collects are not replayed.
 */
abstract class LiveEventsContract {
    abstract fun create(): LiveEvents

    abstract suspend fun emit(
        events: LiveEvents,
        event: LiveEvent,
    )

    /** Starts collecting, runs [send], and returns what arrived. */
    abstract suspend fun collectWhile(
        events: LiveEvents,
        count: Int,
        send: suspend () -> Unit,
    ): List<LiveEvent>

    suspend fun runAll() {
        val events = create()
        val sent = List(THREE) { LiveEvent("agent.status", "a$it", null, null) }
        val received = collectWhile(events, sent.size) { sent.forEach { emit(events, it) } }
        check(received == sent) { "events arrive in the order they were sent: $received" }
    }

    private companion object {
        const val THREE = 3
    }
}
