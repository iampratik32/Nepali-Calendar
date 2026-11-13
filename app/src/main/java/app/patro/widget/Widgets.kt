package app.patro.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.patro.MainActivity
import app.patro.R
import app.patro.bs.Bs
import app.patro.bs.Np
import app.patro.events.CalendarRepo
import app.patro.holidays.HolidayStore
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Glance-style widgets: no card, white system text straight on the wallpaper (with a shadow for
 * light wallpapers). Every size shows both the AD and the BS date.
 */
object Widgets {
    /** One text line; the layout gives lines 1–2 a large size and the rest a small one. */
    data class Line(val text: String, val color: Int)

    const val ACTION_REFRESH = "app.patro.action.WIDGET_REFRESH"

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DIM = 0xFFEDEDED.toInt()
    private const val RED = 0xFFFF3B3B.toInt()

    private val LINE_IDS = intArrayOf(R.id.line1, R.id.line2, R.id.line3, R.id.line4, R.id.line5)
    private val HM = DateTimeFormatter.ofPattern("HH:mm")
    private val DOW = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

    enum class Kind { SMALL, WIDE, NEXT }

    private val worker = Executors.newSingleThreadExecutor()

    /** Same as [updateAll] but off the main thread (it queries the calendar). */
    fun updateAllAsync(ctx: Context, onDone: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        worker.execute {
            runCatching { updateAll(app) }
            onDone?.invoke()
        }
    }

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        HolidayStore.ensureLoaded(ctx)
        HolidayStore.refreshGoogle(ctx)
        update(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, DateWidget::class.java)), Kind.SMALL, refresh = false)
        update(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, DateWideWidget::class.java)), Kind.WIDE, refresh = false)
        update(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, DateNextWidget::class.java)), Kind.NEXT, refresh = false)
        scheduleMidnight(ctx)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray, kind: Kind, refresh: Boolean = true) {
        if (ids.isEmpty()) return
        HolidayStore.ensureLoaded(ctx)
        if (refresh) HolidayStore.refreshGoogle(ctx)
        val views = render(ctx, kind)
        for (id in ids) mgr.updateAppWidget(id, views)
        scheduleMidnight(ctx)
    }

    /** The text lines a widget of [kind] shows. */
    fun lines(ctx: Context, kind: Kind, today: LocalDate, zone: ZoneId): List<Line> {
        if (!Bs.inRange(today)) return listOf(Line(Np.ad(today), WHITE))
        val bs = Bs.fromAd(today)
        val hols = HolidayStore.on(today)
        val dayOff = today.dayOfWeek == DayOfWeek.SATURDAY || hols.any { it.isDayOff }
        val adFull = Np.ad(today)                                     // Fri, 13 Nov 2026
        val adShort = adFull.substringBeforeLast(' ')                 // Fri, 13 Nov
        val npFull = "${Np.full(bs)}, ${Np.weekday(today)}"           // २७ कात्तिक २०८३, शुक्रबार
        val npColor = if (dayOff) RED else WHITE
        val holidayLine = hols.firstOrNull()?.let { Line(it.title, RED) }

        return when (kind) {
            Kind.WIDE -> listOfNotNull(
                Line(adFull, WHITE),
                Line(npFull, npColor),
                holidayLine,
            )
            Kind.SMALL -> listOfNotNull(
                Line(adShort, WHITE),
                Line("${Np.digits(bs.day)} ${Np.month(bs.month)} ${Np.digits(bs.year)}", npColor),
                holidayLine,
                Line(Np.weekday(today), npColor).takeIf { holidayLine == null },
            )
            Kind.NEXT -> buildList {
                add(Line(adFull, WHITE))
                add(Line(npFull, npColor))
                if (holidayLine != null) add(holidayLine)
                nextHolidayLine(today, excludeToday = holidayLine != null)?.let { add(it) }
                nextEventLine(ctx, today, zone)?.let { add(it) }
            }
        }
    }

    private fun nextHolidayLine(today: LocalDate, excludeToday: Boolean): Line? {
        val h = HolidayStore.next(if (excludeToday) today.plusDays(1) else today) ?: return null
        val days = ChronoUnit.DAYS.between(today, h.ad)
        if (days > 60) return null
        return Line("${h.title} · ${Np.daysLeft(days)}", if (h.isDayOff) RED else DIM)
    }

    private fun nextEventLine(ctx: Context, today: LocalDate, zone: ZoneId): Line? {
        val now = System.currentTimeMillis()
        val e = runCatching {
            CalendarRepo.instances(ctx, today, today.plusDays(8), zone).values.flatten()
                .filter { it.end > now }
                .minByOrNull { it.begin }
        }.getOrNull() ?: return null
        val z = if (e.allDay) ZoneOffset.UTC else zone
        val t = Instant.ofEpochMilli(e.begin).atZone(z)
        val day = when (t.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> t.format(DOW)
        }
        val time = if (e.allDay) day else "$day ${t.format(HM)}"
        return Line("$time · ${e.title}", DIM)
    }

    private fun render(ctx: Context, kind: Kind): RemoteViews {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_text)
        val lines = lines(ctx, kind, today, zone)
        LINE_IDS.forEachIndexed { i, id ->
            val line = lines.getOrNull(i)
            if (line == null) {
                rv.setViewVisibility(id, View.GONE)
            } else {
                rv.setViewVisibility(id, View.VISIBLE)
                rv.setTextViewText(id, line.text)
                rv.setTextColor(id, line.color)
            }
        }

        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TODAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        rv.setOnClickPendingIntent(
            android.R.id.background,
            PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        return rv
    }

    /**
     * Alarm a little after local midnight so the date rolls over. `updatePeriodMillis` (30 min)
     * is the backup; time/zone changes and opening the app also refresh.
     */
    fun scheduleMidnight(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val at = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusSeconds(30).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, RefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}

/** 2×2: AD date, BS day + month, BS year + weekday. */
class DateWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        Widgets.update(ctx, mgr, ids, Widgets.Kind.SMALL)
}

/** 4×1 horizontal, like Nothing's glance widget: AD date line + BS date line. */
class DateWideWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        Widgets.update(ctx, mgr, ids, Widgets.Kind.WIDE)
}

/** 4×2: both dates plus the next holiday and next event. */
class DateNextWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        Widgets.update(ctx, mgr, ids, Widgets.Kind.NEXT)
}

/** Midnight alarm + time/zone/locale changes → redraw every widget. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Keep the receiver alive until the background redraw is done.
        val pending = goAsync()
        Widgets.updateAllAsync(ctx) { pending.finish() }
    }
}
