package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.Company
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class AccessAndExpiryTest {
    private val instance = TestData.instance()

    private fun Env.load() = LoadInstanceAccess(repository, auth, signedOut, clock, connectivity)

    @Test
    fun `FR-022 access lists the companies with the role in each`() =
        runTest {
            val env = Env(listOf(instance))
            env.auth.meResult = DomainResult.Success(TestData.access(Role.Operator))

            val access = (env.load()(instance.id) as DomainResult.Success).value

            access.profile.name shouldBe "Alex Operator"
            access.companies shouldBe
                listOf(
                    Company(TestData.acme, "Acme Labs", Role.Operator),
                    Company(TestData.globex, "Globex", Role.Viewer),
                )
            env.repository.get(instance.id)?.lastConnectedAt shouldBe env.clock.now()
        }

    @Test
    fun `ADR-011 a company without a membership row is a viewer unless the person is an instance admin`() =
        runTest {
            val env = Env(listOf(instance))
            val admin =
                TestData.access().let {
                    it.copy(profile = it.profile.copy(isInstanceAdmin = true), memberships = emptyList())
                }
            env.auth.meResult = DomainResult.Success(admin)
            (env.load()(instance.id) as DomainResult.Success).value.companies.map { it.role } shouldBe
                listOf(Role.Owner, Role.Owner)

            env.auth.meResult = DomainResult.Success(TestData.access().copy(memberships = emptyList()))
            (env.load()(instance.id) as DomainResult.Success).value.companies.map { it.role } shouldBe
                listOf(Role.Viewer, Role.Viewer)
        }

    @Test
    fun `FR-023 a 401 marks the factory signed out and keeps its configuration`() =
        runTest {
            val env = Env(listOf(instance))
            env.auth.meResult = DomainResult.Failure(DomainError.Unauthorized)

            env.load()(instance.id) shouldBe DomainResult.Failure(DomainError.Unauthorized)

            env.repository.get(instance.id)?.status shouldBe InstanceStatus.SignedOut
            env.repository.get(instance.id)?.displayName shouldBe "Home factory"
            env.auth.count("listCompanies") shouldBe 0
        }

    @Test
    fun `FR-096 reads retry with backoff and a company list failure is reported`() =
        runTest {
            val env = Env(listOf(instance))
            env.auth.companiesResult = DomainResult.Failure(DomainError.Unreachable)

            env.load()(instance.id) shouldBe DomainResult.Failure(DomainError.Unreachable)

            env.auth.count("listCompanies") shouldBe 4
            env.auth.count("me") shouldBe 1
        }

    @Test
    fun `FR-023 the warning starts exactly five days before expiry`() {
        val env = Env()
        val status = KeyExpiryStatus(env.clock)
        val now = env.clock.now()

        status(null) shouldBe KeyExpiry(KeyExpiryState.NoExpiry, null)
        status(now + 30.days) shouldBe KeyExpiry(KeyExpiryState.Valid, 30)
        status(now + 5.days + 1.hours) shouldBe KeyExpiry(KeyExpiryState.Valid, 6)
        status(now + 5.days) shouldBe KeyExpiry(KeyExpiryState.ExpiringSoon, 5)
        status(now + 1.hours) shouldBe KeyExpiry(KeyExpiryState.ExpiringSoon, 1)
        status(now) shouldBe KeyExpiry(KeyExpiryState.Expired, 0)
        status(now - 2.days) shouldBe KeyExpiry(KeyExpiryState.Expired, 0)
    }

    @Test
    fun `FR-015 an invalidated device key moves the factory to needs re-pairing`() =
        runTest {
            val env = Env(listOf(instance))
            env.storeToken()
            val check = CheckCredentials(env.repository, env.secrets)

            check(instance.id) shouldBe CredentialState.Usable
            env.secrets.denyReads = true
            check(instance.id) shouldBe CredentialState.Denied
            env.secrets.denyReads = false
            env.secrets.invalidated = true
            check(instance.id) shouldBe CredentialState.NeedsRePairing
            env.repository.get(instance.id)?.status shouldBe InstanceStatus.NeedsRePairing
            check(InstanceId("zzz")) shouldBe CredentialState.Missing
        }

    @Test
    fun `a factory without a stored key reports it missing`() =
        runTest {
            val env = Env(listOf(instance))

            CheckCredentials(env.repository, env.secrets)(instance.id) shouldBe CredentialState.Missing
            env.secrets.put(SecretKey(instance.id, SecretName.BoardToken), Secret.of("x"), SecretPolicy(false))
            CheckCredentials(env.repository, env.secrets)(instance.id) shouldBe CredentialState.Usable
        }
}
