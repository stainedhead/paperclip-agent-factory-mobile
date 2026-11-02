package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.attention.RefreshInbox
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionGateway
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-A-3: the composition root hands use cases the decorated gateways, so a 401 anywhere marks the factory. */
class SignedOutWiringTest {
    @Test
    fun `a use case built by the graph marks the factory signed out on a 401`() =
        runTest {
            val graph = PaperclipCore.createDemoGraph()
            (graph.resolveRaw<AttentionGateway>() as FakeAttentionGateway).failures["feed"] = DomainError.Unauthorized
            val home = InstanceId(DemoIds.HOME_FACTORY)

            graph.resolve<RefreshInbox>()(CompanyScope(home, CompanyId(DemoIds.ACME)))

            graph.resolve<InstanceRepository>().get(home)?.status shouldBe InstanceStatus.SignedOut
        }
}
