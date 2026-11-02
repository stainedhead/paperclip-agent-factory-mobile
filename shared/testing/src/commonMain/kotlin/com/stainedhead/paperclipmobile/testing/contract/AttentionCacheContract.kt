package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AttentionCache
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData

/** Behaviour every [AttentionCache] must have (ADR-017). The SQLDelight adapter subclasses this in Phase 1b. */
abstract class AttentionCacheContract {
    abstract fun create(): AttentionCache

    private val home = CompanyScope(InstanceId("inst-1"), TestData.acme)
    private val otherCompany = CompanyScope(InstanceId("inst-1"), TestData.globex)
    private val otherFactory = CompanyScope(InstanceId("inst-2"), TestData.acme)

    suspend fun runAll() {
        emptyUntilWritten()
        writeThenReadRoundTrips()
        scopesAreIsolated()
        theLatestWriteReplacesTheEarlierOne()
        clearRemovesOnlyThatScope()
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
}
