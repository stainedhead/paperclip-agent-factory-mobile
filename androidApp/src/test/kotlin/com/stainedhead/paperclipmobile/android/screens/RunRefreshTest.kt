package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.application.refresh.RefreshVisibleScreen
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.LiveEvent
import com.stainedhead.paperclipmobile.domain.port.AppLifecycle
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import com.stainedhead.paperclipmobile.presentation.RunStatusUi
import com.stainedhead.paperclipmobile.presentation.RunViewerIntent
import com.stainedhead.paperclipmobile.presentation.RunViewerStateHolder
import com.stainedhead.paperclipmobile.umbrella.PaperclipCore
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val HOME = "demo-home"
private const val ACME = "company-acme"
private const val LIVE_RUN = "run-1204-live"
private const val DONE_RUN = "run-1204-done"
private const val WAIT_REAL_MILLIS = 10_000L
private const val POLL_MILLIS = 20L
private const val LIVE_SECONDS = 5
private const val FINISHED_SECONDS = 0
private const val MILLIS_PER_SECOND = 1_000L

private object QuietPorts : Connectivity, AppLifecycle, LiveEvents {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
    override val isForeground: StateFlow<Boolean> = MutableStateFlow(true)
    override val events: Flow<LiveEvent> = emptyFlow()
}

/** A target that counts how often the policy refreshed it and otherwise is the holder's own. */
private class CountedHolderTarget(
    private val inner: RefreshTarget,
) : RefreshTarget by inner {
    val refreshes = AtomicInteger()

    override fun refreshNow() {
        refreshes.incrementAndGet()
        inner.refreshNow()
    }

    override fun isAffectedBy(invalidation: Invalidation) = inner.isAffectedBy(invalidation)
}

/**
 * AND-052 and PRD FR-090: the Run viewer refreshes every 5 seconds while its run is live and not at all once it has
 * finished, and cancelling a running run moves it to cancelled within one refresh cycle (main PRD acceptance
 * criterion 8). The timer is the shared policy's, run here in virtual time; the holder does its work on real threads,
 * so the test waits for it in real time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunRefreshTest {
    private val graph = PaperclipCore.createDemoGraph()
    private val holders = mutableListOf<RunViewerStateHolder>()

    @AfterEach
    fun close() {
        holders.forEach { it.close() }
        graph.close()
    }

    private fun open(id: String): RunViewerStateHolder {
        val holder = graph.runs.createRunViewerStateHolder(HOME, ACME, id)
        holders += holder
        waitFor { holder.state.value.run != null }
        return holder
    }

    private fun waitFor(condition: () -> Boolean) =
        runBlocking {
            var waited = 0L
            while (!condition()) {
                check(waited < WAIT_REAL_MILLIS) { "Timed out waiting for the holder" }
                delay(POLL_MILLIS)
                waited += POLL_MILLIS
            }
        }

    @Test
    fun `a run in progress asks to be refreshed every five seconds`() {
        val holder = open(LIVE_RUN)

        holder.state.value.isLive shouldBe true
        holder.state.value.refreshIntervalSeconds shouldBe LIVE_SECONDS
        runBlocking { holder.refreshTarget.refreshIntervalSeconds.first() } shouldBe LIVE_SECONDS
    }

    @Test
    fun `a finished run asks for no timer at all`() {
        val holder = open(DONE_RUN)

        holder.state.value.isLive shouldBe false
        holder.state.value.refreshIntervalSeconds shouldBe FINISHED_SECONDS
        runBlocking { holder.refreshTarget.refreshIntervalSeconds.first() } shouldBe FINISHED_SECONDS
    }

    @Test
    fun `cancelling a running run moves it to cancelled within one refresh cycle and stops the timer`() {
        val holder = open(LIVE_RUN)
        val target = CountedHolderTarget(holder.refreshTarget)
        val clock = TestScope()
        RefreshVisibleScreen(QuietPorts, QuietPorts, QuietPorts)(clock, target)
        clock.runCurrent()

        // While it is live the policy refreshes it once per five seconds of virtual time.
        clock.advanceTimeBy(LIVE_SECONDS.seconds + 1.milliseconds)
        clock.runCurrent()
        target.refreshes.get() shouldBe 1

        // Cancel from the viewer: confirmation, then the device owner (approved on the demo graph), then the server.
        val tapped = clock.testScheduler.currentTime
        holder.onIntent(RunViewerIntent.CancelTapped)
        waitFor { holder.state.value.confirmCancel }
        holder.onIntent(RunViewerIntent.CancelConfirmed)
        waitFor {
            holder.state.value.run
                ?.status == RunStatusUi.Cancelled
        }
        holder.state.value.isLive shouldBe false
        (clock.testScheduler.currentTime - tapped) shouldBeLessThan LIVE_SECONDS * MILLIS_PER_SECOND

        // One more cycle later it is still cancelled, and the finished run has no timer left.
        clock.advanceTimeBy(LIVE_SECONDS.seconds)
        clock.runCurrent()
        waitFor { !holder.state.value.isCancelling }
        holder.state.value.run
            ?.status shouldBe RunStatusUi.Cancelled
        waitFor { holder.state.value.refreshIntervalSeconds == FINISHED_SECONDS }
        clock.runCurrent()
        val settled = target.refreshes.get()
        clock.advanceTimeBy(60.seconds)
        clock.runCurrent()
        target.refreshes.get() shouldBe settled
    }
}
