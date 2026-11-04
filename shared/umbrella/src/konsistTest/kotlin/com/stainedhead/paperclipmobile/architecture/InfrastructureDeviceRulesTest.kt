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
    fun `the secret store never reaches a database, user defaults or a backed-up directory`() {
        val offenders =
            report(secureStoreRoot) { file ->
                secretStoreViolations(file.name, file.readText())
            }
        Assertions.assertTrue(offenders.isEmpty(), "A secret may live only in the Keychain or Keystore: $offenders")
    }

    @Test
    fun `the secret store reaches a file only in the one record file class, below noBackupFilesDir`() {
        val sources = mainSources(secureStoreRoot)
        val recordFiles = sources.single { it.name == RECORD_FILES }
        Assertions.assertTrue(
            codeMentions(recordFiles.readText(), listOf("noBackupFilesDir")).isNotEmpty(),
            "The records must be placed below noBackupFilesDir",
        )
        val others = sources.filter { it.name != RECORD_FILES }
        Assertions.assertTrue(others.isNotEmpty(), "The rule would pass vacuously")
        val directoryAccess =
            others.flatMap { codeMentions(it.readText(), listOf("File(", "FileOutputStream", "noBackupFilesDir")) }
        Assertions.assertTrue(directoryAccess.isEmpty(), "Only $RECORD_FILES touches files: $directoryAccess")
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
    fun `the Android secret store checks see SharedPreferences, a backed-up directory and a log of a secret`() {
        fun flagged(
            file: String,
            source: String,
        ) = secretStoreViolations(file, source).isNotEmpty()
        Assertions.assertTrue(flagged("Store.kt", "import android.content.SharedPreferences"))
        Assertions.assertTrue(flagged("Store.kt", "val p = context.getSharedPreferences(\"x\", 0)"))
        Assertions.assertTrue(flagged("Store.kt", "val d = context.filesDir"))
        Assertions.assertTrue(flagged("Store.kt", "val d = context.cacheDir"))
        Assertions.assertTrue(flagged("Store.kt", "val d = context.getExternalFilesDir(null)"))
        Assertions.assertTrue(flagged("Store.kt", "val d = context.dataDir"))
        Assertions.assertTrue(flagged("Store.kt", "context.openFileOutput(\"x\", 0)"))
        Assertions.assertTrue(flagged("Store.kt", "import android.util.Log"))
        Assertions.assertTrue(flagged("Store.kt", "import java.io.File"))
        Assertions.assertTrue(flagged("Store.kt", "import java.nio.file.Files"))
        Assertions.assertTrue(flagged("Store.kt", "import androidx.datastore.core.DataStore"))
        Assertions.assertFalse(flagged(RECORD_FILES, "import java.io.File\nimport java.nio.file.Files"))
        Assertions.assertTrue(flagged(RECORD_FILES, "import android.content.SharedPreferences"))
        Assertions.assertFalse(flagged("Store.kt", "val d = File(context.noBackupFilesDir, \"secrets\")"))
        Assertions.assertTrue(PRINTING.containsMatchIn("Log.d(\"tag\", secret.toString())"))
    }

    @Test
    fun `the checks see a stray import, a file API, a print and a public declaration`() {
        val source = "import $APP_PACKAGE.application.X\nimport java.io.File\nimport app.cash.sqldelight.db.SqlDriver\n"
        Assertions.assertEquals(
            listOf("$APP_PACKAGE.application.X", "app.cash.sqldelight.db.SqlDriver"),
            disallowedImports(source, ALLOWED_IMPORTS),
        )
        Assertions.assertEquals(
            listOf("androidx.compose.runtime.Composable", "androidx.room.Room", "com.squareup.okhttp3.OkHttpClient"),
            disallowedImports(
                "import androidx.biometric.BiometricPrompt\nimport androidx.compose.runtime.Composable\n" +
                    "import androidx.room.Room\nimport com.squareup.okhttp3.OkHttpClient",
                ALLOWED_IMPORTS,
            ),
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

    /** Violations of the secret store rules in one source file; only the record file class may use `java.io`. */
    private fun secretStoreViolations(
        fileName: String,
        source: String,
    ): List<String> {
        val banned = if (fileName == RECORD_FILES) PERSISTENCE_IMPORTS - FILE_IMPORTS else PERSISTENCE_IMPORTS
        val code = source.lines().map { it.trim() }.filterNot { it.startsWith("*") || it.startsWith("//") }
        return forbiddenImports(source, banned) +
            codeMentions(source, FOUNDATION_STORES) +
            code.filter { BACKED_UP_DIRECTORIES.containsMatchIn(it) }
    }

    private companion object {
        const val RECORD_FILES = "RecordFiles.kt"
        val BACKED_UP_DIRECTORIES =
            Regex(
                """\b(filesDir|cacheDir|externalCacheDir|externalMediaDirs|dataDir|getFilesDir|getCacheDir|""" +
                    """getExternalFilesDir|getExternalCacheDir|getDataDir|openFileOutput|getDir|""" +
                    """getSharedPreferences|getDatabasePath)\b""",
            )
        val FILE_IMPORTS = listOf("java.io.", "java.nio.file.")
        val ALLOWED_IMPORTS =
            listOf(
                "kotlin.",
                "kotlinx.",
                "platform.",
                "java.",
                "javax.",
                "android.",
                "androidx.biometric.",
                "androidx.core.content.ContextCompat",
                "androidx.fragment.app.FragmentActivity",
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
                "android.util.Log",
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
        val SECURE_STORE_SURFACE = setOf("KeychainSecretStore", "KeystoreSecretStore", "UnavailableSecretStore")
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
                "AuthHost",
                "BiometricPromptBiometrics",
            )
    }
}
