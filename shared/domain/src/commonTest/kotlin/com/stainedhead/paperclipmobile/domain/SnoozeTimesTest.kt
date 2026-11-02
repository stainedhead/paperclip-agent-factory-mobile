package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Instant

/** ADR-020: presets use the device time zone; tonight is 20:00, tomorrow 08:00, next week the next Monday 08:00. */
class SnoozeTimesTest {
    private val berlin = TimeZone.of("Europe/Berlin")
    private val newYork = TimeZone.of("America/New_York")

    private fun until(
        preset: SnoozePreset,
        now: String,
        zone: TimeZone,
    ) = SnoozeTimes.resolve(preset, Instant.parse(now), zone)

    @Test
    fun `one hour is exactly an hour later in any zone`() {
        until(SnoozePreset.OneHour, "2026-11-04T12:00:00Z", berlin) shouldBe Instant.parse("2026-11-04T13:00:00Z")
        until(SnoozePreset.OneHour, "2026-11-04T12:00:00Z", newYork) shouldBe Instant.parse("2026-11-04T13:00:00Z")
    }

    @Test
    fun `tonight is 20 00 local today`() {
        // 12:00Z is 13:00 in Berlin (CET, UTC+1): tonight is 20:00 CET = 19:00Z
        until(SnoozePreset.Tonight, "2026-11-04T12:00:00Z", berlin) shouldBe Instant.parse("2026-11-04T19:00:00Z")
        // 12:00Z is 07:00 in New York (EST, UTC-5): tonight is 20:00 EST = 01:00Z next day
        until(SnoozePreset.Tonight, "2026-11-04T12:00:00Z", newYork) shouldBe Instant.parse("2026-11-05T01:00:00Z")
    }

    @Test
    fun `tonight after the cut-off is not in the future so the preset is unavailable`() {
        // 20:30 in Berlin
        SnoozeTimes.isAvailable(SnoozePreset.Tonight, Instant.parse("2026-11-04T19:30:00Z"), berlin) shouldBe false
        SnoozeTimes.isAvailable(SnoozePreset.Tonight, Instant.parse("2026-11-04T18:59:00Z"), berlin) shouldBe true
        SnoozeTimes.isAvailable(SnoozePreset.OneHour, Instant.parse("2026-11-04T19:30:00Z"), berlin) shouldBe true
    }

    @Test
    fun `tomorrow is 08 00 local the next day`() {
        until(SnoozePreset.Tomorrow, "2026-11-04T12:00:00Z", berlin) shouldBe Instant.parse("2026-11-05T07:00:00Z")
        // late evening in Berlin is still "today" locally: 23:30 CET Nov 4 -> Nov 5 08:00 CET
        until(SnoozePreset.Tomorrow, "2026-11-04T22:30:00Z", berlin) shouldBe Instant.parse("2026-11-05T07:00:00Z")
    }

    @Test
    fun `next week is the next Monday at 08 00 local and a Monday means seven days ahead`() {
        // Wed 2026-11-04 -> Mon 2026-11-09 08:00 CET = 07:00Z
        until(SnoozePreset.NextWeek, "2026-11-04T12:00:00Z", berlin) shouldBe Instant.parse("2026-11-09T07:00:00Z")
        // Mon 2026-11-02 -> Mon 2026-11-09
        until(SnoozePreset.NextWeek, "2026-11-02T12:00:00Z", berlin) shouldBe Instant.parse("2026-11-09T07:00:00Z")
        // Sun 2026-11-01 -> Mon 2026-11-02
        until(SnoozePreset.NextWeek, "2026-11-01T12:00:00Z", berlin) shouldBe Instant.parse("2026-11-02T07:00:00Z")
    }

    @Test
    fun `daylight saving changes are handled by the zone`() {
        // Berlin leaves DST on 2026-10-25: Sat 2026-10-24 -> Mon 2026-10-26 08:00 CET = 07:00Z (not 06:00Z)
        until(SnoozePreset.NextWeek, "2026-10-24T12:00:00Z", berlin) shouldBe Instant.parse("2026-10-26T07:00:00Z")
    }
}
