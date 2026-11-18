/*
 * Ajouts Miah — planification (bilan Telegram, sauvegarde, fin de Pomodoro)
 * et récepteur des alarmes correspondantes.
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.isoron.platform.time.getFirstWeekday
import org.isoron.platform.time.getToday
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.utils.DatabaseUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MiahScheduler {
    const val ACTION_DAILY = "org.isoron.uhabits.miah.ACTION_DAILY"
    const val ACTION_POMODORO_END = "org.isoron.uhabits.miah.ACTION_POMODORO_END"
    private const val RC_DAILY = 7701
    private const val RC_POMO = 7702

    fun scheduleAll(context: Context) {
        try {
            scheduleDaily(context)
            MiahPomodoro.rescheduleIfRunning(context)
        } catch (e: Exception) {
            Log.e("MiahScheduler", "scheduleAll", e)
        }
    }

    private fun pending(context: Context, action: String, rc: Int): PendingIntent {
        val intent = Intent(context, MiahReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            rc,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun setExact(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun scheduleDaily(context: Context) {
        val pi = pending(context, ACTION_DAILY, RC_DAILY)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi)
        var hour = MiahPrefs.summaryHour(context)
        var minute = MiahPrefs.summaryMinute(context)
        val backup = MiahPrefs.backupEveryDays(context) > 0
        if (!MiahPrefs.isTelegramConfigured(context)) return
        if (hour < 0) {
            if (!backup) return
            hour = 21
            minute = 0
        }
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        setExact(context, cal.timeInMillis, pi)
    }

    fun schedulePomodoroEnd(context: Context, triggerAt: Long) {
        setExact(context, triggerAt, pending(context, ACTION_POMODORO_END, RC_POMO))
    }

    fun cancelPomodoroEnd(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context, ACTION_POMODORO_END, RC_POMO))
    }

    // ---------------------------------------------------------------------
    // Bilan et sauvegarde
    // ---------------------------------------------------------------------

    fun buildDailySummary(context: Context, includeWeek: Boolean): String {
        val component = (context.applicationContext as HabitsApplication).component
        val today = getToday()
        val dateStr = SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(Date())
        val sb = StringBuilder()
        sb.append("📊 Bilan du ").append(dateStr).append("\n\n")

        var done = 0
        var total = 0
        var minutesToday = 0.0
        val habits = ArrayList<Habit>()
        for (h in component.habitList) if (!h.isArchived) habits.add(h)

        for (h in habits) {
            total++
            val entry = h.computedEntries.get(today)
            val completed = entry.completed
            if (completed) done++
            if (h.isNumerical) {
                val v = MiahStats.usefulValue(h, h.originalEntries.get(today))
                if (MiahDuration.isDurationUnit(h.unit)) minutesToday += v
                val target = MiahStats.formatValue(h, h.targetValue)
                sb.append(if (completed) "✅ " else "⏳ ")
                    .append(h.name).append(" : ")
                    .append(MiahStats.formatValue(h, v))
                    .append(" / ").append(target).append("\n")
            } else {
                val icon = when {
                    completed -> "✅ "
                    entry.value == Entry.SKIP -> "⏭ "
                    else -> "❌ "
                }
                sb.append(icon).append(h.name).append("\n")
            }
            val notes = h.originalEntries.get(today).notes
            if (notes.isNotBlank()) {
                sb.append("   📝 ").append(notes.trim()).append("\n")
            }
        }
        sb.append("\nRéussite : ").append(done).append("/").append(total)
        if (minutesToday > 0) {
            sb.append("\n⏱ Temps total aujourd'hui : ").append(MiahDuration.format(minutesToday))
        }

        if (includeWeek) {
            val weekStart = today.startOfWeek(getFirstWeekday())
            sb.append("\n\n📅 Bilan de la semaine\n")
            var weekMinutes = 0.0
            for (h in habits) {
                val sum = MiahStats.sumBetween(h, weekStart, today)
                if (h.isNumerical && MiahDuration.isDurationUnit(h.unit)) weekMinutes += sum
                sb.append("• ").append(h.name).append(" : ")
                    .append(MiahStats.formatValue(h, sum)).append("\n")
            }
            if (weekMinutes > 0) {
                sb.append("⏱ Temps total de la semaine : ").append(MiahDuration.format(weekMinutes))
            }
        }
        return sb.toString().trim()
    }

    /** Vrai si aujourd'hui est le dernier jour de la semaine (selon le réglage de Loop). */
    fun isLastDayOfWeek(): Boolean {
        val today = getToday()
        val tomorrow = today.plus(1)
        return tomorrow.startOfWeek(getFirstWeekday()) == tomorrow
    }

    fun makeBackupFile(context: Context): File {
        val dir = File(context.cacheDir, "miah_backup")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val date = SimpleDateFormat("yyyy-MM-dd_HH'h'mm", Locale.ROOT).format(Date())
        val out = File(dir, "Habitudes-sauvegarde-$date.db")
        DatabaseUtils.getDatabaseFile(context).copyTo(out, overwrite = true)
        return out
    }

    fun sendBackup(context: Context, onDone: ((MiahTelegram.Result) -> Unit)? = null) {
        try {
            val file = makeBackupFile(context)
            MiahTelegram.sendDocumentAsync(
                context,
                file,
                "💾 Sauvegarde Habitudes — à importer via Réglages › Importer des données"
            ) { r ->
                if (r.ok) MiahPrefs.setLastBackup(context, System.currentTimeMillis())
                onDone?.invoke(r)
            }
        } catch (e: Exception) {
            Log.e("MiahScheduler", "backup", e)
            onDone?.invoke(MiahTelegram.Result(false, e.message ?: e.toString()))
        }
    }

    fun onDailyAlarm(context: Context) {
        try {
            if (!MiahPrefs.isTelegramConfigured(context)) return
            if (MiahPrefs.summaryHour(context) >= 0) {
                val weekly = MiahPrefs.weeklySummary(context) && isLastDayOfWeek()
                MiahTelegram.sendMessageAsync(context, buildDailySummary(context, weekly))
            }
            val every = MiahPrefs.backupEveryDays(context)
            if (every > 0) {
                val elapsed = System.currentTimeMillis() - MiahPrefs.lastBackup(context)
                val needed = every * 24L * 3600L * 1000L - 2L * 3600L * 1000L
                if (elapsed >= needed) sendBackup(context)
            }
        } finally {
            scheduleDaily(context)
        }
    }
}

class MiahReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                MiahScheduler.ACTION_DAILY -> MiahScheduler.onDailyAlarm(context)
                MiahScheduler.ACTION_POMODORO_END -> MiahPomodoro.onPhaseEnd(context)
            }
        } catch (e: Exception) {
            Log.e("MiahReceiver", "onReceive", e)
        }
    }
}
