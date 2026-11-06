package com.stainedhead.paperclipmobile.infrastructure.api.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Rereview1b F-R-4: the guard must identify the throwaway server positively. A bootstrapped server is used only if it
 * holds the fixed TESTONLY account (sign-in), and the test never signs up on a server that is already in use. A fresh
 * server needs the invite that only `scripts/paperclip-test-server.sh invite` mints inside the test container.
 */
class ServerBootstrapGuardTest {
    private class ScriptedServer(
        private val pending: Boolean,
        private val signInStatus: Int = OK,
        private val acceptStatus: Int = ACCEPTED,
    ) : RawServer("http://localhost:3199") {
        val calls = mutableListOf<String>()
        private var accepted = false

        override fun call(
            method: String,
            path: String,
            body: String?,
            bearer: String?,
            cookie: String?,
        ): RawResponse {
            calls += "$method $path"
            val cookies = listOf("paperclip-default.session_token=abc; Path=/")
            return when {
                path == "/api/health" -> {
                    val state = if (pending && !accepted) "bootstrap_pending" else "ready"
                    RawResponse(OK, """{"bootstrapStatus":"$state"}""", emptyList())
                }

                path.endsWith("/sign-up/email") -> {
                    RawResponse(OK, "{}", cookies)
                }

                path.endsWith("/sign-in/email") -> {
                    RawResponse(signInStatus, "{}", if (signInStatus == OK) cookies else emptyList())
                }

                path.endsWith("/accept") -> {
                    accepted = acceptStatus == ACCEPTED
                    RawResponse(acceptStatus, "{}", emptyList())
                }

                else -> {
                    RawResponse(OK, "{}", emptyList())
                }
            }
        }
    }

    private companion object {
        const val OK = 200
        const val ACCEPTED = 202
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
        const val TOKEN_BYTES = 24
    }

    private val invite = "pcp_bootstrap_" + "ab".repeat(TOKEN_BYTES)

    @Test
    fun `a server in use that does not hold the test account is refused and nothing is created`() {
        val server = ScriptedServer(pending = false, signInStatus = UNAUTHORIZED)
        val failure = assertThrows<IllegalStateException> { ServerBootstrap.establishSession(server, invite) }
        assertTrue(failure.message!!.startsWith("Refusing to run"), failure.message)
        assertFalse(server.calls.any { it.contains("sign-up") }, "must not sign up on a server in use")
    }

    @Test
    fun `a server in use that holds the test account is used without signing up`() {
        val server = ScriptedServer(pending = false)
        val session = ServerBootstrap.establishSession(server, null)
        assertNotNull(session.cookie)
        assertFalse(server.calls.any { it.contains("sign-up") })
    }

    @Test
    fun `a fresh server without the invite of the test container is refused before anything is created`() {
        val server = ScriptedServer(pending = true)
        val failure = assertThrows<IllegalStateException> { ServerBootstrap.establishSession(server, null) }
        assertTrue(failure.message!!.startsWith("Refusing to run"), failure.message)
        assertEquals(listOf("GET /api/health"), server.calls)
    }

    @Test
    fun `a fresh server with the invite of the test container is bootstrapped`() {
        val server = ScriptedServer(pending = true)
        ServerBootstrap.establishSession(server, "http://localhost:3199/invite/$invite")
        assertTrue(server.calls.any { it.endsWith("/sign-up/email") })
        assertTrue(server.calls.any { it.endsWith("/accept") })
    }

    @Test
    fun `a fresh server that rejects the invite is refused`() {
        val server = ScriptedServer(pending = true, acceptStatus = NOT_FOUND)
        assertThrows<IllegalStateException> { ServerBootstrap.establishSession(server, invite) }
    }

    @Test
    fun `the port of a developer's own server is refused even when the test port is overridden to it`() {
        assertNotNull(ContractTarget.refusal("http://localhost:3100", "true", "3100"))
        assertNotNull(ContractTarget.refusal("http://127.0.0.1:3100", "true", "3100"))
    }
}
