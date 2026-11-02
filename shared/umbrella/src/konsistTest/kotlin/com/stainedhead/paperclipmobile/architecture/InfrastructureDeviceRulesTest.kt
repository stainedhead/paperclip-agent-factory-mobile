package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

private const val MODIFIERS = "(?:data |enum |sealed |abstract |open |value )*"
private val TOP_LEVEL_DECLARATION =
    Regex(
        "^$MODIFIERS(?:class|object|interface|fun|val|var|typealias)\\s+(?:<[^>]*>\\s*)?(?:[\\w.]+\\.)?(\\w+)",
    )
private val NOT_PUBLIC_DECLARATION = Regex("^(?:internal|private|expect internal|actual internal)\\b")

private fun mainSources(root: File): List<File> =
    File(root, "src")
        .listFiles { f -> f.isDirectory && f.name.endsWith("Main") }
        .orEmpty()
        .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }

private fun imports(source: String): List<String> =
    source
        .lines()
        .map { it.trim() }
        .filter { it.startsWith("import ") }
        .map { it.removePrefix("import ").trim() }

private fun disallowed(
    source: String,
    allowed: List<String>,
): List<String> = imports(source).filter { import -> allowed.none { import.startsWith(it) } }

private fun forbidden(
    source: String,
    banned: List<String>,
): List<String> = imports(source).filter { import -> banned.any { import.startsWith(it) } }

private fun publicTopLevel(source: String): List<String> =
    source
        .lines()
        .filter { TOP_LEVEL_DECLARATION.containsMatchIn(it) && !NOT_PUBLIC_DECLARATION.containsMatchIn(it) }
        .map { TOP_LEVEL_DECLARATION.find(it)!!.groupValues[1] }

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
            val offenders = report(root) { disallowed(it.readText(), ALLOWED_IMPORTS) }
            Assertions.assertTrue(offenders.isEmpty(), "Unexpected imports in ${root.name}: $offenders")
        }
    }

    @Test
    fun `the secret store never reaches a database, a file or user defaults`() {
        val offenders = report(secureStoreRoot) { forbidden(it.readText(), PERSISTENCE_IMPORTS) }
        Assertions.assertTrue(offenders.isEmpty(), "A secret may live only in the Keychain: $offenders")
    }

    @Test
    fun `the platform services never import a secret type`() {
        val offenders = report(platformRoot) { forbidden(it.readText(), listOf("$APP_PACKAGE.domain.Secret")) }
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
            disallowed(source, ALLOWED_IMPORTS),
        )
        Assertions.assertEquals(
            listOf("java.io.File", "app.cash.sqldelight.db.SqlDriver"),
            forbidden(source, PERSISTENCE_IMPORTS),
        )
        Assertions.assertTrue(PRINTING.containsMatchIn("    println(secret)"))
        Assertions.assertTrue(PRINTING.containsMatchIn("NSLog(\"x\")"))
        Assertions.assertEquals(listOf("Leak"), publicTopLevel("class Leak\ninternal class Fine\n"))
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
                "platform.Foundation.NSUserDefaults",
                "platform.Foundation.NSFileManager",
                "platform.Foundation.NSFileHandle",
                "platform.Foundation.NSKeyedArchiver",
            )
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
