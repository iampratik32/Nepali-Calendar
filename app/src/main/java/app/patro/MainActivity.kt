package app.patro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.patro.events.CalendarRepo
import app.patro.events.EventItem
import app.patro.ui.AppState
import app.patro.ui.DaySheet
import app.patro.ui.EditorScreen
import app.patro.ui.LocalColors
import app.patro.ui.MonthScreen
import app.patro.ui.PatroTheme
import app.patro.ui.Screen
import app.patro.ui.SettingsScreen
import app.patro.ui.TodayScreen
import app.patro.ui.newDraft
import app.patro.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = AppState(applicationContext)
        handleIntent(intent)
        setContent { PatroTheme(state.theme) { PatroRoot(state) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_TODAY, false) == true) {
            state.sheetDate = null
            state.goTo(Screen.Today)
        }
    }

    override fun onResume() {
        super.onResume()
        state.onResume()
        // Opening the app is one of the widget's refresh triggers.
        Widgets.updateAll(this)
    }

    override fun onDestroy() {
        state.stopObserving()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TODAY = "app.patro.TODAY"
    }
}

@Composable
private fun PatroRoot(state: AppState) {
    val c = LocalColors.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.onPermissionsResult()
    }
    val requestCalendar = { permission.launch(CalendarRepo.PERMISSIONS) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // Roll the date over at local midnight while the app is open.
    LaunchedEffect(state.today) {
        val now = ZonedDateTime.now(state.zone())
        val next = now.toLocalDate().plusDays(1).atStartOfDay(state.zone())
        delay(Duration.between(now, next).toMillis() + 1_000)
        state.refreshToday()
    }

    val addEvent: (LocalDate) -> Unit = { date ->
        state.sheetDate = null
        state.push(Screen.Editor(newDraft(state, date)))
    }
    val openEvent: (EventItem) -> Unit = { e ->
        state.sheetDate = null
        // Loading is a single-row query; quick enough to do inline, but keep it off the main thread.
        state.pendingOpen = e.eventId
    }
    LaunchedEffect(state.pendingOpen) {
        val id = state.pendingOpen ?: return@LaunchedEffect
        val draft = withContext(Dispatchers.IO) { CalendarRepo.load(ctx, id, state.zone()) }
        state.pendingOpen = null
        if (draft != null) state.push(Screen.Editor(draft))
    }

    BackHandler(enabled = state.stack.size > 1) { state.pop() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        when (val s = state.screen) {
            Screen.Today -> TodayScreen(state, requestCalendar, addEvent, openEvent)
            Screen.Month -> MonthScreen(state)
            Screen.Settings -> SettingsScreen(state, requestCalendar)
            is Screen.Editor -> EditorScreen(state, s.draft)
        }
        state.sheetDate?.let { date ->
            DaySheet(state, date, addEvent, openEvent, requestCalendar)
        }
    }
}
