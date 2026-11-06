package com.stainedhead.paperclipmobile.infrastructure.api

/**
 * JSON bodies in the shapes the pinned Paperclip server (2026.1023.0) sends, written from its route source
 * (`server/src/routes/health.ts`, `server/src/routes/access.ts`, `server/src/services/board-auth.ts`; research Q2).
 * They are Kotlin strings, not resource files, because a Kotlin/Native test cannot read classpath resources
 * (ADR-041). In CI the contract test checks the same shapes against the real container. Every token is fake.
 */
object ServerFixtures {
    /** `GET /api/health`: the redacted body an anonymous caller gets from an `authenticated` server (health.ts:394). */
    fun healthRedacted(
        status: String = "ok",
        mode: String = "authenticated",
        exposure: String = "private",
        bootstrap: String = "ready",
    ) = """
        {"status":"$status","deploymentMode":"$mode","deploymentExposure":"$exposure","localAiLoginSupported":false,
         "commit":"abc1234","bootstrapStatus":"$bootstrap","bootstrapInviteActive":false,
         "hiddenSettings":["billing"],"somethingNew":{"a":1}}
        """.trimIndent()

    /** The full body (health.ts:421): `local_trusted` servers send it to anyone, with the version. */
    fun healthFull(mode: String = "local_trusted") =
        """
        {"status":"ok","version":"2026.1023.0","serverVersion":"2026.1023.0","commit":"abc1234",
         "deploymentMode":"$mode","deploymentExposure":"private","authReady":true,"bootstrapStatus":"ready",
         "bootstrapInviteActive":false,"features":{"companyDeletionEnabled":true},"serverInfo":{"git":{}}}
        """.trimIndent()

    /** The 503 answer when the database is down (health.ts:318): no `deploymentMode`. */
    const val HEALTH_UNHEALTHY =
        """{"status":"unhealthy","version":"2026.1023.0","commit":"abc","error":"database_unreachable"}"""

    const val HTML_PAGE = "<html><head><title>Sign in</title></head><body>proxy login</body></html>"

    /** `POST /api/cli-auth/challenges` 201 (access.ts:2796-2806). */
    fun challengeCreated(
        id: String,
        challengeToken: String,
        pendingKey: String,
        origin: String?,
        expiresAt: String,
    ): String {
        val path = "/cli-auth/$id?token=$challengeToken"
        val url = if (origin == null) "null" else "\"$origin$path\""
        return """
            {"id":"$id","token":"$challengeToken","boardApiToken":"$pendingKey","approvalPath":"$path",
             "approvalUrl":$url,"pollPath":"/cli-auth/challenges/$id","expiresAt":"$expiresAt",
             "suggestedPollIntervalMs":1000}
            """.trimIndent()
    }

    /** `GET /api/cli-auth/challenges/:id` 200 (access.ts:2829-2836). */
    fun challengePolled(
        id: String,
        status: String,
    ) = """
        {"id":"$id","status":"$status","command":"paperclip-mobile pair","clientName":"Alex's phone",
         "requestedAccess":"board","requestedCompanyId":null,"requestedCompanyName":null,"approvedAt":null,
         "cancelledAt":null,"expiresAt":"2026-11-01T12:10:00.000Z","requiresSignIn":true,"canApprove":false,
         "currentUserId":null}
        """.trimIndent()

    /** `GET /api/cli-auth/me` 200 (access.ts:2889-2897). */
    fun me(
        keyId: String?,
        isInstanceAdmin: Boolean = false,
        memberships: List<Triple<String, String, String>> =
            listOf(Triple(COMPANY_ACME, "owner", "active"), Triple(COMPANY_GLOBEX, "viewer", "active")),
    ): String {
        val rows =
            memberships.joinToString(",") { (id, role, status) ->
                """{"companyId":"$id","membershipRole":"$role","status":"$status"}"""
            }
        val key = if (keyId == null) "null" else "\"$keyId\""
        return """
            {"user":{"id":"user-1","name":"Alex Operator","email":"alex@example.com"},"userId":"user-1",
             "isInstanceAdmin":$isInstanceAdmin,"companyIds":[${memberships.joinToString(",") { "\"${it.first}\"" }}],
             "memberships":[$rows],"source":"board_key","keyId":$key}
            """.trimIndent()
    }

    /** One element of `GET /api/board-api-keys` (board-auth.ts:262-270). */
    fun key(
        id: String,
        name: String,
        lastUsedAt: String? = null,
        expiresAt: String? = "2026-12-01T12:00:00.000Z",
        revokedAt: String? = null,
    ): String {
        fun field(
            name: String,
            value: String?,
        ) = "\"$name\":" + (if (value == null) "null" else "\"$value\"")
        return "{\"id\":\"$id\",\"name\":\"$name\",\"createdAt\":\"2026-11-01T12:00:00.000Z\"," +
            "${field("lastUsedAt", lastUsedAt)},${field("revokedAt", revokedAt)},${field("expiresAt", expiresAt)}}"
    }

    /** `GET /api/companies?scope=accessible` (companies.ts:380-390): full company rows; the app reads two fields. */
    fun companies(): String =
        """
        [{"id":"$COMPANY_ACME","name":"Acme Labs","status":"active","issuePrefix":"ACM","budgetMonthlyCents":0},
         {"id":"$COMPANY_GLOBEX","name":"Globex","status":"active","issuePrefix":"GLB","budgetMonthlyCents":0}]
        """.trimIndent()

    fun error(message: String) = """{"error":"$message"}"""

    const val COMPANY_ACME = "company-acme"
    const val COMPANY_GLOBEX = "company-globex"
}
