package app.patro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.patro.bs.Bs
import app.patro.bs.Np
import app.patro.events.CalendarRepo
import app.patro.holidays.HolidayStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Maps pager pages to months. BS pages cover the converter's whole range; AD pages the same span. */
class MonthPaging(val adFirst: Boolean) {
    private val firstAd: YearMonth = YearMonth.from(Bs.table.firstAd)
    private val lastAd: YearMonth = YearMonth.from(Bs.table.lastAd)

    val count: Int =
        if (adFirst) ((lastAd.year - firstAd.year) * 12 + lastAd.monthValue - firstAd.monthValue + 1)
        else (Bs.maxYear - Bs.minYear + 1) * 12

    fun pageOf(date: LocalDate): Int {
        return if (adFirst) {
            val ym = YearMonth.from(date)
            (ym.year - firstAd.year) * 12 + ym.monthValue - firstAd.monthValue
        } else {
            val bs = Bs.fromAd(date)
            (bs.year - Bs.minYear) * 12 + bs.month - 1
        }.coerceIn(0, count - 1)
    }

    fun page(index: Int): MonthPage =
        if (adFirst) {
            MonthPage.ad(clampYm(firstAd.plusMonths(index.toLong())))
        } else {
            MonthPage.bs(Bs.minYear + index / 12, index % 12 + 1)
        }

    private fun clampYm(ym: YearMonth) = if (ym.isBefore(firstAd)) firstAd else if (ym.isAfter(lastAd)) lastAd else ym
}

@Composable
fun MonthScreen(state: AppState, onRequestCalendar: () -> Unit) {
    val paging = remember(state.adFirst, state.dataGen) { MonthPaging(state.adFirst) }
    // New pager state when switching BS/AD: page indices mean different months.
    val pager = key(paging) { rememberPagerState(initialPage = paging.pageOf(state.today)) { paging.count } }
    val scope = rememberCoroutineScope()
    val backToToday: () -> Unit = { scope.launch { pager.animateScrollToPage(paging.pageOf(state.today)) } }
    LaunchedEffect(state.jumpToToday) { if (state.jumpToToday > 0) pager.scrollToPage(paging.pageOf(state.today)) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        TopBar(
            left = "Patro",
            actions = listOf(
                (if (state.adFirst) "BS" else "AD") to { state.setAdFirstPref(!state.adFirst) },
                "Settings" to { state.push(Screen.Settings) },
            ),
        )
        TodayHeader(state, onRequestCalendar) {
            backToToday()
            state.sheetDate = state.today
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f),
            beyondViewportPageCount = 1,
            key = { it },
        ) { index ->
            MonthPageView(state, paging, index, pager)
        }
    }
}

/** Today in both calendars, plus the next holiday. Tapping it opens today's day sheet. */
@Composable
private fun TodayHeader(state: AppState, onRequestCalendar: () -> Unit, onTap: () -> Unit) {
    val c = LocalColors.current
    val today = state.today
    if (!Bs.inRange(today)) return
    val bs = Bs.fromAd(today)
    val hols = remember(today, state.dataGen) { HolidayStore.on(today) }
    val next = remember(today, state.dataGen) { HolidayStore.next(today.plusDays(1)) }
    val dayOff = today.dayOfWeek == java.time.DayOfWeek.SATURDAY || hols.any { it.isDayOff }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(CardShape)
            .background(c.card)
            .clickable(onClick = onTap)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            "${Np.full(bs)}, ${Np.weekday(today)}",
            style = Type.title,
            color = if (dayOff) c.accent else c.fg,
        )
        Text(Np.ad(today), style = Type.body, color = c.fg)
        hols.forEach { Text(it.title, style = Type.small, color = c.accent) }
        if (next != null) {
            val days = ChronoUnit.DAYS.between(today, next.ad)
            Text(
                "Next: ${next.title} · ${Np.daysLeft(days)} · ${Np.adShort(next.ad)}",
                style = Type.small,
                color = c.dim,
            )
        }
        if (!state.canReadCalendar) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Allow calendar access to show your events and Google's Nepal holidays.",
                style = Type.small,
                color = c.dim,
            )
            Spacer(Modifier.height(8.dp))
            Pill("Allow calendar access", onClick = onRequestCalendar)
        }
    }
}

@Composable
private fun MonthPageView(state: AppState, paging: MonthPaging, index: Int, pager: PagerState) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val page = remember(index, paging) { paging.page(index) }
    val dataGen = state.dataGen
    val holidayMap = remember(page, dataGen) { page.days.associateWith { HolidayStore.on(it) } }
    var eventDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    LaunchedEffect(page, state.eventsGen, state.canReadCalendar) {
        eventDates = withContext(Dispatchers.IO) {
            CalendarRepo.instances(ctx, page.first, page.endExclusive, state.zone()).keys
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(page.title, style = Type.headline, color = c.fg)
                Text(page.subtitle, style = Type.small, color = c.dim)
            }
            RoundButton("‹", "Previous month", { scope.launch { pager.animateScrollToPage(index - 1) } })
            Spacer(Modifier.width(8.dp))
            RoundButton("›", "Next month", { scope.launch { pager.animateScrollToPage(index + 1) } })
        }
        MonthGrid(
            page = page,
            today = state.today,
            holidays = { holidayMap[it].orEmpty() },
            eventDates = eventDates,
            onTap = { state.sheetDate = it },
        )
        // Holidays this month, listed under the grid like a paper patro's footer.
        val listed = page.days.flatMap { d -> holidayMap[d].orEmpty() }.distinctBy { it.ad to it.title }
        if (listed.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            listed.forEach { h ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (h.isDayOff) c.accent else c.dim, 6.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        listOf("${Np.digits(h.bs.day)} ${Np.month(h.bs.month)}", h.title, h.subtitle)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = Type.small,
                        color = c.fg,
                    )
                }
            }
        }
    }
}
