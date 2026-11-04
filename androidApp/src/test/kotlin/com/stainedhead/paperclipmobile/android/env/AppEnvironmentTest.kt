package com.stainedhead.paperclipmobile.android.env

import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.ProbeStatus
import com.stainedhead.paperclipmobile.presentation.StartupNotice
import com.stainedhead.paperclipmobile.umbrella.AndroidGraphOpen
import com.stainedhead.paperclipmobile.umbrella.PaperclipCore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/** A provider a test scripts: each call to [makeGraph] pops the next outcome. */
private class ScriptedProvider(
    override val mode: GraphMode,
    outcomes: List<() -> AndroidGraphOpen>,
) : AppGraphProvider {
    private val remaining = ArrayDeque(outcomes)

    var calls = 0
        private set

    override fun makeGraph(): AndroidGraphOpen {
        calls++
        return remaining.removeFirst()()
    }
}

private fun demoOpen() = AndroidGraphOpen.Opened(PaperclipCore.createDemoGraph())

private fun firstRunOpen() = AndroidGraphOpen.Opened(PaperclipCore.createFirstRunDemoGraph())

private fun unavailable() = AndroidGraphOpen.Unavailable(IllegalStateException("database locked"))

private const val WAIT_MILLIS = 10_000L
private const val DEMO_BADGE = 3

class AppEnvironmentTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val environments = mutableListOf<AppEnvironment>()

    private fun environment(
        provider: AppGraphProvider,
        allowsDemo: Boolean = true,
    ): AppEnvironment = AppEnvironment(provider, scope, allowsDemo).also { environments += it }

    @AfterEach
    fun tearDown() {
        environments.forEach { it.shutdown() }
        scope.cancel()
    }

    @Test
    fun `the demo provider opens a graph with its holders and no notice`() {
        val session = environment(ScriptedProvider(GraphMode.Demo, listOf(::demoOpen))).session.value

        session.mode shouldBe GraphMode.Demo
        session.notice.shouldBeNull()
        session.graph.shouldNotBeNull()
        session.app.shouldNotBeNull()
        session.firstRun.shouldNotBeNull()
    }

    @Test
    fun `an unavailable live graph shows the notice and runs nothing`() {
        val session = environment(ScriptedProvider(GraphMode.Live, listOf(::unavailable))).session.value

        session.notice shouldBe StartupNotice.LiveGraphUnavailable
        session.graph.shouldBeNull()
        session.app.shouldBeNull()
        session.firstRun.shouldBeNull()
        session.inbox.value.shouldBeNull()
    }

    @Test
    fun `an open that throws is the same as unavailable`() {
        val provider = ScriptedProvider(GraphMode.Live, listOf({ error("boom") }))

        environment(provider).session.value.notice shouldBe StartupNotice.LiveGraphUnavailable
    }

    @Test
    fun `try again opens the live graph once per tap`() {
        val provider = ScriptedProvider(GraphMode.Live, listOf(::unavailable, ::unavailable, ::demoOpen))
        val environment = environment(provider)

        provider.calls shouldBe 1
        environment.retryLaunch()
        provider.calls shouldBe 2
        environment.session.value.notice shouldBe StartupNotice.LiveGraphUnavailable
        environment.retryLaunch()
        provider.calls shouldBe 3
        environment.session.value.notice
            .shouldBeNull()
        environment.session.value.graph
            .shouldNotBeNull()
    }

    @Test
    fun `using the demo replaces the session and closes what the old one owned`() {
        val environment = environment(ScriptedProvider(GraphMode.Live, listOf(::demoOpen)))
        val before = environment.session.value

        environment.useDemo()

        val after = environment.session.value
        after.mode shouldBe GraphMode.Demo
        (after.id > before.id) shouldBe true
        before.app?.isClosed shouldBe true
        before.firstRun?.isClosed shouldBe true
        after.app?.isClosed shouldBe false
    }

    @Test
    fun `the demo entry is offered only when the build allows demo controls`() {
        environment(ScriptedProvider(GraphMode.Live, listOf(::demoOpen)), allowsDemo = true)
            .demoEntryAvailable shouldBe true
        environment(ScriptedProvider(GraphMode.Live, listOf(::demoOpen)), allowsDemo = false)
            .demoEntryAvailable shouldBe false
    }

    @Test
    fun `foreground changes reach the graph and survive a replaced graph`() {
        val environment = environment(ScriptedProvider(GraphMode.Demo, listOf(::demoOpen, ::demoOpen)))

        environment.setForeground(false)
        environment.isForegroundOfGraph() shouldBe false
        environment.retryLaunch()
        environment.isForegroundOfGraph() shouldBe false
        environment.setForeground(true)
        environment.isForegroundOfGraph() shouldBe true
    }

    @Test
    fun `the inbox badge is the server count of the selected factory and company`() =
        runBlocking<Unit> {
            val environment = environment(ScriptedProvider(GraphMode.Demo, listOf(::demoOpen)))

            val badge =
                withTimeout(WAIT_MILLIS) {
                    environment.session.value.inboxBadge
                        .first { it > 0 }
                }

            badge shouldBe DEMO_BADGE
        }

    @Test
    fun `the first run flow stays in progress from the pairing step on`() =
        runBlocking<Unit> {
            val session = environment(ScriptedProvider(GraphMode.Demo, listOf(::firstRunOpen))).session.value
            val firstRun = session.firstRun.shouldNotBeNull()
            session.firstRunInProgress.value shouldBe false

            firstRun.send(FirstRunIntent.AddressChanged("https://paperclip.example.com"))
            firstRun.send(FirstRunIntent.ProbeRequested)
            withTimeout(WAIT_MILLIS) { firstRun.state.first { it.probe == ProbeStatus.Accepted } }
            firstRun.send(FirstRunIntent.ContinueRequested)

            withTimeout(WAIT_MILLIS) { session.firstRunInProgress.first { it } }
        }

    private fun AppEnvironment.isForegroundOfGraph(): Boolean =
        session.value.graph
            .shouldNotBeNull()
            .lifecycle.isForeground.value
}
