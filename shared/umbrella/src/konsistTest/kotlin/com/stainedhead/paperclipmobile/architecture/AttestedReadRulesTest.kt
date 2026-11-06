package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * An attested read is the only thing that may stand in for the unlock prompt (ADR-146), so only the unlock use case
 * may call it, apart from the layers that pass it through (the session cache) or implement it (the stores and fakes).
 */
class AttestedReadRulesTest {
    private val allowedCallers =
        listOf(
            "/application/src/commonMain/kotlin/com/stainedhead/paperclipmobile/application/settings/Settings.kt",
            "/application/src/commonMain/kotlin/com/stainedhead/paperclipmobile/application/auth/SessionCaching",
            "/infrastructure/securestore/src/",
            "/shared/domain/src/commonMain/",
            "/shared/testing/src/commonMain/",
        )

    @Test
    fun `only the unlock use case calls readAttested outside the store layers and tests`() {
        val offenders = callers(File(projectRoot, "shared")) + callers(File(projectRoot, "androidApp"))
        Assertions.assertEquals(emptyList<String>(), offenders.filterNot { path -> allowedCallers.any { it in path } })
    }

    @Test
    fun `the rule finds the unlock use case calling readAttested`() {
        val found = callers(File(projectRoot, "shared"))
        Assertions.assertTrue(found.any { it.endsWith("application/settings/Settings.kt") }, found.toString())
    }

    @Test
    fun `the call scan sees a planted call and ignores a comment`() {
        Assertions.assertTrue(callsAttested("val r = secrets.readAttested(key, \"x\")"))
        Assertions.assertFalse(callsAttested("// secrets.readAttested(key) is only a word here"))
    }

    private fun callers(root: File): List<String> =
        root
            .walkTopDown()
            .onEnter { it.name != "build" }
            .filter { it.extension == "kt" }
            .filter { file ->
                val path = file.invariantSeparatorsPath
                "/src/" in path && !("Test/" in path || "/src/test/" in path || "/src/androidTest/" in path)
            }.filter { callsAttested(it.readText()) }
            .map { it.invariantSeparatorsPath }
            .toList()

    private fun callsAttested(source: String): Boolean =
        source
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .lines()
            .map { it.substringBefore("//") }
            .any { Regex("""\.readAttested\(""").containsMatchIn(it) }
}
