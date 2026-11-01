package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.LiveEvent
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.AppLifecycle
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser

/**
 * [Biometrics] reports exactly what the device owner did. [willAnswer] scripts the platform prompt (a fake answers it;
 * a platform bridge test taps the simulator prompt).
 */
abstract class BiometricsContract {
    abstract fun create(): Biometrics

    abstract fun willAnswer(
        biometrics: Biometrics,
        result: BiometricResult,
    )

    suspend fun runAll() {
        for (result in BiometricResult.entries) {
            for (reason in AuthReason.entries) {
                val biometrics = create()
                willAnswer(biometrics, result)
                check(biometrics.authenticate(reason) == result) { "$reason must report $result unchanged" }
            }
        }
    }
}

/** [Clock] never runs backwards. [advance] moves time on (a fake advances; a system clock waits). */
abstract class ClockContract {
    abstract fun create(): Clock

    abstract suspend fun advance(clock: Clock)

    suspend fun runAll() {
        val clock = create()
        val first = clock.now()
        advance(clock)
        val second = clock.now()
        check(second >= first) { "a clock never goes backwards: $first then $second" }
        check(clock.now() >= second) { "a clock never goes backwards between reads" }
    }
}

/** [Connectivity] exposes the current state and follows [setOnline] (a fake) or the network (a platform adapter). */
abstract class ConnectivityContract {
    abstract fun create(): Connectivity

    abstract fun setOnline(
        connectivity: Connectivity,
        online: Boolean,
    )

    fun runAll() {
        val connectivity = create()
        setOnline(connectivity, false)
        check(!connectivity.isOnline.value) { "going offline shows in the state flow" }
        setOnline(connectivity, true)
        check(connectivity.isOnline.value) { "coming back online shows in the state flow" }
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

/** [DeviceTimeZone] answers with a stable zone. */
abstract class DeviceTimeZoneContract {
    abstract fun create(): DeviceTimeZone

    fun runAll() {
        val timeZone = create()
        check(timeZone.current().id.isNotBlank()) { "a zone has an id" }
        check(timeZone.current() == timeZone.current()) { "the zone is stable between reads" }
    }
}

/** [PairingBrowser] takes the approval link (a secret) without failing and without printing it. */
abstract class PairingBrowserContract {
    abstract fun create(): PairingBrowser

    suspend fun runAll() {
        val browser = create()
        val link = Secret.of("https://home.example.com/cli-auth/challenge-TESTONLY?token=challenge-TESTONLY-secret")
        browser.open(link)
        browser.open(link)
        check(!link.toString().contains("challenge-TESTONLY")) { "the approval link redacts itself when printed" }
    }
}

/** [AppLifecycle] shows whether the app is in the foreground and follows [setForeground]. */
abstract class AppLifecycleContract {
    abstract fun create(): AppLifecycle

    abstract fun setForeground(
        lifecycle: AppLifecycle,
        foreground: Boolean,
    )

    fun runAll() {
        val lifecycle = create()
        setForeground(lifecycle, false)
        check(!lifecycle.isForeground.value) { "going to the background shows in the state flow" }
        setForeground(lifecycle, true)
        check(lifecycle.isForeground.value) { "coming to the foreground shows in the state flow" }
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
