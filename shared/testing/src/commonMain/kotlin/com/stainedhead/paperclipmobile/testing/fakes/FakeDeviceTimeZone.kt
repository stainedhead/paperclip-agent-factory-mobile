package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import kotlinx.datetime.TimeZone

class FakeDeviceTimeZone(
    var zone: TimeZone = TimeZone.UTC,
) : DeviceTimeZone {
    override fun current(): TimeZone = zone
}
