package com.stainedhead.paperclipmobile.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.ext.list.imports
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Clean Architecture rules from AGENTS.md. Dependencies point inward only:
 * presentation -> application -> domain <- every infrastructure module
 */
class ArchitectureTest {
    private val root = File(requireNotNull(System.getProperty("pafm.rootDir")) { "pafm.rootDir is not set" })
    private val app = "com.stainedhead.paperclipmobile"

    private fun scope(module: String) = Konsist.scopeFromDirectory("shared/$module/src/commonMain")

    private fun importsOnly(
        module: String,
        allowed: List<String>,
    ) {
        val files = scope(module).files
        Assertions.assertTrue(files.isNotEmpty(), "No source files found for $module: the rule would pass vacuously")
        files.assertTrue(additionalMessage = "$module may import only $allowed") { file ->
            file.imports.all { import -> allowed.any { import.name.startsWith(it) } }
        }
    }

    private val standard = listOf("kotlin.", "kotlinx.coroutines.", "kotlinx.datetime.")

    @Test
    fun `domain depends on nothing but the standard library`() = importsOnly("domain", standard + "$app.domain")

    @Test
    fun `application depends on domain only`() =
        importsOnly("application", standard + "$app.domain" + "$app.application")

    @Test
    fun `presentation depends on application and domain only`() =
        importsOnly("presentation", standard + "$app.domain" + "$app.application" + "$app.presentation")

    @Test
    fun `infrastructure modules depend on domain and never on application, presentation or each other`() {
        val modules = File(root, "shared/infrastructure").listFiles { f -> f.isDirectory }.orEmpty()
        Assertions.assertTrue(modules.isNotEmpty(), "Expected infrastructure modules")
        modules.forEach { module ->
            val sources = File(module, "src/commonMain")
            if (!sources.exists()) return@forEach
            val forbidden =
                listOf("$app.application", "$app.presentation", "$app.umbrella") +
                    modules.filter { it != module }.map { "$app.shared.infrastructure.${it.name}" } +
                    modules.filter { it != module }.map { "$app.infrastructure.${it.name}" }
            Konsist.scopeFromDirectory("shared/infrastructure/${module.name}/src/commonMain").files.assertTrue(
                additionalMessage = "${module.name} must not import $forbidden",
            ) { file -> file.imports.none { import -> forbidden.any { import.name.startsWith(it) } } }
        }
    }

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
            val functions = Konsist.scopeFromDirectory("shared/$module/src/commonMain").functions()
            functions.assertTrue(additionalMessage = "$module must not declare functions that Objective-C reserves") {
                !it.hasPublicOrDefaultModifier || !reserved.matches(it.name)
            }
        }
    }

    @Test
    fun `the architecture rules can see the code they guard`() {
        Assertions.assertTrue(scope("domain").classes().any { it.name == "BaseUri" })
        Assertions.assertTrue(scope("presentation").properties().isNotEmpty())
    }

    private fun KoClassDeclaration.hasOperatorInvoke() =
        functions().any { it.name == "invoke" && it.hasOperatorModifier }
}
