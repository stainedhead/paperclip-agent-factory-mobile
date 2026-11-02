package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure helpers of the contract test. These need no server, so they run (and are checked) on every contract run. */
class ContractHelpersTest {
    private companion object {
        const val TOKEN_BYTES = 24
    }

    @Test
    fun `an invite token is read from a bare token or from an invite url`() {
        val token = "pcp_bootstrap_" + "ab".repeat(TOKEN_BYTES)
        assertEquals(token, InviteToken.parse(token))
        assertEquals(token, InviteToken.parse("http://localhost:3199/invite/$token"))
        assertEquals(token, InviteToken.parse("  http://localhost:3199/invite/$token\n"))
        assertEquals(token, InviteToken.parse("Invite URL: http://localhost:3199/invite/$token"))
        assertNull(InviteToken.parse(""))
        assertNull(InviteToken.parse("http://localhost:3199/invite/"))
    }

    @Test
    fun `a cookie jar keeps the latest value per name and drops expired cookies`() {
        val jar = CookieJar()
        jar.store(
            listOf("paperclip-default.session_token=abc.def; Path=/; HttpOnly; SameSite=Lax", "theme=dark; Path=/"),
        )
        jar.store(listOf("theme=light; Path=/"))
        assertEquals("paperclip-default.session_token=abc.def; theme=light", jar.header())
        jar.store(listOf("theme=; Max-Age=0; Path=/"))
        assertEquals("paperclip-default.session_token=abc.def", jar.header())
    }

    @Test
    fun `the base url handed to the adapters uses localhost because 127 0 0 1 is not an allowed insecure host`() {
        assertEquals("http://localhost:3199", ContractTarget.adapterBase("http://127.0.0.1:3199/"))
        assertEquals("http://localhost:3199", ContractTarget.adapterBase("http://localhost:3199"))
        assertEquals("https://paperclip.example.com", ContractTarget.adapterBase("https://paperclip.example.com/"))
    }

    @Test
    fun `the contract test refuses a target that is not the throwaway test server`() {
        val optIn = "true"
        assertNull(ContractTarget.refusal("http://localhost:3199", optIn, null))
        assertNull(ContractTarget.refusal("http://127.0.0.1:3199/", optIn, null))
        assertNull(ContractTarget.refusal("http://localhost:4100", optIn, "4100"))
        // no opt-in, or anything but the exact word
        assertNotNull(ContractTarget.refusal("http://localhost:3199", null, null))
        assertNotNull(ContractTarget.refusal("http://localhost:3199", "yes", null))
        assertNotNull(ContractTarget.refusal("http://localhost:3199", "", null))
        // a developer's own server, a remote host, a lookalike host, another scheme
        assertNotNull(ContractTarget.refusal("http://localhost:3100", optIn, null))
        assertNotNull(ContractTarget.refusal("https://paperclip.example.com", optIn, null))
        assertNotNull(ContractTarget.refusal("http://localhost.evil.example:3199", optIn, null))
        assertNotNull(ContractTarget.refusal("http://user@localhost:3199", optIn, null))
        assertNotNull(ContractTarget.refusal("https://localhost:3199", optIn, null))
        assertNotNull(ContractTarget.refusal("not a url", optIn, null))
        assertNotNull(ContractTarget.refusal("http://localhost", optIn, null))
    }

    @Test
    fun `the refusal names the reason and never echoes credentials`() {
        val reason = checkNotNull(ContractTarget.refusal("http://secret:pw@prod.example.com:3100", null, null))
        assertTrue(reason.contains("PAPERCLIP_CONTRACT_I_KNOW_THIS_MUTATES"))
        assertFalse(reason.contains("pw"))
    }

    @Test
    fun `a server that reports local trusted or a public exposure is not the test server`() {
        assertNull(ContractTarget.markerRefusal(DeploymentMode.Authenticated, Exposure.Private))
        assertNotNull(ContractTarget.markerRefusal(DeploymentMode.LocalTrusted, Exposure.Private))
        assertNotNull(ContractTarget.markerRefusal(DeploymentMode.Authenticated, Exposure.Public))
    }
}
