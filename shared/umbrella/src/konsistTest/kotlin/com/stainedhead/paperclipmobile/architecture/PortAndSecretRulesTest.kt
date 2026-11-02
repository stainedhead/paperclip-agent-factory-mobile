package com.stainedhead.paperclipmobile.architecture

import com.lemonappdev.konsist.api.Konsist
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

    /**
     * A port is covered when an abstract class in `testing/contract` has a `create()` that returns it (the suite) and a
     * class in the `testing` tests extends that suite (it runs). A KDoc mention of the port covers nothing (F-C-9).
     */
    @Test
    fun `every port in the domain has a contract suite with a subclass that runs it`() {
        val ports = scope("domain").interfaces().filter { it.packagee?.name == portPackage }.map { it.name }
        val suites =
            scope("testing")
                .classes()
                .filter { it.hasAbstractModifier && it.packagee?.name?.endsWith(".contract") == true }
        Assertions.assertTrue(suites.isNotEmpty(), "No contract suites found: the rule would pass vacuously")
        val subclasses = Konsist.scopeFromDirectory("shared/testing/src/commonTest").classes()
        Assertions.assertTrue(subclasses.isNotEmpty(), "No contract subclasses found: the rule would pass vacuously")
        val uncovered =
            ports.filter { port ->
                val suitesForPort =
                    suites.filter { suite ->
                        suite.functions().any { it.name == "create" && it.returnType?.name == port }
                    }
                port !in NO_CONTRACT &&
                    suitesForPort.none { suite -> subclasses.any { it.hasParentWithName(suite.name) } }
            }
        Assertions.assertTrue(
            uncovered.isEmpty(),
            "Ports with no contract suite (abstract class, create() returns the port) that a test extends: $uncovered",
        )
    }

    @Test
    fun `an exempt port really has no contract suite`() {
        val suites =
            scope("testing")
                .classes()
                .filter { it.hasAbstractModifier && it.packagee?.name?.endsWith(".contract") == true }
        val stale =
            NO_CONTRACT.filter { port ->
                suites.any { suite -> suite.functions().any { it.name == "create" && it.returnType?.name == port } }
            }
        Assertions.assertTrue(stale.isEmpty(), "These ports have a suite now, remove them from NO_CONTRACT: $stale")
    }

    @Test
    fun `Secret reveal is called only from infrastructure, the testing fakes and Secret itself`() {
        val sources =
            File(
                projectRoot,
                "shared",
            ).walkTopDown().onEnter { it.name != "build" }.filter { isMainSource(it) }.toList() +
                File(projectRoot, "androidApp").walkTopDown().filter { isAppMainSource(it) }.toList()
        Assertions.assertTrue(
            sources.size > MIN_MAIN_SOURCES,
            "Too few source files found: the rule would pass vacuously",
        )
        val offenders =
            sources
                .filter { revealsASecret(it.readText()) }
                .map { it.relativeTo(projectRoot).invariantSeparatorsPath }
                .filterNot { path ->
                    path.startsWith("shared/infrastructure/") ||
                        path.startsWith("shared/testing/") ||
                        path.endsWith("domain/Secret.kt")
                }
        Assertions.assertTrue(offenders.isEmpty(), "Secret.reveal() is restricted; found in $offenders")
    }

    @Test
    fun `the reveal check sees every way to call it`() {
        listOf(
            "token.reveal()",
            "with(secret) { reveal() }",
            "secret.run { reveal() }",
            "list.map(Secret::reveal)",
            "val f = secret::reveal",
            "secret\n    .reveal()",
        ).forEach { Assertions.assertTrue(revealsASecret(it), "Must be flagged: $it") }
        Assertions.assertFalse(revealsASecret("val revealed = 1 // secret.reveal() in a comment"))
        Assertions.assertFalse(revealsASecret("/** call reveal() */ val x = 1"))
    }

    /** Any use of the identifier `reveal` outside a comment: calls, receivers and callable references alike. */
    private fun revealsASecret(source: String): Boolean =
        source
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .lines()
            .map { it.substringBefore("//") }
            .any { line ->
                Regex("""\breveal\b""").containsMatchIn(line) &&
                    !Regex("""\bfun\s+reveal\b""").containsMatchIn(line)
            }

    @Test
    fun `the rules can see the code they guard`() {
        Assertions.assertTrue(scope("domain").classes().any { it.name == "Secret" })
        Assertions.assertTrue(scope("testing").properties().isNotEmpty())
    }

    private fun isAppMainSource(file: File): Boolean {
        val path = file.invariantSeparatorsPath
        return file.extension == "kt" && "/src/main/" in path && "/build/" !in path
    }

    private fun isMainSource(file: File): Boolean {
        val path = file.invariantSeparatorsPath
        return file.extension == "kt" && "/src/" in path && "Main/" in path && "/build/" !in path
    }

    private companion object {
        const val PORTS_EXPECTED = 10
        const val MIN_MAIN_SOURCES = 100

        /**
         * Ports with no contract suite, each with the reason. They expose only a `StateFlow` that the platform
         * adapter feeds; the type already guarantees what a suite could check, and the only other property (that it
         * follows the real network or the real lifecycle) cannot be driven from common code (F-C-4).
         */
        val NO_CONTRACT =
            mapOf(
                "Connectivity" to "StateFlow of the platform network state",
                "AppLifecycle" to "StateFlow of the platform foreground state",
            ).keys
    }
}
