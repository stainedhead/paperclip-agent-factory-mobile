package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.port.AttentionCache

/** An in-memory [AttentionCache]. Counts writes so tests can check what was cached. */
class FakeAttentionCache : AttentionCache {
    private val feeds = mutableMapOf<CompanyScope, AttentionFeed>()
    var writes = 0
        private set

    override suspend fun read(scope: CompanyScope): AttentionFeed? = feeds[scope]

    override suspend fun write(
        scope: CompanyScope,
        feed: AttentionFeed,
    ) {
        writes += 1
        feeds[scope] = feed
    }

    override suspend fun clear(scope: CompanyScope) {
        feeds.remove(scope)
    }
}
