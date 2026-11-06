package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.testing.contract.AttentionFeedContract
import com.stainedhead.paperclipmobile.testing.contract.AttentionTriageContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

private fun attentionWorld(): WorkWorld = WorkWorld().also { it.home.attention.items += FeedFixtures.standard() }

/** AD-012, AD-014: the shared feed contract (paging, counts, stale cursor), run unchanged against the real adapter. */
class AttentionFeedGatewayContractTest : AttentionFeedContract() {
    override val scope: CompanyScope = WorkWorld().acme

    override fun create(): AttentionGateway = attentionWorld().attention

    @Test
    fun `AD-012 the real adapter passes the attention feed contract`() = runTest { runAll() }
}

/** AD-012, AD-014: the shared triage contract (dismiss, snooze, restore, decide-by), run unchanged. */
class AttentionTriageGatewayContractTest : AttentionTriageContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val now: Instant = WorkWorld().clock.now()

    override fun create(): AttentionGateway = attentionWorld().attention

    @Test
    fun `AD-012 the real adapter passes the attention triage contract`() = runTest { runAll() }
}
