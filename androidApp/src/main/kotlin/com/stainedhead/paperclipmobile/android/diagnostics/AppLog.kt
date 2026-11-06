package com.stainedhead.paperclipmobile.android.diagnostics

import android.util.Log
import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret

private val URL_LIKE = Regex("""[a-z][a-z0-9+.\-]*://|\bwww\.""", RegexOption.IGNORE_CASE)
private val TOKEN_LIKE = Regex("""pcp_""", RegexOption.IGNORE_CASE)
private val UUID_LIKE =
    Regex("""\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""", RegexOption.IGNORE_CASE)
private val PREFIXED_ID =
    Regex(
        """\b(?:bk|key|inst|run|ap|dec|pap|agent|company|user|demo)-[A-Za-z0-9][A-Za-z0-9-]*""",
        RegexOption.IGNORE_CASE,
    )

/**
 * The one way the app writes a log line (AND-018). Release logging is off: [enabled] is false and [debug] returns
 * before it looks at anything. In a debug build a line is written through [sink] (`Log.d`) unless it carries a secret,
 * an address or an id, which is a programming error and is rejected loudly. A Konsist rule keeps `android.util.Log`
 * out of every other file.
 */
class AppLog(
    private val enabled: Boolean,
    private val sink: (tag: String, message: String) -> Unit = { tag, message -> Log.d(tag, message) },
) {
    fun debug(
        tag: String,
        message: String,
        vararg details: Any?,
    ) {
        if (!enabled) return
        val line = listOf(message, *details).joinToString(" ")
        for (detail in details) {
            require(detail !is Secret && !detail.isIdentity()) {
                "A log line must not carry a secret or an id"
            }
        }
        for (text in listOf(tag, line)) {
            require(!URL_LIKE.containsMatchIn(text)) { "A log line must not carry an address" }
            require(
                !TOKEN_LIKE.containsMatchIn(text) && !UUID_LIKE.containsMatchIn(text) &&
                    !PREFIXED_ID.containsMatchIn(text),
            ) {
                "A log line must not carry a token or an id"
            }
        }
        sink(tag, line)
    }

    private fun Any?.isIdentity() = this is InstanceId || this is CompanyId || this is BaseUri

    companion object {
        /** Logging off: what a release build uses. */
        val Off = AppLog(enabled = false)
    }
}
