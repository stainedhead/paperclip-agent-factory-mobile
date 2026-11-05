package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The board token cache of the foreground unlock session (ADR-145) is memory only. This replaces the review's
 * AD-001 / AD-139 statement "nothing caches the token" on purpose, and keeps the part of it that still matters: the
 * token is never written anywhere, never serialised, never named in a string and never revealed by the cache.
 */
class SessionCacheRulesTest {
    private val cacheFile =
        File(
            projectRoot,
            "shared/application/src/commonMain/kotlin/com/stainedhead/paperclipmobile/application/auth/" +
                "SessionCachingSecretStore.kt",
        )

    @Test
    fun `the session cache holds no persistence, serialization, file, settings or logging`() {
        Assertions.assertTrue(cacheFile.isFile, "The cache source moved: update this rule with it")
        Assertions.assertEquals(emptyList<String>(), persistenceUses(cacheFile.readText()))
    }

    @Test
    fun `the session cache never reveals the secret`() {
        Assertions.assertFalse(Regex("""\breveal\b""").containsMatchIn(code(cacheFile.readText())))
    }

    @Test
    fun `only the umbrella composition root constructs the session cache`() {
        val offenders =
            File(projectRoot, "shared")
                .walkTopDown()
                .onEnter { it.name != "build" }
                .filter { it.extension == "kt" && "/src/" in it.invariantSeparatorsPath }
                .filter { "SessionCachingSecretStore(" in code(it.readText()) }
                .map { it.relativeTo(projectRoot).invariantSeparatorsPath }
                .filterNot {
                    "/umbrella/src/commonMain/" in it ||
                        "/src/commonTest/" in it ||
                        "/src/jvmTest/" in it ||
                        "/src/konsistTest/" in it ||
                        it.endsWith("/SessionCachingSecretStore.kt")
                }.toList()
        Assertions.assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `the persistence check flags a planted violation`() {
        val planted =
            """
            import kotlinx.serialization.Serializable
            import java.io.File
            import app.cash.sqldelight.db.SqlDriver
            @Serializable class Leak { fun save(f: File) = f.writeText(token) }
            """.trimIndent()

        Assertions.assertTrue(persistenceUses(planted).size >= PLANTED_MINIMUM)
        Assertions.assertEquals(
            emptyList<String>(),
            persistenceUses("// File and println are only words here\nval x = 1"),
        )
    }

    private fun code(source: String): String =
        source
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun persistenceUses(source: String): List<String> =
        code(source)
            .lines()
            .filter { line -> BANNED.any { it.containsMatchIn(line) } }
            .map { it.trim() }

    private companion object {
        const val PLANTED_MINIMUM = 3

        val BANNED =
            listOf(
                Regex("""\bkotlinx\.serialization\b"""),
                Regex("""@Serializable\b"""),
                Regex("""\bapp\.cash\.sqldelight\b"""),
                Regex("""\bjava\.io\b"""),
                Regex("""\bokio\b"""),
                Regex("""\bkotlinx\.io\b"""),
                Regex("""\bSavedState\w*\b"""),
                Regex("""\bSharedPreferences\b"""),
                Regex("""\bUserDefaults\b"""),
                Regex("""\bAppSettingsStore\b"""),
                Regex("""\bprintln\b"""),
                Regex("""\bLog\.\w+"""),
                Regex("""\bNSLog\b"""),
                Regex("""\bwriteText\b"""),
            )
    }
}
