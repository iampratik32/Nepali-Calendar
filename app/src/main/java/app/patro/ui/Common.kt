package app.patro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.patro.events.EventItem
import java.time.Instant
import java.time.format.DateTimeFormatter

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
