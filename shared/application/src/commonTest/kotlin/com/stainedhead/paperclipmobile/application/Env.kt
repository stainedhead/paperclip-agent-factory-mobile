package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.application.auth.KeyEnrollment
import com.stainedhead.paperclipmobile.application.auth.KeyRevoker
import com.stainedhead.paperclipmobile.application.auth.SignedOutMarker
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
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore

/** Every fake and the shared collaborators, so each test builds only the use case it checks. */
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
    val ids = FakeIdGenerator("inst-")
    val validate = ValidateInstanceAddress(allowInsecureLocal = false)
    val deviceAuth = DeviceAuthGuard(biometrics)
    val revoker = KeyRevoker(auth)
    val signedOut = SignedOutMarker(repository)
    val enrollment = KeyEnrollment(auth, secrets, repository, clock, settings)

    suspend fun storeToken(instance: Instance = TestData.instance()) {
        secrets.put(SecretKey(instance.id, SecretName.BoardToken), Secret.of(TestData.TOKEN), SecretPolicy(false))
    }
}
