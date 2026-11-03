package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.infrastructure.api.FeedFixtures
import com.stainedhead.paperclipmobile.infrastructure.api.ServerFixtures
import com.stainedhead.paperclipmobile.infrastructure.api.StubDecision
import com.stainedhead.paperclipmobile.infrastructure.api.StubPaperclipServer
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.FirstRunStateHolder
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.presentation.ProbeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

const val LIVE_HOST = "home.example.com"
const val LIVE_ADDRESS = "https://home.example.com"
const val LIVE_DECISION_ID = "dddddddd-0000-4000-8000-000000000001"

/** Waits on a real dispatcher: pairing polls with real delays, which `runTest` would skip. */
suspend fun <T> StateFlow<T>.awaitLive(condition: (T) -> Boolean): T =
    withContext(Dispatchers.Default) { withTimeout(20.seconds) { first(condition) } }

/** The person approving the pairing in the browser: approves the challenge the app just opened. */
internal class ApprovingBrowser(
    private val stub: StubPaperclipServer,
) : PairingBrowser {
    override suspend fun open(approvalUrl: Secret) {
        val id = approvalUrl.reveal().substringAfter("/cli-auth/").substringBefore('?')
        stub.approve(LIVE_HOST, id)
    }
}

/** The pinned-server stand-in with one open decision on Acme's Inbox. */
internal fun StubPaperclipServer.withOneDecision(): StubPaperclipServer {
    val work = host(LIVE_HOST).work
    work.attention.items += FeedFixtures.decisionItem(LIVE_DECISION_ID)
    work.decisions.decisions += StubDecision(LIVE_DECISION_ID, ServerFixtures.COMPANY_ACME)
    return this
}

/** Walks the first-run flow to the end: probe, save, pair (the browser approves), choose Acme, finish. */
suspend fun FirstRunStateHolder.completeFirstRun(address: String = LIVE_ADDRESS) {
    onIntent(FirstRunIntent.AddressChanged(address))
    onIntent(FirstRunIntent.ProbeRequested)
    state.awaitLive { it.probe == ProbeStatus.Accepted }
    onIntent(FirstRunIntent.ContinueRequested)
    state.awaitLive { it.step == FirstRunStep.Pair }
    onIntent(FirstRunIntent.PairRequested)
    state.awaitLive { it.step == FirstRunStep.Secure }
    onIntent(FirstRunIntent.DefaultCompanyChosen(ServerFixtures.COMPANY_ACME))
    onIntent(FirstRunIntent.FinishRequested)
    state.awaitLive { it.step == FirstRunStep.Done }
}
