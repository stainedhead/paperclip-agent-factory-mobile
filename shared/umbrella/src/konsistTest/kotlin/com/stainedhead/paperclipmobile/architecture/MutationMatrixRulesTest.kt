package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Spec acceptance 8 and 10 cover every mutation use case through one registry (`MutationMatrix.kt` in the application
 * tests). A use case is a mutation when its `invoke` takes a `CompanyContext` first and returns a `MutationResult`.
 * Adding one without a row in the registry fails here, so the role and timeout matrices cannot silently shrink.
 */
class MutationMatrixRulesTest {
    private val registry =
        File(
            projectRoot,
            "shared/application/src/commonTest/kotlin/com/stainedhead/paperclipmobile/application/MutationMatrix.kt",
        )

    private fun mutationUseCases(): List<String> =
        scope("application")
            .classes()
            .filter { cls ->
                cls.functions().any { fn ->
                    fn.name == "invoke" &&
                        fn.hasOperatorModifier &&
                        fn.parameters
                            .firstOrNull()
                            ?.type
                            ?.name == "CompanyContext" &&
                        fn.returnType?.name?.startsWith("MutationResult") == true
                }
            }.map { it.name }
            .sorted()

    /** The registry without comments, so a mention in a KDoc or `//` line does not count as a row. */
    private fun registryCode(): String =
        registry
            .readText()
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `every mutation use case has a row in the role and timeout matrix`() {
        val useCases = mutationUseCases()
        Assertions.assertTrue(useCases.size >= MIN_MUTATIONS, "Found only $useCases: the rule would pass vacuously")
        val code = registryCode()
        val missing = useCases.filter { !code.contains("MutationCase(\"$it\"") && !code.contains("\"$it\",") }
        Assertions.assertTrue(missing.isEmpty(), "Mutation use cases with no row in MutationMatrix.kt: $missing")
    }

    @Test
    fun `a use case named only in a comment has no row`() {
        val sample = "/** MutationCase(\"Ghost\" */\n// MutationCase(\"Ghost2\",\nval x = 1"
        val stripped =
            sample
                .replace(Regex("""(?s)/\*.*?\*/"""), "")
                .lines()
                .joinToString("\n") { it.substringBefore("//") }
        Assertions.assertFalse(stripped.contains("Ghost"), "Comments must be stripped before the registry is read")
    }

    private companion object {
        const val MIN_MUTATIONS = 20
    }
}
