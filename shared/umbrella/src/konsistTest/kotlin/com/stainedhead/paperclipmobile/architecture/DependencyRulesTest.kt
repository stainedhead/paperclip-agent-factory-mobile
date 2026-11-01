package com.stainedhead.paperclipmobile.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.imports
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Clean Architecture rules from AGENTS.md. Dependencies point inward only:
 * presentation -> application -> domain <- every infrastructure module
 */
class DependencyRulesTest {
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

    @Test
    fun `domain depends on nothing but the standard library`() =
        importsOnly("domain", STANDARD_IMPORTS + "$APP_PACKAGE.domain")

    @Test
    fun `application depends on domain only`() =
        importsOnly("application", STANDARD_IMPORTS + "$APP_PACKAGE.domain" + "$APP_PACKAGE.application")

    @Test
    fun `presentation depends on application and domain only`() =
        importsOnly(
            "presentation",
            STANDARD_IMPORTS + "$APP_PACKAGE.domain" + "$APP_PACKAGE.application" + "$APP_PACKAGE.presentation",
        )

    @Test
    fun `infrastructure modules depend on domain and never on application, presentation or each other`() {
        val modules = File(projectRoot, "shared/infrastructure").listFiles { f -> f.isDirectory }.orEmpty()
        Assertions.assertTrue(modules.isNotEmpty(), "Expected infrastructure modules")
        modules.forEach { module ->
            if (!File(module, "src/commonMain").exists()) return@forEach
            val forbidden =
                listOf("$APP_PACKAGE.application", "$APP_PACKAGE.presentation", "$APP_PACKAGE.umbrella") +
                    modules.filter { it != module }.map { "$APP_PACKAGE.infrastructure.${it.name}" }
            Konsist.scopeFromDirectory("shared/infrastructure/${module.name}/src/commonMain").files.assertTrue(
                additionalMessage = "${module.name} must not import $forbidden",
            ) { file -> file.imports.none { import -> forbidden.any { import.name.startsWith(it) } } }
        }
    }
}
