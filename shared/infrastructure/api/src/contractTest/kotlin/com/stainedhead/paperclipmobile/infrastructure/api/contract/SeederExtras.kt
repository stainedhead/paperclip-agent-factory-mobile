package com.stainedhead.paperclipmobile.infrastructure.api.contract

// Seeds that need only the raw client: wake a run for an issue, a question, an attachment, an approval comment.

/**
 * Wakes [agentId] for [issueId] (`POST /agents/{id}/wakeup` with the issue in the payload, the way an assignment
 * wake does), so the run is linked to the issue. Returns the run id; a `skipped` reply fails the seed.
 */
internal fun Seeder.wake(
    agentId: String,
    issueId: String,
): String {
    val reply =
        raw.call(
            "POST",
            "/api/agents/$agentId/wakeup",
            """{"source":"on_demand","triggerDetail":"manual","reason":"contract",""" +
                """"payload":{"issueId":"$issueId"}}""",
            bearer = key,
        )
    check(reply.status in Seeder.ACCEPTED) { "POST wakeup answered ${reply.describe()}" }
    return checkNotNull(reply.string("id")) { "The wakeup started no run: ${reply.describe()}" }
}

/** A pending single-choice question on the issue thread. Returns the interaction id. */
internal fun Seeder.question(issueId: String): String {
    val body =
        """{"kind":"ask_user_questions","title":"Contract question","payload":{"version":1,"questions":[""" +
            """{"id":"go","prompt":"Proceed with the contract run?","selectionMode":"single","required":true,""" +
            """"options":[{"id":"yes","label":"Yes"},{"id":"no","label":"No"}]}]}}"""
    return required("POST", "/api/issues/$issueId/interactions", body).id
}

/** A small text attachment. Returns the attachment id. */
internal fun Seeder.attachment(
    companyId: String,
    issueId: String,
    fileName: String,
    content: String,
): String {
    val reply =
        raw.upload(
            "/api/companies/$companyId/issues/$issueId/attachments",
            fileName,
            "text/plain",
            content.toByteArray(),
            key,
        )
    check(reply.status in Seeder.CREATED) { "POST attachments answered ${reply.describe()}" }
    return checkNotNull(reply.string("id")) { "The attachment reply has no id: ${reply.describe()}" }
}

/** A comment on an approval, through the raw client. */
internal fun Seeder.approvalComment(
    approvalId: String,
    body: String,
) {
    val reply = raw.call("POST", "/api/approvals/$approvalId/comments", """{"body":"$body"}""", bearer = key)
    check(reply.status in Seeder.CREATED) { "POST approval comments answered ${reply.describe()}" }
}
