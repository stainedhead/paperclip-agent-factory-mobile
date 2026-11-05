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

private val SAVING_API =
    Regex(
        """rememberSaveable|SavedStateHandle|savedStateHandle|\bhandle\.(?:set|get|getStateFlow)\b|""" +
            """onSaveInstanceState|outState\.""",
    )
private val SECRET_WORD = Regex("""secret|reveal|token|approvalUrl|boardKey|password""", RegexOption.IGNORE_CASE)
private const val OPENING = "({["
private const val CLOSING = ")}]"

/** The end of the call that starts at [from] on its line: through its closing bracket, which may be lines later. */
private fun callEnd(
    text: String,
    from: Int,
): Int {
    val lineEnd = text.indexOf('\n', from).let { if (it < 0) text.length else it }
    val open = (from until lineEnd).firstOrNull { text[it] in OPENING || text[it] == ')' }
    if (open == null || text[open] == ')') return lineEnd
    var depth = 0
    for (i in open until text.length) {
        if (text[i] in OPENING) depth++
        if (text[i] in CLOSING && --depth == 0) {
            // A trailing lambda belongs to the same call: `rememberSaveable(key) { ... }`.
            val next = (i + 1 until text.length).firstOrNull { !text[it].isWhitespace() || text[it] == '\n' }
            return if (next != null && text[next] == '{') callEnd(text, next) else i + 1
        }
    }
    return text.length
}

/**
 * The first line of every call that puts something secret-looking into saved state (the Bundle that survives process
 * death), reading the whole call so the secret may sit on a later line. Free text such as the factory address may be
 * saved; a key, a token or the approval link never (AND-019, R-017).
 */
internal fun secretsInSavedState(source: String): List<String> {
    val text = appCodeLines(source).joinToString("\n")
    return SAVING_API
        .findAll(text)
        .map { match ->
            val lineStart = text.lastIndexOf('\n', match.range.first) + 1
            text.substring(lineStart, callEnd(text, match.range.last + 1))
        }.distinct()
        .filter { SECRET_WORD.containsMatchIn(it) }
        .map { it.lines().first().trim() }
        .toList()
}

private val URI_HANDLER = Regex("""LocalUriHandler|\bUriHandler\b|\.openUri\(""")

/** Lines that use the platform URI handler: it opens any scheme and throws without a browser (R-022). */
internal fun uriHandlerUse(source: String): List<String> =
    appCodeLines(source).filter { URI_HANDLER.containsMatchIn(it) }
