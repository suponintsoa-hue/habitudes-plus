/*
 * Ajouts Miah — écran Pomodoro.
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.core.models.Habit
import java.util.Locale

class MiahPomodoroActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private lateinit var startBtn: Button
    private lateinit var pauseBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var skipBtn: Button
    private lateinit var workInput: EditText
    private lateinit var shortInput: EditText
    private lateinit var longInput: EditText
    private lateinit var cyclesInput: EditText
    private lateinit var settingsBox: LinearLayout
    private val habits = ArrayList<Habit?>()

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Pomodoro"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val bg = Color.parseColor("#1B1B1F")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        statusText = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }
        timerText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(statusText)
        root.addView(timerText)

        startBtn = button("▶  Démarrer", "#C62828") { onStartClicked() }
        pauseBtn = button("⏸  Pause", "#455A64") { onPauseResumeClicked() }
        stopBtn = button("■  Arrêter", "#37474F") { onStopClicked() }
        skipBtn = button("⏭  Passer à l'étape suivante", "#37474F") {
            MiahPomodoro.onPhaseEnd(this)
            refresh()
        }
        root.addView(startBtn)
        root.addView(pauseBtn)
        root.addView(stopBtn)
        root.addView(skipBtn)

        // ---- Réglages ----
        settingsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, 0)
        }
        settingsBox.addView(label("Habitude à remplir (minutes ajoutées automatiquement)"))
        val spinner = Spinner(this)
        val component = (applicationContext as HabitsApplication).component
        val names = ArrayList<String>()
        habits.add(null)
        names.add("Aucune")
        // D'abord les habitudes en durée, puis les autres habitudes mesurables.
        val all = component.habitList.filter { !it.isArchived && it.isNumerical }
        val sorted = all.filter { MiahDuration.isDurationUnit(it.unit) } +
            all.filter { !MiahDuration.isDurationUnit(it.unit) }
        for (h in sorted) {
            habits.add(h)
            names.add(if (MiahDuration.isDurationUnit(h.unit)) "⏱ ${h.name}" else "${h.name} (${h.unit})")
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.adapter = adapter
        val current = MiahPrefs.pomoHabitUuid(this)
        val idx = habits.indexOfFirst { it?.uuid == current && current != null }
        spinner.setSelection(if (idx >= 0) idx else 0)
        spinner.setBackgroundColor(Color.parseColor("#2C2C33"))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(Color.WHITE)
                MiahPrefs.setPomoHabitUuid(this@MiahPomodoroActivity, habits[position]?.uuid)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        settingsBox.addView(spinner)

        workInput = numberField(MiahPrefs.pomoWork(this))
        shortInput = numberField(MiahPrefs.pomoShort(this))
        longInput = numberField(MiahPrefs.pomoLong(this))
        cyclesInput = numberField(MiahPrefs.pomoCycles(this))
        settingsBox.addView(label("Travail (minutes)"))
        settingsBox.addView(workInput)
        settingsBox.addView(label("Pause courte (minutes)"))
        settingsBox.addView(shortInput)
        settingsBox.addView(label("Grande pause (minutes)"))
        settingsBox.addView(longInput)
        settingsBox.addView(label("Nombre de sessions avant une grande pause"))
        settingsBox.addView(cyclesInput)
        root.addView(settingsBox)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        saveDurations()
        super.onPause()
    }

    private fun saveDurations() {
        fun v(e: EditText, def: Int, minV: Int) =
            (e.text.toString().toIntOrNull() ?: def).coerceIn(minV, 600)
        MiahPrefs.setPomoDurations(
            this,
            v(workInput, 25, 1),
            v(shortInput, 5, 1),
            v(longInput, 15, 1),
            v(cyclesInput, 4, 1)
        )
    }

    private fun onStartClicked() {
        saveDurations()
        MiahPomodoro.startWork(this)
        refresh()
    }

    private fun onPauseResumeClicked() {
        if (MiahPomodoro.isPaused(this)) MiahPomodoro.resume(this) else MiahPomodoro.pause(this)
        refresh()
    }

    private fun onStopClicked() {
        val elapsed = MiahPomodoro.elapsedWorkMinutes(this)
        if (elapsed > 0 && MiahPrefs.pomoHabitUuid(this) != null) {
            AlertDialog.Builder(this)
                .setTitle("Arrêter le Pomodoro")
                .setMessage("Ajouter les $elapsed min déjà faites à ton habitude ?")
                .setPositiveButton("Oui, ajouter") { _, _ ->
                    MiahPomodoro.stop(this, true)
                    refresh()
                }
                .setNegativeButton("Non") { _, _ ->
                    MiahPomodoro.stop(this, false)
                    refresh()
                }
                .show()
        } else {
            MiahPomodoro.stop(this, false)
            refresh()
        }
    }

    private fun refresh() {
        val phase = MiahPomodoro.phase(this)
        val paused = MiahPomodoro.isPaused(this)
        val running = phase == MiahPomodoro.WORK || phase == MiahPomodoro.SHORT || phase == MiahPomodoro.LONG
        val count = MiahPomodoro.completedSessions(this)
        val cycles = MiahPrefs.pomoCycles(this)

        val ms = if (running) MiahPomodoro.remainingMs(this) else MiahPrefs.pomoWork(this) * 60_000L
        val totalSec = (ms + 999) / 1000
        timerText.text = String.format(Locale.ROOT, "%02d:%02d", totalSec / 60, totalSec % 60)

        val status = when (phase) {
            MiahPomodoro.WORK -> "🍅 Concentration — session ${count + 1}"
            MiahPomodoro.SHORT -> "☕ Pause courte"
            MiahPomodoro.LONG -> "🌿 Grande pause"
            MiahPomodoro.READY -> "Pause terminée — prête ? ($count/$cycles sessions)"
            else -> "Prête à commencer"
        }
        statusText.text = if (paused) "$status (en pause)" else status

        startBtn.visibility = if (running) View.GONE else View.VISIBLE
        startBtn.text = if (phase == MiahPomodoro.READY) "▶  Session suivante" else "▶  Démarrer"
        pauseBtn.visibility = if (running) View.VISIBLE else View.GONE
        pauseBtn.text = if (paused) "▶  Reprendre" else "⏸  Pause"
        stopBtn.visibility = if (phase == MiahPomodoro.IDLE) View.GONE else View.VISIBLE
        skipBtn.visibility = if (running) View.VISIBLE else View.GONE
        settingsBox.visibility = if (running) View.GONE else View.VISIBLE
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.LTGRAY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun numberField(value: Int) = EditText(this).apply {
        setText(value.toString())
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    }

    private fun button(text: String, color: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setBackgroundColor(Color.parseColor(color))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply { setMargins(0, dp(6), 0, dp(6)) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
