package com.stainedhead.paperclipmobile.architecture

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
