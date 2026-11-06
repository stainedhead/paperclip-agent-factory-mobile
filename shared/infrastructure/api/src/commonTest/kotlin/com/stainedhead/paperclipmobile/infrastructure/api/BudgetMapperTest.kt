package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.infrastructure.api.dto.ceilToCents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ADR-016: the server's `numeric` money, as the literal it sends, becomes whole cents without floating point. */
class BudgetMapperTest {
    @Test
    fun `AD-012 whole numbers pass through and fractions round up`() {
        assertEquals(0L, ceilToCents("0"))
        assertEquals(12L, ceilToCents("12"))
        assertEquals(12L, ceilToCents("12.0000000"))
        assertEquals(13L, ceilToCents("12.0000001"))
        assertEquals(13L, ceilToCents("12.5"))
        assertEquals(10_000L, ceilToCents("9999.9999999"))
    }

    @Test
    fun `AD-012 a literal that is not a non-negative decimal is refused`() {
        listOf(
            "",
            "-1",
            "abc",
            "1E21",
            ".5",
            "1.",
            "1.2.3",
            " 1",
            "99999999999999999999",
            "1e",
            "e5",
            "1e-",
            "1e+-3",
            "1.e3",
            "1e3.5",
            "1e400",
            "-1e3",
            "1e21",
        ).forEach { assertNull(ceilToCents(it), it) }
    }

    /**
     * Pinned dist: `amountObserved` and `spentMonthlyCents` are `numeric(24,7)` read with `mode: "number"`, and
     * `monthSpendCents` and window-spend `costCents` are `::double precision`; JavaScript prints such a number below
     * 1e-6 as `5e-7` (`JSON.stringify`), so a tiny spend arrives as an exponent literal.
     */
    @Test
    fun `AD-012 an exponent literal is read exactly and rounded up`() {
        assertEquals(1L, ceilToCents("5e-7"))
        assertEquals(1L, ceilToCents("5E-7"))
        assertEquals(1L, ceilToCents("2.5e-1"))
        assertEquals(1_500L, ceilToCents("1.5e3"))
        assertEquals(1_500L, ceilToCents("1.5e+3"))
        assertEquals(1_000L, ceilToCents("1e3"))
        assertEquals(0L, ceilToCents("0e0"))
        assertEquals(0L, ceilToCents("0.0e-9"))
        assertEquals(124L, ceilToCents("1.235e2"))
    }
}
