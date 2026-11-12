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

    fun holidayDir(ctx: Context) = File(ctx.filesDir, "holidays").apply { mkdirs() }
    fun calendarFile(ctx: Context) = File(ctx.filesDir, "calendar.json")

    fun localVersion(year: Int): Int = files[year]?.version ?: -1
    fun file(year: Int): HolidayFile? = files[year]
    fun years(): Set<Int> = files.keys

    /** Holidays on [date] that pass the display rule. */
    fun on(date: LocalDate): List<Holiday> {
        if (!Bs.inRange(date)) return emptyList()
        val bs = Bs.fromAd(date)
        return files[bs.year]?.holidays?.filter { it.ad == date && it.isShown }.orEmpty()
    }

    /** Shown holidays in a BS month, keyed by BS day. */
    fun inMonth(year: Int, month: Int): Map<Int, List<Holiday>> =
        files[year]?.holidays.orEmpty()
            .filter { it.bs.month == month && it.isShown }
            .groupBy { it.bs.day }

    /** Next shown holiday on or after [from], within about a year. */
    fun next(from: LocalDate): Holiday? {
        if (!Bs.inRange(from)) return null
        val y = Bs.fromAd(from).year
        return (y..y + 1).asSequence()
            .mapNotNull { files[it] }
            .flatMap { it.holidays.asSequence() }
            .filter { it.isShown && !it.ad.isBefore(from) }
            .minByOrNull { it.ad }
    }
}
