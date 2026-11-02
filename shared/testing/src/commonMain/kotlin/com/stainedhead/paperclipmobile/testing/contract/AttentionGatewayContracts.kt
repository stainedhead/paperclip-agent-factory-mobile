package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The feed rules of [AttentionGateway] (research, feed request and cursor paging: `routes/attention.ts:57-92`,
 * `svc/attention.ts:530-546,1893-1937`). The gateway under test holds at least three items.
 */
abstract class AttentionFeedContract {
    abstract fun create(): AttentionGateway

    abstract val scope: CompanyScope

    suspend fun runAll() {
        pagingVisitsEveryItemOnceAndEndsWithANullCursor()
        countsAreCompanyWideOnEveryPage()
        aCursorThatNamesNoItemIsRejected()
    }

    private suspend fun firstPage(gateway: AttentionGateway) =
        gateway.feed(scope, FeedQuery(limit = 2)).mustSucceed("the first page")

    private suspend fun pagingVisitsEveryItemOnceAndEndsWithANullCursor() {
        val gateway = create()
        val seen = mutableListOf<String>()
        var query = FeedQuery(limit = 2)
        var pages = 0
        do {
            val page = gateway.feed(scope, query).mustSucceed("a page")
            check(page.items.size <= 2) { "a page never exceeds the limit" }
            seen += page.items.map { it.id }
            query = FeedQuery(cursor = page.nextCursor, limit = 2)
            pages += 1
            check(pages < MAX_PAGES) { "paging must end" }
        } while (page.nextCursor != null)
        check(
            seen.size >= MIN_ITEMS && seen.toSet().size == seen.size,
        ) { "each item appears exactly once across pages: $seen" }
        check(seen.size == firstPage(gateway).totalCount) { "the pages add up to totalCount" }
    }

    private suspend fun countsAreCompanyWideOnEveryPage() {
        val gateway = create()
        val first = firstPage(gateway)
        val second = gateway.feed(scope, FeedQuery(cursor = first.nextCursor, limit = 2)).mustSucceed("page two")
        check(first.totalCount == second.totalCount) { "totalCount is computed before paging" }
        check(first.deskBadgeCount == second.deskBadgeCount) { "deskBadgeCount is computed before paging" }
        check(first.countsBySourceKind == second.countsBySourceKind) { "counts are computed before paging" }
    }

    private suspend fun aCursorThatNamesNoItemIsRejected() {
        create().feed(scope, FeedQuery(cursor = "no-such-item", limit = 2)).mustBeRejected("a stale cursor")
    }

    private companion object {
        const val MAX_PAGES = 100
        const val MIN_ITEMS = 3
    }
}

/**
 * The per-person triage rules of [AttentionGateway]: dismiss, snooze, undo and decide-by (research, `inbox-dismissals`
 * and `decision-triage`). [now] is the gateway's own notion of the current time.
 */
abstract class AttentionTriageContract {
    abstract fun create(): AttentionGateway

    abstract val scope: CompanyScope
    abstract val now: Instant

    suspend fun runAll() {
        dismissHidesTheItemAndRestoreBringsItBack()
        restoreIsIdempotent()
        snoozeNeedsAFutureTimeAndHidesTheItem()
        decideByIsStoredAndCleared()
    }

    private suspend fun firstItem(gateway: AttentionGateway) =
        gateway
            .feed(scope, FeedQuery())
            .mustSucceed("the feed")
            .items
            .first()

    private suspend fun dismissHidesTheItemAndRestoreBringsItBack() {
        val gateway = create()
        val item = firstItem(gateway)
        gateway.dismiss(scope, item.dismissalKey).mustSucceed("dismiss")
        check(
            gateway
                .feed(scope, FeedQuery())
                .mustSucceed("feed")
                .items
                .none { it.id == item.id },
        ) {
            "a dismissed item leaves the feed"
        }
        gateway.restore(scope, item.dismissalKey).mustSucceed("restore")
        check(
            gateway
                .feed(scope, FeedQuery())
                .mustSucceed("feed")
                .items
                .any { it.id == item.id },
        ) {
            "a restored item returns to the feed"
        }
    }

    private suspend fun restoreIsIdempotent() {
        val gateway = create()
        val key = firstItem(gateway).dismissalKey
        gateway.restore(scope, key).mustSucceed("restore of an item that was never dismissed")
        gateway.restore(scope, key).mustSucceed("a second restore")
    }

    private suspend fun snoozeNeedsAFutureTimeAndHidesTheItem() {
        val gateway = create()
        val item = firstItem(gateway)
        gateway.snooze(scope, item.dismissalKey, now - 1.hours).mustBeRejected("a snooze in the past")
        gateway.snooze(scope, item.dismissalKey, now + 1.hours).mustSucceed("a snooze in the future")
        check(
            gateway
                .feed(scope, FeedQuery())
                .mustSucceed("feed")
                .items
                .none { it.id == item.id },
        ) {
            "a snoozed item leaves the feed"
        }
    }

    private suspend fun decideByIsStoredAndCleared() {
        val gateway = create()
        val item = firstItem(gateway)
        gateway.setDecideBy(scope, item.sourceKind, item.subject.id, DecideBy.Whenever).mustSucceed("set decide-by")
        check(firstItemWithId(gateway, item.id)?.decideBy == DecideBy.Whenever) { "decide-by is stored" }
        gateway.setDecideBy(scope, item.sourceKind, item.subject.id, DecideBy.None).mustSucceed("clear decide-by")
        check(firstItemWithId(gateway, item.id)?.decideBy == DecideBy.None) { "None clears decide-by" }
    }

    private suspend fun firstItemWithId(
        gateway: AttentionGateway,
        id: String,
    ) = (gateway.feed(scope, FeedQuery()) as? DomainResult.Success)?.value?.items?.firstOrNull { it.id == id }
}
