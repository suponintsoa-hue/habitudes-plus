/*
 * Ajouts Miah — point d'entrée appelé quand Loop affiche un rappel.
 * Déclenche l'alarme plein écran et/ou l'envoi Telegram selon les réglages de l'habitude.
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import android.content.Context
import android.util.Log
import org.isoron.uhabits.core.models.Habit

object MiahHooks {
    private const val TAG = "MiahHooks"

    fun onReminderShown(context: Context, habit: Habit, reminderTime: Long) {
        try {
            val prefs = MiahPrefs.prefs(context)
            val key = "last_fired_${habit.uuid}"
            // Évite de re-sonner quand Loop ré-affiche la même notification (mode « collant »).
            if (prefs.getLong(key, -1L) == reminderTime) return
            prefs.edit().putLong(key, reminderTime).apply()

            if (MiahPrefs.isAlarm(context, habit.uuid)) {
                MiahAlarm.show(context, habit)
            }
            if (MiahPrefs.isTelegramReminder(context, habit.uuid) &&
                MiahPrefs.isTelegramConfigured(context)
            ) {
                val question = habit.question.ifBlank { "Pense à ton habitude du jour !" }
                MiahTelegram.sendMessageAsync(
                    context,
                    "⏰ Rappel : ${habit.name}\n$question"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "onReminderShown failed", e)
        }
    }
}
