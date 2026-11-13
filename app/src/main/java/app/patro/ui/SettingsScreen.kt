package app.patro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.patro.BuildConfig
import app.patro.bs.Bs
import app.patro.events.CalendarInfo
import app.patro.events.CalendarRepo
import app.patro.holidays.HolidayStore
import app.patro.holidays.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

private val STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.ENGLISH)

@Composable
fun SettingsScreen(state: AppState, onRequestCalendar: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var syncOk by remember { mutableStateOf(true) }
    var lastSync by remember { mutableStateOf(state.prefs.lastSync) }
    var showLicences by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        TopBar(left = "Settings", onBack = { state.pop() }, actions = emptyList())
        Spacer(Modifier.height(16.dp))

        // Holidays: where they come from.
        PatroCard {
            SectionLabel("Holidays")
            Spacer(Modifier.height(10.dp))
            val googleCount = remember(state.dataGen) { HolidayStore.googleCount() }
            Text(
                if (googleCount > 0) "Google Calendar: $googleCount holidays" else "Google Calendar: none",
                style = Type.body,
                color = c.fg,
            )
            Text(
                if (googleCount > 0) "From calendars marked \"Holidays\" below."
                else "Mark Google's \"Holidays in Nepal\" calendar as Holidays below.",
                style = Type.small,
                color = c.dim,
            )
            Spacer(Modifier.height(10.dp))
            val builtIn = remember(state.dataGen) { HolidayStore.years().sumOf { HolidayStore.file(it)?.holidays?.size ?: 0 } }
            Text("Built into Patro: $builtIn", style = Type.body, color = c.fg)
            Text("Official MoHA holiday list. Sync fetches updates.", style = Type.small, color = c.dim)
            Spacer(Modifier.height(10.dp))
            Text(
                if (lastSync == 0L) "Never synced" else "Last synced: " +
                    Instant.ofEpochMilli(lastSync).atZone(state.zone()).format(STAMP),
                style = Type.small,
                color = c.dim,
            )
            syncMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = Type.small, color = if (syncOk) c.fg else c.accent)
            }
            Spacer(Modifier.height(12.dp))
            Pill(if (syncing) "Syncing…" else "Sync now", accent = true, enabled = !syncing, onClick = {
                syncing = true
                syncMsg = null
                scope.launch {
                    val r = Sync.run(ctx)
                    syncing = false
                    syncOk = r.ok
                    syncMsg = r.summary
                    lastSync = state.prefs.lastSync
                    state.dataChanged()
                }
            })
        }
        Spacer(Modifier.height(12.dp))

        // Calendars
        PatroCard {
            SectionLabel("Calendars")
            Spacer(Modifier.height(10.dp))
            if (!state.canReadCalendar) {
                Text("Patro reads and writes the events your phone already syncs with Google Calendar.", style = Type.small, color = c.dim)
                Spacer(Modifier.height(12.dp))
                Pill("Allow calendar access", onClick = onRequestCalendar)
            } else {
                CalendarPicker(state)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Display
        PatroCard {
            SectionLabel("Display")
            Spacer(Modifier.height(6.dp))
            SpacedRow {
                Column(Modifier.weight(1f)) {
                    Text("AD-first month view", style = Type.body, color = c.fg)
                    Text("Gregorian months with BS dates in the corner", style = Type.small, color = c.dim)
                }
                Switch(
                    checked = state.adFirst,
                    onCheckedChange = { state.setAdFirstPref(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.accent, checkedThumbColor = Color.White,
                        uncheckedTrackColor = c.faint, uncheckedThumbColor = c.dim, uncheckedBorderColor = c.line,
                    ),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Theme", style = Type.body, color = c.fg)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "System", 1 to "Dark", 2 to "Light").forEach { (v, label) ->
                    Pill(label, selected = state.theme == v, onClick = { state.setThemePref(v) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // About
        PatroCard(onClick = { showLicences = !showLicences }) {
            SectionLabel("About")
            Spacer(Modifier.height(8.dp))
            Text("Patro ${BuildConfig.VERSION_NAME}", style = Type.body, color = c.fg)
            Text(
                "BS ${Bs.minYear}–${Bs.maxYear} · no ads, no tracking, network only when you tap Sync.",
                style = Type.small,
                color = c.dim,
            )
            Spacer(Modifier.height(8.dp))
            Text(if (showLicences) "Hide licences" else "Licences ›", style = Type.small, color = c.fg)
            if (showLicences) {
                Spacer(Modifier.height(8.dp))
                val text = remember { ctx.assets.open("licences.txt").bufferedReader().use { it.readText() } }
                Text(text, style = Type.small, color = c.dim)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CalendarPicker(state: AppState) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var cals by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var holidayIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var chosen by remember { mutableStateOf(state.prefs.calendarIds) }
    LaunchedEffect(state.eventsGen) {
        val (all, hol) = withContext(Dispatchers.IO) {
            val all = CalendarRepo.calendars(ctx)
            all to CalendarRepo.holidayCalendarIds(ctx, all)
        }
        cals = all
        holidayIds = hol
    }
    if (cals.isEmpty()) {
        Text(
            "No calendars found. Make sure Google Calendar is installed and \"Share Google Calendar data with other apps\" is on.",
            style = Type.small,
            color = c.dim,
        )
        return
    }
    Text(
        "Tick a calendar to show its events as dots. Mark it \"Holidays\" to show its entries as red " +
            "holidays instead, e.g. Google's \"Holidays in Nepal\".",
        style = Type.small,
        color = c.dim,
    )
    val accounts = cals.groupBy { it.account }
    accounts.forEach { (account, list) ->
        Text(account, style = Type.small, color = c.dim, modifier = Modifier.padding(top = 10.dp))
        list.forEach { cal ->
            val isHoliday = cal.id in holidayIds
            val on = chosen?.contains(cal.id) ?: true
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable(enabled = !isHoliday) {
                        val cur = chosen ?: cals.map { it.id }.toSet()
                        val next = if (on) cur - cal.id else cur + cal.id
                        chosen = next
                        state.prefs.calendarIds = next
                        state.eventsChanged()
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = on && !isHoliday,
                        onCheckedChange = null,
                        enabled = !isHoliday,
                        colors = CheckboxDefaults.colors(checkedColor = Color(cal.color or 0xFF000000.toInt()), uncheckedColor = c.dim),
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(cal.name, style = Type.body, color = c.fg)
                        if (isHoliday) Text("Shown as holidays", style = Type.small, color = c.accent)
                    }
                }
                Pill("Holidays", selected = isHoliday, onClick = {
                    val next = if (isHoliday) holidayIds - cal.id else holidayIds + cal.id
                    holidayIds = next
                    state.prefs.holidayCalendarIds = next
                    state.eventsChanged()
                })
            }
        }
    }
}
