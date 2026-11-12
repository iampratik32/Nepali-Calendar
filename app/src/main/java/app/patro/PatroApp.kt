package app.patro

import android.app.Application
import app.patro.holidays.HolidayStore

class PatroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Small (a few KB of JSON); loads synced calendar data before anything converts dates.
        HolidayStore.ensureLoaded(this)
    }
}
