package app.patro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.patro.bs.Bs
import app.patro.bs.Np
import app.patro.holidays.Holiday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** One page of the month view: either a BS month or (AD-first mode) a Gregorian month. */
data class MonthPage(val title: String, val subtitle: String, val days: List<LocalDate>, val bsMode: Boolean) {
    val first: LocalDate get() = days.first()
    val endExclusive: LocalDate get() = days.last().plusDays(1)

    companion object {
        fun bs(year: Int, month: Int): MonthPage {
            val start = Bs.monthStart(year, month)
            val n = Bs.daysInMonth(year, month)
            val days = List(n) { start.plusDays(it.toLong()) }
            val a = days.first()
            val b = days.last()
            val sub = if (a.year == b.year) {
                "${Np.adMonth(a)} – ${Np.adMonth(b)} ${b.year}"
            } else {
                "${Np.adMonth(a)} ${a.year} – ${Np.adMonth(b)} ${b.year}"
            }
            return MonthPage(Np.monthYear(year, month), sub, days, bsMode = true)
        }

        fun ad(ym: YearMonth): MonthPage {
            // The first/last AD months of the range are only partly convertible; keep those days.
            val days = List(ym.lengthOfMonth()) { ym.atDay(it + 1) }.filter { Bs.inRange(it) }
            val a = Bs.fromAd(days.first())
            val b = Bs.fromAd(days.last())
            val sub = if (a.year == b.year) {
                "${Np.month(a.month)} – ${Np.month(b.month)} ${Np.digits(b.year)}"
            } else {
                "${Np.monthYear(a.year, a.month)} – ${Np.monthYear(b.year, b.month)}"
            }
            val title = "${ym.month.getDisplayName(JTextStyle.FULL, Locale.ENGLISH)} ${ym.year}"
            return MonthPage(title, sub, days, bsMode = false)
        }
    }
}

@Composable
fun WeekdayHeader(bsMode: Boolean) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        for (i in 0..6) {
            Text(
                if (bsMode) Np.WEEKDAYS_SHORT[i] else Np.WEEKDAYS_EN_SHORT[i],
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = Type.label.copy(letterSpacing = 0.5.sp),
                color = if (i == 6) c.accent else c.dim,
            )
        }
    }
}

/**
 * Sunday-first grid. Saturdays and day-off holidays are red; festivals get a red dot,
 * dates with events a plain dot; today has a ring; [selected] is filled.
 */
@Composable
fun MonthGrid(
    page: MonthPage,
    today: LocalDate,
    holidays: (LocalDate) -> List<Holiday>,
    eventDates: Set<LocalDate>,
    selected: LocalDate? = null,
    compact: Boolean = false,
    onTap: (LocalDate) -> Unit,
) {
    val lead = Np.sundayIndex(page.first.dayOfWeek)
    val total = lead + page.days.size
    val rows = (total + 6) / 7
    Column(Modifier.fillMaxWidth()) {
        WeekdayHeader(page.bsMode)
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val idx = r * 7 + col - lead
                    Box(Modifier.weight(1f).aspectRatio(if (compact) 1.1f else 0.82f).padding(2.dp)) {
                        if (idx in page.days.indices) {
                            val date = page.days[idx]
                            DayCell(
                                date = date,
                                bsMode = page.bsMode,
                                isToday = date == today,
                                isSelected = date == selected,
                                hols = holidays(date),
                                hasEvents = date in eventDates,
                                compact = compact,
                                onTap = { onTap(date) },
                            )
                        }
                    }
                }
            }
        }
        if (!compact) Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    bsMode: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hols: List<Holiday>,
    hasEvents: Boolean,
    compact: Boolean,
    onTap: () -> Unit,
) {
    val c = LocalColors.current
    val bs = Bs.fromAd(date)
    val dayOff = date.dayOfWeek == DayOfWeek.SATURDAY || hols.any { it.isDayOff }
    val festival = hols.isNotEmpty() && hols.none { it.isDayOff }
    val shape = RoundedCornerShape(14.dp)
    val main = if (bsMode) Np.digits(bs.day) else date.dayOfMonth.toString()
    val corner = if (bsMode) {
        if (date.dayOfMonth == 1) "${Np.adMonth(date)} 1" else date.dayOfMonth.toString()
    } else {
        if (bs.day == 1) Np.month(bs.month) else Np.digits(bs.day)
    }
    val mainColor = when {
        isSelected -> c.bg
        dayOff -> c.accent
        else -> c.fg
    }
    val desc = buildString {
        append(Np.fullEn(bs)).append(", ").append(Np.ad(date))
        hols.forEach { append(", ").append(it.en) }
        if (hasEvents) append(", has events")
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .then(if (isSelected) Modifier.background(c.fg) else Modifier)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, c.fg, shape) else Modifier)
            .clickable(onClick = onTap)
            .semantics { contentDescription = desc },
    ) {
        Text(
            corner,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 6.dp),
            style = TextStyle(fontSize = if (compact) 8.sp else 9.sp, lineHeight = 10.sp),
            color = if (isSelected) c.bg.copy(alpha = 0.7f) else c.dim,
            maxLines = 1,
        )
        Text(
            main,
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(
                fontSize = if (compact) 16.sp else 21.sp,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = mainColor,
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (festival) Dot(c.accent, 4.dp)
            if (hasEvents) Dot(if (isSelected) c.bg else c.fg, 4.dp)
        }
    }
}
