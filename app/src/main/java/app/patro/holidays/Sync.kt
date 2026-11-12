package app.patro.holidays

import android.content.Context
import app.patro.BuildConfig
import app.patro.bs.Bs
import app.patro.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manual Sync: fetches `index.json`, then any calendar/holiday file whose upstream version is
 * newer than ours. Each file is validated before it replaces the old one (temp file + rename),
 * so a bad download never leaves half-written or invalid data behind.
 */
object Sync {
    data class Result(val ok: Boolean, val summary: String)

    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 1 shl 20 // 1 MiB is far more than any file needs

    suspend fun run(context: Context, baseUrl: String = BuildConfig.DATA_URL): Result =
        withContext(Dispatchers.IO) {
            val ctx = context.applicationContext
            val prefs = Prefs(ctx)
            HolidayStore.ensureLoaded(ctx)
            try {
                val index = DataIndex.parse(fetch(baseUrl + "index.json"))
                val changes = ArrayList<String>()

                // 1. Calendar first, so holiday files are checked against the newest month lengths.
                if (index.calendarVersion > prefs.calendarVersion) {
                    val text = fetch(baseUrl + "calendar.json")
                    val table = CalendarData.parse(text)
                    writeAtomic(HolidayStore.calendarFile(ctx), text)
                    Bs.install(table)
                    prefs.calendarVersion = index.calendarVersion
                    changes += "Calendar data updated (v${index.calendarVersion})"
                }

                // 2. Holiday files.
                for ((year, remoteVersion) in index.holidays.toSortedMap()) {
                    val before = HolidayStore.file(year)
                    if (remoteVersion <= (before?.version ?: -1)) continue
                    val text = fetch(baseUrl + "holidays/$year.json")
                    val parsed = try {
                        HolidayFile.parse(text)
                    } catch (e: DataFormatException) {
                        throw DataFormatException("$year.json: ${e.message}")
                    }
                    if (parsed.bsYear != year) throw DataFormatException("$year.json says bsYear ${parsed.bsYear}")
                    writeAtomic(File(HolidayStore.holidayDir(ctx), "$year.json"), text)
                    changes += describe(year, before, parsed)
                }

                HolidayStore.reload(ctx)
                val summary = if (changes.isEmpty()) "Already up to date" else changes.joinToString("\n")
                prefs.lastSync = System.currentTimeMillis()
                prefs.lastSyncSummary = summary
                Result(true, summary)
            } catch (e: DataFormatException) {
                // Data already on disk is untouched for the file that failed. Reload in case an
                // earlier file in this run was written successfully.
                HolidayStore.reload(ctx)
                Result(false, "Rejected: ${e.message}. Kept existing data.")
            } catch (e: IOException) {
                Result(false, "Couldn't reach the data server (${e.javaClass.simpleName}: ${e.message ?: "no details"})")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                HolidayStore.reload(ctx)
                Result(false, "Sync failed: ${e.message ?: e.javaClass.simpleName}. Kept existing data.")
            }
        }

    private fun describe(year: Int, before: HolidayFile?, after: HolidayFile): String {
        if (before == null) return "$year: ${after.holidays.size} holidays"
        val old = before.holidays.map { it.bs to it.en }.toSet()
        val new = after.holidays.map { it.bs to it.en }.toSet()
        val added = (new - old).size
        val removed = (old - new).size
        val parts = buildList {
            if (added > 0) add("$added added")
            if (removed > 0) add("$removed removed")
            if (isEmpty()) add("details updated")
        }
        return "$year: ${parts.joinToString(", ")}"
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.useCaches = false
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Accept", "application/json, text/plain")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for ${url.substringAfterLast('/')}")
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("File too large")
                }
                return out.toString(Charsets.UTF_8.name())
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Couldn't save ${target.name}")
        }
    }
}
