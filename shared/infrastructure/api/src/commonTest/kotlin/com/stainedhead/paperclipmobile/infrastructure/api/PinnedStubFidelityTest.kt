package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days

/**
 * TASK 0 of 1b-4b: behaviours of the pinned release 2026.1023.0 (compiled `server/dist`) that the 1b-4a stub did not
 * follow, found by reading that dist. They are checked on the stub itself, with raw requests, because the adapters
 * never send these inputs; a stub that is more permissive than the server would let a wrong adapter pass.
 */
class PinnedStubFidelityTest {
    private val world = WorkWorld()
    private val acme = ServerFixtures.COMPANY_ACME

    private suspend fun raw(
        method: HttpMethod,
        vararg segments: String,
        query: List<Pair<String, String>> = emptyList(),
        body: String? = null,
    ): DomainResult<ApiResponse> =
        world.client.send(
            ApiRequest(Destination(TestData.uri()), method, segments.toList(), query, body, Secret.of(TestData.TOKEN)),
        )

    private fun assertRejected(
        reason: String,
        result: DomainResult<ApiResponse>,
    ) {
        val failure = assertIs<DomainResult.Failure>(result)
        assertEquals(DomainError.Rejected(reason), failure.error)
    }

    @Test
    fun `a non UUID id answers 500 where the id column is a uuid`() =
        runTest {
            val internal = DomainResult.Failure(DomainError.ServerError)
            assertEquals(internal, raw(HttpMethod.Get, "approvals", "not-a-uuid"))
            assertEquals(internal, raw(HttpMethod.Get, "decisions", "not-a-uuid"))
            assertEquals(
                internal,
                raw(
                    HttpMethod.Post,
                    "companies",
                    acme,
                    "budget-incidents",
                    "not-a-uuid",
                    "resolve",
                    body = """{"action":"keep_paused"}""",
                ),
            )
        }

    @Test
    fun `a non integer attention limit answers 400 and an out of range one names the range`() =
        runTest {
            world.home.attention.items += FeedFixtures.standard()
            assertRejected(
                "limit must be an integer",
                raw(HttpMethod.Get, "companies", acme, "attention", query = listOf("limit" to "abc")),
            )
            assertRejected(
                "limit must be an integer between 1 and 100",
                raw(HttpMethod.Get, "companies", acme, "attention", query = listOf("limit" to "0")),
            )
        }

    private suspend fun put(json: String) =
        raw(HttpMethod.Put, "companies", acme, "decision-triage", "approval", "ap-1", body = json)

    private suspend fun triage(
        kind: String,
        id: String,
        decideBy: String,
    ) = raw(HttpMethod.Put, "companies", acme, "decision-triage", kind, id, body = """{"decideBy":"$decideBy"}""")

    private suspend fun feedSize(): Int {
        val reply = assertIs<DomainResult.Success<ApiResponse>>(raw(HttpMethod.Get, "companies", acme, "attention"))
        return Regex("\"sourceKind\"").findAll(reply.value.body).count()
    }

    @Test
    fun `a triage snooze in the future hides the card and clearing it brings it back`() =
        runTest {
            world.home.attention.items += FeedFixtures.standard()
            val all = feedSize()
            val until = (world.clock.now() + 1.days).toString()
            assertIs<DomainResult.Success<ApiResponse>>(put("""{"snoozedUntil":"$until"}"""))
            assertEquals(all - 1, feedSize())
            assertIs<DomainResult.Success<ApiResponse>>(put("""{"snoozedUntil":null}"""))
            assertEquals(all, feedSize())
        }

    @Test
    fun `a triage snooze beyond five years is 422 and an invalid calendar date is 400`() =
        runTest {
            world.home.attention.items += FeedFixtures.standard()
            val far = (world.clock.now() + (6 * 366).days).toString()
            assertRejected("snoozedUntil must be within five years", put("""{"snoozedUntil":"$far"}"""))
            assertRejected("Validation error", put("""{"decideBy":"2026-02-31"}"""))
            assertIs<DomainResult.Success<ApiResponse>>(put("""{"decideBy":"2026-02-28"}"""))
        }

    private suspend fun badge(): Int {
        val reply = assertIs<DomainResult.Success<ApiResponse>>(raw(HttpMethod.Get, "companies", acme, "attention"))
        return Regex("\"deskBadgeCount\":(\\d+)").find(reply.value.body)!!.groupValues[1].toInt()
    }

    @Test
    fun `the desk badge counts a decide by date that is today or past and this_week only on a Sunday`() =
        runTest {
            // Cards created on earlier days, so only the decide-by rule can count them (2026-11-01 is a Sunday).
            world.home.attention.items +=
                FeedFixtures.standard().map {
                    FeedFixtures.with(it, "createdAt" to JsonPrimitive("2026-10-01T08:00:00Z"), "decideBy" to JsonNull)
                }
            assertEquals(0, badge())
            triage("approval", "ap-1", "2026-10-30")
            triage("decision", "dec-1", "2026-11-01")
            triage("failed_run", "run-1", "2026-11-02")
            assertEquals(2, badge())
            triage("budget_alert", "b0000001-0000-4000-8000-000000000001", "this_week")
            assertEquals(3, badge())
            // Monday: 2026-11-02 is due today and this_week no longer ends today, so the count stays 3 (not 4).
            world.clock.advance(1.days)
            assertEquals(3, badge())
        }
}
