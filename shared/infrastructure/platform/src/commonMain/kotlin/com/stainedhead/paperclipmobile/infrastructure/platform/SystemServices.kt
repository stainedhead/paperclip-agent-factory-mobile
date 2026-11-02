package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The device clock. It is the only place the app reads the wall clock (AD-032). */
class SystemClock : Clock {
    override fun now(): Instant =
        kotlin.time.Clock.System
            .now()
}

/** Random (version 4) UUIDs, lower case, for ids the app creates such as a factory id (AD-032). */
class UuidIdGenerator : IdGenerator {
    @OptIn(ExperimentalUuidApi::class)
    override fun generate(): String = Uuid.random().toString()
}

/** The zone the person's phone is set to right now; it is read on every call so a zone change is picked up. */
class SystemDeviceTimeZone : DeviceTimeZone {
    override fun current(): TimeZone = TimeZone.currentSystemDefault()
}
