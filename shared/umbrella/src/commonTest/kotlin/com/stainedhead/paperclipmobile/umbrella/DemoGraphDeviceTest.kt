package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.EditMode
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.presentation.KeyExpiryUi
import com.stainedhead.paperclipmobile.presentation.LockState
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesIntent
import com.stainedhead.paperclipmobile.presentation.PairedDevicesStatus
import com.stainedhead.paperclipmobile.presentation.ProbeStatus
import com.stainedhead.paperclipmobile.presentation.RemovalEffect
import com.stainedhead.paperclipmobile.presentation.RoleUi
import com.stainedhead.paperclipmobile.presentation.SettingsIntent
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private suspend fun <S> StateFlow<S>.await(predicate: (S) -> Boolean): S =
    withContext(Dispatchers.Default) { withTimeout(60.seconds) { first(predicate) } }

private const val HOME_ADDRESS = "https://paperclip.tail1234.ts.net"

/** Acceptance criterion 5 for the setup screens: Settings, Manage factories, Edit factory, App and First run. */
class DemoGraphDeviceTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `Settings shows the phone the factories and the person of the mock`() =
        runTest {
            val settings = graph.createSettingsStateHolder()
            val state = settings.state.await { it.factories.size == 3 && it.deviceName.isNotEmpty() }

            state.deviceName shouldBe "Alex's iPhone"
            state.appLockEnabled shouldBe true
            state.idlePeriod shouldBe IdlePeriod.FiveMinutes
            state.destructiveActionAuthAlwaysOn shouldBe true
            state.factories.map { it.name } shouldBe listOf("Home factory", "Cloud factory", "Test factory")
            state.factories[0].status shouldBe FactoryStatusUi.SignedIn
            state.factories[1].expiry.state shouldBe KeyExpiryUi.Expired
            settings.onIntent(SettingsIntent.PairedDevicesRequested(DemoIds.HOME_FACTORY))
            val devices = settings.state.await { it.pairedDevices?.status == PairedDevicesStatus.Loaded }
            devices.profileName shouldBe "Alex Rivera"
            devices.pairedDevices
                ?.keys
                ?.first { it.isCurrentDevice }
                ?.name shouldBe "Alex's iPhone"
            settings.close()
        }

    @Test
    fun `Manage factories lists the three factories in order and asks before removing Cloud factory`() =
        runTest {
            val manage = graph.createManageFactoriesStateHolder()
            val state = manage.state.await { it.rows.size == 3 }

            state.rows.map { it.name } shouldBe listOf("Home factory", "Cloud factory", "Test factory")
            state.rows.map { it.address } shouldBe
                listOf(HOME_ADDRESS, "https://paperclip.example.com", "http://localhost:3100")
            state.rows.map { it.status } shouldBe
                listOf(FactoryStatusUi.SignedIn, FactoryStatusUi.SignedOut, FactoryStatusUi.SignedOut)
            manage.onIntent(ManageFactoriesIntent.RemoveRequested(DemoIds.CLOUD_FACTORY))
            val asked = manage.state.await { it.confirmation != null }
            asked.confirmation?.name shouldBe "Cloud factory"
            asked.confirmation?.effects?.contains(RemovalEffect.StoredKey) shouldBe true
            manage.close()
        }

    @Test
    fun `Edit factory shows the name address access and key expiry of the mock`() =
        runTest {
            val edit = graph.createEditInstanceStateHolder(DemoIds.HOME_FACTORY)
            val state = edit.state.await { it.name.isNotEmpty() && it.companies.isNotEmpty() }

            state.mode shouldBe EditMode.Edit
            state.name shouldBe "Home factory"
            state.address shouldBe HOME_ADDRESS
            state.exposure shouldBe Exposure.Private
            state.status shouldBe FactoryStatusUi.SignedIn
            state.expiry?.daysLeft shouldBe 23
            state.defaultCompanyId shouldBe DemoIds.ACME
            state.companies.map { it.name to it.role } shouldBe
                listOf("Acme Labs" to RoleUi.Owner, "Side Project Co" to RoleUi.Operator)
            state.profileName shouldBe "Alex Rivera"
            state.pinningEnabled shouldBe false
            edit.close()
        }

    @Test
    fun `the app shell opens Home factory on Acme Labs and unlocks with the device`() =
        runTest {
            val app = graph.createAppStateHolder()
            val loaded = app.state.await { it.isLoaded }

            loaded.hasFactories shouldBe true
            loaded.selectedInstanceName shouldBe "Home factory"
            loaded.selectedCompanyId shouldBe DemoIds.ACME
            loaded.lock shouldBe LockState.Locked
            app.onIntent(AppIntent.UnlockRequested)
            app.state.await { it.lock == LockState.Unlocked }
            app.close()
        }

    @Test
    fun `one setForeground call drives both the app lock and the refresh policy`() =
        runTest {
            val app = graph.createAppStateHolder()
            app.state.await { it.isLoaded }
            app.onIntent(AppIntent.UnlockRequested)
            app.state.await { it.lock == LockState.Unlocked }
            val clock = graph.resolve<Clock>() as FakeClock

            graph.lifecycle.setForeground(false)
            graph.lifecycle.isForeground.value shouldBe false
            clock.advance(IdlePeriod.FiveMinutes.minutes.minutes + 1.minutes)
            graph.lifecycle.setForeground(true)

            graph.lifecycle.isForeground.value shouldBe true
            app.state.await { it.lock == LockState.Locked }
            app.close()
        }

    @Test
    fun `First run finds the Home factory on the private network and offers the demo factory`() =
        runTest {
            val firstRun = graph.createFirstRunStateHolder()
            firstRun.onIntent(FirstRunIntent.AddressChanged(HOME_ADDRESS))
            firstRun.onIntent(FirstRunIntent.NameChanged("Home factory"))
            firstRun.onIntent(FirstRunIntent.ProbeRequested)

            val found = firstRun.state.await { it.probe == ProbeStatus.Accepted }

            found.exposure shouldBe Exposure.Private
            found.serverVersion shouldBe "1.4.0"
            found.name shouldBe "Home factory"
            found.step shouldBe FirstRunStep.Address
            firstRun.onIntent(FirstRunIntent.DemoSelected)
            firstRun.state.await { it.isDemo }.step shouldBe FirstRunStep.Done
            firstRun.close()
        }
}
