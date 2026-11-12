package app.patro.bs

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Devanagari / Nepali formatting helpers. */
object Np {
    val MONTHS = listOf(
        "बैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज",
        "कात्तिक", "मंसिर", "पुस", "माघ", "फागुन", "चैत",
    )

    val MONTHS_EN = listOf(
        "Baisakh", "Jestha", "Asar", "Shrawan", "Bhadau", "Asoj",
        "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chait",
    )

    /** Sunday first, as on a paper patro. */
    val WEEKDAYS = listOf("आइतबार", "सोमबार", "मंगलबार", "बुधबार", "बिहीबार", "शुक्रबार", "शनिबार")
    val WEEKDAYS_SHORT = listOf("आइत", "सोम", "मंगल", "बुध", "बिही", "शुक्र", "शनि")
    val WEEKDAYS_EN_SHORT = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    private const val ZERO = '०' // ०

    /** Converts ASCII digits in [s] to Devanagari digits. Other characters are kept. */
    fun digits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') ZERO + (c - '0') else c)
    }

    fun digits(n: Int): String = digits(n.toString())

    /** Converts Devanagari digits back to ASCII. */
    fun ascii(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in ZERO..(ZERO + 9)) '0' + (c - ZERO) else c)
    }

    fun month(month: Int): String = MONTHS[month - 1]
    fun monthEn(month: Int): String = MONTHS_EN[month - 1]

    /** 0 = Sunday … 6 = Saturday. */
    fun sundayIndex(dow: DayOfWeek): Int = dow.value % 7

    fun weekday(date: LocalDate): String = WEEKDAYS[sundayIndex(date.dayOfWeek)]

    /** `कात्तिक २०८३` */
    fun monthYear(year: Int, month: Int): String = "${month(month)} ${digits(year)}"

    /** `२६ कात्तिक २०८३` */
    fun full(bs: BsDate): String = "${digits(bs.day)} ${month(bs.month)} ${digits(bs.year)}"

    /** `कात्तिक २०८३ · बिहीबार` */
    fun subtitle(bs: BsDate, ad: LocalDate): String = "${monthYear(bs.year, bs.month)} · ${weekday(ad)}"

    /** `26 Kartik 2083` */
    fun fullEn(bs: BsDate): String = "${bs.day} ${monthEn(bs.month)} ${bs.year}"

    /** `Thu, 12 Nov 2026` — always English, independent of device locale. */
    fun ad(date: LocalDate): String {
        val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        val mon = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return "$dow, ${date.dayOfMonth} $mon ${date.year}"
    }

    /** `12 Nov` */
    fun adShort(date: LocalDate): String =
        "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"

    fun adMonth(date: LocalDate): String = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** "आज", "भोलि", "३ दिन बाँकी" */
    fun daysLeft(days: Long): String = when {
        days == 0L -> "आज"
        days == 1L -> "भोलि"
        else -> "${digits(days.toInt())} दिन बाँकी"
    }
}
