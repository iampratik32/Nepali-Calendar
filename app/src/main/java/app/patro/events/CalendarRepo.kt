package app.patro.events

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import app.patro.data.Prefs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val writable: Boolean,
)

data class EventItem(
    val eventId: Long,
    val title: String,
    val allDay: Boolean,
    val begin: Long,
    val end: Long,
    val calendarId: Long,
    val color: Int,
    val recurring: Boolean,
)

enum class Repeat(val label: String, val rrule: String?) {
    NONE("Doesn't repeat", null),
    DAILY("Daily", "FREQ=DAILY"),
    WEEKLY("Weekly", "FREQ=WEEKLY"),
    MONTHLY("Monthly", "FREQ=MONTHLY"),
    YEARLY("Yearly", "FREQ=YEARLY"),
    CUSTOM("Custom (kept as is)", null),
}

/** What the editor works on. [customRrule] keeps a rule we can't edit so saving doesn't destroy it. */
data class EventDraft(
    val id: Long? = null,
    val title: String = "",
    val allDay: Boolean = false,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val calendarId: Long = -1,
    /** Minutes before; null = no reminder. */
    val reminder: Int? = null,
    val repeat: Repeat = Repeat.NONE,
    val customRrule: String? = null,
)

/**
 * Google Calendar access through Android's Calendar Provider. The phone's own sync adapter moves
 * events to and from Google; this app never talks to Google directly.
 */
object CalendarRepo {
    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun canRead(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canWrite(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(ctx: Context): List<CalendarInfo> {
        if (!canRead(ctx)) return emptyList()
        val out = ArrayList<CalendarInfo>()
        ctx.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(
                Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
                Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.VISIBLE,
            ),
            null, null, "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}",
        )?.use { c ->
            while (c.moveToNext()) {
                out += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Calendar",
                    account = c.getString(2) ?: "",
                    color = c.getInt(3),
                    writable = c.getInt(4) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }
        return out
    }

    /** Calendar IDs to show: the user's choice, or every calendar if they never chose. */
    fun selectedIds(ctx: Context): Set<Long>? = Prefs(ctx).calendarIds

    /**
     * Event instances overlapping [from, toExclusive), grouped by the local dates they cover.
     * Recurring events come out expanded (that's what the Instances table is for).
     */
    fun instances(
        ctx: Context,
        from: LocalDate,
        toExclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, List<EventItem>> {
        if (!canRead(ctx)) return emptyMap()
        val ids = selectedIds(ctx)
        if (ids != null && ids.isEmpty()) return emptyMap()
        // Widen by a day each side: all-day events are stored in UTC.
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = toExclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val selection = buildString {
            append("${Instances.VISIBLE} = 1")
            if (ids != null) append(" AND ${Instances.CALENDAR_ID} IN (${ids.joinToString(",")})")
        }
        val result = HashMap<LocalDate, MutableList<EventItem>>()
        ctx.contentResolver.query(
            uri,
            arrayOf(
                Instances.EVENT_ID, Instances.TITLE, Instances.ALL_DAY, Instances.BEGIN, Instances.END,
                Instances.CALENDAR_ID, Instances.DISPLAY_COLOR, Instances.RRULE,
            ),
            selection, null, "${Instances.ALL_DAY} DESC, ${Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val item = EventItem(
                    eventId = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                    allDay = c.getInt(2) == 1,
                    begin = c.getLong(3),
                    end = c.getLong(4),
                    calendarId = c.getLong(5),
                    color = c.getInt(6),
                    recurring = !c.getString(7).isNullOrEmpty(),
                )
                for (d in datesOf(item, zone)) {
                    if (!d.isBefore(from) && d.isBefore(toExclusive)) result.getOrPut(d) { ArrayList() } += item
                }
            }
        }
        return result
    }

    /** Local dates an instance covers. All-day events are UTC-midnight based; end is exclusive. */
    fun datesOf(e: EventItem, zone: ZoneId): List<LocalDate> {
        val first: LocalDate
        val lastInclusive: LocalDate
        if (e.allDay) {
            first = Instant.ofEpochMilli(e.begin).atOffset(ZoneOffset.UTC).toLocalDate()
            val endEx = Instant.ofEpochMilli(e.end).atOffset(ZoneOffset.UTC).toLocalDate()
            lastInclusive = if (endEx.isAfter(first)) endEx.minusDays(1) else first
        } else {
            first = Instant.ofEpochMilli(e.begin).atZone(zone).toLocalDate()
            val endMs = if (e.end > e.begin) e.end - 1 else e.begin
            lastInclusive = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()
        }
        val out = ArrayList<LocalDate>()
        var d = first
        while (!d.isAfter(lastInclusive) && out.size < 366) {
            out += d
            d = d.plusDays(1)
        }
        return out
    }

    fun load(ctx: Context, eventId: Long, zone: ZoneId = ZoneId.systemDefault()): EventDraft? {
        if (!canRead(ctx)) return null
        var draft: EventDraft? = null
        ctx.contentResolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            arrayOf(Events.TITLE, Events.DTSTART, Events.DTEND, Events.DURATION, Events.ALL_DAY, Events.CALENDAR_ID, Events.RRULE),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val allDay = c.getInt(4) == 1
            val startMs = c.getLong(1)
            val endMs = if (!c.isNull(2) && c.getLong(2) > 0) c.getLong(2) else startMs + durationMs(c.getString(3))
            val z = if (allDay) ZoneOffset.UTC else zone
            val rrule = c.getString(6)
            val repeat = when {
                rrule.isNullOrEmpty() -> Repeat.NONE
                else -> Repeat.entries.firstOrNull { it.rrule == rrule } ?: Repeat.CUSTOM
            }
            draft = EventDraft(
                id = eventId,
                title = c.getString(0) ?: "",
                allDay = allDay,
                start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), z),
                end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMs), z),
                calendarId = c.getLong(5),
                repeat = repeat,
                customRrule = if (repeat == Repeat.CUSTOM) rrule else null,
            )
        }
        val d = draft ?: return null
        var reminder: Int? = null
        ctx.contentResolver.query(
            Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES),
            "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), "${Reminders.MINUTES} ASC",
        )?.use { c -> if (c.moveToFirst()) reminder = c.getInt(0) }
        return d.copy(reminder = reminder)
    }

    /** Inserts or updates. Returns the event ID. Throws on failure. */
    fun save(ctx: Context, d: EventDraft, zone: ZoneId = ZoneId.systemDefault()): Long {
        val v = ContentValues()
        val startMs: Long
        val endMs: Long
        if (d.allDay) {
            startMs = d.start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val endDate = maxOf(d.end.toLocalDate(), d.start.toLocalDate())
            endMs = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            v.put(Events.EVENT_TIMEZONE, "UTC")
        } else {
            startMs = d.start.atZone(zone).toInstant().toEpochMilli()
            endMs = maxOf(d.end.atZone(zone).toInstant().toEpochMilli(), startMs)
            v.put(Events.EVENT_TIMEZONE, zone.id)
        }
        v.put(Events.TITLE, d.title.trim())
        v.put(Events.CALENDAR_ID, d.calendarId)
        v.put(Events.ALL_DAY, if (d.allDay) 1 else 0)
        v.put(Events.DTSTART, startMs)
        v.put(Events.HAS_ALARM, if (d.reminder != null) 1 else 0)
        val rrule = if (d.repeat == Repeat.CUSTOM) d.customRrule else d.repeat.rrule
        if (rrule != null) {
            // Recurring events must use DURATION instead of DTEND.
            v.put(Events.RRULE, rrule)
            v.putNull(Events.DTEND)
            val secs = (endMs - startMs) / 1000
            v.put(Events.DURATION, if (d.allDay) "P${maxOf(1, secs / 86400)}D" else "P${secs}S")
        } else {
            v.putNull(Events.RRULE)
            v.putNull(Events.DURATION)
            v.put(Events.DTEND, endMs)
        }
        val cr = ctx.contentResolver
        val id = if (d.id == null) {
            val uri = cr.insert(Events.CONTENT_URI, v) ?: error("Calendar refused the event")
            ContentUris.parseId(uri)
        } else {
            val n = cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, d.id), v, null, null)
            if (n == 0) error("Event no longer exists")
            cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(d.id.toString()))
            d.id
        }
        if (d.reminder != null) {
            cr.insert(
                Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(Reminders.EVENT_ID, id)
                    put(Reminders.MINUTES, d.reminder)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                },
            )
        }
        Prefs(ctx).lastEditCalendar = d.calendarId
        return id
    }

    fun delete(ctx: Context, eventId: Long) {
        ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    /** Parses RFC 5545 durations as Android stores them: P1D, P3600S, PT1H, P1W … */
    fun durationMs(s: String?): Long {
        if (s.isNullOrEmpty()) return 0
        val m = Regex("""([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?(?:(\d+)S)?""").matchEntire(s.trim())
            ?: return 0
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        val secs = g(2) * 7 * 86400 + g(3) * 86400 + g(4) * 3600 + g(5) * 60 + g(6) + g(7)
        return secs * 1000
    }
}
