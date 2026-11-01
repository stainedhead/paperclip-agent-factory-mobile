package com.stainedhead.paperclipmobile.application.attention

import app.cash.turbine.test
import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RefreshInboxTest {
    @Test
    fun `FR-095 a refresh asks for the first page sorted by decide-by and caches it`() =
        runTest {
            val env = Env()
            val result = RefreshInbox(env.attention, env.attentionCache)(env.scope)

            (result as DomainResult.Success).value.items.size shouldBe 3
            env.attention.queries shouldBe listOf(FeedQuery())
            env.attentionCache
                .read(env.scope)
                ?.items
                ?.size shouldBe 3
        }

    @Test
    fun `FR-096 a transient failure is retried three times then reported and the cache is left alone`() =
        runTest {
            val env = Env()
            env.attentionCache.write(env.scope, AttentionFixtures.feed(badge = 9))
            env.attention.failures["feed"] = DomainError.Unreachable

            val result = RefreshInbox(env.attention, env.attentionCache)(env.scope)

            (result as DomainResult.Failure).error shouldBe DomainError.Unreachable
            env.attention.count("feed") shouldBe 4
            env.attentionCache.read(env.scope)?.deskBadgeCount shouldBe 9
        }

    @Test
    fun `a signed out key is not retried`() =
        runTest {
            val env = Env()
            env.attention.failures["feed"] = DomainError.Unauthorized

            RefreshInbox(env.attention, env.attentionCache)(env.scope)

            env.attention.count("feed") shouldBe 1
        }
}

class LoadInboxTest {
    @Test
    fun `FR-095 a cached feed is emitted first and then the fresh one which replaces the cache`() =
        runTest {
            val env = Env()
            env.attentionCache.write(env.scope, AttentionFixtures.feed(badge = 9))

            LoadInbox(env.attentionCache, RefreshInbox(env.attention, env.attentionCache))(env.scope).test {
                (awaitItem() as InboxLoad.Cached).feed.deskBadgeCount shouldBe 9
                (awaitItem() as InboxLoad.Fresh).feed.items.size shouldBe 3
                awaitComplete()
            }
            env.attentionCache
                .read(env.scope)
                ?.items
                ?.size shouldBe 3
        }

    @Test
    fun `with an empty cache only the fresh feed is emitted`() =
        runTest {
            val env = Env()

            LoadInbox(env.attentionCache, RefreshInbox(env.attention, env.attentionCache))(env.scope).test {
                awaitItem().shouldBeFresh()
                awaitComplete()
            }
        }

    @Test
    fun `FR-095 when the refresh fails the cached feed was already emitted and the error follows`() =
        runTest {
            val env = Env()
            env.attentionCache.write(env.scope, AttentionFixtures.feed(badge = 9))
            env.attention.failures["feed"] = DomainError.Forbidden

            LoadInbox(env.attentionCache, RefreshInbox(env.attention, env.attentionCache))(env.scope).test {
                awaitItem() shouldBe InboxLoad.Cached(AttentionFixtures.feed(badge = 9))
                awaitItem() shouldBe InboxLoad.Failed(DomainError.Forbidden)
                awaitComplete()
            }
        }

    @Test
    fun `with no cache and a failed refresh only the error is emitted`() =
        runTest {
            val env = Env()
            env.attention.failures["feed"] = DomainError.Forbidden

            LoadInbox(env.attentionCache, RefreshInbox(env.attention, env.attentionCache))(env.scope).test {
                awaitItem() shouldBe InboxLoad.Failed(DomainError.Forbidden)
                awaitComplete()
            }
        }

    private fun InboxLoad.shouldBeFresh() {
        (this is InboxLoad.Fresh) shouldBe true
    }
}

class LoadMoreInboxTest {
    @Test
    fun `FR-030 the next page is requested with its cursor and is never cached`() =
        runTest {
            val env = Env()
            env.attention.items = (1..60).map { AttentionFixtures.approvalItem("a$it") }
            val first = RefreshInbox(env.attention, env.attentionCache)(env.scope)
            val cursor = (first as DomainResult.Success).value.nextCursor
            cursor shouldNotBe null
            env.attentionCache.write(env.scope, AttentionFixtures.feed(badge = 7))

            val more = LoadMoreInbox(env.attention)(env.scope, cursor.orEmpty())

            (more as DomainResult.Success).value.items.size shouldBe 10
            env.attention.queries
                .last()
                .cursor shouldBe cursor
            env.attentionCache.read(env.scope)?.deskBadgeCount shouldBe 7
        }

    @Test
    fun `a cursor the server no longer knows is reported as the server's rejection`() =
        runTest {
            val env = Env()

            val more = LoadMoreInbox(env.attention)(env.scope, "gone")

            (more as DomainResult.Failure).error shouldBe
                DomainError.Rejected("Attention cursor no longer matches the filtered feed")
        }
}

class ReadCachedInboxTest {
    @Test
    fun `FR-095 offline the cached first page is read without any network call`() =
        runTest {
            val env = Env()
            env.attentionCache.write(env.scope, AttentionFixtures.feed(badge = 9))

            ReadCachedInbox(env.attentionCache)(env.scope)?.deskBadgeCount shouldBe 9
            env.attention.count("feed") shouldBe 0
        }

    @Test
    fun `an empty cache reads as nothing`() =
        runTest {
            ReadCachedInbox(Env().attentionCache)(Env().scope) shouldBe null
        }
}
