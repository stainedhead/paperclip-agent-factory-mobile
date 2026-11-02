package com.stainedhead.paperclipmobile.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/** Shape rules for use cases, state holders and the names Swift sees. */
class ConventionRulesTest {
    private fun KoClassDeclaration.hasOperatorInvoke() =
        functions().any { it.name == "invoke" && it.hasOperatorModifier }

    @Test
    fun `every use case exposes exactly one public operator invoke`() {
        val useCases = scope("application").classes().filter { it.hasOperatorInvoke() }
        Assertions.assertTrue(useCases.isNotEmpty(), "Expected at least one use case")
        useCases.assertTrue(additionalMessage = "A use case has exactly one public operator fun invoke") {
            it.functions().count { function -> function.name == "invoke" && function.hasOperatorModifier } == 1
        }
    }

    @Test
    fun `every state holder exposes a state flow and accepts intents`() {
        val holders = scope("presentation").classes().filter { it.name.endsWith("StateHolder") }
        Assertions.assertTrue(holders.isNotEmpty(), "Expected at least one state holder")
        holders.assertTrue(additionalMessage = "State holders expose a `state` flow and `onIntent`") { holder ->
            holder.properties().any { it.name == "state" && it.type?.name?.startsWith("StateFlow") == true } &&
                holder.functions().any { it.name == "onIntent" }
        }
    }

    @Test
    fun `swift-facing modules avoid names Objective-C reserves`() {
        // Kotlin/Native renames functions starting with these prefixes (for example newFoo becomes doNewFoo in Swift).
        val reserved = Regex("^(new|alloc|copy|mutableCopy|init)([A-Z].*)?$")
        listOf("presentation", "umbrella").forEach { module ->
            scope(module).functions().assertTrue(
                additionalMessage = "$module must not declare functions that Objective-C reserves",
            ) { !it.hasPublicOrDefaultModifier || !reserved.matches(it.name) }
        }
    }

    @Test
    fun `the architecture rules can see the code they guard`() {
        Assertions.assertTrue(scope("domain").classes().any { it.name == "BaseUri" })
        Assertions.assertTrue(scope("presentation").properties().isNotEmpty())
    }
}
