package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.BaseUriError
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SpkiPin
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class InstanceUseCasesTest {
    private val healthy = FakeHealthGateway.healthy()

    private fun add(env: Env) = AddInstance(env.repository, env.clock, env.ids, env.validate)

    @Test
    fun `FR-005 the probe accepts an authenticated server`() =
        runTest {
            val env = Env()

            val result = ProbeInstance(env.health)(TestData.uri())

            result.shouldBeInstanceOf<ProbeResult.Accepted>()
            env.health.lastProbed shouldBe TestData.uri()
        }

    @Test
    fun `FR-005 the probe refuses local_trusted and fails closed on unknown modes`() =
        runTest {
            val env = Env()
            env.health.response = DomainResult.Success(FakeHealthGateway.healthy(mode = DeploymentMode.LocalTrusted))
            (ProbeInstance(env.health)(TestData.uri()) as ProbeResult.Refused).reason shouldBe ProbeRefusal.LocalTrusted

            env.health.response = DomainResult.Success(FakeHealthGateway.healthy(mode = DeploymentMode.Unknown))
            (ProbeInstance(env.health)(TestData.uri()) as ProbeResult.Refused).reason shouldBe
                ProbeRefusal.UnknownDeploymentMode
        }

    @Test
    fun `FR-005 a probe failure is passed on as a domain error`() =
        runTest {
            val env = Env()
            env.health.response = DomainResult.Failure(DomainError.Unreachable)

            ProbeInstance(env.health)(TestData.uri()) shouldBe ProbeResult.Failed(DomainError.Unreachable)
        }

    @Test
    fun `FR-001 adding a factory stores it signed out with the next sort order`() =
        runTest {
            val env = Env(listOf(TestData.instance("old", "Cloud", sortOrder = 4)))

            val result = add(env)(" Home factory ", "HTTPS://Home.example.com/", healthy)

            val added = (result as AddInstanceResult.Added).instance
            added.id shouldBe InstanceId("inst-1")
            added.displayName shouldBe "Home factory"
            added.baseUri.value shouldBe "https://home.example.com"
            added.sortOrder shouldBe 5
            added.status shouldBe InstanceStatus.SignedOut
            env.repository.addCalls shouldBe 1
        }

    @Test
    fun `FR-001 names are 1 to 40 characters and unique ignoring case`() =
        runTest {
            val env = Env(listOf(TestData.instance("a", "Home factory")))

            (add(env)("   ", TestData.HOME_URI, healthy) as AddInstanceResult.InvalidName).issue shouldBe
                InstanceNameIssue.Empty
            (add(env)("x".repeat(41), TestData.HOME_URI, healthy) as AddInstanceResult.InvalidName).issue shouldBe
                InstanceNameIssue.TooLong
            (add(env)("home FACTORY", TestData.HOME_URI, healthy) as AddInstanceResult.InvalidName).issue shouldBe
                InstanceNameIssue.Taken
            add(env)("x".repeat(40), TestData.HOME_URI, healthy).shouldBeInstanceOf<AddInstanceResult.Added>()
            env.repository.addCalls shouldBe 1
        }

    @Test
    fun `FR-003 an insecure or malformed address is not added`() =
        runTest {
            val env = Env()

            (add(env)("Home", "http://home.example.com", healthy) as AddInstanceResult.InvalidAddress).error shouldBe
                BaseUriError.InsecureHttp
            env.repository.addCalls shouldBe 0
        }

    @Test
    fun `FR-005 a refused server is not added even if the caller skipped the probe`() =
        runTest {
            val env = Env()
            val local = FakeHealthGateway.healthy(mode = DeploymentMode.LocalTrusted)

            (add(env)("Home", TestData.HOME_URI, local) as AddInstanceResult.Refused).reason shouldBe
                ProbeRefusal.LocalTrusted
            env.repository.addCalls shouldBe 0
        }

    @Test
    fun `FR-006 renaming keeps credentials and a name may stay the same`() =
        runTest {
            val instance = TestData.instance()
            val env = Env(listOf(instance))
            env.storeToken(instance)
            val update = UpdateInstance(env.repository, env.secrets, env.validate)

            val result = update(instance.id, instance.toEdit().copy(displayName = "Renamed"))

            (result as UpdateInstanceResult.Updated).credentialsCleared shouldBe false
            env.repository.get(instance.id)?.displayName shouldBe "Renamed"
            env.repository.get(instance.id)?.status shouldBe InstanceStatus.SignedIn
            env.secrets.pathsFor(instance.id).size shouldBe 1
            update(instance.id, instance.toEdit()).shouldBeInstanceOf<UpdateInstanceResult.Updated>()
        }

    @Test
    fun `ADR-012 a new address deletes the credentials so a key never goes to another host`() =
        runTest {
            val instance = TestData.instance()
            val env = Env(listOf(instance))
            env.storeToken(instance)
            val update = UpdateInstance(env.repository, env.secrets, env.validate)

            val result = update(instance.id, instance.toEdit().copy(address = "https://other.example.com"))

            (result as UpdateInstanceResult.Updated).credentialsCleared shouldBe true
            env.secrets.pathsFor(instance.id) shouldBe emptySet()
            val stored = env.repository.get(instance.id)!!
            stored.status shouldBe InstanceStatus.SignedOut
            stored.boardKeyId shouldBe null
            stored.baseUri.value shouldBe "https://other.example.com"
        }

    @Test
    fun `FR-006 editing validates the name the address and the target`() =
        runTest {
            val instance = TestData.instance("a", "Home")
            val env = Env(listOf(instance, TestData.instance("b", "Cloud", sortOrder = 1)))
            val update = UpdateInstance(env.repository, env.secrets, env.validate)

            (
                update(
                    instance.id,
                    instance.toEdit().copy(displayName = "cloud"),
                ) as UpdateInstanceResult.InvalidName
            ).issue shouldBe
                InstanceNameIssue.Taken
            (
                update(
                    instance.id,
                    instance.toEdit().copy(address = "nope"),
                ) as UpdateInstanceResult.InvalidAddress
            ).error shouldBe
                BaseUriError.MissingScheme
            update(InstanceId("zzz"), instance.toEdit()) shouldBe UpdateInstanceResult.NotFound
            env.repository.updateCalls shouldBe 0
        }

    @Test
    fun `FR-016 pins and the default company are saved`() =
        runTest {
            val instance = TestData.instance()
            val env = Env(listOf(instance))
            val pin = SpkiPin.parse("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=")!!

            UpdateInstance(env.repository, env.secrets, env.validate)(
                instance.id,
                instance.toEdit().copy(defaultCompanyId = TestData.globex, pins = listOf(pin)),
            )

            env.repository.get(instance.id)?.pins shouldBe listOf(pin)
            env.repository.get(instance.id)?.defaultCompanyId shouldBe TestData.globex
        }

    @Test
    fun `FR-006 reorder needs exactly the configured factories`() =
        runTest {
            val env =
                Env(listOf(TestData.instance("a", "A", sortOrder = 0), TestData.instance("b", "B", sortOrder = 1)))
            val reorder = ReorderInstances(env.repository)

            reorder(listOf(InstanceId("a"))) shouldBe ReorderResult.Mismatch
            reorder(listOf(InstanceId("a"), InstanceId("zzz"))) shouldBe ReorderResult.Mismatch
            env.repository.reorderCalls shouldBe 0

            reorder(listOf(InstanceId("b"), InstanceId("a"))) shouldBe ReorderResult.Reordered
            ObserveInstances(env.repository)().first().map { it.id.value } shouldBe listOf("b", "a")
        }

    @Test
    fun `FR-007 switching instance and company is remembered`() =
        runTest {
            val env = Env(listOf(TestData.instance("a", "A")))

            SwitchInstance(env.repository, env.settings)(InstanceId("a")) shouldBe SwitchResult.Switched
            env.settings.current.selectedInstanceId shouldBe InstanceId("a")
            SwitchInstance(env.repository, env.settings)(InstanceId("zzz")) shouldBe SwitchResult.NotFound

            SwitchCompany(env.repository)(InstanceId("a"), CompanyId("c2")) shouldBe SwitchResult.Switched
            env.repository.get(InstanceId("a"))?.lastCompanyId shouldBe CompanyId("c2")
            SwitchCompany(env.repository)(InstanceId("zzz"), CompanyId("c2")) shouldBe SwitchResult.NotFound
        }

    @Test
    fun `FR-004 and FR-008 a deep link round trips and never carries credentials`() {
        val instance = TestData.instance(name = "Home & café")
        val link = BuildInstanceLink()(instance)

        link shouldBe "paperclipmobile://add?uri=https%3A%2F%2Fhome.example.com&name=Home%20%26%20caf%C3%A9"
        ParseAddLink()(link) shouldBe AddLinkResult.Parsed("https://home.example.com", "Home & café")
        SecretKey(instance.id, SecretName.BoardToken).path.let { link.contains("token") } shouldBe false
    }

    @Test
    fun `FR-004 bad links are rejected`() {
        val parse = ParseAddLink()

        parse("https://example.com") shouldBe AddLinkResult.Invalid
        parse("paperclipmobile://add?name=Home") shouldBe AddLinkResult.Invalid
        parse("paperclipmobile://add?uri=https%3A%2F%2Fa.example.com&token=pcp_board_x") shouldBe AddLinkResult.Invalid
        parse("paperclipmobile://add?uri=a&uri=b") shouldBe AddLinkResult.Invalid
        parse("paperclipmobile://add?uri=%ZZ") shouldBe AddLinkResult.Invalid
        parse("paperclipmobile://add?uri=%C3%28") shouldBe AddLinkResult.Invalid
        parse("paperclipmobile://add?uri=%C") shouldBe AddLinkResult.Invalid
        parse("PaperclipMobile://add?uri=https%3A%2F%2Fa.example.com") shouldBe
            AddLinkResult.Parsed("https://a.example.com", null)
        parse("paperclipmobile://add?uri=https://a.example.com&name=") shouldBe
            AddLinkResult.Parsed("https://a.example.com", null)
        parse("paperclipmobile://add?uri=") shouldBe AddLinkResult.Invalid
    }
}
