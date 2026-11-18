/*
 * Ajouts Miah — réglages des nouvelles fonctions (alarme, Telegram, Pomodoro).
 * Stockés à part, sans toucher à la base de données de Loop.
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import android.content.Context
import android.content.SharedPreferences

object MiahPrefs {
    private const val FILE = "miah_extras"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Par habitude (clé = uuid de l'habitude) ----

    fun isAlarm(context: Context, uuid: String?): Boolean =
        uuid != null && prefs(context).getBoolean("alarm_$uuid", false)

    fun setAlarm(context: Context, uuid: String?, value: Boolean) {
        if (uuid == null) return
        prefs(context).edit().putBoolean("alarm_$uuid", value).apply()
    }

    fun isTelegramReminder(context: Context, uuid: String?): Boolean =
        uuid != null && prefs(context).getBoolean("tg_$uuid", false)

    fun setTelegramReminder(context: Context, uuid: String?, value: Boolean) {
        if (uuid == null) return
        prefs(context).edit().putBoolean("tg_$uuid", value).apply()
    }

    // ---- Telegram ----

    fun botToken(context: Context): String = prefs(context).getString("tg_token", "")!!.trim()
    fun chatId(context: Context): String = prefs(context).getString("tg_chat", "")!!.trim()
    fun isTelegramConfigured(context: Context) =
        botToken(context).isNotEmpty() && chatId(context).isNotEmpty()

    fun setTelegram(context: Context, token: String, chat: String) {
        prefs(context).edit()
            .putString("tg_token", token.trim())
            .putString("tg_chat", chat.trim())
            .apply()
    }

    /** Heure du bilan quotidien (-1 = désactivé). */
    fun summaryHour(context: Context): Int = prefs(context).getInt("summary_hour", -1)
    fun summaryMinute(context: Context): Int = prefs(context).getInt("summary_min", 0)
    fun setSummaryTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt("summary_hour", hour).putInt("summary_min", minute).apply()
    }

    /** Bilan hebdomadaire envoyé le dernier jour de la semaine. */
    fun weeklySummary(context: Context): Boolean = prefs(context).getBoolean("summary_week", true)
    fun setWeeklySummary(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean("summary_week", v).apply()

    /** Sauvegarde : 0 = jamais, 1 = chaque jour, 7 = chaque semaine. */
    fun backupEveryDays(context: Context): Int = prefs(context).getInt("backup_days", 7)
    fun setBackupEveryDays(context: Context, v: Int) =
        prefs(context).edit().putInt("backup_days", v).apply()

    fun lastBackup(context: Context): Long = prefs(context).getLong("backup_last", 0L)
    fun setLastBackup(context: Context, v: Long) =
        prefs(context).edit().putLong("backup_last", v).apply()

    // ---- Pomodoro ----

    fun pomoWork(context: Context): Int = prefs(context).getInt("pomo_work", 25)
    fun pomoShort(context: Context): Int = prefs(context).getInt("pomo_short", 5)
    fun pomoLong(context: Context): Int = prefs(context).getInt("pomo_long", 15)
    fun pomoCycles(context: Context): Int = prefs(context).getInt("pomo_cycles", 4)

    fun setPomoDurations(context: Context, work: Int, short: Int, long: Int, cycles: Int) {
        prefs(context).edit()
            .putInt("pomo_work", work)
            .putInt("pomo_short", short)
            .putInt("pomo_long", long)
            .putInt("pomo_cycles", cycles)
            .apply()
    }

    fun pomoHabitUuid(context: Context): String? = prefs(context).getString("pomo_habit", null)
    fun setPomoHabitUuid(context: Context, uuid: String?) =
        prefs(context).edit().putString("pomo_habit", uuid).apply()
}
