package app.patro.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.patro.bs.Bs
import app.patro.events.CalendarRepo
import app.patro.holidays.HolidayStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

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
fun MonthScreen(state: AppState) {
    val c = LocalColors.current
    val paging = remember(state.adFirst, state.dataGen) { MonthPaging(state.adFirst) }
    val pager = rememberPagerState(initialPage = paging.pageOf(state.today)) { paging.count }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        TopBar(
            left = "Month",
            onBack = { state.pop() },
            actions = listOf(
                (if (state.adFirst) "BS" else "AD") to { state.setAdFirstPref(!state.adFirst) },
                "Today" to { scope.launch { pager.animateScrollToPage(paging.pageOf(state.today)) } },
            ),
        )
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
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
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
        val listed = page.days.flatMap { d -> holidayMap[d].orEmpty() }
        if (listed.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            listed.forEach { h ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (h.isDayOff) c.accent else c.dim, 6.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${app.patro.bs.Np.digits(h.bs.day)} ${app.patro.bs.Np.month(h.bs.month)} · ${h.np} · ${h.en}",
                        style = Type.small,
                        color = c.fg,
                    )
                }
            }
        }
    }
}
