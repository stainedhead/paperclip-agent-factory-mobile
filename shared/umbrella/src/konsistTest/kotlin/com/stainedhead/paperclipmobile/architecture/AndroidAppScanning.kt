package com.stainedhead.paperclipmobile.architecture

import java.io.File

/** Kotlin sources the app ships (`main`, `debug`, `release`); unit and instrumented tests are not product code. */
internal fun androidAppSources(root: File): List<File> =
    listOf("main", "debug", "release")
        .map { File(root, "androidApp/src/$it") }
        .filter { it.isDirectory }
        .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }

/** Infrastructure packages the app may import: platform types the umbrella exposes through `api`. */
private val ALLOWED_INFRASTRUCTURE = "$APP_PACKAGE.infrastructure.platform"
private val INFRASTRUCTURE = "$APP_PACKAGE.infrastructure."

internal fun appCodeLines(source: String): List<String> =
    source
        .lines()
        .map { it.trim() }
        .filterNot { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }

internal fun appInfrastructureImports(source: String): List<String> =
    importsOf(source).filter { it.startsWith(INFRASTRUCTURE) && !it.startsWith(ALLOWED_INFRASTRUCTURE) }

private val REVEAL_CALL = Regex("""\.\s*reveal\s*\(""")

internal fun secretReveals(source: String): List<String> =
    appCodeLines(source).filter {
        REVEAL_CALL.containsMatchIn(it)
    }

private val LITERAL_TEXT = Regex("""\bText\(\s*(?:text\s*=\s*)?"""")

internal fun literalTexts(source: String): List<String> =
    appCodeLines(source).filter {
        LITERAL_TEXT.containsMatchIn(it)
    }

/** Lines naming `BiometricPrompt` in a file that declares composables; only `AuthHost` may touch the prompt. */
internal fun composableBiometricCalls(source: String): List<String> =
    if ("@Composable" in source) appCodeLines(source).filter { "BiometricPrompt" in it } else emptyList()

private val SYSTEM_LOG = Regex("""^import android\.util\.Log\b|(?<![A-Za-z0-9_.])Log\.(?:v|d|i|w|e|wtf)\(""")

/** Lines that use `android.util.Log` directly; the app logs through `AppLog` only (AND-018). */
internal fun directLogUse(source: String): List<String> = appCodeLines(source).filter { SYSTEM_LOG.containsMatchIn(it) }

private val SUPPRESSION = Regex("""@(?:file:)?Suppress(?:Lint|Warnings)?\b""")

/** A rule is changed in its config file, never switched off where it fires (AGENTS.md "Toolchain"). */
internal fun inlineSuppressions(source: String): List<String> =
    appCodeLines(source).filter { SUPPRESSION.containsMatchIn(it) }

private val SAVING_API = Regex("""rememberSaveable|SavedStateHandle|savedStateHandle|onSaveInstanceState|outState\.""")
private val SECRET_WORD = Regex("""secret|reveal|token|approvalUrl|boardKey|password""", RegexOption.IGNORE_CASE)

/**
 * Lines that put something secret-looking into saved state (the Bundle that survives process death). Free text such as
 * the factory address may be saved; a key, a token or the approval link never (AND-019).
 */
internal fun secretsInSavedState(source: String): List<String> =
    appCodeLines(source).filter { SAVING_API.containsMatchIn(it) && SECRET_WORD.containsMatchIn(it) }

private val URI_HANDLER = Regex("""LocalUriHandler|\bUriHandler\b|\.openUri\(""")

/** Lines that use the platform URI handler: it opens any scheme and throws without a browser (R-022). */
internal fun uriHandlerUse(source: String): List<String> =
    appCodeLines(source).filter { URI_HANDLER.containsMatchIn(it) }
