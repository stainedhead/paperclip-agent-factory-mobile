package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.LogChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** F-C-2: how a decoded reply is classified, and what `verified` records. Pure, no server needed. */
class LiveVerifiedTest {
    private val ops = listOf("A.one", "A.two", "A.three")

    private fun ledger() = LiveLedger(ops, emptyMap(), emptyMap())

    @Test
    fun `an unknown operation name is refused`() {
        val ledger = ledger()
        val refused = runCatching { ledger.record("A.nine", "", hasData = true) }
        assertTrue(refused.isFailure)
    }

    @Test
    fun `only a non empty decoded value counts as data`() {
        assertFalse(hasData(emptyList<String>()))
        assertTrue(hasData(listOf(1)))
        assertFalse(hasData(emptyMap<String, String>()))
        assertFalse(hasData(""))
        assertFalse(hasData("   "))
        assertTrue(hasData("text"))
        assertFalse(hasData(LogChunk(content = "", nextOffset = null)))
        assertTrue(hasData(LogChunk(content = "hello", nextOffset = null)))
        assertTrue(hasData(1))
    }

    @Test
    fun `verified records data or empty from the result and a failure is recorded and thrown`() {
        val ledger = ledger()
        DomainResult.Success(listOf("row")).verified("A.one", into = ledger)
        DomainResult.Success(emptyList<String>()).verified("A.two", into = ledger)
        assertEquals(setOf("A.one"), ledger.withData())
        assertEquals(setOf("A.two"), ledger.reachableButEmpty())
        val refused = runCatching { DomainResult.Failure(DomainError.NotFound).verified("A.three", into = ledger) }
        assertTrue(refused.isFailure)
        assertTrue(ledger.verdict().problems.any { it.contains("A.three") && it.contains("NotFound") })
    }

    @Test
    fun `a run that used no server passes and says so`() {
        val ledger = ledger()
        assertEquals(emptyList<String>(), ledger.verdict().problems)
        assertTrue(ledger.render().contains("no server was used"))
        assertTrue(ledger.render().lines().any { it.startsWith("VERDICT: PASS") })
    }
}
