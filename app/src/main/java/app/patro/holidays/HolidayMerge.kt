package app.patro.holidays

import app.patro.bs.Bs
import java.time.LocalDate
import java.util.Locale

/**
 * Pure helpers for holidays that come from a Google holiday calendar (e.g. "Holidays in Nepal")
 * synced on the phone, and for merging them with Patro's own data files.
 */
object HolidayMerge {

    /**
     * Turns a holiday-calendar event into a [Holiday].
     *
     * Google marks some entries as observances rather than days off. The exact wording of the event
     * description isn't documented, so this is a best guess: "observance" anywhere → festival
     * (red dot), everything else → day off (red date).
     */
    fun fromCalendarEvent(title: String, description: String?, date: LocalDate): Holiday? {
        if (!Bs.inRange(date)) return null
        val name = title.trim().ifEmpty { return null }
        val desc = description.orEmpty().lowercase(Locale.ROOT)
        val kind = if ("observance" in desc && "public holiday" !in desc) Holiday.KIND_FESTIVAL else Holiday.KIND_PUBLIC
        return Holiday(
            bs = Bs.fromAd(date),
            ad = date,
            np = "",
            en = name,
            kind = kind,
            scope = Holiday.SCOPE_NATIONAL,
            major = true,
            fromGoogle = true,
        )
    }

    /**
     * Holidays for one date: Patro's own entries first, then Google's entries that aren't
     * already covered by one of ours (same date, similar English name).
     */
    fun merge(own: List<Holiday>, google: List<Holiday>): List<Holiday> {
        if (google.isEmpty()) return own
        if (own.isEmpty()) return google.distinctBy { norm(it.en) }
        val ownNames = own.map { norm(it.en) }
        val extra = google.filter { g ->
            val n = norm(g.en)
            ownNames.none { o -> o.isNotEmpty() && n.isNotEmpty() && (o in n || n in o) }
        }.distinctBy { norm(it.en) }
        return own + extra
    }

    /** Lower-case letters only, so "Bhai Tika" and "Bhai-tika" compare equal. */
    fun norm(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetter() }

    /** Heuristic for spotting Google's public holiday calendars among the phone's calendars. */
    fun looksLikeHolidayCalendar(ownerAccount: String?, name: String?): Boolean {
        val owner = ownerAccount.orEmpty().lowercase(Locale.ROOT)
        val n = name.orEmpty().lowercase(Locale.ROOT)
        return "#holiday@group.v.calendar.google.com" in owner || "holiday" in n || "बिदा" in n
    }
}
