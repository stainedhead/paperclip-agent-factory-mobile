package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * F-3 (ADR-151): the sentence a system prompt shows when the board key is read comes from the platform, in the
 * person's language. The API adapters never carry a reason text of their own: it was once the internal string
 * "call paperclip", which reached the screen.
 */
class KeyReadReasonRulesTest {
    private val apiMain = File(projectRoot, "shared/infrastructure/api/src/commonMain")

    @Test
    fun `no secret store read in the API adapters passes a literal reason`() {
        val sources = apiMain.walkTopDown().filter { it.extension == "kt" }.toList()
        Assertions.assertTrue(sources.isNotEmpty(), "No API sources found: the rule would pass vacuously")
        val offenders = sources.flatMap { file -> literalReasons(file.readText()).map { "${file.name}: $it" } }
        Assertions.assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `the literal reason scan flags planted violations and leaves injected reasons alone`() {
        val planted =
            """
            val a = secrets.get(SecretKey(id, SecretName.BoardToken), "call paperclip")
            val b = secrets.readAttested(key, "unlock")
            private companion object { const val REASON = "call paperclip" }
            """.trimIndent()

        Assertions.assertEquals(PLANTED_VIOLATIONS, literalReasons(planted).size)
        Assertions.assertEquals(
            emptyList<String>(),
            literalReasons("val a = secrets.get(SecretKey(id, SecretName.BoardToken), keyReadReason)"),
        )
    }

    private fun literalReasons(source: String): List<String> =
        source
            .lines()
            .map { it.substringBefore("//") }
            .filter { line -> BANNED.any { it.containsMatchIn(line) } }
            .map { it.trim() }

    private companion object {
        const val PLANTED_VIOLATIONS = 3

        val BANNED =
            listOf(
                Regex("""\.(get|readAttested)\(.*,\s*"[^"]*"\s*\)"""),
                Regex("""\bconst val REASON\b"""),
            )
    }
}
