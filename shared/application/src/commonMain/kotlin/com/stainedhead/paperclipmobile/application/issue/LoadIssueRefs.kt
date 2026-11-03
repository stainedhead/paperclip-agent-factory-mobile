package com.stainedhead.paperclipmobile.application.issue

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueRef
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_IN_FLIGHT = 4

/**
 * Names the issues that runs work on ("PAP-1204 Add retries to webhook ingest"): a run only carries the issue id.
 * Best effort: an issue that cannot be read is left out and the screen shows the run without it. Each id is read once,
 * a few at a time (A10). Nothing is dropped: a cap would leave later rows without a name and no one would know why (R8);
 * the number of distinct issues is bounded by the runs the screen lists.
 */
class LoadIssueRefs(
    private val issues: IssueGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        ids: Collection<IssueId?>,
    ): Map<IssueId, IssueRef> {
        val gate = Semaphore(MAX_IN_FLIGHT)
        return coroutineScope {
            ids
                .filterNotNull()
                .distinct()
                .map { id -> async { gate.withPermit { read(scope, id) } } }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    private suspend fun read(
        scope: CompanyScope,
        id: IssueId,
    ): Pair<IssueId, IssueRef>? =
        (issues.get(scope, id) as? DomainResult.Success)?.value?.let { detail ->
            id to IssueRef(detail.issue.id, detail.issue.identifier, detail.issue.title, detail.issue.status)
        }
}
