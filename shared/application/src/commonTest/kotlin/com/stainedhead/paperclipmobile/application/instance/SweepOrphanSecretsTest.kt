package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Review1b F-B-2: secrets of a factory whose record is gone are removed; the secrets of a known factory are not. */
class SweepOrphanSecretsTest {
    private val known = TestData.instance("known")

    private fun Env.seed(id: String) = secrets.seed(SecretKey(InstanceId(id), SecretName.BoardToken), TestData.TOKEN)

    @Test
    fun `secrets of a factory with no row are deleted and a known factory keeps its own`() =
        runTest {
            val env = Env(listOf(known))
            env.seed("known")
            env.seed("orphan-1")
            env.seed("orphan-2")

            SweepOrphanSecrets(env.repository, env.secrets)() shouldBe SweepResult(removed = 2, failed = 0)

            env.secrets.paths shouldBe setOf("instance/known/boardToken")
        }

    @Test
    fun `with no factory rows every namespace is an orphan`() =
        runTest {
            val env = Env()
            env.seed("a")
            env.seed("b")

            SweepOrphanSecrets(env.repository, env.secrets)() shouldBe SweepResult(removed = 2, failed = 0)

            env.secrets.paths shouldBe emptySet()
        }

    @Test
    fun `a namespace that cannot be deleted does not stop the others and is reported as failed`() =
        runTest {
            val env = Env()
            env.seed("a")
            env.seed("b")
            env.secrets.failNamespaceDeletes += InstanceId("a")

            SweepOrphanSecrets(env.repository, env.secrets)() shouldBe SweepResult(removed = 1, failed = 1)

            env.secrets.paths shouldBe setOf("instance/a/boardToken")
        }

    @Test
    fun `nothing to sweep is zero`() =
        runTest {
            SweepOrphanSecrets(Env(listOf(known)).repository, Env().secrets)() shouldBe SweepResult(0, 0)
        }
}
