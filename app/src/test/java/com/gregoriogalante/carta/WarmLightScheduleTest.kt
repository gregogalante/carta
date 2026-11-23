package com.gregoriogalante.carta

import com.gregoriogalante.carta.WarmLightSchedule.DAY_KELVIN
import com.gregoriogalante.carta.WarmLightSchedule.NIGHT_KELVIN
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WarmLightScheduleTest {

    private fun kelvinAt(hour: Int, minute: Int = 0) = WarmLightSchedule.kelvinAt(LocalTime.of(hour, minute))

    @Test
    fun `is neutral by day and warm at night`() {
        assertEquals(DAY_KELVIN, kelvinAt(12))
        assertEquals(DAY_KELVIN, kelvinAt(8))
        assertEquals(DAY_KELVIN, kelvinAt(19))
        assertEquals(NIGHT_KELVIN, kelvinAt(21))
        assertEquals(NIGHT_KELVIN, kelvinAt(23, 59))
        assertEquals(NIGHT_KELVIN, kelvinAt(3))
        assertEquals(NIGHT_KELVIN, kelvinAt(6))
    }

    @Test
    fun `ramps halfway through evening and morning`() {
        assertEquals(3500, kelvinAt(20))
        assertEquals(3500, kelvinAt(7))
    }

    @Test
    fun `next change is the first quarter hour of the evening ramp`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 23, 19, 15),
            WarmLightSchedule.nextChange(LocalDateTime.of(2026, 11, 23, 12, 7, 30)),
        )
    }

    @Test
    fun `next change after the evening is in the morning`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 24, 6, 15),
            WarmLightSchedule.nextChange(LocalDateTime.of(2026, 11, 23, 22, 0)),
        )
    }
}
