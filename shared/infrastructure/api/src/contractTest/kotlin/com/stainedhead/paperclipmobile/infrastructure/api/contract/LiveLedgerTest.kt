package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.LogChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** F-C-2 and F-C-3: what the live run may call verified, and when it must fail. Pure, no server needed. */
class LiveLedgerTest {
    private val ops = listOf("A.one", "A.two", "A.three")

    private fun ledger(
        skipAllowed: Map<String, String> = emptyMap(),
        emptyAllowed: Map<String, String> = emptyMap(),
    ) = LiveLedger(ops, skipAllowed, emptyAllowed)

    @Test
    fun `an operation that decoded an empty reply is reachable but not verified with data`() {
        val ledger = ledger()
        ledger.record("A.one", "", hasData = false)
        assertEquals(emptySet<String>(), ledger.withData())
        assertEquals(setOf("A.one"), ledger.reachableButEmpty())
        assertTrue(ledger.verdict().problems.any { it.contains("A.one") && it.contains("empty") })
    }

    @Test
    fun `data is sticky over an empty reply whatever the order of the tests`() {
        val first = ledger()
        first.record("A.one", "", hasData = true)
        first.record("A.one", "", hasData = false)
        val second = ledger()
        second.record("A.one", "", hasData = false)
        second.record("A.one", "", hasData = true)
        assertEquals(setOf("A.one"), first.withData())
        assertEquals(setOf("A.one"), second.withData())
        assertEquals(emptySet<String>(), second.reachableButEmpty())
    }

    @Test
    fun `a failure is not erased by a later success`() {
        val ledger = ledger()
        ledger.failed("A.one", "boom")
        ledger.record("A.one", "", hasData = true)
        assertTrue(ledger.verdict().problems.any { it.contains("A.one") && it.contains("boom") })
    }

    @Test
    fun `an error path alone does not count as verified with data`() {
        val ledger = ledger()
        ledger.errorPath("A.one", "(error path Unauthorized)")
        assertEquals(emptySet<String>(), ledger.withData())
        assertTrue(ledger.verdict().problems.any { it.contains("A.one") })
    }

    @Test
    fun `an empty reply is accepted only on the documented allow list`() {
        val ledger = ledger(emptyAllowed = mapOf("A.one" to "the seeded data cannot make it non-empty"))
        ledger.record("A.one", "", hasData = false)
        val rendered = ledger.render()
        assertFalse(ledger.verdict().problems.any { it.contains("A.one") })
        assertTrue(rendered.contains("the seeded data cannot make it non-empty"))
    }

    @Test
    fun `a skip fails the run unless the operation is on the skip allow list`() {
        val strict = ledger()
        strict.skipped("A.one", "no card in the feed")
        assertTrue(strict.verdict().problems.any { it.contains("A.one") && it.contains("no card in the feed") })
        assertTrue(strict.render().contains("SKIPPED"))
        val allowed = ledger(skipAllowed = mapOf("A.one" to "needs an agent run context"))
        allowed.skipped("A.one", "no card in the feed")
        assertFalse(allowed.verdict().problems.any { it.contains("A.one") })
    }

    @Test
    fun `an operation nobody touched fails the run unless it is allow listed`() {
        val ledger = ledger()
        ledger.record("A.one", "", hasData = true)
        ledger.record("A.two", "", hasData = true)
        assertEquals(listOf("A.three never ran against the live server"), ledger.verdict().problems)
        val allowed = LiveLedger(ops, mapOf("A.three" to "agent only"), emptyMap())
        allowed.record("A.one", "", hasData = true)
        allowed.record("A.two", "", hasData = true)
        assertEquals(emptyList<String>(), allowed.verdict().problems)
        assertTrue(allowed.render().contains("SKIPPED"))
    }

    @Test
    fun `a mismatch fails the run and the count is printed even when it is zero`() {
        val clean = ledger()
        ops.forEach { clean.record(it, "", hasData = true) }
        assertTrue(clean.render().contains("MISMATCH 0"))
        val dirty = ledger()
        ops.forEach { dirty.record(it, "", hasData = true) }
        dirty.mismatch("attention dismiss value", "expected active=true")
        assertTrue(dirty.render().contains("MISMATCH 1"))
        assertTrue(dirty.verdict().problems.any { it.contains("attention dismiss value") })
    }

    @Test
    fun `the report ends with a machine readable verdict line`() {
        val failing = ledger()
        failing.failed("A.one", "boom")
        assertTrue(failing.render().lines().any { it.startsWith("VERDICT: FAIL") })
        val passing = ledger()
        ops.forEach { passing.record(it, "", hasData = true) }
        assertTrue(passing.render().lines().any { it == "VERDICT: PASS" })
    }
}
