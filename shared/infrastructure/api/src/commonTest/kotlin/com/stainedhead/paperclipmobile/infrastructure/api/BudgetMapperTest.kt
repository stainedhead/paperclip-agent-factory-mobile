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
    fun `AD-012 a literal that is not a plain non-negative decimal is refused`() {
        listOf("", "-1", "abc", "1e3", "1E21", ".5", "1.", "1.2.3", " 1", "99999999999999999999").forEach {
            assertNull(ceilToCents(it), it)
        }
    }
}
