package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.infrastructure.securestore.UnavailableSecretStore
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import com.stainedhead.paperclipmobile.presentation.ProbeStatus
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-033, AD-040: the graph a JVM host gets by default stores no secret and approves no destructive action. */
class LiveDefaultsTest {
    private val key = SecretKey(InstanceId("inst-1"), SecretName.BoardToken)

    @Test
    fun `the default JVM platform refuses to store a secret and never approves a destructive action`() =
        runTest {
            val platform = defaultLivePlatform()

            platform.secretStore.put(key, Secret.of("pcp_board_TESTONLY1"), SecretPolicy(false)) shouldBe
                SecretWriteResult.Failed
            platform.biometrics.authenticate(AuthReason.ConfirmDestructiveAction) shouldBe BiometricResult.Unavailable
            platform.connectivity.isOnline.value shouldBe true
            platform.persistence.instances
                .observeAll()
                .first() shouldBe emptyList()
            platform.release()
            platform.persistence.close()
        }

    @Test
    fun `the public createLiveGraph builds on that platform and starts at the first-run step`() {
        val graph = PaperclipCore.createLiveGraph(allowInsecureLocal = false, FakePairingBrowser())

        graph
            .createFirstRunStateHolder()
            .state.value.step shouldBe FirstRunStep.Address
        graph.close()
    }

    @Test
    fun `pairing fails closed when the secret store cannot store the key`() =
        runTest {
            val platform = LiveTestPlatform(secretStore = UnavailableSecretStore()).also { it.stub.withOneDecision() }
            val graph = PaperclipCore.createLiveGraph(false, ApprovingBrowser(platform.stub), platform)
            val firstRun = graph.createFirstRunStateHolder()

            firstRun.onIntent(FirstRunIntent.AddressChanged(LIVE_ADDRESS))
            firstRun.onIntent(FirstRunIntent.ProbeRequested)
            firstRun.state.awaitLive { it.probe == ProbeStatus.Accepted }
            firstRun.onIntent(FirstRunIntent.ContinueRequested)
            firstRun.state.awaitLive { it.step == FirstRunStep.Pair }
            firstRun.onIntent(FirstRunIntent.PairRequested)
            val failed = firstRun.state.awaitLive { it.pairing == PairingStatus.Failed }

            failed.step shouldBe FirstRunStep.Pair
            firstRun.close()
            graph.close()
        }
}
