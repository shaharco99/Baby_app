package com.oryareach.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.oryareach.app.MainActivity
import com.oryareach.app.R
import com.oryareach.core.ui.text.asLtrIsolate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The home-screen widget: how long until the next feed, and when the last one was.
 *
 * Reads nothing from the database. The widget is drawn by the launcher at any moment — after a
 * reboot, with the app locked, with the workspace key sealed away — so it works from two
 * timestamps kept in plain preferences by [FeedWidgetStore], written by the same code path that
 * arms the feed reminder. Only times are stored: no child, no amount, nothing from a record.
 *
 * The clock on it is a [android.widget.Chronometer], which the launcher ticks itself, so the
 * widget is not re-drawn every second. It is re-drawn when the feed moves (a feed logged here or
 * pulled from the partner's phone), when the reminder rings — which is the moment "next feed
 * in" has to become "overdue by" — and after a reboot.
 */
class FeedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, FeedWidget.render(context)) }
    }
}

object FeedWidget {

    /** Re-draws every placed copy of the widget from the stored times. Cheap; safe to over-call. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, FeedWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = render(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    internal fun render(context: Context, now: Long = System.currentTimeMillis()): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_feed)
        views.setOnClickPendingIntent(R.id.widget_feed_root, openApp(context))

        val lastFedAt = FeedWidgetStore.lastFedAt(context)
        val dueAt = FeedWidgetStore.dueAt(context)
        if (lastFedAt == null || dueAt == null) {
            views.setTextViewText(R.id.widget_feed_label, context.getString(R.string.widget_feed_empty))
            views.setViewVisibility(R.id.widget_feed_timer, View.GONE)
            views.setViewVisibility(R.id.widget_feed_last, View.GONE)
            return views
        }

        val overdue = dueAt <= now
        views.setTextViewText(
            R.id.widget_feed_label,
            context.getString(if (overdue) R.string.widget_feed_overdue else R.string.widget_feed_next),
        )
        // The chronometer's base is on the elapsed-realtime clock, so the wall-clock distance to
        // the due time is carried over onto it. Counting down to the due time, or up from it.
        val base = SystemClock.elapsedRealtime() + (dueAt - now)
        views.setChronometer(R.id.widget_feed_timer, base, null, true)
        views.setChronometerCountDown(R.id.widget_feed_timer, !overdue)
        views.setTextColor(
            R.id.widget_feed_timer,
            context.getColor(if (overdue) R.color.widget_error else R.color.widget_foreground),
        )
        views.setViewVisibility(R.id.widget_feed_timer, View.VISIBLE)

        views.setTextViewText(
            R.id.widget_feed_last,
            context.getString(R.string.widget_feed_last, clock(lastFedAt).asLtrIsolate()),
        )
        views.setViewVisibility(R.id.widget_feed_last, View.VISIBLE)
        return views
    }

    private fun clock(epochMillis: Long): String {
        val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val REQUEST_OPEN_APP = 40
}

/**
 * The widget's two timestamps. Separate from [com.oryareach.app.notifications.ReminderAlarms]'
 * own preferences because those drop the due time the moment the alarm rings, and the widget
 * needs it most after that — to say how late the feed is.
 */
object FeedWidgetStore {
    private const val PREFS = "feed-widget"
    private const val KEY_LAST_FED_AT = "last-fed-at"
    private const val KEY_DUE_AT = "due-at"

    fun save(context: Context, lastFedAt: Long, dueAt: Long) {
        prefs(context).edit().putLong(KEY_LAST_FED_AT, lastFedAt).putLong(KEY_DUE_AT, dueAt).apply()
        FeedWidget.refresh(context)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        FeedWidget.refresh(context)
    }

    fun lastFedAt(context: Context): Long? = prefs(context).getLong(KEY_LAST_FED_AT, 0L).takeIf { it > 0L }

    fun dueAt(context: Context): Long? = prefs(context).getLong(KEY_DUE_AT, 0L).takeIf { it > 0L }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
