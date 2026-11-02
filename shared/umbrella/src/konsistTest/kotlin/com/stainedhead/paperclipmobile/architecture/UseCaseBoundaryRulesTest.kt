package com.stainedhead.paperclipmobile.architecture

import com.lemonappdev.konsist.api.ext.list.imports
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Use cases are the only callers of gateway ports and the attention cache (AGENTS.md "Clean Architecture", ADR-019).
 * A state holder that reached a gateway would skip the permission, validation and device-authentication order that
 * every mutation use case enforces.
 */
class UseCaseBoundaryRulesTest {
    private val gatewayPattern = Regex("""\.domain\.port\.\w+(Gateway|Cache)$""")

    /** A named gateway import, or a wildcard import of the whole port package (F-C-9). */
    private fun reachesAGateway(
        importName: String,
        isWildcard: Boolean,
    ): Boolean = gatewayPattern.containsMatchIn(importName) || (isWildcard && importName.endsWith(".domain.port"))

    @Test
    fun `the gateway import check flags named and wildcard imports`() {
        Assertions.assertTrue(reachesAGateway("$APP_PACKAGE.domain.port.DecisionGateway", false))
        Assertions.assertTrue(reachesAGateway("$APP_PACKAGE.domain.port", true))
        Assertions.assertFalse(reachesAGateway("$APP_PACKAGE.domain.port.Clock", false))
        Assertions.assertFalse(reachesAGateway("$APP_PACKAGE.domain.port.BiometricResult", false))
    }

    @Test
    fun `presentation never imports a gateway or the attention cache`() {
        val files = scope("presentation").files
        Assertions.assertTrue(files.isNotEmpty(), "No presentation files found: the rule would pass vacuously")
        files.assertTrue(additionalMessage = "State holders must call use cases, never gateway ports") { file ->
            file.imports.none { reachesAGateway(it.name, it.isWildcard) }
        }
    }

    @Test
    fun `domain ports named Gateway are only used by application classes`() {
        val application = scope("application").files
        Assertions.assertTrue(
            application.any { file -> file.imports.any { gatewayPattern.containsMatchIn(it.name) } },
            "Expected use cases to import gateways: the rule would pass vacuously",
        )
        listOf("domain").forEach { module ->
            scope(module).files.filter { it.packagee?.name?.endsWith(".port") != true }.assertTrue(
                additionalMessage = "$module (outside domain.port) must not use gateway ports",
            ) { file -> file.imports.none { gatewayPattern.containsMatchIn(it.name) } }
        }
    }

    @Test
    fun `every mutation use case that needs a role takes a CompanyContext`() {
        val mutating =
            scope("application").classes().filter { cls ->
                cls.functions().any { it.name == "invoke" && it.hasOperatorModifier } &&
                    cls.packagee?.name?.let { name ->
                        listOf(
                            "attention",
                            "decision",
                            "approval",
                            "budget",
                            "issue",
                            "interaction",
                            "agent",
                            "run",
                            "dashboard",
                        ).any {
                            name.endsWith(".$it")
                        }
                    } == true &&
                    listOf(
                        "Load",
                        "Refresh",
                        "Poll",
                        "Read",
                        "Available",
                        "List",
                        "Search",
                        "Tail",
                    ).none { cls.name.startsWith(it) }
            }
        Assertions.assertTrue(mutating.isNotEmpty(), "Expected mutation use cases")
        mutating.assertTrue(
            additionalMessage = "Mutation use cases take a CompanyContext as their first parameter",
        ) { cls ->
            cls
                .functions()
                .first { it.name == "invoke" }
                .parameters
                .firstOrNull()
                ?.type
                ?.name == "CompanyContext"
        }
    }
}
