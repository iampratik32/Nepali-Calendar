package app.patro.bs

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** A Bikram Sambat date. [month] is 1..12 (1 = Baisakh). */
data class BsDate(val year: Int, val month: Int, val day: Int) : Comparable<BsDate> {
    override fun compareTo(other: BsDate): Int =
        compareValuesBy(this, other, BsDate::year, BsDate::month, BsDate::day)

    /** ISO-like form used in data files: `2083-07-25`. */
    override fun toString(): String = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)

    companion object {
        /** Parses `yyyy-mm-dd`. Does not check the day against month length. */
        fun parse(s: String): BsDate {
            val p = s.trim().split('-')
            require(p.size == 3) { "Bad BS date: $s" }
            return BsDate(p[0].toInt(), p[1].toInt(), p[2].toInt())
        }
    }
}

/**
 * Month-length table for a contiguous range of BS years.
 * Year [firstYear] month 1 day 1 is pinned to [anchorAd].
 */
class BsTable(
    val firstYear: Int,
    private val months: Array<IntArray>,
    val anchorAd: LocalDate = DEFAULT_ANCHOR,
) {
    val lastYear: Int get() = firstYear + months.size - 1

    /** Epoch day of BS (firstYear + i)-01-01, plus one trailing entry for the end of the range. */
    private val yearStart = LongArray(months.size + 1)

    init {
        require(months.isNotEmpty()) { "Empty table" }
        var acc = anchorAd.toEpochDay()
        for (i in months.indices) {
            val m = months[i]
            require(m.size == 12) { "Year ${firstYear + i}: expected 12 months, got ${m.size}" }
            require(m.all { it in 29..32 }) { "Year ${firstYear + i}: month length out of 29..32" }
            yearStart[i] = acc
            acc += m.sum()
        }
        yearStart[months.size] = acc
    }

    val firstAd: LocalDate get() = LocalDate.ofEpochDay(yearStart[0])
    val lastAd: LocalDate get() = LocalDate.ofEpochDay(yearStart[months.size] - 1)

    fun daysInMonth(year: Int, month: Int): Int {
        checkYear(year)
        require(month in 1..12) { "Month out of range: $month" }
        return months[year - firstYear][month - 1]
    }

    fun daysInYear(year: Int): Int {
        checkYear(year)
        return months[year - firstYear].sum()
    }

    fun toEpochDay(bs: BsDate): Long {
        checkYear(bs.year)
        require(bs.month in 1..12) { "Month out of range: $bs" }
        val m = months[bs.year - firstYear]
        require(bs.day in 1..m[bs.month - 1]) { "Day out of range: $bs" }
        var d = yearStart[bs.year - firstYear]
        for (i in 0 until bs.month - 1) d += m[i]
        return d + bs.day - 1
    }

    fun fromEpochDay(epochDay: Long): BsDate {
        require(epochDay >= yearStart[0] && epochDay < yearStart[months.size]) {
            "Date ${LocalDate.ofEpochDay(epochDay)} outside BS $firstYear–$lastYear"
        }
        // Binary search for the year.
        var lo = 0
        var hi = months.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (yearStart[mid] <= epochDay) lo = mid else hi = mid - 1
        }
        var rem = (epochDay - yearStart[lo]).toInt()
        val m = months[lo]
        var month = 0
        while (rem >= m[month]) {
            rem -= m[month]
            month++
        }
        return BsDate(firstYear + lo, month + 1, rem + 1)
    }

    fun contains(year: Int) = year in firstYear..lastYear

    /** Month lengths, for serialising or comparing. */
    fun monthsOf(year: Int): IntArray = months[year - firstYear].copyOf()

    private fun checkYear(year: Int) =
        require(year in firstYear..lastYear) { "BS year $year outside $firstYear–$lastYear" }

    companion object {
        /** BS 2000-01-01 = AD 1943-04-14 (anchor used by sonill/nepali-dates). */
        val DEFAULT_ANCHOR: LocalDate = LocalDate.of(1943, 4, 14)
        const val ANCHOR_YEAR = 2000

        fun fromPacked(firstYear: Int, packed: String): BsTable {
            require(packed.length % 12 == 0) { "Packed data length must be a multiple of 12" }
            val years = Array(packed.length / 12) { y ->
                IntArray(12) { m -> packed[y * 12 + m] - '0' + 29 }
            }
            return BsTable(firstYear, years)
        }

        /** Builds a table from `{"2000":[...], "2001":[...]}` style data. Years must be contiguous from 2000. */
        fun fromYearMap(map: Map<Int, IntArray>): BsTable {
            require(map.isNotEmpty()) { "No years" }
            val first = map.keys.min()
            val last = map.keys.max()
            require(first == ANCHOR_YEAR) { "Data must start at BS $ANCHOR_YEAR (anchor), starts at $first" }
            val years = Array(last - first + 1) { i ->
                map[first + i] ?: throw IllegalArgumentException("Missing BS year ${first + i}")
            }
            return BsTable(first, years)
        }
    }
}

/** BS ⇄ AD conversion. Uses the bundled table unless [install] replaced it (e.g. after Sync). */
object Bs {
    val KATHMANDU: ZoneId = ZoneId.of("Asia/Kathmandu")

    @Volatile
    var table: BsTable = BsTable.fromPacked(BsData.FIRST_YEAR, BsData.PACKED)
        private set

    val bundled: BsTable by lazy { BsTable.fromPacked(BsData.FIRST_YEAR, BsData.PACKED) }

    fun install(newTable: BsTable) {
        table = newTable
    }

    val minYear: Int get() = table.firstYear
    val maxYear: Int get() = table.lastYear

    fun fromAd(date: LocalDate): BsDate = table.fromEpochDay(date.toEpochDay())
    fun toAd(bs: BsDate): LocalDate = LocalDate.ofEpochDay(table.toEpochDay(bs))
    fun daysInMonth(year: Int, month: Int): Int = table.daysInMonth(year, month)
    fun today(zone: ZoneId = KATHMANDU): BsDate = fromAd(LocalDate.now(zone))

    fun isValid(bs: BsDate): Boolean =
        table.contains(bs.year) && bs.month in 1..12 && bs.day in 1..table.daysInMonth(bs.year, bs.month)

    fun inRange(date: LocalDate): Boolean = !date.isBefore(table.firstAd) && !date.isAfter(table.lastAd)

    /** Month arithmetic on (year, month) pairs. */
    fun addMonths(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val idx = year * 12 + (month - 1) + delta
        return Pair(Math.floorDiv(idx, 12), Math.floorMod(idx, 12) + 1)
    }

    /** First AD day of a BS month. */
    fun monthStart(year: Int, month: Int): LocalDate = toAd(BsDate(year, month, 1))
}
