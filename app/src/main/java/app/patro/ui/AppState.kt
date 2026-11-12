package app.patro.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.patro.data.Prefs
import app.patro.events.CalendarRepo
import app.patro.events.EventDraft
import app.patro.holidays.HolidayStore
import app.patro.widget.Widgets
import java.time.LocalDate
import java.time.ZoneId

sealed interface Screen {
    data object Today : Screen
    data object Month : Screen
    data object Settings : Screen
    data class Editor(val draft: EventDraft) : Screen
}

/** App-wide UI state. Lives as long as the activity (config changes are handled in-place). */
class AppState(private val ctx: Context) {
    val prefs = Prefs(ctx)

    val stack = mutableStateListOf<Screen>(Screen.Today)
    val screen: Screen get() = stack.last()

    var theme by mutableIntStateOf(prefs.theme)
        private set
    var adFirst by mutableStateOf(prefs.adFirst)
        private set
    var today: LocalDate by mutableStateOf(LocalDate.now(zone()))
        private set

    /** Bumped when holiday data reloads. */
    var dataGen by mutableIntStateOf(HolidayStore.generation)
        private set

    /** Bumped when calendar data changes (provider notifications, our own edits, permission grants). */
    var eventsGen by mutableIntStateOf(0)
        private set

    var canReadCalendar by mutableStateOf(CalendarRepo.canRead(ctx))
        private set

    /** Day sheet: the date currently open, or null. */
    var sheetDate: LocalDate? by mutableStateOf(null)

    /** Event ID waiting to be loaded into the editor. */
    var pendingOpen: Long? by mutableStateOf(null)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            eventsGen++
        }
    }
    private var observing = false

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun push(s: Screen) {
        stack += s
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun goTo(s: Screen) {
        stack.clear()
        stack += s
    }

    fun setThemePref(v: Int) {
        prefs.theme = v
        theme = v
    }

    fun setAdFirstPref(v: Boolean) {
        prefs.adFirst = v
        adFirst = v
    }

    fun refreshToday() {
        today = LocalDate.now(zone())
    }

    fun dataChanged() {
        dataGen = HolidayStore.generation
        Widgets.updateAllAsync(ctx)
    }

    fun eventsChanged() {
        eventsGen++
        Widgets.updateAllAsync(ctx)
    }

    fun onPermissionsResult() {
        canReadCalendar = CalendarRepo.canRead(ctx)
        startObserving()
        eventsChanged()
    }

    fun startObserving() {
        if (observing || !canReadCalendar) return
        runCatching {
            ctx.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        }
    }

    fun stopObserving() {
        if (!observing) return
        ctx.contentResolver.unregisterContentObserver(observer)
        observing = false
    }

    fun onResume() {
        refreshToday()
        val could = canReadCalendar
        canReadCalendar = CalendarRepo.canRead(ctx)
        if (canReadCalendar != could) eventsGen++
        startObserving()
        if (dataGen != HolidayStore.generation) dataGen = HolidayStore.generation
    }
}
