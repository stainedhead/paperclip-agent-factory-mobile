@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.instance.InstanceNameIssue
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test

class EditInstanceStateHolderTest {
    private val home = TestData.instance("home", "Home factory")
    private val validPin = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="

    private fun TestScope.holder(
        env: Env,
        id: String? = "home",
    ): EditInstanceStateHolder {
        val useCases =
            EditInstanceUseCases(
                env.validateAddress,
                env.get,
                env.probe,
                env.add,
                env.update,
                env.loadAccess,
                env.pair,
                env.signOut,
                env.remove,
                env.removeHereOnly,
                env.signOutHereOnly,
                env.expiry,
            )
        return EditInstanceStateHolder(
            id,
            useCases,
            env.connectivity,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
    }

    private fun TestScope.loaded(env: Env = Env(listOf(home))): Pair<EditInstanceStateHolder, Env> {
        val holder = holder(env)
        advanceUntilIdle()
        return holder to env
    }

    @Test
    fun `FR-006 editing shows the factory its access and the roles read-only`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val (holder, _) = loaded(env)

            val state = holder.state.value
            state.mode shouldBe EditMode.Edit
            state.name shouldBe "Home factory"
            state.address shouldBe "https://home.example.com"
            state.status shouldBe FactoryStatusUi.SignedIn
            state.defaultCompanyId shouldBe TestData.acme.value
            state.companies.map { it.role } shouldBe listOf(RoleUi.Owner, RoleUi.Viewer)
            state.profileName shouldBe "Alex Operator"
            state.profileEmail shouldBe "alex@example.com"
            state.canSave shouldBe true
        }

    @Test
    fun `a missing factory shows not found`() =
        runTest {
            val holder = holder(Env(), id = "zzz")
            advanceUntilIdle()

            holder.state.value.error shouldBe ErrorUi(ErrorKey.NotFound)
            holder.state.value.isBusy shouldBe false
        }

    @Test
    fun `a signed out factory does not load access`() =
        runTest {
            val env = Env(listOf(home.copy(status = InstanceStatus.SignedOut)))
            val (holder, _) = loaded(env)

            env.auth.count("me") shouldBe 0
            holder.state.value.status shouldBe FactoryStatusUi.SignedOut
        }

    @Test
    fun `FR-023 a 401 while loading access shows signed out`() =
        runTest {
            val env = Env(listOf(home))
            env.auth.meResult = DomainResult.Failure(DomainError.Unauthorized)
            val (holder, _) = loaded(env)

            holder.state.value.status shouldBe FactoryStatusUi.SignedOut
            holder.state.value.error shouldBe ErrorUi(ErrorKey.SignedOut)
        }

    @Test
    fun `FR-006 renaming saves a taken name is flagged and the same name is fine`() =
        runTest {
            val other = TestData.instance("other", "Cloud", sortOrder = 1)
            val env = Env(listOf(home, other))
            val (holder, _) = loaded(env)

            holder.onIntent(EditInstanceIntent.NameChanged("cloud"))
            holder.onIntent(EditInstanceIntent.SaveRequested)
            advanceUntilIdle()
            holder.state.value.nameIssue shouldBe InstanceNameIssue.Taken
            holder.state.value.saved shouldBe false

            holder.onIntent(EditInstanceIntent.NameChanged("Renamed"))
            holder.onIntent(EditInstanceIntent.SaveRequested)
            advanceUntilIdle()
            holder.state.value.saved shouldBe true
            env.repository.get(home.id)?.displayName shouldBe "Renamed"
            holder.onIntent(EditInstanceIntent.NameChanged(""))
            holder.state.value.canSave shouldBe false
        }

    @Test
    fun `ADR-012 a new address needs a probe warns the key will be dropped and saving signs out`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val (holder, _) = loaded(env)

            holder.onIntent(EditInstanceIntent.AddressChanged("https://moved.example.com"))
            holder.state.value.addressChangeClearsKey shouldBe true
            holder.state.value.canSave shouldBe false

            holder.onIntent(EditInstanceIntent.ProbeRequested)
            advanceUntilIdle()
            holder.state.value.probe shouldBe ProbeStatus.Accepted
            holder.state.value.canSave shouldBe true

            holder.onIntent(EditInstanceIntent.SaveRequested)
            advanceUntilIdle()

            holder.state.value.status shouldBe FactoryStatusUi.SignedOut
            holder.state.value.addressChangeClearsKey shouldBe false
            env.secrets.paths shouldBe emptySet()

            holder.onIntent(EditInstanceIntent.AddressChanged("https://moved.example.com"))
            holder.state.value.addressChangeClearsKey shouldBe false
        }

    @Test
    fun `A11 the state says when the address can be checked so the app holds no rule`() =
        runTest {
            val (holder, _) = loaded()
            holder.state.value.canCheckAddress shouldBe true

            holder.onIntent(EditInstanceIntent.AddressChanged("http://home.example.com"))
            holder.state.value.canCheckAddress shouldBe false

            holder.onIntent(EditInstanceIntent.AddressChanged(""))
            holder.state.value.canCheckAddress shouldBe false

            holder.onIntent(EditInstanceIntent.AddressChanged("https://moved.example.com"))
            holder.state.value.canCheckAddress shouldBe true
        }

    @Test
    fun `B-14 the state says when there is something typed that Cancel would throw away`() =
        runTest {
            val (holder, _) = loaded()
            holder.state.value.hasUnsavedChanges shouldBe false

            holder.onIntent(EditInstanceIntent.NameChanged("Renamed"))
            holder.state.value.hasUnsavedChanges shouldBe true

            holder.onIntent(EditInstanceIntent.NameChanged("Home factory"))
            holder.state.value.hasUnsavedChanges shouldBe false

            holder.onIntent(EditInstanceIntent.AddressChanged("https://moved.example.com"))
            holder.state.value.hasUnsavedChanges shouldBe true
        }

    @Test
    fun `FR-003 an invalid address shows its issue and blocks saving`() =
        runTest {
            val (holder, env) = loaded()

            holder.onIntent(EditInstanceIntent.AddressChanged("http://home.example.com"))

            holder.state.value.addressIssue shouldBe AddressIssue.MustUseHttps
            holder.state.value.canSave shouldBe false
            holder.onIntent(EditInstanceIntent.ProbeRequested)
            advanceUntilIdle()
            env.health.probeCalls shouldBe 0
        }

    @Test
    fun `FR-005 the probe result is shown local trusted is refused and offline blocks the probe`() =
        runTest {
            val (holder, env) = loaded()
            holder.onIntent(EditInstanceIntent.AddressChanged("https://other.example.com"))

            env.health.response = DomainResult.Success(FakeHealthGateway.healthy(mode = DeploymentMode.LocalTrusted))
            holder.onIntent(EditInstanceIntent.ProbeRequested)
            advanceUntilIdle()
            holder.state.value.probe shouldBe ProbeStatus.Refused
            holder.state.value.probeRefusal shouldBe ProbeRefusal.LocalTrusted
            holder.state.value.canSave shouldBe false

            env.health.response = DomainResult.Failure(DomainError.Timeout)
            holder.onIntent(EditInstanceIntent.ProbeRequested)
            advanceUntilIdle()
            holder.state.value.probe shouldBe ProbeStatus.Failed
            holder.state.value.error shouldBe ErrorUi(ErrorKey.Timeout)

            env.connectivity.setOnline(false)
            advanceUntilIdle()
            val calls = env.health.probeCalls
            holder.onIntent(EditInstanceIntent.ProbeRequested)
            holder.state.value.isOffline shouldBe true
            holder.state.value.error shouldBe ErrorUi(ErrorKey.Unreachable)
            env.health.probeCalls shouldBe calls
            holder.onIntent(EditInstanceIntent.MessageDismissed)
            holder.state.value.error shouldBe null
        }

    @Test
    fun `FR-001 add mode needs an accepted probe then adds the factory and switches to edit`() =
        runTest {
            val env = Env()
            val holder = holder(env, id = null)
            advanceUntilIdle()
            holder.state.value.mode shouldBe EditMode.Add

            holder.onIntent(EditInstanceIntent.NameChanged("Cloud"))
            holder.onIntent(EditInstanceIntent.AddressChanged("https://cloud.example.com"))
            holder.state.value.canSave shouldBe false
            holder.onIntent(EditInstanceIntent.SaveRequested)
            advanceUntilIdle()
            env.repository.addCalls shouldBe 0

            holder.onIntent(EditInstanceIntent.ProbeRequested)
            advanceUntilIdle()
            holder.onIntent(EditInstanceIntent.SaveRequested)
            advanceUntilIdle()

            holder.state.value.saved shouldBe true
            holder.state.value.mode shouldBe EditMode.Edit
            holder.state.value.instanceId shouldBe "inst-1"
            holder.state.value.status shouldBe FactoryStatusUi.SignedOut
            env.repository.current
                .single()
                .displayName shouldBe "Cloud"
        }

    @Test
    fun `adding with a taken name or after a refused re-check shows the issue`() =
        runTest {
            val env = Env(listOf(home))
            val holder = holder(env, id = null)
            advanceUntilIdle()
            holder.onIntent(EditInstanceIntent.NameChanged("HOME factory"))
            holder.onIntent(EditInstanceIntent.AddressChanged("https://cloud.example.com"))
            holder.onIntent(EditInstanceIntent.ProbeRequested)
            advanceUntilIdle()

            holder.onIntent(EditInstanceIntent.SaveRequested)
            advanceUntilIdle()

            holder.state.value.nameIssue shouldBe InstanceNameIssue.Taken
            holder.state.value.isBusy shouldBe false
            env.repository.addCalls shouldBe 0
        }

    @Test
    fun `FR-016 pins can be added rejected when invalid removed and toggled`() =
        runTest {
            val (holder, env) = loaded()

            holder.onIntent(EditInstanceIntent.PinningToggled(true))
            holder.state.value.pinEntryOpen shouldBe true
            holder.onIntent(EditInstanceIntent.PinAdded("not a pin"))
            holder.state.value.pinInvalid shouldBe true
            holder.onIntent(EditInstanceIntent.PinAdded(validPin))
            holder.state.value.pins shouldBe listOf(validPin)
            holder.state.value.pinInvalid shouldBe false
            holder.state.value.pinEntryOpen shouldBe false
            holder.onIntent(EditInstanceIntent.PinAdded(validPin))
            holder.state.value.pins.size shouldBe 1

            holder.onIntent(EditInstanceIntent.SaveRequested)
            advanceUntilIdle()
            env.repository
                .get(home.id)
                ?.pins
                ?.map { it.value } shouldBe listOf(validPin)

            holder.onIntent(EditInstanceIntent.PinningToggled(true))
            holder.state.value.pinEntryOpen shouldBe false
            holder.onIntent(EditInstanceIntent.PinRemoved(validPin))
            holder.state.value.pinningEnabled shouldBe false
            holder.onIntent(EditInstanceIntent.PinningToggled(true))
            holder.onIntent(EditInstanceIntent.PinningToggled(false))
            holder.state.value.pinningEnabled shouldBe false
            holder.state.value.pinEntryOpen shouldBe false
        }

    @Test
    fun `FR-022 the default company can be changed`() =
        runTest {
            val (holder, env) = loaded()

            holder.onIntent(EditInstanceIntent.DefaultCompanyChosen(TestData.globex.value))
            holder.onIntent(EditInstanceIntent.SaveRequested)
            advanceUntilIdle()

            env.repository.get(home.id)?.defaultCompanyId shouldBe TestData.globex
        }

    @Test
    fun `FR-020 re-pair walks to signed in and cancel failure and offline are handled`() =
        runTest {
            val signedOut = home.copy(status = InstanceStatus.SignedOut)
            val env = Env(listOf(signedOut))
            val (holder, _) = loaded(env)
            env.auth.pollResults.add(DomainResult.Success(ChallengeStatus.Approved))

            holder.onIntent(EditInstanceIntent.RePairRequested)
            holder.onIntent(EditInstanceIntent.RePairRequested)
            advanceUntilIdle()

            holder.state.value.pairing shouldBe PairingStatus.Idle
            holder.state.value.status shouldBe FactoryStatusUi.SignedIn
            holder.state.value.companies.size shouldBe 2
            env.auth.count("createChallenge") shouldBe 1
        }

    @Test
    fun `FR-020 a re-pair can be cancelled and its failures are shown`() =
        runTest {
            val env = Env(listOf(home.copy(status = InstanceStatus.SignedOut)))
            val (holder, _) = loaded(env)

            holder.onIntent(EditInstanceIntent.RePairRequested)
            testScheduler.advanceTimeBy(1_500)
            holder.onIntent(EditInstanceIntent.PairCancelled)
            advanceUntilIdle()
            holder.state.value.pairing shouldBe PairingStatus.Cancelled
            env.auth.lastPendingToken?.isWiped shouldBe true
            holder.onIntent(EditInstanceIntent.PairCancelled)

            val outcomes =
                mapOf(
                    DomainResult.Success(ChallengeStatus.Cancelled) to PairingStatus.Cancelled,
                    DomainResult.Success(ChallengeStatus.Expired) to PairingStatus.Expired,
                    DomainResult.Failure(DomainError.NotFound) to PairingStatus.Failed,
                )
            outcomes.forEach { (poll, expected) ->
                val e = Env(listOf(home.copy(status = InstanceStatus.SignedOut)))
                val (h, _) = loaded(e)
                e.auth.pollResults.add(poll)
                h.onIntent(EditInstanceIntent.RePairRequested)
                advanceUntilIdle()
                h.state.value.pairing shouldBe expected
            }

            val e = Env(listOf(home.copy(status = InstanceStatus.SignedOut)))
            val (h, _) = loaded(e)
            e.auth.createResult = DomainResult.Failure(DomainError.Unreachable)
            h.onIntent(EditInstanceIntent.RePairRequested)
            advanceUntilIdle()
            h.state.value.error shouldBe ErrorUi(ErrorKey.Unreachable)

            e.connectivity.setOnline(false)
            advanceUntilIdle()
            val creates = e.auth.count("createChallenge")
            h.onIntent(EditInstanceIntent.RePairRequested)
            e.auth.count("createChallenge") shouldBe creates
            h.close()
        }

    @Test
    fun `FR-024 sign out asks first then revokes once and keeps the factory`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val (holder, _) = loaded(env)

            holder.onIntent(EditInstanceIntent.SignOutRequested)
            holder.state.value.confirmation shouldBe EditConfirmation.SignOut
            env.auth.count("revokeCurrentKey") shouldBe 0

            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.status shouldBe FactoryStatusUi.SignedOut
            holder.state.value.serverKeyOutcome shouldBe ServerKeyOutcome.Revoked
            holder.state.value.companies shouldBe emptyList()
            env.auth.count("revokeCurrentKey") shouldBe 1
            env.secrets.paths shouldBe emptySet()
            env.repository.current.size shouldBe 1
        }

    @Test
    fun `F-A-2 a declined key prompt on sign out keeps the factory signed in and shows the declined key`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val (holder, _) = loaded(env)
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.DeviceAuthDeclined)

            holder.onIntent(EditInstanceIntent.SignOutRequested)
            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.error shouldBe ErrorUi(ErrorKey.AuthDeclined)
            holder.state.value.status shouldBe FactoryStatusUi.SignedIn
            holder.state.value.isBusy shouldBe false
            env.secrets.paths.isEmpty() shouldBe false
        }

    @Test
    fun `FR-006 remove asks first needs the device and every auth failure leaves it in place`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val (holder, _) = loaded(env)

            holder.onIntent(EditInstanceIntent.RemoveRequested)
            holder.onIntent(EditInstanceIntent.ConfirmationDismissed)
            holder.state.value.confirmation shouldBe null
            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)

            listOf(
                BiometricResult.Cancelled to DeviceAuthIssue.Cancelled,
                BiometricResult.LockedOut to DeviceAuthIssue.LockedOut,
            ).forEach { (result, issue) ->
                env.biometrics.result = result
                holder.onIntent(EditInstanceIntent.RemoveRequested)
                holder.onIntent(EditInstanceIntent.ConfirmationAccepted)
                advanceUntilIdle()
                holder.state.value.deviceAuthIssue shouldBe issue
                holder.state.value.removed shouldBe false
                env.auth.count("revokeCurrentKey") shouldBe 0
                env.repository.deletedIds shouldBe emptyList()
            }

            env.biometrics.result = BiometricResult.Approved
            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)
            advanceUntilIdle()
            holder.state.value.removed shouldBe true
            holder.state.value.serverKeyOutcome shouldBe ServerKeyOutcome.Revoked
            env.secrets.paths shouldBe emptySet()
            holder.close()
        }

    @Test
    fun `add mode has nothing to sign out or remove and a vanished factory counts as removed`() =
        runTest {
            val env = Env()
            val holder = holder(env, id = null)
            advanceUntilIdle()
            holder.onIntent(EditInstanceIntent.SignOutRequested)
            holder.onIntent(EditInstanceIntent.RemoveRequested)
            holder.state.value.confirmation shouldBe null
            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)

            val e2 = Env(listOf(home))
            val (h2, _) = loaded(e2)
            h2.onIntent(EditInstanceIntent.RemoveRequested)
            e2.repository.delete(home.id)
            h2.onIntent(EditInstanceIntent.ConfirmationAccepted)
            advanceUntilIdle()
            h2.state.value.removed shouldBe true
        }

    @Test
    fun `viewer roles are shown read-only and unknown roles are viewers`() =
        runTest {
            val env = Env(listOf(home))
            env.auth.meResult = DomainResult.Success(TestData.access(Role.Viewer))
            val (holder, _) = loaded(env)
            env.storeToken(home)

            holder.state.value.companies
                .map { it.role }
                .toSet() shouldBe setOf(RoleUi.Viewer)
        }

    @Test
    fun `F-B-1 canSave stays false after the address is edited during a probe`() =
        runTest {
            val (holder, env) = loaded()
            val gate = CompletableDeferred<Unit>()
            env.health.onProbe = { uri -> if (uri.value == "https://a.example.com") gate.await() }
            holder.onIntent(EditInstanceIntent.AddressChanged("https://a.example.com"))
            holder.onIntent(EditInstanceIntent.ProbeRequested)
            advanceUntilIdle()

            holder.onIntent(EditInstanceIntent.AddressChanged("https://b.example.com"))
            gate.complete(Unit)
            advanceUntilIdle()
            holder.onIntent(EditInstanceIntent.SaveRequested)
            advanceUntilIdle()

            holder.state.value.probe shouldBe ProbeStatus.Idle
            holder.state.value.canSave shouldBe false
            env.repository
                .get(home.id)
                ?.baseUri
                ?.value shouldBe TestData.HOME_URI
        }

    @Test
    fun `F-B-1 a late refusal in the edit screen does not label the new address`() =
        runTest {
            val (holder, env) = loaded()
            val gate = CompletableDeferred<Unit>()
            env.health.onProbe = { uri -> if (uri.value == "https://a.example.com") gate.await() }
            env.health.response = DomainResult.Success(FakeHealthGateway.healthy(DeploymentMode.LocalTrusted))
            holder.onIntent(EditInstanceIntent.AddressChanged("https://a.example.com"))
            holder.onIntent(EditInstanceIntent.ProbeRequested)
            advanceUntilIdle()

            holder.onIntent(EditInstanceIntent.AddressChanged("https://b.example.com"))
            gate.complete(Unit)
            advanceUntilIdle()

            holder.state.value.probe shouldBe ProbeStatus.Idle
            holder.state.value.probeRefusal shouldBe null
        }

    @Test
    fun `F-B-1 a late probe that cannot be cancelled is still dropped in the edit screen`() =
        runTest {
            val (holder, env) = loaded()
            val gate = CompletableDeferred<Unit>()
            env.health.onProbe =
                { uri -> if (uri.value == "https://a.example.com") withContext(NonCancellable) { gate.await() } }
            holder.onIntent(EditInstanceIntent.AddressChanged("https://a.example.com"))
            holder.onIntent(EditInstanceIntent.ProbeRequested)
            advanceUntilIdle()

            holder.onIntent(EditInstanceIntent.AddressChanged("https://b.example.com"))
            gate.complete(Unit)
            advanceUntilIdle()

            holder.state.value.probe shouldBe ProbeStatus.Idle
            holder.state.value.canSave shouldBe false
        }

    @Test
    fun `storageFailureIsNotReportedAsMalformed or not found in a re-pair`() =
        runTest {
            val env = Env(listOf(home.copy(status = InstanceStatus.SignedOut)))
            val (holder, _) = loaded(env)
            env.secrets.failWrites = true
            env.auth.pollResults.add(DomainResult.Success(ChallengeStatus.Approved))

            holder.onIntent(EditInstanceIntent.RePairRequested)
            advanceUntilIdle()

            holder.state.value.error shouldBe ErrorUi(ErrorKey.StorageFailed)
            holder.state.value.pairing shouldBe PairingStatus.Failed
        }
}
