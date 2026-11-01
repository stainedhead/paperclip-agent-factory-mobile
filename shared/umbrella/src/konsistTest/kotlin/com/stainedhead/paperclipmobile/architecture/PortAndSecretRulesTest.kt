package com.stainedhead.paperclipmobile.architecture

import com.lemonappdev.konsist.api.ext.list.properties
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/** Rules about ports and secrets (AGENTS.md "Mocking and Fakes", "Security Rules"). */
class PortAndSecretRulesTest {
    private val portPackage = "$APP_PACKAGE.domain.port"

    @Test
    fun `every port in the domain has a fake named FakeX in shared testing`() {
        val ports = scope("domain").interfaces().filter { it.packagee?.name == portPackage }.map { it.name }
        Assertions.assertTrue(ports.size >= PORTS_EXPECTED, "Expected the ports to be found, got $ports")
        val fakes = scope("testing").classes().map { it.name }.toSet()
        val missing = ports.filter { "Fake$it" !in fakes }
        Assertions.assertTrue(missing.isEmpty(), "Ports without a fake in shared/testing: $missing")
    }

    @Test
    fun `every port in the domain has a contract suite in shared testing that names it`() {
        val ports = scope("domain").interfaces().filter { it.packagee?.name == portPackage }.map { it.name }
        val contractDir = File(projectRoot, "shared/testing/src/commonMain/kotlin")
        val contracts =
            contractDir
                .walkTopDown()
                .filter { it.extension == "kt" && it.parentFile.name == "contract" }
                .joinToString("\n") { it.readText() }
        Assertions.assertTrue(contracts.isNotBlank(), "No contract suites found: the rule would pass vacuously")
        val missing = ports.filter { "[$it]" !in contracts }
        Assertions.assertTrue(missing.isEmpty(), "Ports without a contract suite in shared/testing/contract: $missing")
    }

    @Test
    fun `Secret reveal is called only from infrastructure, the testing fakes and Secret itself`() {
        val sources = File(projectRoot, "shared").walkTopDown().filter { isMainSource(it) }.toList()
        Assertions.assertTrue(sources.isNotEmpty(), "No source files found: the rule would pass vacuously")
        val offenders =
            sources
                .filter { it.readText().contains(".reveal()") || it.readText().contains("::reveal") }
                .map { it.relativeTo(projectRoot).path }
                .filterNot { path ->
                    path.startsWith("shared/infrastructure/") ||
                        path.startsWith("shared/testing/") ||
                        path.endsWith("domain/Secret.kt")
                }
        Assertions.assertTrue(offenders.isEmpty(), "Secret.reveal() is restricted; found in $offenders")
    }

    @Test
    fun `the rules can see the code they guard`() {
        Assertions.assertTrue(scope("domain").classes().any { it.name == "Secret" })
        Assertions.assertTrue(scope("testing").properties().isNotEmpty())
    }

    private fun isMainSource(file: File): Boolean {
        val path = file.invariantSeparatorsPath
        return file.extension == "kt" && "/src/" in path && "Main/" in path && "/build/" !in path
    }

    private companion object {
        const val PORTS_EXPECTED = 10
    }
}
