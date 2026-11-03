package com.stainedhead.paperclipmobile.infrastructure.persistence

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AttentionCache
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.contract.AttentionCacheContract
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class SqlAttentionCacheContractTest : AttentionCacheContract() {
    override fun create(): AttentionCache = SqlAttentionCache(TestDatabase().db, FakeClock())

    @Test
    fun `AD-021 the SQL attention cache passes the attention cache contract`() = runTest { runAll() }
}

class SqlAttentionCacheTest {
    private val scope = CompanyScope(InstanceId("inst-1"), TestData.acme)
    private val clock = FakeClock()

    @Test
    fun `AD-020 a feed with every detail kind and every optional field survives a round trip`() =
        runTest {
            val cache = SqlAttentionCache(TestDatabase().db, clock)
            val feed = RichFeed.feed()
            cache.write(scope, feed)
            val read = cache.read(scope)!!
            read.items.size shouldBe feed.items.size
            read.items.zip(feed.items).forEach { (got, want) -> got shouldBe want }
            read shouldBe feed
        }

    @Test
    fun `AD-020 the updated time is the device clock at the moment of the write`() =
        runTest {
            val cache = SqlAttentionCache(TestDatabase().db, clock)
            cache.write(scope, AttentionFixtures.feed())
            cache.updatedAt(scope) shouldBe clock.now()
            clock.advance(5.minutes)
            cache.write(scope, AttentionFixtures.feed())
            cache.updatedAt(scope) shouldBe clock.now()
            cache.updatedAt(CompanyScope(InstanceId("other"), TestData.acme)) shouldBe null
        }

    @Test
    fun `AD-020 a rewrite keeps one row per scope`() =
        runTest {
            val database = TestDatabase()
            val cache = SqlAttentionCache(database.db, clock)
            repeat(3) { cache.write(scope, AttentionFixtures.feed()) }
            database.scalar("SELECT count(*) FROM attention_cache") shouldBe 1
        }

    @Test
    fun `AD-020 a feed of more than 200 items keeps 200 and no cursor that would skip the rest`() =
        runTest {
            val cache = SqlAttentionCache(TestDatabase().db, clock)
            cache.write(scope, RichFeed.many(250, cursor = "next-page"))
            val read = cache.read(scope)!!
            read.items.size shouldBe 200
            read.items.map { it.id } shouldBe
                RichFeed
                    .many(250)
                    .items
                    .take(200)
                    .map { it.id }
            read.nextCursor shouldBe null
            read.totalCount shouldBe 300
        }

    @Test
    fun `AD-020 a feed of exactly 200 items keeps its cursor`() =
        runTest {
            val cache = SqlAttentionCache(TestDatabase().db, clock)
            cache.write(scope, RichFeed.many(200, cursor = "next-page"))
            val read = cache.read(scope)!!
            read.items.size shouldBe 200
            read.nextCursor shouldBe "next-page"
        }

    @Test
    fun `AD-020 a payload that cannot be decoded is a miss and the row is removed`() =
        runTest {
            val database = TestDatabase()
            val cache = SqlAttentionCache(database.db, clock)
            cache.write(scope, AttentionFixtures.feed())
            database.execute("UPDATE attention_cache SET payload='{not json'")
            cache.read(scope) shouldBe null
            database.scalar("SELECT count(*) FROM attention_cache") shouldBe 0
        }

    @Test
    fun `AD-020 a row written by a payload layout this version does not know is a miss and is removed`() =
        runTest {
            val database = TestDatabase()
            val cache = SqlAttentionCache(database.db, clock)
            cache.write(scope, AttentionFixtures.feed())
            database.execute("UPDATE attention_cache SET format=99")
            cache.read(scope) shouldBe null
            database.scalar("SELECT count(*) FROM attention_cache") shouldBe 0
        }
}
