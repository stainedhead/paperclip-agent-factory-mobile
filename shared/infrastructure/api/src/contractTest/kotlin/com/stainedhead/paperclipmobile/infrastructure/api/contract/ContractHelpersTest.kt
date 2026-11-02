package com.stainedhead.paperclipmobile.infrastructure.api.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
