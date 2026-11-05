package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test

/** R-013: a stray file in the record directory never breaks listing, and a write is still atomic and readable. */
class RecordFilesTest {
    private val f = StoreFixture()
    private val strayBytes = "stray".encodeToByteArray()

    private fun stray(name: String) = File(f.dir, name).also { it.writeBytes(strayBytes) }

    @Test
    fun `R-013 namespaces lists the valid names and skips a name with a bad percent sequence`() =
        runTest {
            f.store.put(f.key, Secret.of("one"), f.open) shouldBe SecretWriteResult.Stored
            stray("bad%zz.rec")
            stray("also%4.rec")

            f.store.namespaces() shouldBe setOf(InstanceId("a"))
        }

    @Test
    fun `R-013 paths skips undecodable names and leaves the files alone`() {
        val files = RecordFiles(f.dir)
        files.write("instance/a/boardToken", strayBytes)
        val bad = stray("bad%zz.rec")

        files.paths() shouldContainExactly listOf("instance/a/boardToken")
        bad.exists() shouldBe true
    }

    @Test
    fun `R-013 deleting a namespace and reading still work beside a stray file`() =
        runTest {
            f.store.put(f.key, Secret.of("one"), f.open) shouldBe SecretWriteResult.Stored
            stray("bad%zz.rec")

            (f.read() as SecretReadResult.Found).secret shouldBe Secret.of("one")
            f.store.deleteNamespace(InstanceId("a"))
            f.read() shouldBe SecretReadResult.Missing
            f.store.namespaces() shouldBe emptySet()
        }
}
