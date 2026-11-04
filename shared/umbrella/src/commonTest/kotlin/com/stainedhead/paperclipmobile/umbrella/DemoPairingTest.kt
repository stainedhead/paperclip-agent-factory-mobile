package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.EditInstanceIntent
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import com.stainedhead.paperclipmobile.presentation.ProbeStatus
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** Only a safety net: every wait is for a state, never for a duration. */
private val SAFETY_NET = 60.seconds

private suspend fun <S> StateFlow<S>.await(predicate: (S) -> Boolean): S =
    withContext(Dispatchers.Default) { withTimeout(SAFETY_NET) { first(predicate) } }

/** Pairing works on graphs made of fakes: the scripted browser approval (UI-010, demo mode and UI tests). */
class DemoPairingTest {
    @Test
    fun `the first-run demo graph starts with no factory and pairs a new one through to Done`() =
        runTest {
            val graph = PaperclipCore.createFirstRunDemoGraph()
            val app = graph.createAppStateHolder()
            app.state.await { it.isLoaded }.hasFactories shouldBe false
            val holder = graph.createFirstRunStateHolder()

            holder.onIntent(FirstRunIntent.AddressChanged("https://paperclip.example.org"))
            holder.onIntent(FirstRunIntent.ProbeRequested)
            holder.state.await { it.probe == ProbeStatus.Accepted }
            holder.onIntent(FirstRunIntent.ContinueRequested)
            holder.state.await { it.step == FirstRunStep.Pair }
            holder.onIntent(FirstRunIntent.PairRequested)
            val secure = holder.state.await { it.step == FirstRunStep.Secure }
            secure.companies.map { it.name } shouldBe listOf("Acme Labs", "Side Project Co")

            holder.onIntent(FirstRunIntent.FinishRequested)
            holder.state.await { it.step == FirstRunStep.Done }
            app.state.await { it.hasFactories }.selectedInstanceName shouldBe "paperclip.example.org"
            holder.close()
            app.close()
        }

    @Test
    fun `pairing the signed out Cloud factory again works twice in one demo graph`() =
        runTest {
            val graph = PaperclipCore.createDemoGraph()
            val edit = graph.createEditInstanceStateHolder(DemoIds.CLOUD_FACTORY)
            edit.state.await { it.status == FactoryStatusUi.SignedOut }

            repeat(2) {
                edit.onIntent(EditInstanceIntent.RePairRequested)
                edit.state.await { it.pairing == PairingStatus.Waiting || it.status == FactoryStatusUi.SignedIn }
                val state =
                    edit.state.await {
                        it.pairing == PairingStatus.Idle && it.status == FactoryStatusUi.SignedIn
                    }
                state.error shouldBe null
                // The access load after pairing must finish (and succeed) before the next sign out starts.
                edit.state.await { it.companies.isNotEmpty() && it.status == FactoryStatusUi.SignedIn }.error shouldBe
                    null
                edit.onIntent(EditInstanceIntent.SignOutRequested)
                edit.onIntent(EditInstanceIntent.ConfirmationAccepted)
                edit.state.await { it.status != FactoryStatusUi.SignedIn }
            }
            edit.close()
        }
}
