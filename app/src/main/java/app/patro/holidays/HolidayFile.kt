package app.patro.holidays

import app.patro.bs.Bs
import app.patro.bs.BsDate
import app.patro.bs.BsTable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

class DataFormatException(message: String) : Exception(message)

data class Holiday(
    val bs: BsDate,
    val ad: LocalDate,
    val np: String,
    val en: String,
    /** `public` (day off) or `festival` (big festival, not a day off). */
    val kind: String,
    /** `national`, `women`, `valley` or `community`. */
    val scope: String,
    val major: Boolean,
    /** True when it came from a Google holiday calendar on the phone rather than Patro's data files. */
    val fromGoogle: Boolean = false,
) {
    val isDayOff: Boolean get() = kind == KIND_PUBLIC

    /** Main label: the Nepali name when we have one. */
    val title: String get() = np.ifBlank { en }

    /** Second label: the English name, if the title is Nepali. */
    val subtitle: String get() = if (np.isNotBlank()) en else ""

    /** Default display rule: nationwide, or a big festival worth showing anyway. */
    val isShown: Boolean get() = scope == SCOPE_NATIONAL || major

    companion object {
        const val KIND_PUBLIC = "public"
        const val KIND_FESTIVAL = "festival"
        const val SCOPE_NATIONAL = "national"
        val KINDS = setOf(KIND_PUBLIC, KIND_FESTIVAL)
        val SCOPES = setOf(SCOPE_NATIONAL, "women", "valley", "community")
    }
}

data class HolidayFile(
    val bsYear: Int,
    val version: Int,
    val source: String,
    val updated: String,
    val holidays: List<Holiday>,
) {
    companion object {
        /**
         * Parses and validates a holiday file. Every entry's `bs` and `ad` must agree with the
         * converter, so a typo in either is caught before the file replaces the old one.
         */
        fun parse(text: String): HolidayFile {
            try {
                val o = JSONObject(text)
                val year = o.getInt("bsYear")
                val version = o.getInt("version")
                if (version < 0) throw DataFormatException("version must be ≥ 0")
                val arr = o.getJSONArray("holidays")
                val list = ArrayList<Holiday>(arr.length())
                for (i in 0 until arr.length()) {
                    list += parseHoliday(arr.getJSONObject(i), year, i)
                }
                list.sortBy { it.ad }
                return HolidayFile(year, version, o.optString("source"), o.optString("updated"), list)
            } catch (e: JSONException) {
                throw DataFormatException("Not valid holiday JSON: ${e.message}")
            }
        }

        private fun parseHoliday(h: JSONObject, year: Int, i: Int): Holiday {
            fun fail(msg: String): Nothing = throw DataFormatException("Entry #${i + 1}: $msg")
            val bs = try {
                BsDate.parse(h.getString("bs"))
            } catch (e: NumberFormatException) {
                fail("bad bs date")
            } catch (e: IllegalArgumentException) {
                fail("bad bs date")
            }
            if (bs.year != year) fail("bs $bs is not in $year")
            if (!Bs.isValid(bs)) fail("bs $bs does not exist")
            val ad = try {
                LocalDate.parse(h.getString("ad"))
            } catch (e: DateTimeParseException) {
                fail("bad ad date")
            }
            val expected = Bs.toAd(bs)
            if (expected != ad) fail("bs $bs is $expected, not $ad")
            val np = h.getString("np").trim()
            val en = h.getString("en").trim()
            if (np.isEmpty() && en.isEmpty()) fail("no name")
            val kind = h.getString("kind")
            if (kind !in Holiday.KINDS) fail("unknown kind '$kind'")
            val scope = h.getString("scope")
            if (scope !in Holiday.SCOPES) fail("unknown scope '$scope'")
            return Holiday(bs, ad, np, en, kind, scope, h.optBoolean("major", false))
        }
    }
}

/** `index.json`: which data files exist upstream and their versions. */
data class DataIndex(val calendarVersion: Int, val holidays: Map<Int, Int>) {
    companion object {
        fun parse(text: String): DataIndex {
            try {
                val o = JSONObject(text)
                val cal = o.optJSONObject("calendar")?.optInt("version", 0) ?: 0
                val h = o.optJSONObject("holidays")
                val map = HashMap<Int, Int>()
                if (h != null) for (k in h.keys()) {
                    val y = k.toIntOrNull() ?: throw DataFormatException("Bad year key '$k'")
                    map[y] = h.getInt(k)
                }
                return DataIndex(cal, map)
            } catch (e: JSONException) {
                throw DataFormatException("Not valid index JSON: ${e.message}")
            }
        }
    }
}

/** `calendar.json`: `{"2000":[30,32,...], ...}`. */
object CalendarData {
    fun parse(text: String): BsTable {
        try {
            val o = JSONObject(text)
            val map = HashMap<Int, IntArray>()
            for (k in o.keys()) {
                val y = k.toIntOrNull() ?: continue // ignore "$schema", comments, etc.
                val arr: JSONArray = o.getJSONArray(k)
                map[y] = IntArray(arr.length()) { arr.getInt(it) }
            }
            val table = try {
                BsTable.fromYearMap(map)
            } catch (e: IllegalArgumentException) {
                throw DataFormatException(e.message ?: "Bad calendar data")
            }
            // Must not shrink the range we already support, or saved dates could stop converting.
            val bundled = Bs.bundled
            if (table.lastYear < bundled.lastYear) {
                throw DataFormatException("Calendar data ends at ${table.lastYear}, need ${bundled.lastYear}")
            }
            for (y in table.firstYear..table.lastYear) {
                val days = table.daysInYear(y)
                if (days !in 364..367) throw DataFormatException("BS $y has $days days")
            }
            return table
        } catch (e: JSONException) {
            throw DataFormatException("Not valid calendar JSON: ${e.message}")
        }
    }
}
