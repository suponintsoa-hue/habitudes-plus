/*
 * Ajouts Miah — habitudes en durée (heures / minutes).
 * Une habitude « mesurable » dont l'unité est « min » est traitée comme une durée :
 * les valeurs sont stockées en minutes et affichées en « 1h25 ».
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import java.util.Locale
import kotlin.math.roundToLong

object MiahDuration {
    const val DURATION_UNIT = "min"

    private val units = setOf("min", "mn", "minute", "minutes", "durée", "duree")

    fun isDurationUnit(unit: String?): Boolean =
        unit != null && units.contains(unit.trim().lowercase(Locale.ROOT))

    /** 85.0 -> "1h25", 45.0 -> "45 min", 120.0 -> "2h" */
    fun format(minutes: Double): String {
        if (minutes <= 0.0) return "0 min"
        val total = minutes.roundToLong()
        val h = total / 60
        val m = total % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "${h}h"
            else -> String.format(Locale.ROOT, "%dh%02d", h, m)
        }
    }

    /** Format compact pour le bouton de la liste : "1h25", "45m", "12h". */
    fun formatShort(minutes: Double): String {
        if (minutes <= 0.0) return "0"
        val total = minutes.roundToLong()
        val h = total / 60
        val m = total % 60
        return when {
            h == 0L -> "${m}m"
            h >= 100 -> "${h}h"
            m == 0L -> "${h}h"
            else -> String.format(Locale.ROOT, "%dh%02d", h, m)
        }
    }

    /**
     * Interprète une saisie de durée.
     * "1:30", "1h30", "1h", "1 h 30" -> minutes. Renvoie null si ce n'est pas une durée
     * (dans ce cas la saisie est traitée comme un nombre normal).
     */
    fun parse(input: String): Double? {
        val s = input.trim().lowercase(Locale.ROOT).replace(" ", "")
        if (s.isEmpty()) return null
        val sep = when {
            s.contains(':') -> ':'
            s.contains('h') -> 'h'
            else -> return null
        }
        val parts = s.split(sep)
        if (parts.size != 2) return null
        val h = parts[0].replace(',', '.').toDoubleOrNull() ?: 0.0
        val mStr = parts[1].removeSuffix("min").removeSuffix("mn").removeSuffix("m")
        val m = if (mStr.isEmpty()) 0.0 else (mStr.replace(',', '.').toDoubleOrNull() ?: return null)
        if (h < 0 || m < 0) return null
        return h * 60.0 + m
    }
}
