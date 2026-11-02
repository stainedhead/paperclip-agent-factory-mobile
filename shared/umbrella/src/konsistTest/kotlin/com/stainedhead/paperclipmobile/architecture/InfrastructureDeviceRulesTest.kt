package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The device-service modules (`shared/infrastructure/securestore` and `shared/infrastructure/platform`, Phase 1b
 * AD-030 to AD-033): a secret exists only in the Keychain, so the secret store may not touch databases, files or user
 * defaults; both modules see only the domain and the platform; nothing prints.
 */
class InfrastructureDeviceRulesTest {
    private val secureStoreRoot = File(projectRoot, "shared/infrastructure/securestore")
    private val platformRoot = File(projectRoot, "shared/infrastructure/platform")

    private fun report(
        root: File,
        check: (File) -> List<String>,
    ): List<String> {
        val sources = mainSources(root)
        Assertions.assertTrue(sources.isNotEmpty(), "No sources found in ${root.name}: the rule would pass vacuously")
        return sources.flatMap { file -> check(file).map { "${file.name}: $it" } }
    }

    @Test
    fun `securestore and platform import only the domain, the standard library and the platform`() {
        listOf(secureStoreRoot, platformRoot).forEach { root ->
            val offenders = report(root) { disallowedImports(it.readText(), ALLOWED_IMPORTS) }
            Assertions.assertTrue(offenders.isEmpty(), "Unexpected imports in ${root.name}: $offenders")
        }
    }

    @Test
    fun `the secret store never reaches a database, a file or user defaults`() {
        val offenders =
            report(secureStoreRoot) {
                forbiddenImports(it.readText(), PERSISTENCE_IMPORTS) + codeMentions(it.readText(), FOUNDATION_STORES)
            }
        Assertions.assertTrue(offenders.isEmpty(), "A secret may live only in the Keychain: $offenders")
    }

    @Test
    fun `the platform services never import a secret type`() {
        val offenders = report(platformRoot) { forbiddenImports(it.readText(), listOf("$APP_PACKAGE.domain.Secret")) }
        Assertions.assertTrue(offenders.isEmpty(), "Platform services must not see a Secret: $offenders")
    }

    @Test
    fun `neither module prints or logs`() {
        val offenders =
            listOf(secureStoreRoot, platformRoot).flatMap { root ->
                report(root) { file ->
                    file
                        .readLines()
                        .filter { PRINTING.containsMatchIn(it.substringBefore("//")) }
                }
            }
        Assertions.assertTrue(offenders.isEmpty(), "Nothing in the device modules may print or log: $offenders")
    }

    @Test
    fun `the public surface is the adapters and nothing else`() {
        val secure = report(secureStoreRoot) { publicTopLevel(it.readText()) }
        Assertions.assertEquals(
            SECURE_STORE_SURFACE,
            secure.map { it.substringAfter(": ") }.toSet(),
            "securestore public declarations changed: $secure",
        )
        val platform = report(platformRoot) { publicTopLevel(it.readText()) }
        Assertions.assertEquals(
            PLATFORM_SURFACE,
            platform.map { it.substringAfter(": ") }.toSet(),
            "platform public declarations changed: $platform",
        )
    }

    @Test
    fun `the checks see a stray import, a file API, a print and a public declaration`() {
        val source = "import $APP_PACKAGE.application.X\nimport java.io.File\nimport app.cash.sqldelight.db.SqlDriver\n"
        Assertions.assertEquals(
            listOf("$APP_PACKAGE.application.X", "app.cash.sqldelight.db.SqlDriver"),
            disallowedImports(source, ALLOWED_IMPORTS),
        )
        Assertions.assertEquals(
            listOf("java.io.File", "app.cash.sqldelight.db.SqlDriver"),
            forbiddenImports(source, PERSISTENCE_IMPORTS),
        )
        Assertions.assertTrue(PRINTING.containsMatchIn("    println(secret)"))
        Assertions.assertTrue(PRINTING.containsMatchIn("NSLog(\"x\")"))
        Assertions.assertEquals(listOf("Leak"), publicTopLevel("class Leak\ninternal class Fine\n"))
        Assertions.assertEquals(
            listOf("platform.Foundation.*"),
            forbiddenImports("import platform.Foundation.*\nimport platform.Security.*", PERSISTENCE_IMPORTS),
        )
        Assertions.assertEquals(
            listOf("val d = platform.Foundation.NSUserDefaults.standardUserDefaults"),
            codeMentions("val d = platform.Foundation.NSUserDefaults.standardUserDefaults", FOUNDATION_STORES),
        )
    }

    private companion object {
        val ALLOWED_IMPORTS =
            listOf(
                "kotlin.",
                "kotlinx.",
                "platform.",
                "java.",
                "javax.",
                "android.",
                "$APP_PACKAGE.domain",
                "$APP_PACKAGE.infrastructure.securestore",
                "$APP_PACKAGE.infrastructure.platform",
            )
        val PERSISTENCE_IMPORTS =
            listOf(
                "app.cash.sqldelight.",
                "androidx.sqlite.",
                "androidx.datastore.",
                "android.content.SharedPreferences",
                "android.database.",
                "java.io.",
                "java.nio.file.",
                "kotlinx.io.",
                "platform.Foundation.*",
                "platform.Foundation.NSUserDefaults",
                "platform.Foundation.NSFileManager",
                "platform.Foundation.NSFileHandle",
                "platform.Foundation.NSKeyedArchiver",
            )
        val FOUNDATION_STORES = listOf("NSUserDefaults", "NSFileManager", "NSFileHandle", "NSKeyedArchiver")
        val PRINTING = Regex("""\b(println|print|NSLog|Log\.[a-z])\(""")
        val SECURE_STORE_SURFACE = setOf("KeychainSecretStore", "UnavailableSecretStore")
        val PLATFORM_SURFACE =
            setOf(
                "SystemClock",
                "UuidIdGenerator",
                "SystemDeviceTimeZone",
                "UnavailableBiometrics",
                "OptimisticConnectivity",
                "LocalAuthenticationBiometrics",
                "ReasonTexts",
                "NwConnectivity",
            )
    }
}
