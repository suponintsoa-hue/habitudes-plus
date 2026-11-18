/*
 * Ajouts Miah — carte « Totaux » sur l'écran d'une habitude.
 * Durée : total en heures/minutes. Mesurable : somme des valeurs. Oui/Non : nombre de fois.
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getFirstWeekday
import org.isoron.platform.time.getToday
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.utils.sres
import java.text.DecimalFormat

class MiahTotalsCardView(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
    }

    fun setHabit(habit: Habit, color: Int) {
        removeAllViews()
        val stats = MiahStats.totals(habit)

        addView(
            TextView(context).apply {
                text = "Totaux"
                setTextColor(color)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, 0, 0, dp(8))
            }
        )

        addRow("Aujourd'hui", stats.today, habit)
        addRow("Cette semaine", stats.week, habit)
        addRow("Ce mois-ci", stats.month, habit)
        addRow("Cette année", stats.year, habit)
        addRow("Total", stats.allTime, habit, bold = true)
        addRow("Moyenne / jour (30 j)", stats.last30 / 30.0, habit)
    }

    private fun addRow(label: String, value: Double, habit: Habit, bold: Boolean = false) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val main = sres.getColor(R.attr.contrast100)
        val sub = sres.getColor(R.attr.contrast60)
        row.addView(
            TextView(context).apply {
                text = label
                setTextColor(sub)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            TextView(context).apply {
                text = MiahStats.formatValue(habit, value)
                setTextColor(main)
                gravity = Gravity.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                if (bold) setTypeface(typeface, Typeface.BOLD)
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
        addView(row)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

/** Calculs partagés par la carte Totaux et le bilan Telegram. */
object MiahStats {

    data class Totals(
        val today: Double,
        val week: Double,
        val month: Double,
        val year: Double,
        val allTime: Double,
        val last30: Double
    )

    /** Valeur « utile » d'une entrée : minutes / quantité, ou 1 pour une coche Oui. */
    fun usefulValue(habit: Habit, entry: Entry): Double {
        return if (habit.isNumerical) {
            if (entry.value <= 0 || entry.value == Entry.SKIP) 0.0 else entry.value / 1000.0
        } else {
            if (entry.value == Entry.YES_MANUAL) 1.0 else 0.0
        }
    }

    fun sumBetween(habit: Habit, from: LocalDate, to: LocalDate): Double {
        var sum = 0.0
        for (e in habit.originalEntries.getKnown()) {
            if (e.date.isOlderThan(from) || e.date.isNewerThan(to)) continue
            sum += usefulValue(habit, e)
        }
        return sum
    }

    fun totals(habit: Habit): Totals {
        val today = getToday()
        var t = 0.0
        var w = 0.0
        var m = 0.0
        var y = 0.0
        var all = 0.0
        var l30 = 0.0
        val weekStart = today.startOfWeek(getFirstWeekday())
        val monthStart = today.startOfMonth()
        val yearStart = today.startOfYear()
        val start30 = today.minus(29)
        for (e in habit.originalEntries.getKnown()) {
            if (e.date.isNewerThan(today)) continue
            val v = usefulValue(habit, e)
            if (v == 0.0) continue
            all += v
            if (e.date == today) t += v
            if (!e.date.isOlderThan(weekStart)) w += v
            if (!e.date.isOlderThan(monthStart)) m += v
            if (!e.date.isOlderThan(yearStart)) y += v
            if (!e.date.isOlderThan(start30)) l30 += v
        }
        return Totals(t, w, m, y, all, l30)
    }

    fun formatValue(habit: Habit, value: Double): String {
        return when {
            habit.isNumerical && MiahDuration.isDurationUnit(habit.unit) -> MiahDuration.format(value)
            habit.isNumerical -> {
                val n = DecimalFormat("#.##").format(value)
                if (habit.unit.isBlank()) n else "$n ${habit.unit}"
            }
            else -> {
                if (value == Math.floor(value)) {
                    "${value.toInt()} fois"
                } else {
                    "${DecimalFormat("#.#").format(value)} fois"
                }
            }
        }
    }
}
