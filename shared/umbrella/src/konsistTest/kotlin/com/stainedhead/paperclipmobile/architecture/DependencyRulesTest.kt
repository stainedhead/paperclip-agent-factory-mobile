package com.stainedhead.paperclipmobile.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.imports
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

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

    /** The Kotlin files of every `src/<set>Main` source set of [module] (commonMain, iosMain, androidMain...). */
    private fun mainSources(module: File): List<File> =
        File(module, "src")
            .listFiles { f -> f.isDirectory && f.name.endsWith("Main") }
            .orEmpty()
            .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }

    /** The imports in [sources] that reach a package in [forbidden], as `file: import`. */
    private fun forbiddenImports(
        sources: List<File>,
        forbidden: List<String>,
    ): List<String> =
        sources.flatMap { file ->
            file
                .readLines()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .map { it.removePrefix("import ").trim() }
                .filter { import -> forbidden.any { import.startsWith(it) } }
                .map { "${file.name}: $it" }
        }

    private fun forbiddenFor(
        module: File,
        modules: List<File>,
    ) = listOf("$APP_PACKAGE.application", "$APP_PACKAGE.presentation", "$APP_PACKAGE.umbrella") +
        modules.filter { it != module }.map { "$APP_PACKAGE.infrastructure.${it.name}" }

    @Test
    fun `infrastructure modules depend on domain and never on application, presentation or each other`() {
        val modules = File(projectRoot, "shared/infrastructure").listFiles { f -> f.isDirectory }.orEmpty().toList()
        Assertions.assertTrue(modules.isNotEmpty(), "Expected infrastructure modules")
        modules.forEach { module ->
            val scanned = mainSources(module)
            val everything =
                File(
                    module,
                    "src",
                ).walkTopDown().filter { it.extension == "kt" && "Main" in it.path }.toList()
            Assertions.assertEquals(
                everything.size,
                scanned.size,
                "${module.name}: a Kotlin file outside src/<set>Main would escape the rule",
            )
            val offenders = forbiddenImports(scanned, forbiddenFor(module, modules))
            Assertions.assertTrue(
                offenders.isEmpty(),
                "${module.name} must not import application, presentation or other infra: $offenders",
            )
        }
    }

    @Test
    fun `the infrastructure rule scans platform source sets and reports a violation there`() {
        val root = Files.createTempDirectory("pafm-infra-fixture").toFile()
        try {
            val modules = listOf(File(root, "securestore"), File(root, "api"))
            val iosOnly = File(modules[0], "src/iosMain/kotlin/Keychain.kt")
            iosOnly.parentFile.mkdirs()
            iosOnly.writeText("package x\nimport $APP_PACKAGE.application.SignOut\nimport $APP_PACKAGE.domain.Secret\n")
            val android = File(modules[0], "src/androidMain/kotlin/Keystore.kt")
            android.parentFile.mkdirs()
            android.writeText("package x\nimport $APP_PACKAGE.infrastructure.api.Client\n")

            val scanned = mainSources(modules[0])
            val offenders = forbiddenImports(scanned, forbiddenFor(modules[0], modules))

            Assertions.assertEquals(2, scanned.size, "iosMain and androidMain must both be scanned")
            Assertions.assertEquals(
                listOf(
                    "Keystore.kt: $APP_PACKAGE.infrastructure.api.Client",
                    "Keychain.kt: $APP_PACKAGE.application.SignOut",
                ),
                offenders.sortedBy { if (it.startsWith("Keystore")) 0 else 1 },
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
