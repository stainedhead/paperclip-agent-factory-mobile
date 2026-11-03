package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore

/**
 * Two paired factories on two servers, a signed-out one, and a stub server holding their keys: the world the auth
 * adapter tests run in. `inst-1` is home.example.com with [TestData.TOKEN]; `inst-2` is other.example.com with
 * [OTHER_TOKEN]; `inst-signed-out` is configured but the device holds no key for it.
 */
class AuthWorld(
    allowInsecureLocal: Boolean = false,
    pinsEnforced: Boolean = false,
) {
    val clock = FakeClock()
    val server = StubPaperclipServer(clock::now)
    val secrets = FakeSecretStore()
    val trace = mutableListOf<String>()
    val instances =
        FakeInstanceRepository(
            listOf(
                TestData.instance("inst-1").copy(boardKeyId = KEY_CURRENT),
                TestData.instance("inst-2", uri = "https://other.example.com").copy(boardKeyId = KEY_CURRENT),
                TestData.instance("inst-signed-out", uri = "https://signed-out.example.com"),
            ),
        )
    val client = PaperclipHttpClient(server.engine, allowInsecureLocal, pinsEnforced) { trace += it }
    val gateway = AuthGatewayAdapter(client, instances, secrets)

    init {
        server.issueKey(HOME, TestData.TOKEN, KEY_CURRENT, "Alex's iPhone")
        server.issueKey(HOME, "pcp_board_TESTONLY_laptop", KEY_OTHER, "paperclipai cli")
        server.issueKey(OTHER, OTHER_TOKEN, KEY_CURRENT, "Alex's iPhone")
        server.host("signed-out.example.com")
        secrets.seed(SecretKey(InstanceId("inst-1"), SecretName.BoardToken), TestData.TOKEN)
        secrets.seed(SecretKey(InstanceId("inst-2"), SecretName.BoardToken), OTHER_TOKEN)
    }

    companion object {
        const val HOME = "home.example.com"
        const val OTHER = "other.example.com"
        const val OTHER_TOKEN = "pcp_board_TESTONLY0000000000000002"
        const val KEY_CURRENT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val KEY_OTHER = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    }
}
