@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.refresh

import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.LiveEvent
import com.stainedhead.paperclipmobile.testing.fakes.FakeAppLifecycle
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeLiveEvents
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private class CountingTarget(
    seconds: Int,
    private val affectedBy: (Invalidation) -> Boolean = { false },
) : RefreshTarget {
    val interval = MutableStateFlow(seconds)
    var refreshes = 0
        private set

    override val refreshIntervalSeconds: Flow<Int> = interval

    override fun refreshNow() {
        refreshes += 1
    }

    override fun isAffectedBy(invalidation: Invalidation) = affectedBy(invalidation)
}

private class Rig(
    scope: TestScope,
    val target: CountingTarget,
    online: Boolean = true,
    foreground: Boolean = true,
) {
    val connectivity = FakeConnectivity(online)
    val lifecycle = FakeAppLifecycle(foreground)
    val events = FakeLiveEvents()
    val session = RefreshVisibleScreen(connectivity, lifecycle, events)(scope.backgroundScope, target)

    init {
        scope.runCurrent()
    }
}

private fun TestScope.after(seconds: Int) {
    advanceTimeBy(seconds.seconds)
    runCurrent()
}

class RefreshVisibleScreenTest {
    @Test
    fun `FR-090 a visible screen refreshes every 15 seconds and not at the start`() =
        runTest {
            val rig = Rig(this, CountingTarget(15))
            rig.target.refreshes shouldBe 0

            after(14)
            rig.target.refreshes shouldBe 0
            after(1)
            rig.target.refreshes shouldBe 1
            after(30)
            rig.target.refreshes shouldBe 3
        }

    @Test
    fun `FR-090 a screen with a running run refreshes every 5 seconds`() =
        runTest {
            val rig = Rig(this, CountingTarget(5))

            after(20)

            rig.target.refreshes shouldBe 4
        }

    @Test
    fun `FR-090 a new interval restarts the timer when a run starts or finishes`() =
        runTest {
            val rig = Rig(this, CountingTarget(15))
            after(10)

            rig.target.interval.value = 5
            after(4)
            rig.target.refreshes shouldBe 0
            after(1)
            rig.target.refreshes shouldBe 1

            rig.target.interval.value = 0
            after(10 * 60)
            rig.target.refreshes shouldBe 1
        }

    @Test
    fun `FR-090 a screen with no interval never refreshes on the timer`() =
        runTest {
            val rig = Rig(this, CountingTarget(0))

            advanceTimeBy(10.minutes)
            runCurrent()

            rig.target.refreshes shouldBe 0
        }

    @Test
    fun `FR-090 an offline phone does not refresh and refreshes at once when it is back`() =
        runTest {
            val rig = Rig(this, CountingTarget(15))
            rig.connectivity.setOnline(false)
            rig.target.refreshes shouldBe 0

            after(60)
            rig.target.refreshes shouldBe 0

            rig.connectivity.setOnline(true)
            runCurrent()
            rig.target.refreshes shouldBe 1
            after(15)
            rig.target.refreshes shouldBe 2
        }

    @Test
    fun `FR-090 the background stops refreshing and the foreground refreshes at once and resumes`() =
        runTest {
            val rig = Rig(this, CountingTarget(15))
            after(15)
            rig.target.refreshes shouldBe 1

            rig.lifecycle.setForeground(false)
            after(120)
            rig.target.refreshes shouldBe 1

            rig.lifecycle.setForeground(true)
            runCurrent()
            rig.target.refreshes shouldBe 2
            after(15)
            rig.target.refreshes shouldBe 3
        }

    @Test
    fun `a screen that starts in the background refreshes when the app comes to the foreground`() =
        runTest {
            val rig = Rig(this, CountingTarget(15), foreground = false)
            after(60)
            rig.target.refreshes shouldBe 0

            rig.lifecycle.setForeground(true)
            runCurrent()

            rig.target.refreshes shouldBe 1
        }

    @Test
    fun `stopping a session cancels it cleanly`() =
        runTest {
            val rig = Rig(this, CountingTarget(15))
            after(15)

            rig.session.stop()
            after(120)
            rig.connectivity.setOnline(false)
            rig.connectivity.setOnline(true)
            runCurrent()

            rig.target.refreshes shouldBe 1
        }
}

class RefreshTeardownTest {
    @Test
    fun `stopping a session unsubscribes from live events`() =
        runTest {
            val rig = Rig(this, CountingTarget(15))
            rig.events.subscriberCount.value shouldBe 1

            rig.session.stop()
            runCurrent()

            rig.events.subscriberCount.value shouldBe 0
        }

    @Test
    fun `an inactive screen holds no live event subscription`() =
        runTest {
            val rig = Rig(this, CountingTarget(15), foreground = false)

            rig.events.subscriberCount.value shouldBe 0
        }
}

class RefreshOnLiveEventsTest {
    private val agentStatus = LiveEvent("agent.status", "a1", null, null)

    @Test
    fun `FR-091 a live event that affects the screen refreshes it at once`() =
        runTest {
            val rig = Rig(this, CountingTarget(15) { it == Invalidation.Agent("a1") })

            rig.events.emit(agentStatus)
            runCurrent()

            rig.target.refreshes shouldBe 1
        }

    @Test
    fun `FR-091 a live event for something else does not refresh the screen`() =
        runTest {
            val rig = Rig(this, CountingTarget(15) { it == Invalidation.Agent("a1") })

            rig.events.emit(LiveEvent("agent.status", "a2", null, null))
            rig.events.emit(LiveEvent("plugin.ui.updated", null, null, null))
            runCurrent()

            rig.target.refreshes shouldBe 0
        }

    @Test
    fun `a burst of events refreshes once and then once more after the throttle`() =
        runTest {
            val rig = Rig(this, CountingTarget(15) { it is Invalidation.Agent })

            repeat(5) { rig.events.emit(agentStatus) }
            runCurrent()
            rig.target.refreshes shouldBe 1

            after(2)
            rig.target.refreshes shouldBe 2
        }

    @Test
    fun `events are ignored while the app is in the background or offline`() =
        runTest {
            val rig = Rig(this, CountingTarget(15) { it is Invalidation.Agent })
            rig.lifecycle.setForeground(false)
            runCurrent()

            rig.events.emit(agentStatus)
            runCurrent()
            rig.connectivity.setOnline(false)
            rig.lifecycle.setForeground(true)
            runCurrent()
            rig.events.emit(agentStatus)
            runCurrent()

            rig.target.refreshes shouldBe 0
        }
}
