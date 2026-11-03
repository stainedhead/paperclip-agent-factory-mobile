package com.stainedhead.paperclipmobile.infrastructure.api.contract

import java.io.File

private const val NO_SERVER = "\nPaperclip live contract run: no server was used.\nVERDICT: PASS (no server)\n"

/** The printed table of the run (F-C-2, F-C-3). The last lines are the machine-readable verdict the build reads. */
internal fun LiveLedger.render(): String {
    val run = snapshot()
    if (!run.used()) return NO_SERVER
    val out = StringBuilder("\n===== Paperclip live contract run: operations =====\n")
    out.dataSection(run)
    out.emptySection(run)
    out.failureSection(run)
    out.skipSection(run)
    out.append("MISMATCH ${run.mismatches.size}\n")
    run.mismatches.toSortedMap().forEach { (label, text) -> out.append("  [MISMATCH] $label: $text\n") }
    if (run.notes.isNotEmpty()) out.append("Observed on the live server:\n")
    run.notes.toSortedMap().forEach { (topic, text) -> out.append("  - $topic: $text\n") }
    val problems = run.verdict().problems
    out.append(if (problems.isEmpty()) "VERDICT: PASS\n" else "VERDICT: FAIL (${problems.size} problems)\n")
    problems.forEach { out.append("  problem: $it\n") }
    return out.toString()
}

private fun StringBuilder.dataSection(run: LiveSnapshot) {
    append("Verified live WITH DATA (non-empty, decoded): ${run.data.size} of ${run.operations.size}\n")
    run.operations.filter { run.data.containsKey(it) }.forEach {
        append("  [live]  $it ${run.data[it].orEmpty()}".trimEnd() + "\n")
    }
}

private fun StringBuilder.emptySection(run: LiveSnapshot) {
    append("Reachable but EMPTY (decoded an empty reply, not verification): ${run.empty.size}\n")
    run.operations.filter { run.empty.containsKey(it) }.forEach {
        append("  [empty] $it${run.emptyAllowed[it]?.let { why -> " (allowed: $why)" }.orEmpty()}\n")
    }
}

private fun StringBuilder.failureSection(run: LiveSnapshot) {
    val failed = run.operations.filter { run.failures.containsKey(it) }
    append("Tried live and FAILED: ${failed.size}\n")
    failed.forEach { append("  [FAIL]  $it: ${run.failures[it]}\n") }
}

private fun StringBuilder.skipSection(run: LiveSnapshot) {
    val skipped =
        run.operations.filter {
            !run.data.containsKey(it) && !run.failures.containsKey(it) &&
                !run.empty.containsKey(it) && !run.errorPaths.containsKey(it)
        } + run.skips.keys.filter { !run.data.containsKey(it) }
    val unique = skipped.distinct()
    append("SKIPPED or not exercised: ${unique.size}\n")
    unique.forEach {
        val reason = run.skips[it] ?: run.skipAllowed[it]?.let { why -> "$why (allow-listed)" } ?: "never ran"
        append("  [SKIPPED] $it: $reason\n")
    }
}

internal fun LiveLedger.withData(): Set<String> = snapshot().data.keys

internal fun LiveLedger.reachableButEmpty(): Set<String> = snapshot().empty.keys

internal fun LiveLedger.write(reportDir: String?) {
    val text = render()
    println(text)
    reportDir?.let { dir ->
        File(dir).mkdirs()
        File(dir, "live-operations.txt").writeText(text)
    }
}
