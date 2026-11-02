package com.stainedhead.paperclipmobile.application.issue

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueRef
import com.stainedhead.paperclipmobile.domain.port.IssueGateway

/**
 * Names the issues that runs work on ("PAP-1204 Add retries to webhook ingest"): a run only carries the issue id.
 * Best effort: an issue that cannot be read is left out and the screen shows the run without it. Each id is read once.
 */
class LoadIssueRefs(
    private val issues: IssueGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        ids: Collection<IssueId?>,
    ): Map<IssueId, IssueRef> =
        ids
            .filterNotNull()
            .distinct()
            .mapNotNull { id ->
                (issues.get(scope, id) as? DomainResult.Success)?.value?.let { detail ->
                    id to IssueRef(detail.issue.id, detail.issue.identifier, detail.issue.title, detail.issue.status)
                }
            }.toMap()
}
