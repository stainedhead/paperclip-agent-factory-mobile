package com.stainedhead.paperclipmobile.architecture

import com.lemonappdev.konsist.api.ext.list.functions
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * The presentation layer is what Swift sees: no `Result`, no declared generics (AGENTS.md "Swift and Kotlin Interop").
 * `StateFlow<X>` and `List<X>` used as types are fine; declaring a generic type or function is not.
 */
class PresentationSurfaceRulesTest {
    private val forbiddenTypes = setOf("Result", "DomainResult")

    @Test
    fun `no public presentation class, function or property declares type parameters`() {
        val scope = scope("presentation")
        Assertions.assertTrue(scope.classes().isNotEmpty(), "Expected presentation classes")
        scope.classes().filter { it.hasPublicOrDefaultModifier }.assertTrue(
            additionalMessage = "Public presentation classes must not be generic",
        ) { it.typeParameters.isEmpty() }
        scope.functions().filter { it.hasPublicOrDefaultModifier }.assertTrue(
            additionalMessage = "Public presentation functions must not be generic",
        ) { it.typeParameters.isEmpty() }
    }

    @Test
    fun `no public presentation signature mentions Result or DomainResult`() {
        val scope = scope("presentation")
        scope.functions().filter { it.hasPublicOrDefaultModifier }.assertTrue(
            additionalMessage = "Public presentation functions must not use Result types",
        ) { function ->
            function.returnType?.name !in forbiddenTypes && function.parameters.none { it.type.name in forbiddenTypes }
        }
        scope.properties().filter { it.hasPublicOrDefaultModifier }.assertTrue(
            additionalMessage = "Public presentation properties must not use Result types",
        ) { it.type?.name !in forbiddenTypes }
    }
}
