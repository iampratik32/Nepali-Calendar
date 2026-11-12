package app.patro.events

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarRepoTest {
    private val ktm = ZoneId.of("Asia/Kathmandu")

    private fun item(begin: Long, end: Long, allDay: Boolean) =
        EventItem(1, "t", allDay, begin, end, 1, 0, false)

    private fun utcMidnight(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun allDaySingleDayUsesUtcDate() {
        val d = LocalDate.of(2026, 11, 12)
        val e = item(utcMidnight(d), utcMidnight(d.plusDays(1)), allDay = true)
        // Must not leak into the 13th even though UTC midnight is 05:45 in Kathmandu.
        assertEquals(listOf(d), CalendarRepo.datesOf(e, ktm))
    }

    @Test fun allDayMultiDay() {
        val d = LocalDate.of(2026, 11, 12)
        val e = item(utcMidnight(d), utcMidnight(d.plusDays(3)), allDay = true)
        assertEquals(listOf(d, d.plusDays(1), d.plusDays(2)), CalendarRepo.datesOf(e, ktm))
    }

    @Test fun timedEventEndingAtMidnightStaysOnOneDay() {
        val d = LocalDate.of(2026, 11, 12)
        val start = d.atTime(22, 0).atZone(ktm).toInstant().toEpochMilli()
        val end = d.plusDays(1).atStartOfDay(ktm).toInstant().toEpochMilli()
        assertEquals(listOf(d), CalendarRepo.datesOf(item(start, end, false), ktm))
    }

    @Test fun timedEventOverMidnightSpansTwoDays() {
        val d = LocalDate.of(2026, 11, 12)
        val start = d.atTime(23, 0).atZone(ktm).toInstant().toEpochMilli()
        val end = d.plusDays(1).atTime(1, 0).atZone(ktm).toInstant().toEpochMilli()
        assertEquals(listOf(d, d.plusDays(1)), CalendarRepo.datesOf(item(start, end, false), ktm))
    }

    @Test fun durations() {
        assertEquals(86_400_000L, CalendarRepo.durationMs("P1D"))
        assertEquals(3_600_000L, CalendarRepo.durationMs("P3600S"))
        assertEquals(5_400_000L, CalendarRepo.durationMs("PT1H30M"))
        assertEquals(7 * 86_400_000L, CalendarRepo.durationMs("P1W"))
        assertEquals(0L, CalendarRepo.durationMs(null))
        assertEquals(0L, CalendarRepo.durationMs("garbage"))
    }
}
