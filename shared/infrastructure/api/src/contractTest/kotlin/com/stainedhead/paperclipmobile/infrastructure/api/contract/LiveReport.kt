package com.stainedhead.paperclipmobile.infrastructure.api.contract

import java.util.concurrent.ConcurrentHashMap

/** The outcome of a run: empty [problems] is a pass. */
internal class LiveVerdict(
    val problems: List<String>,
) {
    val passed: Boolean get() = problems.isEmpty()
}

/** What the run did, by outcome (operation or label to detail). */
internal class LiveRecords(
    val data: Map<String, String>,
    val empty: Map<String, String>,
    val errorPaths: Map<String, String>,
    val failures: Map<String, String>,
    val skips: Map<String, String>,
    val mismatches: Map<String, String>,
)

/** A frozen copy of what the run did, with the allow lists it is judged against. */
internal class LiveSnapshot(
    val operations: List<String>,
    val skipAllowed: Map<String, String>,
    val emptyAllowed: Map<String, String>,
    private val records: LiveRecords,
    val notes: Map<String, String>,
) {
    val data: Map<String, String> get() = records.data
    val empty: Map<String, String> get() = records.empty
    val errorPaths: Map<String, String> get() = records.errorPaths
    val failures: Map<String, String> get() = records.failures
    val skips: Map<String, String> get() = records.skips
    val mismatches: Map<String, String> get() = records.mismatches

    fun used(): Boolean = listOf(data, empty, errorPaths, failures, skips, mismatches).any { it.isNotEmpty() }

    fun verdict(): LiveVerdict {
        if (!used()) return LiveVerdict(emptyList())
        val problems = operations.mapNotNull { problemOf(it) }.toMutableList()
        mismatches.toSortedMap().forEach { (label, text) -> problems += "MISMATCH $label: $text" }
        return LiveVerdict(problems)
    }

    private fun problemOf(operation: String): String? =
        when {
            failures.containsKey(operation) -> "$operation failed: ${failures[operation]}"
            data.containsKey(operation) || skipAllowed.containsKey(operation) -> null
            empty.containsKey(operation) && emptyAllowed.containsKey(operation) -> null
            skips.containsKey(operation) -> "$operation was SKIPPED (${skips[operation]}) and is not on the allow list"
            empty.containsKey(operation) -> "$operation decoded only an empty reply and is not on the empty allow list"
            errorPaths.containsKey(operation) -> "$operation only ran its error path, never a successful reply"
            else -> "$operation never ran against the live server"
        }
}

/**
 * Collects what the contract run did to every gateway operation (AD-050, F-C-2, F-C-3). An operation is verified
 * live only when a call returned a successful, decoded, NON-EMPTY result; a reply that decoded to an empty list or
 * text is reported as "reachable but empty" and is not verification. Every other state is a problem that fails the
 * run, unless the operation is on an explicit allow list with a written reason: a failure, a mismatch with what the
 * adapter assumes, a skip, an operation that never ran, a reply that was only ever empty, or an error path alone.
 */
internal open class LiveLedger(
    private val operations: List<String>,
    private val skipAllowed: Map<String, String>,
    private val emptyAllowed: Map<String, String>,
) {
    private val data = ConcurrentHashMap<String, String>()
    private val empty = ConcurrentHashMap<String, String>()
    private val errorPaths = ConcurrentHashMap<String, String>()
    private val failures = ConcurrentHashMap<String, String>()
    private val skips = ConcurrentHashMap<String, String>()
    private val mismatches = ConcurrentHashMap<String, String>()
    private val notes = ConcurrentHashMap<String, String>()

    /** A successful call. [hasData] false means the reply decoded but was empty. Data is sticky over empty. */
    fun record(
        operation: String,
        detail: String,
        hasData: Boolean,
    ) {
        known(operation)
        if (hasData) {
            data[operation] = detail
            empty.remove(operation)
        } else if (!data.containsKey(operation)) {
            empty[operation] = detail
        }
    }

    fun errorPath(
        operation: String,
        detail: String,
    ) {
        known(operation)
        errorPaths[operation] = detail
    }

    /** An operation that was tried against the live server and did not behave as the adapter assumes. Sticky. */
    fun failed(
        operation: String,
        reason: String,
    ) {
        known(operation)
        failures[operation] = reason
    }

    /** A step the run could not perform. It fails the run unless the operation is on the skip allow list. */
    fun skipped(
        operation: String,
        reason: String,
    ) {
        known(operation)
        skips[operation] = reason
    }

    /** A fact about the live server that differs from what the adapters assume. Counted, printed, fails the run. */
    fun mismatch(
        label: String,
        text: String,
    ) {
        mismatches[label] = text
    }

    /** A fact about the live server worth keeping in the run output (what the stub did not know). */
    fun note(
        topic: String,
        text: String,
    ) {
        notes[topic] = text
    }

    fun verdict(): LiveVerdict = snapshot().verdict()

    fun snapshot(): LiveSnapshot =
        LiveSnapshot(
            operations,
            skipAllowed,
            emptyAllowed,
            LiveRecords(
                data.toMap(),
                empty.toMap(),
                errorPaths.toMap(),
                failures.toMap(),
                skips.toMap(),
                mismatches.toMap(),
            ),
            notes.toMap(),
        )

    private fun known(operation: String) = require(operations.contains(operation)) { "Unknown operation $operation" }
}

/** Every port method of the gateways, judged against [SKIP_ALLOWED] and [EMPTY_ALLOWED]. */
internal object LiveReport : LiveLedger(OPERATIONS, SKIP_ALLOWED, EMPTY_ALLOWED)
