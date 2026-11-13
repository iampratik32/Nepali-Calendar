package app.patro.holidays

import android.content.Context
import android.util.Log
import app.patro.bs.Bs
import app.patro.data.Prefs
import java.io.File
import java.time.LocalDate

/**
 * Holiday + calendar data on disk.
 *
 * - Calendar month lengths: bundled in code ([app.patro.bs.BsData]); `filesDir/calendar.json` wins if Sync wrote one.
 * - Holidays: bundled seed in `assets/holidays/<year>.json`; `filesDir/holidays/<year>.json` wins if its version is higher.
 */
object HolidayStore {
    private const val TAG = "HolidayStore"

    @Volatile
    private var files: Map<Int, HolidayFile> = emptyMap()

    @Volatile
    private var loaded = false

    /** Entries from the phone's holiday calendars (see [refreshGoogle]), keyed by date. */
    @Volatile
    private var google: Map<LocalDate, List<Holiday>> = emptyMap()

    /** Bumped on every reload so UI can recompose. */
    @Volatile
    var generation: Int = 0
        private set

    fun ensureLoaded(context: Context) {
        if (!loaded) synchronized(this) { if (!loaded) reload(context) }
    }

    @Synchronized
    fun reload(context: Context) {
        val ctx = context.applicationContext
        loadCalendar(ctx)
        val result = HashMap<Int, HolidayFile>()
        // Bundled seeds.
        ctx.assets.list("holidays")?.forEach { name ->
            val year = name.removeSuffix(".json").toIntOrNull() ?: return@forEach
            runCatching { HolidayFile.parse(ctx.assets.open("holidays/$name").bufferedReader().readText()) }
                .onSuccess { if (it.bsYear == year) result[year] = it }
                .onFailure { Log.w(TAG, "Bad bundled $name: ${it.message}") }
        }
        // Synced copies, if newer.
        holidayDir(ctx).listFiles()?.forEach { f ->
            val year = f.name.removeSuffix(".json").toIntOrNull() ?: return@forEach
            if (!f.name.endsWith(".json")) return@forEach
            runCatching { HolidayFile.parse(f.readText()) }
                .onSuccess { hf ->
                    val cur = result[year]
                    if (hf.bsYear == year && (cur == null || hf.version >= cur.version)) result[year] = hf
                }
                .onFailure { Log.w(TAG, "Bad synced ${f.name}: ${it.message}") }
        }
        files = result
        loaded = true
        generation++
    }

    private fun loadCalendar(ctx: Context) {
        val f = calendarFile(ctx)
        if (!f.exists()) return
        runCatching { CalendarData.parse(f.readText()) }
            .onSuccess { Bs.install(it) }
            .onFailure {
                Log.w(TAG, "Ignoring bad calendar.json: ${it.message}")
                Prefs(ctx).calendarVersion = 0
            }
    }

    /**
     * Reloads holidays from the phone's Google holiday calendars for about a year back and two
     * ahead. Does a provider query, so call it off the main thread. Returns true if anything changed.
     */
    fun refreshGoogle(context: Context): Boolean {
        val ctx = context.applicationContext
        val today = LocalDate.now()
        val fresh = runCatching {
            app.patro.events.CalendarRepo.holidayInstances(ctx, today.minusYears(1), today.plusYears(2))
        }.getOrElse { emptyMap() }
        if (fresh == google) return false
        google = fresh
        synchronized(this) { generation++ }
        return true
    }

    fun holidayDir(ctx: Context) = File(ctx.filesDir, "holidays").apply { mkdirs() }
    fun calendarFile(ctx: Context) = File(ctx.filesDir, "calendar.json")

    fun localVersion(year: Int): Int = files[year]?.version ?: -1
    fun file(year: Int): HolidayFile? = files[year]
    fun years(): Set<Int> = files.keys

    /** Holidays on [date] that pass the display rule: Patro's data plus the phone's holiday calendars. */
    fun on(date: LocalDate): List<Holiday> {
        if (!Bs.inRange(date)) return emptyList()
        val bs = Bs.fromAd(date)
        val own = files[bs.year]?.holidays?.filter { it.ad == date && it.isShown }.orEmpty()
        return HolidayMerge.merge(own, google[date].orEmpty())
    }

    /** Next shown holiday on or after [from], within about a year. */
    fun next(from: LocalDate): Holiday? {
        if (!Bs.inRange(from)) return null
        val y = Bs.fromAd(from).year
        val own = (y..y + 1).asSequence()
            .mapNotNull { files[it] }
            .flatMap { it.holidays.asSequence() }
            .filter { it.isShown && !it.ad.isBefore(from) }
            .minByOrNull { it.ad }
        val g = google.entries.asSequence()
            .filter { !it.key.isBefore(from) && it.value.isNotEmpty() }
            .minByOrNull { it.key }
        return when {
            g == null -> own
            own == null || g.key.isBefore(own.ad) -> on(g.key).firstOrNull() ?: g.value.first()
            else -> own
        }
    }
}
