package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.InstanceLocks
import com.stainedhead.paperclipmobile.application.attention.DismissItem
import com.stainedhead.paperclipmobile.application.auth.SignedOutMarker
import com.stainedhead.paperclipmobile.application.decision.DecideDecision
import com.stainedhead.paperclipmobile.application.decision.LoadDecision
import com.stainedhead.paperclipmobile.application.settings.UnlockApp
import com.stainedhead.paperclipmobile.application.settings.UnlockSession
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * F-B-6: holders run on `Dispatchers.Default`, so a guard that reads a flag and then sets it lets two callers
 * both pass.
 * Each scenario fires several intents from different threads at once, many times, and asserts the use case ran exactly
 * once per iteration. The iteration count is fixed, not time based.
 */
class AtomicGuardsTest {
    private val iterations = 60
    private val callers = 4

    private companion object {
        const val SETTLE_MS = 20L
    }

    private class CountingDecisions(
        private val inner: DecisionGateway,
        val decides: AtomicInteger,
    ) : DecisionGateway by inner {
        override suspend fun decide(
            scope: CompanyScope,
            id: DecisionId,
            optionId: String,
            inputValues: Map<String, String>,
            idempotencyKey: String,
        ): DomainResult<Decision> {
            decides.incrementAndGet()
            delay(1)
            return inner.decide(scope, id, optionId, inputValues, idempotencyKey)
        }
    }

    private class CountingAttention(
        private val inner: AttentionGateway,
        val dismissals: AtomicInteger,
    ) : AttentionGateway by inner {
        override suspend fun dismiss(
            scope: CompanyScope,
            itemKey: String,
        ): DomainResult<Dismissal> {
            dismissals.incrementAndGet()
            delay(1)
            return inner.dismiss(scope, itemKey)
        }
    }

    private class CountingBiometrics(
        val prompts: AtomicInteger,
    ) : Biometrics {
        override suspend fun authenticate(reason: AuthReason): BiometricResult {
            prompts.incrementAndGet()
            delay(1)
            return BiometricResult.Approved
        }
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun <S> StateFlow<S>.await(predicate: (S) -> Boolean) = withTimeout(10.seconds) { first(predicate) }

    /** Waits for the first call, then a moment more for any duplicate to show up, and returns the final count. */
    private suspend fun settled(counter: AtomicInteger): Int {
        withTimeout(10.seconds) { while (counter.get() == 0) delay(1) }
        delay(SETTLE_MS)
        return counter.get()
    }

    /** Runs [block] on [callers] threads at the same instant. */
    private suspend fun race(block: () -> Unit) =
        withContext(Dispatchers.Default) {
            val gate = CompletableDeferred<Unit>()
            val jobs = List(callers) { async { gate.await().also { block() } } }
            delay(5)
            gate.complete(Unit)
            jobs.awaitAll()
        }

    private fun env(role: Role = Role.Owner): Env {
        val env = Env(listOf(TestData.instance()))
        env.auth.meResult = DomainResult.Success(TestData.access(role))
        return env
    }

    @Test
    fun `concurrent ConfirmTapped sends the decision once`() =
        runBlocking<Unit> {
            var overSent = 0
            repeat(iterations) {
                val env = env()
                val decides = AtomicInteger()
                val useCases =
                    env.decision.copy(
                        decide =
                            DecideDecision(
                                CountingDecisions(env.decisions, decides),
                                DeviceAuthGuard(env.biometrics),
                                FakeIdGenerator("k-"),
                            ),
                    )
                val scope = newScope()
                val holder =
                    DecisionStateHolder(
                        useCases,
                        env.connectivity,
                        env.clock,
                        scope,
                        DecisionTarget(CompanyTarget("inst-1", TestData.acme.value), "dec-1", null, null, null),
                    )
                holder.state.await { it.phase == DetailPhase.Content }
                holder.onIntent(DecisionIntent.OptionSelected("ship"))
                race { holder.onIntent(DecisionIntent.ConfirmTapped) }
                if (settled(decides) != 1) overSent += 1
                holder.close()
                scope.cancel()
            }
            overSent shouldBe 0
        }

    @Test
    fun `concurrent Refresh loads the decision once`() =
        runBlocking<Unit> {
            var extraLoads = 0
            repeat(iterations) {
                val env = env()
                val loads = AtomicInteger()
                val useCases = env.decision.copy(load = countingLoad(env, loads))
                val scope = newScope()
                val holder =
                    DecisionStateHolder(
                        useCases,
                        env.connectivity,
                        env.clock,
                        scope,
                        DecisionTarget(CompanyTarget("inst-1", TestData.acme.value), "dec-1", null, null, null),
                    )
                holder.state.await { it.phase == DetailPhase.Content }
                val before = loads.get()
                race { holder.onIntent(DecisionIntent.Refresh) }
                delay(20)
                if (loads.get() - before != 1) extraLoads += 1
                holder.close()
                scope.cancel()
            }
            extraLoads shouldBe 0
        }

    private fun countingLoad(
        env: Env,
        loads: AtomicInteger,
    ) = LoadDecision(
        object : DecisionGateway by env.decisions {
            override suspend fun get(
                scope: CompanyScope,
                id: DecisionId,
            ): DomainResult<Decision> {
                loads.incrementAndGet()
                delay(10)
                return DomainResult.Success(AttentionFixtures.decision())
            }
        },
    )

    @Test
    fun `concurrent DismissTapped dismisses the card once`() =
        runBlocking<Unit> {
            var overSent = 0
            repeat(iterations) {
                val env = env()
                env.attention.items = listOf(AttentionFixtures.approvalItem("ap-1"))
                val dismissals = AtomicInteger()
                val useCases = env.inbox.copy(dismiss = DismissItem(CountingAttention(env.attention, dismissals)))
                val scope = newScope()
                val holder =
                    InboxStateHolder(
                        useCases,
                        env.connectivity,
                        env.clock,
                        scope,
                        CompanyTarget("inst-1", TestData.acme.value),
                    )
                holder.state.await { it.phase == InboxPhase.Content }
                race { holder.onIntent(InboxIntent.DismissTapped("approval:ap-1")) }
                if (settled(dismissals) != 1) overSent += 1
                holder.close()
                scope.cancel()
            }
            overSent shouldBe 0
        }

    @Test
    fun `concurrent UnlockRequested prompts the device once`() =
        runBlocking<Unit> {
            var overPrompted = 0
            repeat(iterations) {
                val env = env()
                val prompts = AtomicInteger()
                val useCases =
                    AppUseCases(
                        env.observeSettings,
                        env.observeInstances,
                        UnlockSession(
                            UnlockApp(CountingBiometrics(prompts)),
                            env.secrets,
                            SignedOutMarker(env.repository, InstanceLocks()),
                        ),
                        env.shouldLock,
                        env.switchInstance,
                        env.switchCompany,
                    )
                val scope = newScope()
                val holder = AppStateHolder(useCases, env.clock, scope)
                holder.state.await { it.isLoaded }
                race { holder.onIntent(AppIntent.UnlockRequested) }
                if (settled(prompts) != 1) overPrompted += 1
                holder.close()
                scope.cancel()
            }
            overPrompted shouldBe 0
        }
}
