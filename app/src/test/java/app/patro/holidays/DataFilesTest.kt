package app.patro.holidays

import app.patro.bs.Bs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the app's own validation over everything in `data/` and the bundled assets. */
class DataFilesTest {
    private val root: File = listOf(File(".."), File(".")).first { File(it, "data/index.json").exists() }

    @Test fun indexMatchesFiles() {
        val index = DataIndex.parse(File(root, "data/index.json").readText())
        val dir = File(root, "data/holidays")
        val onDisk = dir.listFiles()!!.filter { it.name.endsWith(".json") }
            .associate { it.name.removeSuffix(".json").toInt() to HolidayFile.parse(it.readText()) }
        assertEquals("index.json lists exactly the holiday files present", onDisk.keys, index.holidays.keys)
        for ((year, file) in onDisk) {
            assertEquals("$year.json bsYear", year, file.bsYear)
            assertEquals("$year.json version vs index.json", index.holidays[year], file.version)
        }
    }

    @Test fun calendarFileIsValid() {
        val t = CalendarData.parse(File(root, "data/calendar.json").readText())
        assertEquals(2000, t.firstYear)
        assertTrue(t.lastYear >= Bs.bundled.lastYear)
    }

    @Test fun bundledSeedsAreValid() {
        val dir = File(root, "app/src/main/assets/holidays")
        val seeds = dir.listFiles()!!.filter { it.name.endsWith(".json") }
        assertTrue(seeds.isNotEmpty())
        for (f in seeds) {
            val parsed = HolidayFile.parse(f.readText())
            assertEquals(f.name, f.name.removeSuffix(".json").toInt(), parsed.bsYear)
        }
    }
}
