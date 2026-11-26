package com.mercan.maliyem.di

import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockModuleTest {
    @Test fun `an existing clock follows changes to the system time zone`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))
            val clock = ClockModule.provideClock()
            val fixedZoneClock = clock.withZone(ZoneId.of("UTC"))
            assertEquals(ZoneId.of("Europe/Istanbul"), clock.zone)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(ZoneId.of("America/Los_Angeles"), clock.zone)
            assertEquals(ZoneId.of("UTC"), fixedZoneClock.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
