package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore

/**
 * One paired factory (`inst-1`, home.example.com) whose key is owner of Acme and viewer of Globex (as
 * `ServerFixtures.me` says), a configured factory with no key (`inst-signed-out`), and the stub server behind them.
 * The world the company-scoped adapter tests (budgets, approvals, decisions, attention) run in.
 */
class WorkWorld(
    roles: Map<String, String>? =
        mapOf(
            ServerFixtures.COMPANY_ACME to "owner",
            ServerFixtures.COMPANY_GLOBEX to "viewer",
        ),
) {
    val clock = FakeClock()
    val server = StubPaperclipServer(clock::now)
    val secrets = FakeSecretStore()
    val trace = mutableListOf<String>()
    val instances =
        FakeInstanceRepository(
            listOf(
                TestData.instance("inst-1"),
                TestData.instance("inst-2", uri = "https://other.example.com"),
                TestData.instance("inst-signed-out", uri = "https://signed-out.example.com"),
            ),
        )
    val client = PaperclipHttpClient(server.engine, allowInsecureLocal = false) { trace += it }
    val acme = CompanyScope(InstanceId("inst-1"), TestData.acme)
    val globex = CompanyScope(InstanceId("inst-1"), TestData.globex)
    val elsewhere = CompanyScope(InstanceId("inst-1"), CompanyId("company-elsewhere"))
    val otherFactory = CompanyScope(InstanceId("inst-2"), TestData.acme)
    val signedOut = CompanyScope(InstanceId("inst-signed-out"), TestData.acme)
    val unknownFactory = CompanyScope(InstanceId("inst-missing"), TestData.acme)
    internal val home: StubWork get() = server.host(HOME).work
    internal val other: StubWork get() = server.host(OTHER).work

    val budget = BudgetGatewayAdapter(client, instances, secrets)
    val approvals = ApprovalGatewayAdapter(client, instances, secrets)
    val decisions = DecisionGatewayAdapter(client, instances, secrets)
    val attention = AttentionGatewayAdapter(client, instances, secrets)
    val dashboard = DashboardGatewayAdapter(client, instances, secrets)

    init {
        server.issueKey(HOME, TestData.TOKEN, KEY_ID, "Alex's iPhone", roles)
        server.issueKey(OTHER, OTHER_TOKEN, KEY_ID, "Alex's iPad", roles)
        server.host("signed-out.example.com")
        secrets.seed(SecretKey(InstanceId("inst-2"), SecretName.BoardToken), OTHER_TOKEN)
        secrets.seed(SecretKey(InstanceId("inst-1"), SecretName.BoardToken), TestData.TOKEN)
    }

    companion object {
        const val HOME = "home.example.com"
        const val OTHER = "other.example.com"
        const val OTHER_TOKEN = "pcp_board_TESTONLY0000000000000002"
        const val KEY_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    }
}
