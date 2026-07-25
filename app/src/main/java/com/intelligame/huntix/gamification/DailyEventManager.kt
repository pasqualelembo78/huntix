package com.intelligame.huntix.gamification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.intelligame.huntix.HomeActivity
import com.intelligame.huntix.R
import java.util.Calendar

/**
 * DailyEventManager — manages daily event timing and notifications.
 *
 * Uses AlarmManager to schedule notifications at:
 * - 5 minutes before event start (reminder)
 * - Event start time (go!)
 *
 * Priority: monthly events (ElfHunt, Tombolata) override daily events.
 * Daily events only show when no monthly event is active.
 */
object DailyEventManager {

    private const val CHANNEL_ID = "huntix_daily_events"
    private const val REMINDER_REQUEST_CODE = 9000
    private const val START_REQUEST_CODE = 9100

    // ── State ──────────────────────────────────────────────

    fun isDailyEventActive(): Boolean {
        val event = DailyEventRegistry.getTodayEvent() ?: return false
        return isWithinEventWindow(event)
    }

    fun isWithinEventWindow(event: DailyEvent): Boolean {
        val cal = Calendar.getInstance()
        val nowHour = cal.get(Calendar.HOUR_OF_DAY)
        val nowMinute = cal.get(Calendar.MINUTE)
        val nowTotal = nowHour * 60 + nowMinute
        val startTotal = event.startHour * 60 + event.startMinute
        val endTotal = event.endHour * 60 + event.endMinute
        return nowTotal in startTotal until endTotal
    }

    fun minutesUntilEvent(): Int {
        val event = DailyEventRegistry.getTodayEvent() ?: return -1
        val cal = Calendar.getInstance()
        val nowTotal = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startTotal = event.startHour * 60 + event.startMinute
        return startTotal - nowTotal
    }

    fun minutesLeftInEvent(): Int {
        val event = DailyEventRegistry.getTodayEvent() ?: return -1
        val cal = Calendar.getInstance()
        val nowTotal = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val endTotal = event.endHour * 60 + event.endMinute
        return endTotal - nowTotal
    }

    fun timeUntilEventFormatted(): String {
        val mins = minutesUntilEvent()
        if (mins < 0) return ""
        val h = mins / 60
        val m = mins % 60
        return when {
            h > 0 -> "${h}h ${m}min"
            m > 0 -> "${m}min"
            else -> "Inizia ora!"
        }
    }

    /**
     * Should we show the daily event banner in Home?
     * Only if:
     * 1. There's a daily event today
     * 2. We're within 30 min before or during the event
     * 3. No monthly event is active
     */
    fun shouldShowDailyEventBanner(): Boolean {
        if (SpecialEventManager.hasActiveEvent()) return false
        val event = DailyEventRegistry.getTodayEvent() ?: return false
        val minsUntil = minutesUntilEvent()
        return minsUntil in -60..30 || isWithinEventWindow(event)
    }

    // ── Notifications ──────────────────────────────────────

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Eventi Giornalieri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifiche per gli eventi giornalieri di Huntix"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun scheduleDailyNotifications(context: Context) {
        createNotificationChannel(context)
        val event = DailyEventRegistry.getTodayEvent() ?: return

        scheduleReminderNotification(context, event)
        scheduleStartNotification(context, event)
    }

    fun cancelAllNotifications(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderIntent = Intent(context, DailyEventReceiver::class.java).apply {
            action = "REMINDER"
        }
        val startIntent = Intent(context, DailyEventReceiver::class.java).apply {
            action = "START"
        }
        am.cancel(PendingIntent.getBroadcast(context, REMINDER_REQUEST_CODE, reminderIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        am.cancel(PendingIntent.getBroadcast(context, START_REQUEST_CODE, startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    private fun scheduleReminderNotification(context: Context, event: DailyEvent) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, event.startHour)
            set(Calendar.MINUTE, event.startMinute)
            add(Calendar.MINUTE, -5) // 5 min before
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (cal.timeInMillis <= System.currentTimeMillis()) return // already past

        val intent = Intent(context, DailyEventReceiver::class.java).apply {
            action = "REMINDER"
            putExtra("title", event.title)
            putExtra("emoji", event.emoji)
        }
        val pending = PendingIntent.getBroadcast(context, REMINDER_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
    }

    private fun scheduleStartNotification(context: Context, event: DailyEvent) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, event.startHour)
            set(Calendar.MINUTE, event.startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (cal.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, DailyEventReceiver::class.java).apply {
            action = "START"
            putExtra("title", event.title)
            putExtra("emoji", event.emoji)
        }
        val pending = PendingIntent.getBroadcast(context, START_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
    }

    fun showNotification(context: Context, title: String, body: String, id: Int) {
        createNotificationChannel(context)

        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }
}

/**
 * BroadcastReceiver to handle scheduled notifications.
 */
class DailyEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Huntix"
        val emoji = intent.getStringExtra("emoji") ?: "\uD83C\uDFAE"

        when (intent.action) {
            "REMINDER" -> {
                DailyEventManager.showNotification(
                    context,
                    "$emoji Tra 5 minuti inizia $title!",
                    "Preparati, l'evento sta per iniziare!",
                    REMINDER_NOTIF_ID
                )
                DailyEventManager.scheduleDailyNotifications(context)
            }
            "START" -> {
                DailyEventManager.showNotification(
                    context,
                    "$emoji $title è INIZIATO!",
                    "Gioca ora! L'evento dura 60 minuti.",
                    START_NOTIF_ID
                )
            }
        }
    }

    companion object {
        private const val REMINDER_NOTIF_ID = 9500
        private const val START_NOTIF_ID = 9600
    }
}
