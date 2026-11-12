package app.patro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.patro.bs.Bs
import app.patro.bs.Np
import app.patro.events.CalendarRepo
import app.patro.events.EventItem
import app.patro.holidays.HolidayStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    state: AppState,
    date: LocalDate,
    onAddEvent: (LocalDate) -> Unit,
    onOpenEvent: (EventItem) -> Unit,
    onRequestCalendar: () -> Unit,
) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bs = Bs.fromAd(date)
    val hols = remember(date, state.dataGen) { HolidayStore.on(date) }
    var events by remember(date) { mutableStateOf<List<EventItem>>(emptyList()) }
    LaunchedEffect(date, state.eventsGen, state.canReadCalendar) {
        events = withContext(Dispatchers.IO) {
            CalendarRepo.instances(ctx, date, date.plusDays(1), state.zone())[date].orEmpty()
        }
    }
    ModalBottomSheet(
        onDismissRequest = { state.sheetDate = null },
        sheetState = sheet,
        containerColor = c.card,
        contentColor = c.fg,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DotText(
                    Np.digits(bs.day),
                    dot = 6.dp,
                    color = if (date.dayOfWeek == java.time.DayOfWeek.SATURDAY || hols.any { it.isDayOff }) c.accent else c.fg,
                    contentDescription = Np.fullEn(bs),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(Np.subtitle(bs, date), style = Type.title, color = c.fg)
                    Text("${Np.ad(date)} · ${Np.fullEn(bs)}", style = Type.small, color = c.dim)
                }
            }
            if (hols.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Holiday")
                hols.forEach { h ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (h.isDayOff) c.accent else c.dim, 8.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(h.np, style = Type.body, color = if (h.isDayOff) c.accent else c.fg)
                            Text(
                                h.en + (if (h.isDayOff) " · public holiday" else " · festival"),
                                style = Type.small,
                                color = c.dim,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SpacedRow {
                SectionLabel("Events")
                if (state.canReadCalendar) Pill("+ Add event", onClick = { onAddEvent(date) })
            }
            Spacer(Modifier.height(4.dp))
            when {
                !state.canReadCalendar -> {
                    Spacer(Modifier.height(8.dp))
                    Pill("Allow calendar access", onClick = onRequestCalendar)
                }
                events.isEmpty() -> Text("No events.", style = Type.body, color = c.dim, modifier = Modifier.padding(vertical = 8.dp))
                else -> events.forEach { EventRow(it, state) { onOpenEvent(it) } }
            }
        }
    }
}
