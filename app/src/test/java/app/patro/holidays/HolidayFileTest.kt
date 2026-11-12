package app.patro.holidays

import app.patro.bs.BsDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class HolidayFileTest {
    private fun file(vararg entries: String, year: Int = 2083) = """
        {"bsYear": $year, "version": 3, "source": "test", "updated": "2026-11-12",
         "holidays": [${entries.joinToString(",")}]}
    """.trimIndent()

    private fun entry(
        bs: String = "2083-07-25", ad: String = "2026-11-11",
        kind: String = "public", scope: String = "national", major: Boolean = true,
    ) = """{"bs":"$bs","ad":"$ad","np":"भाइटीका","en":"Bhai Tika","kind":"$kind","scope":"$scope","major":$major}"""

    private fun rejects(text: String, contains: String) {
        try {
            HolidayFile.parse(text)
            fail("expected rejection containing '$contains'")
        } catch (e: DataFormatException) {
            assertTrue("message was: ${e.message}", e.message!!.contains(contains))
        }
    }

    @Test fun parsesValidFile() {
        val f = HolidayFile.parse(file(entry()))
        assertEquals(2083, f.bsYear)
        assertEquals(3, f.version)
        val h = f.holidays.single()
        assertEquals(BsDate(2083, 7, 25), h.bs)
        assertEquals(LocalDate.of(2026, 11, 11), h.ad)
        assertTrue(h.isDayOff)
        assertTrue(h.isShown)
    }

    @Test fun rejectsBsAdMismatch() = rejects(file(entry(ad = "2026-11-12")), "is 2026-11-11")
    @Test fun rejectsNonexistentBsDay() = rejects(file(entry(bs = "2083-07-31", ad = "2026-11-17")), "does not exist")
    @Test fun rejectsWrongYear() = rejects(file(entry(), year = 2084), "not in 2084")
    @Test fun rejectsUnknownScope() = rejects(file(entry(scope = "galaxy")), "unknown scope")
    @Test fun rejectsUnknownKind() = rejects(file(entry(kind = "holiday")), "unknown kind")
    @Test fun rejectsBrokenJson() = rejects("{\"bsYear\": 2083, \"holidays\": [", "Not valid")
    @Test fun rejectsTruncatedFile() = rejects(file(entry()).dropLast(5), "Not valid")
    @Test fun rejectsMissingField() = rejects(file("""{"bs":"2083-07-25","ad":"2026-11-11"}"""), "Not valid")

    @Test fun displayRule() {
        val f = HolidayFile.parse(
            file(
                entry(scope = "national", major = false),
                entry(bs = "2083-07-26", ad = "2026-11-12", scope = "community", major = false),
                entry(bs = "2083-07-27", ad = "2026-11-13", scope = "women", major = true, kind = "festival"),
            ),
        )
        assertEquals(listOf(true, false, true), f.holidays.map { it.isShown })
        assertFalse(f.holidays[2].isDayOff)
    }

    @Test fun indexParses() {
        val i = DataIndex.parse("""{"calendar": {"version": 3}, "holidays": {"2083": 5, "2084": 1}}""")
        assertEquals(3, i.calendarVersion)
        assertEquals(mapOf(2083 to 5, 2084 to 1), i.holidays)
    }

    @Test fun calendarRejectsBadMonthLength() {
        try {
            CalendarData.parse("""{"2000": [30,32,31,32,31,30,30,30,29,30,29,40]}""")
            fail()
        } catch (e: DataFormatException) {
            assertTrue(e.message!!.contains("29..32"))
        }
    }

    @Test fun calendarRejectsShorterRange() {
        try {
            CalendarData.parse("""{"2000": [30,32,31,32,31,30,30,30,29,30,29,31]}""")
            fail()
        } catch (e: DataFormatException) {
            assertTrue(e.message, e.message!!.contains("ends at"))
        }
    }
}
