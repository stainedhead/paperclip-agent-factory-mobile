package com.stainedhead.paperclipmobile.infrastructure.persistence

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PaperclipPersistenceTest {
    @Test
    fun `AD-022 cache rows whose factory no longer exists are removed when the database is opened`() {
        val database = TestDatabase()
        database.execute(
            "INSERT INTO attention_cache(instance_id, company_id, payload, updated_at, format) " +
                "VALUES ('gone', 'c1', '{}', '2026-11-02T10:00:00Z', 1)",
        )
        PaperclipPersistence(database.driver, FakeClock(), recreated = false)
        database.scalar("SELECT count(*) FROM attention_cache") shouldBe 0
    }

    @Test
    fun `AD-022 opening keeps the cache of a factory that exists`() =
        runTest {
            val database = TestDatabase()
            val first = PaperclipPersistence(database.driver, FakeClock(), recreated = false)
            first.instances.add(TestData.instance("kept"))
            first.attentionCache.write(CompanyScope(InstanceId("kept"), TestData.acme), AttentionFixtures.feed())

            val second = PaperclipPersistence(database.driver, FakeClock(), recreated = false)

            second.instances
                .observeAll()
                .first()
                .size shouldBe 1
            second.attentionCache.read(CompanyScope(InstanceId("kept"), TestData.acme)) shouldBe
                AttentionFixtures.feed()
        }
}
