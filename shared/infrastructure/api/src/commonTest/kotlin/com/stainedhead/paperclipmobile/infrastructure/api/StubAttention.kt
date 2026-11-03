package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private class StubDismissal(
    val kind: String,
    val dismissedAt: Instant,
    val snoozedUntil: Instant?,
)

private fun JsonObject.str(name: String): String = (this[name] as JsonPrimitive).content

private val JsonObject.sourceId: String get() = (this["subject"] as JsonObject).str("id")

/**
 * The feed, dismissals and triage as the pinned server does them (`routes/attention.ts:57-92`,
 * `routes/inbox-dismissals.ts:9-134`, `routes/decision-queues.ts:165-186`, `services/attention.ts:192-208,1885-1937`).
 * Items keep the order they were added in (the stub does not rank). A dismissal hides a card while it is active: a
 * dismiss while `dismissedAt >= activityAt`, a snooze while `snoozedUntil > now`.
 */
internal class StubAttention(
    private val clock: () -> Instant,
) {
    val items = mutableListOf<JsonObject>()
    private val dismissals = mutableMapOf<String, StubDismissal>()
    private val decideBy = mutableMapOf<String, JsonElement>()
    private val snoozes = mutableMapOf<String, Instant>()

    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        if (s.size < MIN || s[0] != "companies") return null
        val company = s[1]
        val method = request.method
        return when {
            method == HttpMethod.Get && s.size == MIN && s[2] == "attention" -> {
                feed(request, company)
            }

            s[2] == "inbox-dismissals" -> {
                dismissals(request, company)
            }

            method == HttpMethod.Put && s.size == TRIAGE && s[2] == "decision-triage" -> {
                triage(request, company, s[3], s[4])
            }

            else -> {
                null
            }
        }
    }

    private fun dismissals(
        request: StubRequest,
        company: String,
    ): StubReply? {
        val s = request.segments
        return when {
            request.method == HttpMethod.Post && s.size == MIN -> dismiss(request, company)
            request.method == HttpMethod.Delete && s.size == KEYED -> restore(company, s[3])
            else -> null
        }
    }

    private fun feed(
        request: StubRequest,
        company: String,
    ): StubReply {
        val sort = request.query["sort"] ?: "activity"
        if (sort != "activity" && sort != "decide") return StubReply.error(400, "sort must be 'activity' or 'decide'")
        limitRefusal(request.query["limit"])?.let { return it }
        val limit = request.query["limit"]?.trim()?.toInt() ?: DEFAULT_LIMIT
        val visible = visible(company)
        val from = startOf(request.query["cursor"], sort, visible)
        if (from < 0) return cursorRefusal(from)
        val page = visible.drop(from).take(limit)
        val more = from + page.size < visible.size
        val next = if (more) StubCursor.encode(sort, page.last().str("id")) else null
        return StubReply.ok(FeedFixtures.page(page, next, visible, badge(visible)))
    }

    /** `routes/attention.js:36-39` (an integer) and `services/attention.js:1565-1568` (1 to 100). */
    private fun limitRefusal(raw: String?): StubReply? {
        val limit = raw?.trim()?.toIntOrNull()
        return when {
            raw == null -> null
            limit == null -> StubReply.error(400, "limit must be an integer")
            limit !in 1..MAX_LIMIT -> StubReply.error(400, "limit must be an integer between 1 and $MAX_LIMIT")
            else -> null
        }
    }

    /** Where the page starts, or a negative code: -1 for a cursor that is not one, -2 for one naming a gone item. */
    private fun startOf(
        cursor: String?,
        sort: String,
        visible: List<JsonObject>,
    ): Int {
        if (cursor == null) return 0
        val id = StubCursor.decode(cursor, sort) ?: return BAD_CURSOR
        val index = visible.indexOfFirst { it.str("id") == id }
        return if (index < 0) GONE_CURSOR else index + 1
    }

    private fun cursorRefusal(code: Int) =
        if (code == BAD_CURSOR) {
            StubReply.error(400, "Invalid attention cursor")
        } else {
            StubReply.error(400, "Attention cursor no longer matches the filtered feed")
        }

    /** `isNewToday || isDecideNow` over the whole feed (`services/attention.js:297-335,1620`). */
    private fun badge(visible: List<JsonObject>): Int {
        val today = clock().toString().substringBefore('T')
        val isSunday = clock().toLocalDateTime(TimeZone.UTC).dayOfWeek == DayOfWeek.SUNDAY
        return visible.count {
            val due = (it["decideBy"] as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
            val decideNow =
                when {
                    due == "today" -> true
                    due == "this_week" -> isSunday
                    due != null && DATE.matches(due) -> due <= today
                    else -> false
                }
            decideNow || it.str("createdAt").substringBefore('T') >= today
        }
    }

    private fun visible(company: String): List<JsonObject> =
        items
            .filter { it.str("companyId") == company }
            .filterNot { active(company, it) }
            .filterNot { snoozed(company, it) }
            .map { item ->
                val key = "$company|${item.str("sourceKind")}|${item.sourceId}"
                decideBy[key]?.let { JsonObject(item + ("decideBy" to it)) } ?: item
            }

    private fun snoozed(
        company: String,
        item: JsonObject,
    ): Boolean = snoozes["$company|${item.str("sourceKind")}|${item.sourceId}"]?.let { it > clock() } == true

    private fun active(
        company: String,
        item: JsonObject,
    ): Boolean {
        val d = dismissals["$company|${item.str("dismissalKey")}"] ?: return false
        if (d.kind == "snooze") return d.snoozedUntil?.let { it > clock() } == true
        return d.dismissedAt >= Instant.parse(item.str("activityAt"))
    }

    private fun dismiss(
        request: StubRequest,
        company: String,
    ): StubReply {
        val body = request.jsonBody() ?: return StubReply.invalid()
        val key = body.string("itemKey")?.trim()?.takeIf { ITEM_KEY.matches(it) } ?: return StubReply.invalid()
        val kind = (if (body.has("kind")) body.string("kind") else "dismiss")?.takeIf { it in KINDS }
        val until = parseFuture(body.string("snoozedUntil"))
        val untilInvalid = if (kind == "snooze") until == null else body.has("snoozedUntil")
        if (kind == null || untilInvalid) return StubReply.invalid()
        val now = clock()
        val snoozedUntil = until.takeIf { kind == "snooze" }
        dismissals["$company|$key"] = StubDismissal(kind, now, snoozedUntil)
        return StubReply.created(dismissalRow(company, key, kind, now, snoozedUntil))
    }

    private fun dismissalRow(
        company: String,
        key: String,
        kind: String,
        now: Instant,
        until: Instant?,
    ) = buildJsonObject {
        put("id", "dismissal-1")
        put("companyId", company)
        put("userId", "user-1")
        put("itemKey", key)
        put("kind", kind)
        put("dismissedAt", now.toString())
        put("snoozedUntil", until?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("updatedAt", now.toString())
    }.toString()

    private fun parseFuture(text: String?): Instant? {
        val parsed =
            try {
                text?.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
            } catch (_: IllegalArgumentException) {
                null
            }
        return parsed?.takeIf { it > clock() }
    }

    private fun restore(
        company: String,
        key: String,
    ): StubReply {
        if (!ITEM_KEY.matches(key)) return StubReply.error(400, "Unsupported inbox item key")
        dismissals.remove("$company|$key")
        return StubReply.noContent()
    }

    private fun triage(
        request: StubRequest,
        company: String,
        kind: String,
        sourceId: String,
    ): StubReply {
        val body = request.jsonBody()
        val snooze = body?.get("snoozedUntil")
        val snoozeAt = (snooze as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::parseInstant)
        triageRefusal(body, snoozeAt, kind, sourceId)?.let { return it }
        val key = "$company|$kind|$sourceId"
        body?.get("decideBy")?.let { decideBy[key] = it }
        if (snooze is JsonNull) snoozes.remove(key)
        if (snoozeAt != null) snoozes[key] = snoozeAt
        return StubReply.ok("""{"companyId":"$company","sourceKind":"$kind","sourceId":"$sourceId"}""")
    }

    /** The order of the server: identity, then the body schema, then the source read (404), then the service rule. */
    private fun triageRefusal(
        body: JsonObject?,
        snoozeAt: Instant?,
        kind: String,
        sourceId: String,
    ): StubReply? =
        when {
            kind !in FeedFixtures.SOURCE_KINDS || sourceId.isBlank() -> {
                StubReply.error(400, "Invalid attention source identity")
            }

            !triageBodyValid(body, snoozeAt) -> {
                StubReply.invalid()
            }

            items.none { it.str("sourceKind") == kind && it.sourceId == sourceId } -> {
                StubReply.notFound("Attention source not found")
            }

            snoozeAt != null && snoozeAt > clock() + FIVE_YEARS -> {
                StubReply.unprocessable("snoozedUntil must be within five years")
            }

            else -> {
                null
            }
        }

    /** `updateDecisionTriageSchema`: strict, at least one key, a decide-by value and a datetime. */
    private fun triageBodyValid(
        body: JsonObject?,
        snoozeAt: Instant?,
    ): Boolean {
        if (body == null || body.isEmpty() || !body.keys.all { it in TRIAGE_KEYS }) return false
        val snooze = body["snoozedUntil"]
        return validDecideBy(body["decideBy"]) && (snooze == null || snooze is JsonNull || snoozeAt != null)
    }

    private fun parseInstant(text: String): Instant? =
        try {
            Instant.parse(text)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun validDecideBy(value: JsonElement?): Boolean {
        if (value == null || value is JsonNull) return true
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return false
        return text in setOf("today", "this_week", "whenever") || isCalendarDate(text)
    }

    /** `calendarDateSchema` (`shared/dist/validators/decision-queue.js:28-31`): the date must exist. */
    private fun isCalendarDate(text: String): Boolean {
        if (!DATE.matches(text)) return false
        val (year, month, day) = text.split('-').map { it.toInt() }
        val leap = year % LEAP_CYCLE == 0 && (year % CENTURY != 0 || year % LEAP_CENTURY == 0)
        val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return month in 1..12 && day in 1..days[month - 1]
    }

    private companion object {
        const val MIN = 3
        const val KEYED = 4
        const val TRIAGE = 5
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 100
        const val BAD_CURSOR = -1
        const val GONE_CURSOR = -2
        const val LEAP_CYCLE = 4
        const val CENTURY = 100
        const val LEAP_CENTURY = 400
        val FIVE_YEARS = (5 * 366).days
        val KINDS = setOf("dismiss", "snooze")
        val TRIAGE_KEYS = setOf("decideBy", "snoozedUntil")
        val ITEM_KEY = Regex("^(approval|join|run|attention):.+$")
        val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
