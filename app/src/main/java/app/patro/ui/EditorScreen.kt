package app.patro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.patro.bs.Bs
import app.patro.bs.Np
import app.patro.events.CalendarInfo
import app.patro.events.CalendarRepo
import app.patro.events.EventDraft
import app.patro.events.Repeat
import app.patro.holidays.HolidayStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val REMINDERS: List<Pair<Int?, String>> = listOf(
    null to "None", 10 to "10 min", 30 to "30 min", 60 to "1 hour", 1440 to "1 day",
)
private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** Starting point for a new event on [date]: next full hour, one hour long. */
fun newDraft(state: AppState, date: LocalDate): EventDraft {
    val now = LocalTime.now(state.zone())
    val startTime = if (date == state.today && now.hour < 23) LocalTime.of(now.hour + 1, 0) else LocalTime.of(9, 0)
    val start = date.atTime(startTime)
    return EventDraft(start = start, end = start.plusHours(1), calendarId = state.prefs.lastEditCalendar, reminder = 10)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(state: AppState, initial: EventDraft) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember(initial) { mutableStateOf(initial) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var pickTime by remember { mutableStateOf<Boolean?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val all = withContext(Dispatchers.IO) { CalendarRepo.calendars(ctx) }.filter { it.writable }
        calendars = all
        if (all.none { it.id == d.calendarId }) {
            // Prefer a Google account calendar named after the account (the primary one).
            val pick = all.firstOrNull { it.name == it.account } ?: all.firstOrNull()
            if (pick != null) d = d.copy(calendarId = pick.id)
        }
    }

    fun save() {
        if (d.title.isBlank()) { error = "Add a title"; return }
        if (calendars.none { it.id == d.calendarId }) { error = "No writable calendar on this phone"; return }
        if (d.end.isBefore(d.start)) { error = "End is before start"; return }
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { CalendarRepo.save(ctx, d, state.zone()) } }
            busy = false
            r.onSuccess { state.eventsChanged(); state.pop() }
                .onFailure { error = it.message ?: "Couldn't save" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        TopBar(
            left = if (d.id == null) "New event" else "Edit event",
            onBack = { state.pop() },
            actions = listOf("Save" to { if (!busy) save() }),
        )
        Spacer(Modifier.height(16.dp))
        PatroCard {
            SectionLabel("Title")
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = d.title,
                onValueChange = { d = d.copy(title = it); error = null },
                textStyle = Type.title.copy(color = c.fg),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (d.title.isEmpty()) Text("Event name", style = Type.title, color = c.dim)
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        PatroCard {
            SpacedRow {
                Text("All day", style = Type.body, color = c.fg)
                Switch(
                    checked = d.allDay,
                    onCheckedChange = { d = d.copy(allDay = it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.accent, checkedThumbColor = Color.White,
                        uncheckedTrackColor = c.faint, uncheckedThumbColor = c.dim, uncheckedBorderColor = c.line,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            DateTimeRow("Starts", d.start, d.allDay, onDate = { pickDate = true }, onTime = { pickTime = true })
            Spacer(Modifier.height(12.dp))
            DateTimeRow("Ends", d.end, d.allDay, onDate = { pickDate = false }, onTime = { pickTime = false })
        }
        Spacer(Modifier.height(12.dp))
        PatroCard {
            SectionLabel("Calendar")
            Spacer(Modifier.height(8.dp))
            var open by remember { mutableStateOf(false) }
            val cur = calendars.firstOrNull { it.id == d.calendarId }
            Box {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = calendars.isNotEmpty()) { open = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cur != null) Dot(Color(cur.color or 0xFF000000.toInt()), 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cur?.name ?: "No writable calendar", style = Type.body, color = c.fg)
                        if (cur != null && cur.account != cur.name) Text(cur.account, style = Type.small, color = c.dim)
                    }
                    Text("▾", style = Type.body, color = c.dim)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    calendars.forEach { cal ->
                        DropdownMenuItem(
                            text = { Text("${cal.name}  ·  ${cal.account}", style = Type.small) },
                            onClick = { d = d.copy(calendarId = cal.id); open = false },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PatroCard {
            SectionLabel("Reminder")
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDERS.forEach { (min, label) ->
                    Pill(label, selected = d.reminder == min, onClick = { d = d.copy(reminder = min) })
                }
                // Keep an existing reminder that isn't one of our presets.
                val r = d.reminder
                if (r != null && REMINDERS.none { it.first == r }) Pill("$r min", selected = true, onClick = {})
            }
            Spacer(Modifier.height(20.dp))
            SectionLabel("Repeat")
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.entries.filter { it != Repeat.CUSTOM || d.customRrule != null }.forEach { r ->
                    Pill(r.label, selected = d.repeat == r, onClick = { d = d.copy(repeat = r) })
                }
            }
            if (d.id != null && d.repeat != Repeat.NONE) {
                Spacer(Modifier.height(10.dp))
                Text("Changes apply to every occurrence.", style = Type.small, color = c.dim)
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = Type.body, color = c.accent)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(if (busy) "Saving…" else "Save", accent = true, enabled = !busy, onClick = { save() })
            if (d.id != null) Pill("Delete", onClick = { confirmDelete = true })
        }
        Spacer(Modifier.height(32.dp))
    }

    pickDate?.let { isStart ->
        DatePickDialog(
            state = state,
            initial = (if (isStart) d.start else d.end).toLocalDate(),
            onDismiss = { pickDate = null },
            onPick = { date ->
                d = if (isStart) {
                    // Moving the start keeps the duration.
                    val dur = Duration.between(d.start, d.end)
                    val s = date.atTime(d.start.toLocalTime())
                    d.copy(start = s, end = s.plus(dur))
                } else {
                    d.copy(end = date.atTime(d.end.toLocalTime()))
                }
                pickDate = null
            },
        )
    }
    pickTime?.let { isStart ->
        TimePickDialog(
            initial = (if (isStart) d.start else d.end).toLocalTime(),
            onDismiss = { pickTime = null },
            onPick = { t ->
                d = if (isStart) {
                    val dur = Duration.between(d.start, d.end)
                    val s = d.start.toLocalDate().atTime(t)
                    d.copy(start = s, end = s.plus(dur))
                } else {
                    d.copy(end = d.end.toLocalDate().atTime(t))
                }
                pickTime = null
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete event?") },
            text = { Text(if (d.repeat != Repeat.NONE) "This deletes every occurrence." else "This can't be undone.") },
            confirmButton = {
                Pill("Delete", accent = true, onClick = {
                    confirmDelete = false
                    val id = d.id ?: return@Pill
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { CalendarRepo.delete(ctx, id) } }
                        state.eventsChanged()
                        state.pop()
                    }
                })
            },
            dismissButton = { Pill("Cancel", onClick = { confirmDelete = false }) },
            containerColor = c.card,
        )
    }
}

@Composable
private fun DateTimeRow(label: String, at: LocalDateTime, allDay: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    val c = LocalColors.current
    val date = at.toLocalDate()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.small, color = c.dim, modifier = Modifier.width(56.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(c.faint)
                .clickable(onClick = onDate)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // BS and AD side by side, as the plan asks.
            if (Bs.inRange(date)) Text(Np.full(Bs.fromAd(date)), style = Type.body, color = c.fg)
            Text(Np.ad(date), style = Type.small, color = c.dim)
        }
        if (!allDay) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.faint)
                    .clickable(onClick = onTime)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Text(at.format(HM), style = Type.body, color = c.fg)
            }
        }
    }
}

@Composable
fun DatePickDialog(state: AppState, initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = LocalColors.current
    val paging = remember(state.adFirst) { MonthPaging(state.adFirst) }
    var index by remember { mutableStateOf(paging.pageOf(initial)) }
    var selected by remember { mutableStateOf(initial) }
    val page = remember(index) { paging.page(index) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = null,
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(page.title, style = Type.title, color = c.fg)
                        Text(page.subtitle, style = Type.small, color = c.dim)
                    }
                    RoundButton("‹", "Previous month", { if (index > 0) index-- })
                    Spacer(Modifier.width(6.dp))
                    RoundButton("›", "Next month", { if (index < paging.count - 1) index++ })
                }
                Spacer(Modifier.height(12.dp))
                MonthGrid(
                    page = page,
                    today = state.today,
                    holidays = { HolidayStore.on(it) },
                    eventDates = emptySet(),
                    selected = selected,
                    compact = true,
                    onTap = { selected = it },
                )
                Spacer(Modifier.height(8.dp))
                if (Bs.inRange(selected)) Text(Np.full(Bs.fromAd(selected)), style = Type.body, color = c.fg)
                Text(Np.ad(selected), style = Type.small, color = c.dim)
            }
        },
        confirmButton = { Pill("OK", accent = true, onClick = { onPick(selected) }) },
        dismissButton = { Pill("Cancel", onClick = onDismiss) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val c = LocalColors.current
    val tp = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        text = {
            TimePicker(
                state = tp,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.faint,
                    selectorColor = c.accent,
                    timeSelectorSelectedContainerColor = c.fg,
                    timeSelectorSelectedContentColor = c.bg,
                    timeSelectorUnselectedContainerColor = c.faint,
                    timeSelectorUnselectedContentColor = c.fg,
                    clockDialSelectedContentColor = Color.White,
                    clockDialUnselectedContentColor = c.fg,
                ),
            )
        },
        confirmButton = { Pill("OK", accent = true, onClick = { onPick(LocalTime.of(tp.hour, tp.minute)) }) },
        dismissButton = { Pill("Cancel", onClick = onDismiss) },
    )
}

