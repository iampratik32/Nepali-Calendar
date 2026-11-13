package app.patro.holidays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayMergeTest {
    private val day = LocalDate.of(2026, 11, 11)

    private fun own(en: String, np: String = "भाइटीका") = HolidayFile.parse(
        """{"bsYear":2083,"version":1,"holidays":[{"bs":"2083-07-25","ad":"2026-11-11","np":"$np","en":"$en","kind":"public","scope":"national","major":true}]}""",
    ).holidays.single()

    @Test fun googleEventBecomesDayOff() {
        val h = HolidayMerge.fromCalendarEvent("Bhai Tika", "Public holiday", day)!!
        assertTrue(h.isDayOff)
        assertTrue(h.fromGoogle)
        assertTrue(h.isShown)
        assertEquals("Bhai Tika", h.title)
        assertEquals("", h.subtitle)
    }

    @Test fun noDescriptionCountsAsDayOff() =
        assertTrue(HolidayMerge.fromCalendarEvent("Dashain", null, day)!!.isDayOff)

    @Test fun observanceIsFestival() =
        assertFalse(HolidayMerge.fromCalendarEvent("Some Day", "Observance\nTo hide observances…", day)!!.isDayOff)

    @Test fun blankTitleIgnored() = assertNull(HolidayMerge.fromCalendarEvent("  ", null, day))

    @Test fun ownEntryWinsOverSimilarGoogleName() {
        val g = HolidayMerge.fromCalendarEvent("Tihar (Bhai Tika)", null, day)!!
        val merged = HolidayMerge.merge(listOf(own("Bhai Tika")), listOf(g))
        assertEquals(1, merged.size)
        assertFalse(merged.single().fromGoogle)
    }

    @Test fun differentGoogleHolidayIsAdded() {
        val g = HolidayMerge.fromCalendarEvent("Something Else", null, day)!!
        assertEquals(2, HolidayMerge.merge(listOf(own("Bhai Tika")), listOf(g)).size)
    }

    @Test fun googleDuplicatesCollapse() {
        val a = HolidayMerge.fromCalendarEvent("Dashain", null, day)!!
        assertEquals(1, HolidayMerge.merge(emptyList(), listOf(a, a.copy())).size)
    }

    @Test fun detectsHolidayCalendars() {
        assertTrue(HolidayMerge.looksLikeHolidayCalendar("en.np#holiday@group.v.calendar.google.com", "Holidays in Nepal"))
        assertTrue(HolidayMerge.looksLikeHolidayCalendar(null, "Holidays in Nepal"))
        assertFalse(HolidayMerge.looksLikeHolidayCalendar("me@gmail.com", "Personal"))
    }
}
