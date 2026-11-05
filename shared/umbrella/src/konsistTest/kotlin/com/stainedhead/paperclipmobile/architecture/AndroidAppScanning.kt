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

private fun codeLines(source: String): List<String> =
    source
        .lines()
        .map { it.trim() }
        .filterNot { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }

internal fun appInfrastructureImports(source: String): List<String> =
    importsOf(source).filter { it.startsWith(INFRASTRUCTURE) && !it.startsWith(ALLOWED_INFRASTRUCTURE) }

private val REVEAL_CALL = Regex("""\.\s*reveal\s*\(""")

internal fun secretReveals(source: String): List<String> = codeLines(source).filter { REVEAL_CALL.containsMatchIn(it) }

private val LITERAL_TEXT = Regex("""\bText\(\s*(?:text\s*=\s*)?"""")

internal fun literalTexts(source: String): List<String> = codeLines(source).filter { LITERAL_TEXT.containsMatchIn(it) }

/** Lines naming `BiometricPrompt` in a file that declares composables; only `AuthHost` may touch the prompt. */
internal fun composableBiometricCalls(source: String): List<String> =
    if ("@Composable" in source) codeLines(source).filter { "BiometricPrompt" in it } else emptyList()

private val SYSTEM_LOG = Regex("""^import android\.util\.Log\b|(?<![A-Za-z0-9_.])Log\.(?:v|d|i|w|e|wtf)\(""")

/** Lines that use `android.util.Log` directly; the app logs through `AppLog` only (AND-018). */
internal fun directLogUse(source: String): List<String> = codeLines(source).filter { SYSTEM_LOG.containsMatchIn(it) }

private val SUPPRESSION = Regex("""@(?:file:)?Suppress(?:Lint|Warnings)?\b""")

/** A rule is changed in its config file, never switched off where it fires (AGENTS.md "Toolchain"). */
internal fun inlineSuppressions(source: String): List<String> =
    codeLines(source).filter { SUPPRESSION.containsMatchIn(it) }

private val SAVING_API = Regex("""rememberSaveable|SavedStateHandle|savedStateHandle|onSaveInstanceState|outState\.""")
private val SECRET_WORD = Regex("""secret|reveal|token|approvalUrl|boardKey|password""", RegexOption.IGNORE_CASE)

/**
 * Lines that put something secret-looking into saved state (the Bundle that survives process death). Free text such as
 * the factory address may be saved; a key, a token or the approval link never (AND-019).
 */
internal fun secretsInSavedState(source: String): List<String> =
    codeLines(source).filter { SAVING_API.containsMatchIn(it) && SECRET_WORD.containsMatchIn(it) }

private val TEMPLATE_GLUE = Regex(""""[^"\n]*\$\{?[\w.()]+\}?\s?[.:,;·-]\s\$\{?[\w.(]""")
private val DOT_SEPARATOR = Regex("""joinToString\(\s*(?:"\s*·\s*"|SEPARATOR)|const val SEPARATOR\b""")

/**
 * Lines that build a user-facing sentence from parts in code: a template with punctuation between two values, or a
 * list joined with a hard-wired separator. Punctuation and order belong in a string resource (R-029).
 */
internal fun gluedSentences(source: String): List<String> =
    codeLines(source).filter { TEMPLATE_GLUE.containsMatchIn(it) || DOT_SEPARATOR.containsMatchIn(it) }

private val CLASSIFICATION_EXTENSION = Regex("""\bfun\s+\w+Ui\.(?:tone|isLive|visibleActions|hintText)\s*\(""")
private val CLASSIFICATION_TYPE = Regex("""\b(?:class|object|interface)\s+(?:FactoryStatusPresentation|ExpiryPresentation|FactoryStatusLabel)\b""")
private val TONE_ARM = Regex("""^(?!ToneUi\.)[\w.]+(?:,\s*[\w.]+)*\s*->\s*StatusTone\.|\bto\s+StatusTone\.""")

/**
 * Lines where the app decides a classification the shared presentation already decides (R-019): an extension that says
 * whether a run is live, which row buttons show, which tone a status earns or how a decide-by is written; a `when` arm
 * that maps a status to a tone; a copy of the factory status or expiry decision. Mapping the shared `ToneUi` family to a
 * colour is the app's job and is allowed.
 */
internal fun appClassifications(source: String): List<String> =
    codeLines(source).filter {
        CLASSIFICATION_EXTENSION.containsMatchIn(it) || CLASSIFICATION_TYPE.containsMatchIn(it) ||
            TONE_ARM.containsMatchIn(it)
    }
