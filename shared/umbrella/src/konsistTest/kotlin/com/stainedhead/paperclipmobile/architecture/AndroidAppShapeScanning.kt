package com.stainedhead.paperclipmobile.architecture

private val TEMPLATE_GLUE = Regex(""""[^"\n]*\$\{?[\w.()]+\}?\s?[.:,;·-]\s\$\{?[\w.(]""")
private val DOT_SEPARATOR = Regex("""joinToString\(\s*(?:"\s*·\s*"|SEPARATOR)|const val SEPARATOR\b""")

/**
 * Lines that build a user-facing sentence from parts in code: a template with punctuation between two values, or a
 * list joined with a hard-wired separator. Punctuation and order belong in a string resource (R-029).
 */
internal fun gluedSentences(source: String): List<String> =
    codeLines(source).filter { TEMPLATE_GLUE.containsMatchIn(it) || DOT_SEPARATOR.containsMatchIn(it) }

private val CLASSIFICATION_EXTENSION = Regex("""\bfun\s+\w+Ui\.(?:tone|isLive|visibleActions|hintText)\s*\(""")
private val CLASSIFICATION_TYPE =
    Regex("""\b(?:class|object|interface)\s+(?:FactoryStatusPresentation|ExpiryPresentation|FactoryStatusLabel)\b""")
private val TONE_ARM = Regex("""^(?!ToneUi\.)[\w.]+(?:,\s*[\w.]+)*\s*->\s*StatusTone\.|\bto\s+StatusTone\.""")

/**
 * Lines where the app decides a classification the shared presentation already decides (R-019): an extension that says
 * whether a run is live, which row buttons show, which tone a status earns or how a decide-by is written; a `when` arm
 * that maps a status to a tone; a copy of the factory status or expiry decision. Mapping the shared `ToneUi` family to
 * a colour is the app's job and is allowed.
 */
internal fun appClassifications(source: String): List<String> =
    codeLines(source).filter {
        CLASSIFICATION_EXTENSION.containsMatchIn(it) || CLASSIFICATION_TYPE.containsMatchIn(it) ||
            TONE_ARM.containsMatchIn(it)
    }
