package com.stainedhead.paperclipmobile.infrastructure.api

/**
 * Issue bodies in the shapes of the pinned release 2026.1023.0 (compiled dist, not recorded). Written out in full
 * with the issue gateway; this skeleton carries the fields the issue detail needs for ownership.
 */
internal object IssueRowFixtures {
    fun detail(issue: StubIssue) =
        """{"id":"${issue.id}","companyId":"${issue.company}","identifier":"${issue.identifier}","title":"${issue.title}",
           "status":"${issue.status}","priority":"${issue.priority}"}"""
}
