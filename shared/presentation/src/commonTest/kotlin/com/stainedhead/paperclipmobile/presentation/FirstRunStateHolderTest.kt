@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.instance.InstanceNameIssue
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FirstRunStateHolderTest {
    private fun TestScope.testScope() = CoroutineScope(StandardTestDispatcher(testScheduler))

    private fun TestScope.holder(env: Env = Env { testScheduler.currentTime }) =
        FirstRunStateHolder(env.firstRun, testScope())

    private fun TestScope.holderWithEnv(): Pair<FirstRunStateHolder, Env> {
        val env = Env { testScheduler.currentTime }
        return FirstRunStateHolder(env.firstRun, testScope()) to env
    }

    private fun FirstRunStateHolder.type(address: String) = onIntent(FirstRunIntent.AddressChanged(address))

    @Test
    fun `starts empty with nothing to continue and no error shown`() =
        runTest {
            holder().state.value shouldBe FirstRunState()
        }

    @Test
    fun `FR-003 a valid address enables continue and shows the normalised address`() =
        runTest {
            val holder = holder()

            holder.type("HTTPS://Paperclip.example.com/")

            holder.state.value shouldBe
                FirstRunState(
                    address = "HTTPS://Paperclip.example.com/",
                    normalizedAddress = "https://paperclip.example.com",
                    issue = null,
                    canContinue = true,
                )
        }

    @Test
    fun `FR-003 each invalid address shows its issue key and cannot continue`() =
        runTest {
            val cases =
                mapOf(
                    "paperclip.example.com" to AddressIssue.MissingScheme,
                    "ftp://paperclip.example.com" to AddressIssue.MustUseHttps,
                    "http://paperclip.example.com" to AddressIssue.MustUseHttps,
                    "https://" to AddressIssue.MissingHost,
                    "https://bad host" to AddressIssue.InvalidHost,
                    "https://paperclip.example.com:99999" to AddressIssue.InvalidPort,
                    "https://user:pw@paperclip.example.com" to AddressIssue.RemoveCredentials,
                    "https://paperclip.example.com?x=1" to AddressIssue.RemoveQueryOrFragment,
                )
            cases.forEach { (text, issue) ->
                val holder = holder()
                holder.type(text)
                holder.state.value.issue shouldBe issue
                holder.state.value.canContinue shouldBe false
                holder.state.value.normalizedAddress shouldBe null
            }
        }

    @Test
    fun `an empty address shows no issue and cannot continue`() =
        runTest {
            val holder = holder()
            holder.type("https://a.example.com")
            holder.type("")

            holder.state.value.issue shouldBe null
            holder.state.value.canContinue shouldBe false
        }

    @Test
    fun `FR-005 an accepted probe shows the exposure and an edit resets it`() =
        runTest {
            val (holder, env) = holderWithEnv()
            holder.type(TestData.HOME_URI)

            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()

            holder.state.value.probe shouldBe ProbeStatus.Accepted
            holder.state.value.exposure shouldBe Exposure.Private
            env.health.probeCalls shouldBe 1

            holder.type("https://other.example.com")
            holder.state.value.probe shouldBe ProbeStatus.Idle
        }

    @Test
    fun `FR-005 local trusted is refused with a reason and cannot be added`() =
        runTest {
            val (holder, env) = holderWithEnv()
            env.health.response = DomainResult.Success(FakeHealthGateway.healthy(mode = DeploymentMode.LocalTrusted))
            holder.type(TestData.HOME_URI)

            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()

            holder.state.value.probe shouldBe ProbeStatus.Refused
            holder.state.value.probeRefusal shouldBe ProbeRefusal.LocalTrusted
            holder.state.value.step shouldBe FirstRunStep.Address
            env.repository.addCalls shouldBe 0
        }

    @Test
    fun `FR-005 an unreachable server shows an error key`() =
        runTest {
            val (holder, env) = holderWithEnv()
            env.health.response = DomainResult.Failure(DomainError.Unreachable)
            holder.type(TestData.HOME_URI)

            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()

            holder.state.value.probe shouldBe ProbeStatus.Failed
            holder.state.value.error shouldBe ErrorUi(ErrorKey.Unreachable)
            holder.onIntent(FirstRunIntent.ErrorDismissed)
            holder.state.value.error shouldBe null
        }

    @Test
    fun `probing an invalid address or twice at once does nothing`() =
        runTest {
            val (holder, env) = holderWithEnv()

            holder.onIntent(FirstRunIntent.ProbeRequested)
            holder.type(TestData.HOME_URI)
            holder.onIntent(FirstRunIntent.ProbeRequested)
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()

            env.health.probeCalls shouldBe 1
        }

    @Test
    fun `FR-009 the whole flow walks address pair and secure and lands on done`() =
        runTest {
            val (holder, env) = holderWithEnv()
            env.auth.pollResults.add(DomainResult.Success(ChallengeStatus.Approved))
            holder.type(TestData.HOME_URI)
            holder.onIntent(FirstRunIntent.NameChanged("Home factory"))
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()

            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()
            holder.state.value.step shouldBe FirstRunStep.Pair

            holder.onIntent(FirstRunIntent.PairRequested)
            holder.state.value.pairing shouldBe PairingStatus.Waiting
            advanceUntilIdle()
            holder.state.value.step shouldBe FirstRunStep.Secure
            holder.state.value.companies
                .map { it.name to it.role } shouldBe
                listOf("Acme Labs" to RoleUi.Owner, "Globex" to RoleUi.Viewer)
            holder.state.value.defaultCompanyId shouldBe null

            holder.onIntent(FirstRunIntent.DefaultCompanyChosen(TestData.acme.value))
            holder.onIntent(FirstRunIntent.AppLockChosen(false))
            holder.onIntent(FirstRunIntent.FinishRequested)
            advanceUntilIdle()

            holder.state.value.step shouldBe FirstRunStep.Done
            env.settings.current.appLockEnabled shouldBe false
            env.settings.current.selectedInstanceId
                ?.value shouldBe "inst-1"
            env.repository.current
                .single()
                .defaultCompanyId shouldBe TestData.acme
            env.repository.current
                .single()
                .status shouldBe InstanceStatus.SignedIn
        }

    @Test
    fun `a single company is chosen as the default automatically`() =
        runTest {
            val (holder, env) = holderWithEnv()
            env.auth.pollResults.add(DomainResult.Success(ChallengeStatus.Approved))
            env.auth.companiesResult = DomainResult.Success(TestData.companyRefs().take(1))
            holder.type(TestData.HOME_URI)
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.PairRequested)
            advanceUntilIdle()

            holder.state.value.defaultCompanyId shouldBe TestData.acme.value
        }

    @Test
    fun `FR-001 a taken name is shown on the name field and nothing is saved`() =
        runTest {
            val env = Env(listOf(TestData.instance(name = "Home"))) { testScheduler.currentTime }
            val holder = FirstRunStateHolder(env.firstRun, testScope())
            holder.type("https://other.example.com")
            holder.onIntent(FirstRunIntent.NameChanged("home"))
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()

            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()

            holder.state.value.nameIssue shouldBe InstanceNameIssue.Taken
            holder.state.value.step shouldBe FirstRunStep.Address
            holder.state.value.isBusy shouldBe false
            holder.onIntent(FirstRunIntent.NameChanged("Other"))
            holder.state.value.nameIssue shouldBe null
        }

    @Test
    fun `the name defaults to the host and going back then forward edits the factory instead of adding another`() =
        runTest {
            val (holder, env) = holderWithEnv()
            holder.type("https://home.example.com:8443/paperclip")
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()
            env.repository.current
                .single()
                .displayName shouldBe "home.example.com"

            holder.onIntent(FirstRunIntent.BackRequested)
            holder.state.value.step shouldBe FirstRunStep.Address
            holder.onIntent(FirstRunIntent.NameChanged("Renamed"))
            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()

            holder.state.value.step shouldBe FirstRunStep.Pair
            env.repository.addCalls shouldBe 1
            env.repository.current
                .single()
                .displayName shouldBe "Renamed"
        }

    @Test
    fun `FR-020 cancelling a pairing in progress stops polling and wipes the token`() =
        runTest {
            val (holder, env) = holderWithEnv()
            holder.type(TestData.HOME_URI)
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.PairRequested)
            holder.onIntent(FirstRunIntent.PairRequested)
            testScheduler.advanceTimeBy(2_500)

            holder.onIntent(FirstRunIntent.PairCancelled)
            advanceUntilIdle()

            holder.state.value.pairing shouldBe PairingStatus.Cancelled
            env.auth.count("createChallenge") shouldBe 1
            env.auth.lastPendingToken?.isWiped shouldBe true
            env.secrets.putCalls shouldBe 0
            holder.onIntent(FirstRunIntent.PairCancelled)
        }

    @Test
    fun `pairing outcomes other than success are shown as keys`() =
        runTest {
            val outcomes =
                mapOf(
                    DomainResult.Success(ChallengeStatus.Cancelled) to PairingStatus.Cancelled,
                    DomainResult.Success(ChallengeStatus.Expired) to PairingStatus.Expired,
                    DomainResult.Failure(DomainError.NotFound) to PairingStatus.Failed,
                )
            outcomes.forEach { (poll, expected) ->
                val (holder, env) = holderWithEnv()
                env.auth.pollResults.add(poll)
                holder.type(TestData.HOME_URI)
                holder.onIntent(FirstRunIntent.ProbeRequested)
                advanceUntilIdle()
                holder.onIntent(FirstRunIntent.ContinueRequested)
                advanceUntilIdle()
                holder.onIntent(FirstRunIntent.PairRequested)
                advanceUntilIdle()

                holder.state.value.pairing shouldBe expected
                holder.state.value.step shouldBe FirstRunStep.Pair
            }
        }

    @Test
    fun `FR-021 a pasted key is validated and a bad one shows an error and stores nothing`() =
        runTest {
            val (holder, env) = holderWithEnv()
            holder.type(TestData.HOME_URI)
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()

            holder.onIntent(FirstRunIntent.ManualKeySubmitted("nonsense"))
            advanceUntilIdle()
            holder.state.value.error shouldBe ErrorUi(ErrorKey.Malformed)
            env.secrets.putCalls shouldBe 0

            env.auth.meWithKeyResult = DomainResult.Failure(DomainError.Unauthorized)
            holder.onIntent(FirstRunIntent.ManualKeySubmitted(" ${TestData.TOKEN} "))
            advanceUntilIdle()
            holder.state.value.error shouldBe ErrorUi(ErrorKey.SignedOut)
            holder.state.value.pairing shouldBe PairingStatus.Failed
            env.secrets.putCalls shouldBe 0

            env.auth.meWithKeyResult = DomainResult.Success(TestData.access(Role.Operator))
            holder.onIntent(FirstRunIntent.ManualKeySubmitted(TestData.TOKEN))
            advanceUntilIdle()
            holder.state.value.step shouldBe FirstRunStep.Secure
        }

    @Test
    fun `failing to load the companies after pairing is shown and stays on the pair step`() =
        runTest {
            val (holder, env) = holderWithEnv()
            env.auth.pollResults.add(DomainResult.Success(ChallengeStatus.Approved))
            env.auth.companiesResult = DomainResult.Failure(DomainError.Forbidden)
            holder.type(TestData.HOME_URI)
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.PairRequested)
            advanceUntilIdle()

            holder.state.value.step shouldBe FirstRunStep.Pair
            holder.state.value.error shouldBe ErrorUi(ErrorKey.NoPermission)
        }

    @Test
    fun `FR-009 the demo option finishes setup with no network and finish is ignored before secure`() =
        runTest {
            val (holder, env) = holderWithEnv()

            holder.onIntent(FirstRunIntent.FinishRequested)
            holder.onIntent(FirstRunIntent.PairRequested)
            holder.onIntent(FirstRunIntent.ContinueRequested)
            holder.onIntent(FirstRunIntent.ManualKeySubmitted("x"))
            holder.state.value.step shouldBe FirstRunStep.Address

            holder.onIntent(FirstRunIntent.DemoSelected)

            holder.state.value.step shouldBe FirstRunStep.Done
            holder.state.value.isDemo shouldBe true
            env.health.probeCalls shouldBe 0
            holder.close()
        }

    private fun Env.gateProbes(): Map<String, CompletableDeferred<Unit>> {
        val gates = mapOf("https://a.example.com" to CompletableDeferred<Unit>())
        health.onProbe = { uri -> gates[uri.value]?.await() }
        return gates
    }

    @Test
    fun `F-B-1 a probe result for an address that was edited meanwhile is ignored`() =
        runTest {
            val (holder, env) = holderWithEnv()
            val gates = env.gateProbes()
            holder.type("https://a.example.com")
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()

            holder.type("https://b.example.com")
            gates.getValue("https://a.example.com").complete(Unit)
            advanceUntilIdle()
            holder.onIntent(FirstRunIntent.ContinueRequested)
            advanceUntilIdle()

            holder.state.value.probe shouldBe ProbeStatus.Idle
            holder.state.value.canContinue shouldBe true
            env.repository.addCalls shouldBe 0
            env.health.lastProbed?.value shouldBe "https://a.example.com"
        }

    @Test
    fun `F-B-1 a late refusal does not label the new address`() =
        runTest {
            val (holder, env) = holderWithEnv()
            val gates = env.gateProbes()
            env.health.response = DomainResult.Success(FakeHealthGateway.healthy(DeploymentMode.LocalTrusted))
            holder.type("https://a.example.com")
            holder.onIntent(FirstRunIntent.ProbeRequested)
            advanceUntilIdle()

            holder.type("https://b.example.com")
            gates.getValue("https://a.example.com").complete(Unit)
            advanceUntilIdle()

            holder.state.value.probe shouldBe ProbeStatus.Idle
            holder.state.value.probeRefusal shouldBe null
        }
}
