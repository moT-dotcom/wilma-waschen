package de.kinolicht.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import de.kinolicht.core.AppConfig
import de.kinolicht.core.Scheduler
import java.time.ZonedDateTime

/** Plant für jede Regel den nächsten Termin über den AlarmManager. */
object AlarmScheduler {
    private const val PREFS = "alarms"
    private const val KEY_IDS = "ids"

    @Synchronized
    fun rescheduleAll(context: Context, config: AppConfig) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().forEach { id ->
            alarmManager.cancel(pendingIntent(context, id))
        }
        val now = ZonedDateTime.now()
        val scheduled = mutableSetOf<String>()
        for (rule in config.rules) {
            val next = Scheduler.nextRun(rule, now, config.location) ?: continue
            val at = next.toInstant().toEpochMilli()
            val pi = pendingIntent(context, rule.id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            scheduled += rule.id
        }
        prefs.edit().putStringSet(KEY_IDS, scheduled).apply()
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    private fun pendingIntent(context: Context, ruleId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(LightService.ACTION_RULE)
            .setData(Uri.parse("kinolicht://rule/$ruleId"))
            .putExtra(LightService.EXTRA_ID, ruleId)
        return PendingIntent.getBroadcast(
            context, ruleId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(LightService.EXTRA_ID) ?: return
        LightService.start(context, LightService.ACTION_RULE) { putExtra(LightService.EXTRA_ID, id) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = KinoLichtApp.from(context)
        AlarmScheduler.rescheduleAll(context, app.store.config.value)
        // Nach dem Hochfahren ist der Beamer an: LEDs wie beim Einschalten behandeln.
        val action = if (intent.action == Intent.ACTION_BOOT_COMPLETED) LightService.ACTION_BOOT else null
        LightService.start(context, action)
    }
}
