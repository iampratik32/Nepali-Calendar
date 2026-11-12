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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.patro.bs.Bs
import app.patro.bs.Np
import app.patro.events.CalendarRepo
import app.patro.events.EventItem
import app.patro.holidays.HolidayStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(state: AppState, onRequestCalendar: () -> Unit, onAddEvent: (LocalDate) -> Unit, onOpenEvent: (EventItem) -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val today = state.today
    if (!Bs.inRange(today)) {
        Text("Date outside supported range", color = c.fg, modifier = Modifier.statusBarsPadding().padding(24.dp))
        return
    }
    val bs = Bs.fromAd(today)
    val dataGen = state.dataGen
    val todaysHolidays = remember(today, dataGen) { HolidayStore.on(today) }
    val next = remember(today, dataGen) { HolidayStore.next(today.plusDays(if (todaysHolidays.isEmpty()) 0 else 1)) }

    var events by remember { mutableStateOf<List<EventItem>>(emptyList()) }
    LaunchedEffect(today, state.eventsGen, state.canReadCalendar) {
        events = withContext(Dispatchers.IO) {
            CalendarRepo.instances(ctx, today, today.plusDays(1), state.zone())[today].orEmpty()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        TopBar(
            left = "PATRO",
            actions = listOf("Month" to { state.push(Screen.Month) }, "Settings" to { state.push(Screen.Settings) }),
        )

        // Hero
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 28.dp)) {
            val dayOff = today.dayOfWeek == java.time.DayOfWeek.SATURDAY || todaysHolidays.any { it.isDayOff }
            DotText(
                Np.digits(bs.day),
                dot = 14.dp,
                color = if (dayOff) c.accent else c.fg,
                dimColor = c.faint,
                animate = true,
                contentDescription = Np.fullEn(bs),
            )
            Spacer(Modifier.height(20.dp))
            Text(Np.subtitle(bs, today), style = Type.headline, color = c.fg)
            Spacer(Modifier.height(4.dp))
            Text(Np.ad(today), style = Type.body, color = c.dim)
            todaysHolidays.forEach {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(c.accent, 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${it.np} · ${it.en}", style = Type.body, color = c.accent)
                }
            }
        }

        // Next holiday
        PatroCard(onClick = { next?.let { state.sheetDate = it.ad } }) {
            SectionLabel("Next holiday")
            Spacer(Modifier.height(10.dp))
            if (next == null) {
                Text("No upcoming holidays in the data.", style = Type.body, color = c.fg)
                Text("Settings → Sync to fetch the latest list.", style = Type.small, color = c.dim)
            } else {
                val days = ChronoUnit.DAYS.between(today, next.ad)
                Text("${next.np} · ${Np.daysLeft(days)}", style = Type.title, color = c.fg)
                Spacer(Modifier.height(2.dp))
                Text("${next.en} · ${Np.full(next.bs)} · ${Np.adShort(next.ad)}", style = Type.small, color = c.dim)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Today's events
        PatroCard {
            SpacedRow {
                SectionLabel("Today")
                if (state.canReadCalendar) RoundButton("+", "Add event", { onAddEvent(today) })
            }
            Spacer(Modifier.height(6.dp))
            if (!state.canReadCalendar) {
                Text("Show your Google Calendar events here.", style = Type.body, color = c.fg)
                Spacer(Modifier.height(12.dp))
                Pill("Allow calendar access", onClick = onRequestCalendar)
            } else if (events.isEmpty()) {
                Text("Nothing scheduled.", style = Type.body, color = c.dim)
            } else {
                events.forEach { EventRow(it, state) { onOpenEvent(it) } }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TopBar(left: String, actions: List<Pair<String, () -> Unit>>, onBack: (() -> Unit)? = null) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundButton("‹", "Back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        SectionLabel(left, Modifier.weight(1f), color = c.fg)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (label, action) -> Pill(label, onClick = action) }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun EventRow(e: EventItem, state: AppState, onClick: () -> Unit) {
    val c = LocalColors.current
    val zone = state.zone()
    val time = if (e.allDay) "All day" else
        Instant.ofEpochMilli(e.begin).atZone(zone).format(TIME) + "–" + Instant.ofEpochMilli(e.end).atZone(zone).format(TIME)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(Color(e.color or 0xFF000000.toInt()), 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = Type.body, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(time + if (e.recurring) " · repeats" else "", style = Type.small, color = c.dim)
        }
    }
}
