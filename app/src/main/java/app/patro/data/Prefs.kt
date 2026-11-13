package app.patro.data

import android.content.Context
import android.content.SharedPreferences

/** All app settings. Plain SharedPreferences, no DataStore. */
class Prefs(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("patro", Context.MODE_PRIVATE)

    var lastSync: Long
        get() = p.getLong("last_sync", 0L)
        set(v) = p.edit().putLong("last_sync", v).apply()

    var lastSyncSummary: String
        get() = p.getString("last_sync_summary", "") ?: ""
        set(v) = p.edit().putString("last_sync_summary", v).apply()

    /** Version of `filesDir/calendar.json`; 0 = using the bundled table. */
    var calendarVersion: Int
        get() = p.getInt("calendar_version", 0)
        set(v) = p.edit().putInt("calendar_version", v).apply()

    var adFirst: Boolean
        get() = p.getBoolean("ad_first", false)
        set(v) = p.edit().putBoolean("ad_first", v).apply()

    /** 0 = follow system, 1 = dark, 2 = light. */
    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) = p.edit().putInt("theme", v).apply()

    /** Calendar IDs the user chose to show. `null` = not chosen yet (show all visible ones). */
    var calendarIds: Set<Long>?
        get() = p.getStringSet("calendar_ids", null)?.mapNotNull { it.toLongOrNull() }?.toSet()
        set(v) {
            if (v == null) p.edit().remove("calendar_ids").apply()
            else p.edit().putStringSet("calendar_ids", v.map { it.toString() }.toSet()).apply()
        }

    /** Calendars treated as holiday sources. `null` = auto-detect Google holiday calendars. */
    var holidayCalendarIds: Set<Long>?
        get() = p.getStringSet("holiday_calendar_ids", null)?.mapNotNull { it.toLongOrNull() }?.toSet()
        set(v) {
            if (v == null) p.edit().remove("holiday_calendar_ids").apply()
            else p.edit().putStringSet("holiday_calendar_ids", v.map { it.toString() }.toSet()).apply()
        }

    /** Calendar ID last used in the event editor. */
    var lastEditCalendar: Long
        get() = p.getLong("last_edit_calendar", -1L)
        set(v) = p.edit().putLong("last_edit_calendar", v).apply()

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p.unregisterOnSharedPreferenceChangeListener(l)
}
