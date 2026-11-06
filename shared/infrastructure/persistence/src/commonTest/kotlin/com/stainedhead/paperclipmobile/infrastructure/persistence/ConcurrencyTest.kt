package com.stainedhead.paperclipmobile.infrastructure.persistence

import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test

/** `runTest` is single threaded, so these tests switch to real threads: a lost update needs real parallelism. */
class ConcurrencyTest {
    private val workers = 16
    private val rounds = 30

    @Test
    fun `AD-021 parallel updates of different instances lose none`() =
        runTest {
            val repo = TestDatabase().let { SqlInstanceRepository(it.db, it.dispatcher) }
            repeat(workers) { repo.add(TestData.instance("i$it", "start", sortOrder = it)) }

            withContext(Dispatchers.Default) {
                (0 until workers)
                    .map { worker ->
                        async {
                            repeat(rounds) { round ->
                                repo.update(TestData.instance("i$worker", "w$worker-r$round", sortOrder = worker))
                            }
                        }
                    }.awaitAll()
            }

            val names = repo.observeAll().first().associate { it.id.value to it.displayName }
            names shouldBe (0 until workers).associate { "i$it" to "w$it-r${rounds - 1}" }
        }

    @Test
    fun `AD-021 parallel writes to all three ports lose none`() =
        runTest {
            val database = TestDatabase()
            val repo = SqlInstanceRepository(database.db, database.dispatcher)
            val settings = SqlAppSettingsStore(database.db, database.dispatcher)
            val cache = SqlAttentionCache(database.db, FakeClock(), database.dispatcher)

            withContext(Dispatchers.Default) {
                (0 until workers)
                    .map { worker ->
                        async {
                            repo.add(TestData.instance("i$worker", "I$worker", sortOrder = worker))
                            cache.write(CompanyScope(InstanceId("i$worker"), TestData.acme), AttentionFixtures.feed())
                            settings.save(AppSettings("device-$worker"))
                        }
                    }.awaitAll()
            }

            repo.observeAll().first().size shouldBe workers
            database.scalar("SELECT count(*) FROM attention_cache") shouldBe workers.toLong()
            database.scalar("SELECT count(*) FROM app_settings") shouldBe 1
        }

    @Test
    fun `AD-022 deleting instances while others are cached in parallel leaves no orphan cache row`() =
        runTest {
            val database = TestDatabase()
            val repo = SqlInstanceRepository(database.db, database.dispatcher)
            val cache = SqlAttentionCache(database.db, FakeClock(), database.dispatcher)
            repeat(workers) { repo.add(TestData.instance("i$it", "I$it", sortOrder = it)) }

            withContext(Dispatchers.Default) {
                (0 until workers)
                    .map { worker ->
                        async {
                            cache.write(CompanyScope(InstanceId("i$worker"), TestData.acme), AttentionFixtures.feed())
                            if (worker % 2 == 0) repo.delete(InstanceId("i$worker"))
                        }
                    }.awaitAll()
            }

            database.scalar("SELECT count(*) FROM instance_config") shouldBe (workers / 2).toLong()
            database.scalar("SELECT count(*) FROM attention_cache") shouldBe (workers / 2).toLong()
        }
}
