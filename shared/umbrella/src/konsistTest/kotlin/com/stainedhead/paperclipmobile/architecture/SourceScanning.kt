package com.stainedhead.paperclipmobile.architecture

import java.io.File

/**
 * Text scanning shared by the infrastructure rules (F-C-6). Regexes are fragile, so every form they must catch has a
 * planted-violation test in `SourceScanningRulesTest`; a rule that cannot fail on a planted violation is not a rule.
 */
internal fun mainSources(root: File): List<File> =
    File(root, "src")
        .listFiles { f -> f.isDirectory && f.name.endsWith("Main") }
        .orEmpty()
        .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }

internal fun importsOf(source: String): List<String> =
    source
        .lines()
        .map { it.trim() }
        .filter { it.startsWith("import ") }
        .map { it.removePrefix("import ").trim() }

internal fun disallowedImports(
    source: String,
    allowed: List<String>,
): List<String> = importsOf(source).filter { import -> allowed.none { import.startsWith(it) } }

internal fun forbiddenImports(
    source: String,
    banned: List<String>,
): List<String> = importsOf(source).filter { import -> banned.any { import.startsWith(it) } }

private const val DECLARATION_MODIFIERS =
    "public|data|enum|sealed|abstract|open|value|suspend|inline|tailrec|operator|infix|const|lateinit|annotation|" +
        "fun|expect|actual|external|final"
private val LEADING_ANNOTATIONS = Regex("^(?:@[\\w.:]+(?:\\([^)]*\\))?\\s+)+")
private val TOP_LEVEL_DECLARATION =
    Regex(
        "^(?:(?:$DECLARATION_MODIFIERS)\\s+)*(?:class|object|interface|fun|val|var|typealias)\\s+" +
            "(?:<[^>]*>\\s*)?(?:[\\w.<>?, ]+\\.)?(\\w+)",
    )
private val NOT_PUBLIC_DECLARATION =
    Regex("^(?:(?:expect|actual)\\s+)?(?:internal|private)\\b")

/**
 * Names of the top-level declarations in [source] that are neither `internal` nor `private`: every modifier form
 * (`suspend fun`, `inline fun`, `const val`, `fun interface`, `annotation class`, `lateinit var`, explicit `public`,
 * `expect`/`actual`) and declarations with annotations on the same line.
 */
internal fun publicTopLevel(source: String): List<String> =
    source
        .lines()
        .map { it.replace(LEADING_ANNOTATIONS, "") }
        .filter { TOP_LEVEL_DECLARATION.containsMatchIn(it) && !NOT_PUBLIC_DECLARATION.containsMatchIn(it) }
        .map { TOP_LEVEL_DECLARATION.find(it)!!.groupValues[1] }

private val CLASS_HEADER = Regex("^class\\s+(\\w+)\\b([^({]*)\\(", RegexOption.MULTILINE)

/** Public classes of [source] whose name matches [named] and whose primary constructor is not `internal`. */
internal fun classesWithoutInternalConstructor(
    source: String,
    named: Regex,
): List<String> =
    CLASS_HEADER
        .findAll(source)
        .filter { named.matches(it.groupValues[1]) && !it.groupValues[2].contains("internal constructor") }
        .map { it.groupValues[1] }
        .toList()

/** Code lines of [source] (no comments) that name any of [names], whatever the import form. */
internal fun codeMentions(
    source: String,
    names: List<String>,
): List<String> =
    source
        .lines()
        .map { it.trim() }
        .filterNot { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }
        .filter { line -> names.any { line.contains(it) } }
