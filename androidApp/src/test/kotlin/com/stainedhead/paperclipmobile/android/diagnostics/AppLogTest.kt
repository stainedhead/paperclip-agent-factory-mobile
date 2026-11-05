package com.stainedhead.paperclipmobile.android.diagnostics

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The one place the app logs from: off in release, and in debug it refuses secrets, addresses and ids. */
class AppLogTest {
    private val lines = mutableListOf<String>()
    private val log = AppLog(enabled = true) { tag, message -> lines += "$tag: $message" }

    @Test
    fun `a plain event is written with its typed details`() {
        log.debug("graph", "open finished", GraphOpenOutcome.Opened, 3)

        lines shouldBe listOf("graph: open finished Opened 3")
    }

    @Test
    fun `a release build writes nothing and looks at nothing`() {
        val off = AppLog(enabled = false) { _, _ -> error("must not be called") }

        off.debug("graph", "https://paperclip.example.com", Secret.of("pcp_board_TESTONLY0000000000DEMOHOME"))
    }

    @Test
    fun `a secret is rejected`() {
        shouldThrow<IllegalArgumentException> { log.debug("auth", "key stored", Secret.of("pcp_board_TESTONLY0000")) }
        lines shouldBe emptyList()
    }

    @Test
    fun `a token in the text is rejected`() {
        shouldThrow<IllegalArgumentException> { log.debug("auth", "sent pcp_board_TESTONLY0000000000DEMOHOME") }
    }

    @Test
    fun `an address in the text or in a detail is rejected`() {
        shouldThrow<IllegalArgumentException> { log.debug("api", "GET https://paperclip.example.com/api/health") }
        shouldThrow<IllegalArgumentException> { log.debug("api", "failed", "http://localhost:3100") }
        val uri =
            (
                BaseUri.parse(
                    "https://paperclip.example.com",
                    allowInsecureLocal = false,
                ) as BaseUriResult.Valid
            ).uri
        shouldThrow<IllegalArgumentException> { log.debug("api", "failed", uri) }
    }

    @Test
    fun `an id is rejected, as a typed id, as a uuid and as a prefixed id`() {
        shouldThrow<IllegalArgumentException> { log.debug("db", "loaded", InstanceId("demo-home")) }
        shouldThrow<IllegalArgumentException> { log.debug("db", "loaded", CompanyId("company-acme")) }
        shouldThrow<IllegalArgumentException> { log.debug("db", "row 3f2b8c1e-5d7a-4b1c-9e0f-1a2b3c4d5e6f") }
        shouldThrow<IllegalArgumentException> { log.debug("auth", "key bk-demo-a91f revoked") }
    }

    @Test
    fun `nothing rejected was written`() {
        runCatching { log.debug("api", "https://paperclip.example.com") }

        lines shouldBe emptyList()
    }
}
