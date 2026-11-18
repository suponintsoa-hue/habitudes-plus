/*
 * Ajouts Miah — logique du Pomodoro.
 * Chaque session de travail terminée ajoute ses minutes à l'habitude choisie.
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.util.Log
import androidx.core.app.NotificationCompat
import org.isoron.platform.time.getToday
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import kotlin.math.max

object MiahPomodoro {
    const val IDLE = "idle"
    const val WORK = "work"
    const val SHORT = "short"
    const val LONG = "long"
    const val READY = "ready" // pause terminée, en attente du prochain lancement

    private const val CHANNEL_ID = "MIAH_POMODORO"
    private const val NOTIF_ID = 0x4D5000

    private fun p(context: Context) = MiahPrefs.prefs(context)

    fun phase(context: Context): String = p(context).getString("pomo_phase", IDLE)!!
    fun endAt(context: Context): Long = p(context).getLong("pomo_end", 0L)
    fun isPaused(context: Context): Boolean = p(context).getBoolean("pomo_paused", false)
    fun pausedRemaining(context: Context): Long = p(context).getLong("pomo_remaining", 0L)
    fun completedSessions(context: Context): Int = p(context).getInt("pomo_count", 0)
    fun phaseLengthMin(context: Context): Int = p(context).getInt("pomo_len", 0)

    fun remainingMs(context: Context): Long = when {
        isPaused(context) -> pausedRemaining(context)
        phase(context) == WORK || phase(context) == SHORT || phase(context) == LONG ->
            max(0L, endAt(context) - System.currentTimeMillis())
        else -> 0L
    }

    private fun startPhase(context: Context, phase: String, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        p(context).edit()
            .putString("pomo_phase", phase)
            .putLong("pomo_end", end)
            .putInt("pomo_len", minutes)
            .putBoolean("pomo_paused", false)
            .apply()
        MiahScheduler.schedulePomodoroEnd(context, end)
    }

    fun startWork(context: Context) {
        startPhase(context, WORK, MiahPrefs.pomoWork(context))
        notify(context, "🍅 Concentration", "${MiahPrefs.pomoWork(context)} min — c'est parti !", sound = false)
    }

    fun pause(context: Context) {
        if (isPaused(context)) return
        val remaining = remainingMs(context)
        MiahScheduler.cancelPomodoroEnd(context)
        p(context).edit().putBoolean("pomo_paused", true).putLong("pomo_remaining", remaining).apply()
    }

    fun resume(context: Context) {
        if (!isPaused(context)) return
        val end = System.currentTimeMillis() + pausedRemaining(context)
        p(context).edit().putBoolean("pomo_paused", false).putLong("pomo_end", end).apply()
        MiahScheduler.schedulePomodoroEnd(context, end)
    }

    /** Minutes déjà faites dans la session de travail en cours. */
    fun elapsedWorkMinutes(context: Context): Int {
        if (phase(context) != WORK) return 0
        val total = phaseLengthMin(context) * 60_000L
        return ((total - remainingMs(context)) / 60_000L).toInt()
    }

    fun stop(context: Context, addElapsed: Boolean) {
        if (addElapsed) {
            val m = elapsedWorkMinutes(context)
            if (m > 0) addMinutesToHabit(context, m)
        }
        MiahScheduler.cancelPomodoroEnd(context)
        p(context).edit()
            .putString("pomo_phase", IDLE)
            .putBoolean("pomo_paused", false)
            .putInt("pomo_count", 0)
            .apply()
        cancelNotification(context)
    }

    fun rescheduleIfRunning(context: Context) {
        val ph = phase(context)
        if ((ph == WORK || ph == SHORT || ph == LONG) && !isPaused(context)) {
            val end = endAt(context)
            if (end > System.currentTimeMillis()) {
                MiahScheduler.schedulePomodoroEnd(context, end)
            } else {
                onPhaseEnd(context)
            }
        }
    }

    fun onPhaseEnd(context: Context) {
        when (phase(context)) {
            WORK -> {
                val minutes = phaseLengthMin(context)
                val added = addMinutesToHabit(context, minutes)
                val count = completedSessions(context) + 1
                p(context).edit().putInt("pomo_count", count).apply()
                val cycles = max(1, MiahPrefs.pomoCycles(context))
                val isLong = count % cycles == 0
                val breakMin = if (isLong) MiahPrefs.pomoLong(context) else MiahPrefs.pomoShort(context)
                startPhase(context, if (isLong) LONG else SHORT, breakMin)
                val habitText = if (added != null) "\n+$minutes min ajoutées à « $added »" else ""
                notify(
                    context,
                    "✅ Pomodoro n°$count terminé !",
                    (if (isLong) "Grande pause" else "Pause") + " de $breakMin min.$habitText",
                    sound = true
                )
            }
            SHORT, LONG -> {
                p(context).edit().putString("pomo_phase", READY).apply()
                notify(
                    context,
                    "☕ Pause terminée",
                    "Ouvre le Pomodoro pour lancer la session suivante.",
                    sound = true
                )
            }
        }
    }

    private fun findHabit(context: Context): Habit? {
        val uuid = MiahPrefs.pomoHabitUuid(context) ?: return null
        val component = (context.applicationContext as HabitsApplication).component
        return component.habitList.getByUUID(uuid)
    }

    /** Ajoute des minutes à la valeur du jour. Renvoie le nom de l'habitude, ou null. */
    fun addMinutesToHabit(context: Context, minutes: Int): String? {
        return try {
            val habit = findHabit(context) ?: return null
            if (!habit.isNumerical) return null
            val component = (context.applicationContext as HabitsApplication).component
            val today = getToday()
            val entry = habit.originalEntries.get(today)
            val current = if (entry.value > 0 && entry.value != Entry.SKIP) entry.value else 0
            val newValue = current + minutes * 1000
            component.commandRunner.run(
                CreateRepetitionCommand(component.habitList, habit, today, newValue, entry.notes)
            )
            habit.name
        } catch (e: Exception) {
            Log.e("MiahPomodoro", "addMinutes", e)
            null
        }
    }

    private fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "Pomodoro", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(
            MiahAlarm.alarmUri(),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)
    }

    private fun notify(context: Context, title: String, text: String, sound: Boolean) {
        try {
            createChannel(context)
            val open = PendingIntent.getActivity(
                context,
                NOTIF_ID,
                Intent(context, MiahPomodoroActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(!sound)
                .setSilent(!sound)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, n)
        } catch (e: SecurityException) {
            Log.e("MiahPomodoro", "notify", e)
        }
    }

    private fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
    }
}
