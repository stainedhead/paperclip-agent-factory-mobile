package com.stainedhead.paperclipmobile.presentation

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
import com.stainedhead.paperclipmobile.application.instance.GetInstance
import com.stainedhead.paperclipmobile.application.instance.ObserveInstances
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
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.testing.TestData
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

/** All fakes and every slice 1a-1 use case, wired the way the umbrella wires them. */
class Env(
    instances: List<Instance> = emptyList(),
    clockElapsed: () -> Long = { 0L },
) {
    val clock = FakeClock(elapsedMillis = clockElapsed)
    val repository = FakeInstanceRepository(instances)
    val secrets = FakeSecretStore()
    val settings = FakeAppSettingsStore(AppSettings(deviceName = "Alex's iPhone"))
    val auth = FakeAuthGateway()
    val health = FakeHealthGateway()
    val biometrics = FakeBiometrics()
    val browser = FakePairingBrowser()
    val connectivity = FakeConnectivity()
    private val ids = FakeIdGenerator("inst-")
    private val validate = ValidateInstanceAddress(allowInsecureLocal = false)
    private val deviceAuth = DeviceAuthGuard(biometrics)
    private val revoker = KeyRevoker(auth)
    private val signedOutMarker = SignedOutMarker(repository)
    private val enrollment = KeyEnrollment(auth, secrets, repository, clock, settings)

    val probe = ProbeInstance(health)
    val add = AddInstance(repository, clock, ids, validate)
    val update = UpdateInstance(repository, secrets, validate)
    val get = GetInstance(repository)
    val pair = PairInstance(repository, auth, browser, clock, settings, enrollment)
    val saveKey = SaveManualBoardKey(repository, auth, enrollment)
    val loadAccess = LoadInstanceAccess(repository, auth, signedOutMarker, clock)
    val updateSettings = UpdateAppSettings(settings)
    val switchInstance = SwitchInstance(repository, settings)
    val switchCompany = SwitchCompany(repository)
    val observeInstances = ObserveInstances(repository)
    val observeSettings = ObserveAppSettings(settings)
    val reorder = ReorderInstances(repository)
    val remove = RemoveInstance(repository, secrets, revoker, deviceAuth)
    val signOut = SignOutInstance(repository, secrets, revoker)
    val signOutEverywhere = SignOutEverywhere(repository, signOut)
    val listKeys = ListBoardKeys(repository, auth, signedOutMarker)
    val revokeKey = RevokeBoardKey(repository, auth, signOut, signedOutMarker)
    val expiry = KeyExpiryStatus(clock)
    val checkCredentials = CheckCredentials(repository, secrets)
    val unlock = UnlockApp(biometrics)
    val shouldLock = ShouldLockApp(clock)
    val validateAddress = validate

    val firstRun =
        FirstRunUseCases(validate, probe, add, update, get, pair, saveKey, loadAccess, updateSettings, switchInstance)

    suspend fun storeToken(instance: Instance = TestData.instance()) {
        secrets.put(SecretKey(instance.id, SecretName.BoardToken), Secret.of(TestData.TOKEN), SecretPolicy(false))
    }
}
