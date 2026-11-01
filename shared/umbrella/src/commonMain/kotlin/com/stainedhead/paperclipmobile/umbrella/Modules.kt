package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.application.auth.CheckCredentials
import com.stainedhead.paperclipmobile.application.auth.KeyEnrollment
import com.stainedhead.paperclipmobile.application.auth.KeyExpiryStatus
import com.stainedhead.paperclipmobile.application.auth.KeyRevoker
import com.stainedhead.paperclipmobile.application.auth.ListBoardKeys
import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.application.auth.PairInstance
import com.stainedhead.paperclipmobile.application.auth.RevokeBoardKey
import com.stainedhead.paperclipmobile.application.auth.SaveManualBoardKey
import com.stainedhead.paperclipmobile.application.auth.SignOutEverywhere
import com.stainedhead.paperclipmobile.application.auth.SignOutInstance
import com.stainedhead.paperclipmobile.application.auth.SignedOutMarker
import com.stainedhead.paperclipmobile.application.instance.AddInstance
import com.stainedhead.paperclipmobile.application.instance.BuildInstanceLink
import com.stainedhead.paperclipmobile.application.instance.GetInstance
import com.stainedhead.paperclipmobile.application.instance.ObserveInstances
import com.stainedhead.paperclipmobile.application.instance.ParseAddLink
import com.stainedhead.paperclipmobile.application.instance.ProbeInstance
import com.stainedhead.paperclipmobile.application.instance.RemoveInstance
import com.stainedhead.paperclipmobile.application.instance.ReorderInstances
import com.stainedhead.paperclipmobile.application.instance.SwitchCompany
import com.stainedhead.paperclipmobile.application.instance.SwitchInstance
import com.stainedhead.paperclipmobile.application.instance.UpdateInstance
import com.stainedhead.paperclipmobile.application.settings.ObserveAppSettings
import com.stainedhead.paperclipmobile.application.settings.ShouldLockApp
import com.stainedhead.paperclipmobile.application.settings.UnlockApp
import com.stainedhead.paperclipmobile.application.settings.UpdateAppSettings
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.HealthGateway
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.presentation.AppStateHolder
import com.stainedhead.paperclipmobile.presentation.AppUseCases
import com.stainedhead.paperclipmobile.presentation.EditInstanceStateHolder
import com.stainedhead.paperclipmobile.presentation.EditInstanceUseCases
import com.stainedhead.paperclipmobile.presentation.FactoriesStateHolder
import com.stainedhead.paperclipmobile.presentation.FactoriesUseCases
import com.stainedhead.paperclipmobile.presentation.FirstRunStateHolder
import com.stainedhead.paperclipmobile.presentation.FirstRunUseCases
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesStateHolder
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesUseCases
import com.stainedhead.paperclipmobile.presentation.SettingsStateHolder
import com.stainedhead.paperclipmobile.presentation.SettingsUseCases
import com.stainedhead.paperclipmobile.testing.fakes.FakeAppSettingsStore
import com.stainedhead.paperclipmobile.testing.fakes.FakeAuthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import org.koin.dsl.module

/** Every port, backed by the shared fakes until the Phase 1b adapters replace them one by one. */
internal fun portsModule() =
    module {
        single<Clock> { FakeClock() }
        single<IdGenerator> { FakeIdGenerator("factory-") }
        single<InstanceRepository> { FakeInstanceRepository() }
        single<SecretStore> { FakeSecretStore() }
        single<AppSettingsStore> { FakeAppSettingsStore() }
        single<Biometrics> { FakeBiometrics() }
        single<Connectivity> { FakeConnectivity() }
        single<PairingBrowser> { FakePairingBrowser() }
        single<HealthGateway> { FakeHealthGateway() }
        single<AuthGateway> { FakeAuthGateway() }
    }

internal fun useCaseModule(allowInsecureLocal: Boolean) =
    module {
        single { ValidateInstanceAddress(allowInsecureLocal) }
        single { DeviceAuthGuard(get()) }
        single { KeyRevoker(get()) }
        single { SignedOutMarker(get()) }
        single { KeyEnrollment(get(), get(), get(), get(), get()) }
        instanceUseCases()
        authUseCases()
        settingsUseCases()
    }

private fun org.koin.core.module.Module.instanceUseCases() {
    single { ProbeInstance(get()) }
    single { AddInstance(get(), get(), get(), get()) }
    single { UpdateInstance(get(), get(), get()) }
    single { GetInstance(get()) }
    single { ReorderInstances(get()) }
    single { RemoveInstance(get(), get(), get(), get()) }
    single { ObserveInstances(get()) }
    single { SwitchInstance(get(), get()) }
    single { SwitchCompany(get()) }
    single { BuildInstanceLink() }
    single { ParseAddLink() }
}

private fun org.koin.core.module.Module.authUseCases() {
    single { PairInstance(get(), get(), get(), get(), get(), get()) }
    single { SaveManualBoardKey(get(), get(), get()) }
    single { LoadInstanceAccess(get(), get(), get(), get()) }
    single { SignOutInstance(get(), get(), get()) }
    single { SignOutEverywhere(get(), get()) }
    single { ListBoardKeys(get(), get(), get()) }
    single { RevokeBoardKey(get(), get(), get(), get()) }
    single { KeyExpiryStatus(get()) }
    single { CheckCredentials(get(), get()) }
}

private fun org.koin.core.module.Module.settingsUseCases() {
    single { ObserveAppSettings(get()) }
    single { UpdateAppSettings(get()) }
    single { UnlockApp(get()) }
    single { ShouldLockApp(get()) }
}

/** State holders are factories: each screen gets its own, built with the scope the graph passes in. */
internal fun presentationModule() =
    module {
        factory { params ->
            FirstRunStateHolder(
                FirstRunUseCases(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()),
                params.get(),
            )
        }
        factory { params -> AppStateHolder(AppUseCases(get(), get(), get(), get(), get(), get()), get(), params.get()) }
        factory { params ->
            FactoriesStateHolder(
                FactoriesUseCases(get(), get(), get(), get(), get(), get(), get()),
                get(),
                get(),
                params.get(),
            )
        }
        factory { params ->
            ManageFactoriesStateHolder(ManageFactoriesUseCases(get(), get(), get()), params.get())
        }
        factory { params ->
            SettingsStateHolder(
                SettingsUseCases(get(), get(), get(), get(), get(), get(), get(), get(), get()),
                get(),
                params.get(),
            )
        }
        factory { params ->
            EditInstanceStateHolder(
                params.getOrNull<String>(),
                EditInstanceUseCases(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()),
                get(),
                params.get(),
            )
        }
    }
