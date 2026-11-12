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
import app.patro.dotmatrix.DotMatrix
import app.patro.events.CalendarRepo
import app.patro.holidays.HolidayStore
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Rendering + scheduling shared by both widget sizes. */
object Widgets {
    const val ACTION_REFRESH = "app.patro.action.WIDGET_REFRESH"
    private const val DOT_PX = 22f
    private val HM = DateTimeFormatter.ofPattern("HH:mm")
    private val DOW = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Same as [updateAll] but off the main thread (it may query the calendar). */
    fun updateAllAsync(ctx: Context) {
        val app = ctx.applicationContext
        worker.execute { runCatching { updateAll(app) } }
    }

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        update(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, DateWidget::class.java)), withNext = false)
        update(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, DateNextWidget::class.java)), withNext = true)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray, withNext: Boolean) {
        if (ids.isEmpty()) return
        HolidayStore.ensureLoaded(ctx)
        val views = render(ctx, withNext)
        for (id in ids) mgr.updateAppWidget(id, views)
        scheduleMidnight(ctx)
    }

    private fun render(ctx: Context, withNext: Boolean): RemoteViews {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rv = RemoteViews(ctx.packageName, if (withNext) R.layout.widget_date_next else R.layout.widget_date)
        if (!Bs.inRange(today)) return rv
        val bs = Bs.fromAd(today)
        val hols = HolidayStore.on(today)

        // Drawn white; the layout tints it with @color/widget_fg so it follows light/dark.
        val red = today.dayOfWeek == DayOfWeek.SATURDAY || hols.any { it.isDayOff }
        val shown = if (red) R.id.digits_red else R.id.digits
        val hidden = if (red) R.id.digits else R.id.digits_red
        rv.setImageViewBitmap(shown, DotMatrix(Np.digits(bs.day)).toBitmap(DOT_PX, 0xFFFFFFFF.toInt()))
        rv.setContentDescription(shown, Np.fullEn(bs))
        rv.setViewVisibility(shown, View.VISIBLE)
        rv.setViewVisibility(hidden, View.GONE)
        rv.setTextViewText(R.id.month, Np.month(bs.month))
        rv.setTextViewText(R.id.ad, Np.ad(today).substringBeforeLast(' '))
        rv.setViewVisibility(R.id.holiday_dot, if (hols.isNotEmpty()) View.VISIBLE else View.GONE)

        if (withNext) fillNext(ctx, rv, today, zone)

        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TODAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        rv.setOnClickPendingIntent(
            android.R.id.background,
            PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        return rv
    }

    private fun fillNext(ctx: Context, rv: RemoteViews, today: LocalDate, zone: ZoneId) {
        val now = System.currentTimeMillis()
        val event = runCatching {
            CalendarRepo.instances(ctx, today, today.plusDays(8), zone).values.flatten()
                .filter { if (it.allDay) !Instant.ofEpochMilli(it.end).isBefore(Instant.ofEpochMilli(now)) else it.end > now }
                .distinctBy { it.eventId to it.begin }
                .minByOrNull { it.begin }
        }.getOrNull()
        val holiday = HolidayStore.next(today)
        val holidayLine = holiday?.let {
            val days = ChronoUnit.DAYS.between(today, it.ad)
            "${it.np} · ${Np.daysLeft(days)}"
        }

        if (event != null) {
            rv.setTextViewText(R.id.next_label, "NEXT EVENT")
            rv.setTextViewText(R.id.next_title, event.title)
            rv.setTextViewText(R.id.next_when, describe(event.begin, event.allDay, today, zone))
            if (holidayLine != null) {
                rv.setTextViewText(R.id.next2, holidayLine)
                rv.setViewVisibility(R.id.next2, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.next2, View.GONE)
            }
        } else if (holiday != null) {
            rv.setTextViewText(R.id.next_label, "NEXT HOLIDAY")
            rv.setTextViewText(R.id.next_title, holiday.np)
            rv.setTextViewText(R.id.next_when, "${Np.daysLeft(ChronoUnit.DAYS.between(today, holiday.ad))} · ${holiday.en}")
            rv.setViewVisibility(R.id.next2, View.GONE)
        } else {
            rv.setTextViewText(R.id.next_label, "NEXT")
            rv.setTextViewText(R.id.next_title, "Nothing coming up")
            rv.setTextViewText(R.id.next_when, "")
            rv.setViewVisibility(R.id.next2, View.GONE)
        }
    }

    private fun describe(begin: Long, allDay: Boolean, today: LocalDate, zone: ZoneId): String {
        val z = if (allDay) java.time.ZoneOffset.UTC else zone
        val t = Instant.ofEpochMilli(begin).atZone(z)
        val d = t.toLocalDate()
        val day = when (d) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> t.format(DOW)
        }
        return if (allDay) day else "$day ${t.format(HM)}"
    }

    /**
     * Inexact alarm a little after local midnight so the date rolls over. `updatePeriodMillis`
     * (30 min) is the backup; time/zone changes and opening the app also refresh.
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

class DateWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        Widgets.update(ctx, mgr, ids, withNext = false)
}

class DateNextWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        Widgets.update(ctx, mgr, ids, withNext = true)
}

/** Midnight alarm + time/zone/locale changes → redraw every widget. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Widgets.updateAll(ctx)
    }
}
