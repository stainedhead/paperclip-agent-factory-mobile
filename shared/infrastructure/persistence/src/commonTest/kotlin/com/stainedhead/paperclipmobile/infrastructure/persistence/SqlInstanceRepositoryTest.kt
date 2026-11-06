package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.turbine.test
import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.SpkiPin
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.contract.InstanceRepositoryContract
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

class SqlInstanceRepositoryContractTest : InstanceRepositoryContract() {
    override fun create() = SqlInstanceRepository(TestDatabase().db)

    @Test
    fun `AD-021 the SQL instance repository passes the instance repository contract`() = runTest { runAll() }
}

class SqlInstanceRepositoryTest {
    private val pinA = SpkiPin.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")!!
    private val pinB = SpkiPin.parse("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")!!

    @Test
    fun `AD-020 every field survives a round trip when all optional fields are set`() =
        runTest {
            val repo = SqlInstanceRepository(TestDatabase().db)
            val full =
                TestData.instance().copy(
                    boardKeyId = "key-1",
                    keyExpiresAt = Instant.parse("2027-01-02T03:04:05.123456789Z"),
                    defaultCompanyId = CompanyId("c-default"),
                    lastCompanyId = CompanyId("c-last"),
                    pins = listOf(pinA, pinB),
                    createdAt = Instant.parse("2026-11-02T10:00:00.987654321Z"),
                    lastConnectedAt = Instant.parse("2026-11-02T11:00:00.000000001Z"),
                    deploymentMode = DeploymentMode.Authenticated,
                    exposure = Exposure.Public,
                    authMethod = AuthMethod.ManualBoardKey,
                    status = InstanceStatus.NeedsRePairing,
                )
            repo.add(full)
            repo.get(full.id) shouldBe full
        }

    @Test
    fun `AD-020 every field survives a round trip when all optional fields are unset`() =
        runTest {
            val repo = SqlInstanceRepository(TestDatabase().db)
            val bare =
                TestData.instance().copy(
                    boardKeyId = null,
                    keyExpiresAt = null,
                    defaultCompanyId = null,
                    lastCompanyId = null,
                    pins = emptyList(),
                    lastConnectedAt = null,
                )
            repo.add(bare)
            repo.get(bare.id) shouldBe bare
        }

    @Test
    fun `AD-020 a debug http localhost address is read back`() =
        runTest {
            val local = (BaseUri.parse("http://localhost:3100", allowInsecureLocal = true) as BaseUriResult.Valid).uri
            val repo = SqlInstanceRepository(TestDatabase().db)
            repo.add(TestData.instance().copy(baseUri = local))
            repo.get(InstanceId("inst-1"))?.baseUri shouldBe local
        }

    @Test
    fun `AD-020 unknown enum names written by another app version read as the safe value`() =
        runTest {
            val database = TestDatabase()
            val repo = SqlInstanceRepository(database.db)
            repo.add(TestData.instance())
            database.execute(
                "UPDATE instance_config SET deployment_mode='Future', exposure='Future', " +
                    "auth_method='Future', status='Future'",
            )
            val read = repo.get(InstanceId("inst-1"))!!
            read.deploymentMode shouldBe DeploymentMode.Unknown
            read.exposure shouldBe Exposure.Unknown
            read.authMethod shouldBe AuthMethod.ManualBoardKey
            read.status shouldBe InstanceStatus.SignedOut
        }

    @Test
    fun `AD-020 a row that can no longer be read is skipped and not fatal`() =
        runTest {
            val database = TestDatabase()
            val repo = SqlInstanceRepository(database.db)
            repo.add(TestData.instance("good", "Good", sortOrder = 0))
            repo.add(TestData.instance("bad", "Bad", sortOrder = 1))
            database.execute("UPDATE instance_config SET base_uri='not a uri' WHERE id='bad'")
            repo.observeAll().first().map { it.id.value } shouldBe listOf("good")
            repo.get(InstanceId("bad")) shouldBe null
        }

    @Test
    fun `AD-020 a stored pin that is not a valid digest is dropped`() =
        runTest {
            val database = TestDatabase()
            val repo = SqlInstanceRepository(database.db)
            repo.add(TestData.instance().copy(pins = listOf(pinA)))
            database.execute("UPDATE instance_config SET pins='${pinA.value} garbage'")
            repo.get(InstanceId("inst-1"))?.pins shouldBe listOf(pinA)
        }

    @Test
    fun `AD-021 observeAll emits again after every change`() =
        runTest {
            val repo = SqlInstanceRepository(TestDatabase().db)
            repo.observeAll().test {
                awaitItem() shouldBe emptyList()
                repo.add(TestData.instance("a", "A"))
                awaitItem().map { it.displayName } shouldBe listOf("A")
                repo.update(TestData.instance("a", "A2"))
                awaitItem().map { it.displayName } shouldBe listOf("A2")
                repo.delete(InstanceId("a"))
                awaitItem() shouldBe emptyList()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `AD-021 update of an unknown id adds nothing`() =
        runTest {
            val repo = SqlInstanceRepository(TestDatabase().db)
            repo.update(TestData.instance("ghost", "Ghost"))
            repo.observeAll().first() shouldBe emptyList()
        }

    @Test
    fun `AD-021 reorder leaves instances it does not name where they are`() =
        runTest {
            val repo = SqlInstanceRepository(TestDatabase().db)
            repo.add(TestData.instance("a", "A", sortOrder = 0))
            repo.add(TestData.instance("b", "B", sortOrder = 1))
            repo.add(TestData.instance("c", "C", sortOrder = 7))
            repo.reorder(listOf(InstanceId("b"), InstanceId("a")))
            repo.observeAll().first().map { it.id.value to it.sortOrder } shouldBe
                listOf("b" to 0, "a" to 1, "c" to 7)
        }

    @Test
    fun `AD-022 deleting an instance leaves no row of it in any table and keeps the others`() =
        runTest {
            val database = TestDatabase()
            val repo = SqlInstanceRepository(database.db)
            repo.add(TestData.instance("a", "A", sortOrder = 0))
            repo.add(TestData.instance("b", "B", sortOrder = 1))
            for ((instance, company) in listOf("a" to "c1", "a" to "c2", "b" to "c1")) {
                database.execute(
                    "INSERT INTO attention_cache(instance_id, company_id, payload, updated_at) " +
                        "VALUES ('$instance', '$company', '{}', '2026-11-02T10:00:00Z')",
                )
            }

            repo.delete(InstanceId("a"))

            database.scalar("SELECT count(*) FROM instance_config WHERE id='a'") shouldBe 0
            database.scalar("SELECT count(*) FROM attention_cache WHERE instance_id='a'") shouldBe 0
            database.scalar("SELECT count(*) FROM instance_config WHERE id='b'") shouldBe 1
            database.scalar("SELECT count(*) FROM attention_cache WHERE instance_id='b'") shouldBe 1
        }
}
