package com.stainedhead.paperclipmobile.infrastructure.api.contract

/** The signed-in board user of a fresh server: the cookies a browser would hold. Never printed. */
internal class BoardSession(
    val cookie: String,
)

/**
 * Brings a fresh authenticated Paperclip server to the point where a board user can approve a pairing (AD-050):
 *
 * 1. `POST /api/auth/sign-up/email` (Better Auth) creates the user and signs it in; an existing user signs in instead,
 *    so a second run against the same container works.
 * 2. While `/api/health` reports `bootstrap_pending`, `POST /api/invites/{token}/accept` with the bootstrap CEO invite
 *    makes that user the first instance admin (202 `bootstrapAccepted`). The invite is created in the container by
 *    `scripts/paperclip-test-server.sh invite`; its token arrives in `PAPERCLIP_CONTRACT_BOOTSTRAP_INVITE`.
 *
 * Nothing secret is printed: failures carry the status and the server's short error text only.
 */
internal object ServerBootstrap {
    const val EMAIL = "contract-test@example.test"
    private const val PASSWORD = "TESTONLY-contract-password-0000"
    private const val NAME = "Contract Test"
    private const val ACCEPTED = 202
    private val OK = 200..299

    fun establishSession(
        raw: RawServer,
        inviteText: String?,
    ): BoardSession {
        val jar = CookieJar()
        signIn(raw, jar)
        if (isBootstrapPending(raw)) acceptBootstrapInvite(raw, jar, inviteText)
        val session = raw.call("GET", "/api/auth/get-session", cookie = jar.header())
        check(session.status in OK) { "The session is not valid after sign-in: ${session.describe()}" }
        return BoardSession(jar.header())
    }

    fun isBootstrapPending(raw: RawServer): Boolean {
        val health = raw.call("GET", "/api/health")
        check(health.status in OK) { "GET /api/health answered ${health.describe()}" }
        return health.string("bootstrapStatus") == "bootstrap_pending"
    }

    private fun signIn(
        raw: RawServer,
        jar: CookieJar,
    ) {
        val signUp = raw.call("POST", "/api/auth/sign-up/email", credentials(withName = true))
        jar.store(signUp.setCookies)
        if (signUp.status in OK && !jar.isEmpty) return
        // The user exists from an earlier run on this container: sign in with the same fixed test credentials.
        val signInReply = raw.call("POST", "/api/auth/sign-in/email", credentials(withName = false))
        jar.store(signInReply.setCookies)
        check(signInReply.status in OK && !jar.isEmpty) {
            "Sign-up answered ${signUp.describe()}; sign-in answered ${signInReply.describe()}"
        }
    }

    private fun acceptBootstrapInvite(
        raw: RawServer,
        jar: CookieJar,
        inviteText: String?,
    ) {
        val token =
            inviteText?.let(InviteToken::parse)
                ?: error(
                    "The server reports bootstrap_pending and PAPERCLIP_CONTRACT_BOOTSTRAP_INVITE holds no invite. " +
                        "Create one with scripts/paperclip-test-server.sh invite.",
                )
        val reply = raw.call("POST", "/api/invites/$token/accept", """{"requestType":"human"}""", cookie = jar.header())
        check(reply.status == ACCEPTED) { "Accepting the bootstrap invite answered ${reply.describe()}" }
        check(!isBootstrapPending(raw)) { "The server still reports bootstrap_pending after the invite was accepted" }
    }

    private fun credentials(withName: Boolean): String =
        if (withName) {
            """{"email":"$EMAIL","password":"$PASSWORD","name":"$NAME"}"""
        } else {
            """{"email":"$EMAIL","password":"$PASSWORD"}"""
        }
}
