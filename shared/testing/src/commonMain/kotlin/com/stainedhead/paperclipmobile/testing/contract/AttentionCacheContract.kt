package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AttentionCache
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData

/** Behaviour every [AttentionCache] must have (ADR-017). The SQLDelight adapter subclasses this in Phase 1b. */
abstract class AttentionCacheContract {
    abstract fun create(): AttentionCache

    private companion object {
        const val EXTRA_TOTAL = 40
    }

    private val home = CompanyScope(InstanceId("inst-1"), TestData.acme)
    private val otherCompany = CompanyScope(InstanceId("inst-1"), TestData.globex)
    private val otherFactory = CompanyScope(InstanceId("inst-2"), TestData.acme)

    suspend fun runAll() {
        emptyUntilWritten()
        writeThenReadRoundTrips()
        scopesAreIsolated()
        theLatestWriteReplacesTheEarlierOne()
        clearRemovesOnlyThatScope()
        aLongerFeedIsCutAndItsCursorCleared()
    }

    private suspend fun emptyUntilWritten() {
        check(create().read(home) == null) { "a new cache must be empty" }
    }

    private suspend fun writeThenReadRoundTrips() {
        val cache = create()
        val feed = AttentionFixtures.feed()
        cache.write(home, feed)
        check(cache.read(home) == feed) { "read must return what was written" }
    }

    private suspend fun scopesAreIsolated() {
        val cache = create()
        cache.write(home, AttentionFixtures.feed())
        check(cache.read(otherCompany) == null) { "another company must not see the feed" }
        check(cache.read(otherFactory) == null) { "another factory must not see the feed" }
    }

    private suspend fun theLatestWriteReplacesTheEarlierOne() {
        val cache = create()
        cache.write(home, AttentionFixtures.feed(badge = 1))
        cache.write(home, AttentionFixtures.feed(badge = 2))
        check(cache.read(home)?.deskBadgeCount == 2) { "the latest write must win" }
    }

    private suspend fun clearRemovesOnlyThatScope() {
        val cache = create()
        cache.write(home, AttentionFixtures.feed())
        cache.write(otherCompany, AttentionFixtures.feed())
        cache.clear(home)
        check(cache.read(home) == null) { "clear must remove the feed" }
        check(cache.read(otherCompany) != null) { "clear must not touch another scope" }
    }

    private suspend fun aLongerFeedIsCutAndItsCursorCleared() {
        val cache = create()
        val max = AttentionCache.MAX_ITEMS
        val items = (1..max + 1).map { AttentionFixtures.item("long-$it") }
        cache.write(home, AttentionFixtures.feed(items = items, next = "server-cursor", total = max + EXTRA_TOTAL))
        val cut = cache.read(home)!!
        check(cut.items == items.take(max)) { "a feed over the limit must keep exactly its first $max items" }
        check(cut.nextCursor == null) { "a cut feed must not keep a cursor that points past the dropped items" }
        check(cut.totalCount == max + EXTRA_TOTAL) { "a cut feed must keep the company-wide total" }
        cache.write(home, AttentionFixtures.feed(items = items.take(max), next = "server-cursor"))
        check(cache.read(home)?.nextCursor == "server-cursor") { "a feed at the limit must keep its cursor" }
    }
}
