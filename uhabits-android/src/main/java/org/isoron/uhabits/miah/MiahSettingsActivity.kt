/*
 * Ajouts Miah — réglages Telegram, bilan, sauvegarde et alarmes.
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import android.app.Activity
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.isoron.uhabits.HabitsApplication
import java.util.Locale

class MiahSettingsActivity : Activity() {

    private lateinit var tokenInput: EditText
    private lateinit var chatInput: EditText
    private lateinit var summaryCheck: CheckBox
    private lateinit var summaryTimeBtn: Button
    private lateinit var permissionsText: TextView
    private val bg = Color.parseColor("#1B1B1F")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Telegram & alarmes"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
            setBackgroundColor(bg)
        }

        // ---------------- Telegram ----------------
        root.addView(header("Telegram"))
        root.addView(
            text(
                "1. Dans Telegram, ouvre @BotFather, envoie /newbot et suis les étapes.\n" +
                    "2. Copie le « token » qu'il te donne et colle-le ci-dessous.\n" +
                    "3. Ouvre ton nouveau bot et envoie-lui « bonjour ».\n" +
                    "4. Appuie sur « Trouver mon chat ID »."
            )
        )
        tokenInput = field("Token du bot (ex. 123456:ABC-...)", MiahPrefs.botToken(this))
        chatInput = field("Chat ID", MiahPrefs.chatId(this))
        chatInput.inputType = InputType.TYPE_CLASS_TEXT
        root.addView(tokenInput)
        root.addView(
            button("Trouver mon chat ID") {
                val token = tokenInput.text.toString().trim()
                if (token.isEmpty()) {
                    toast("Colle d'abord le token du bot")
                    return@button
                }
                toast("Recherche…")
                MiahTelegram.findChatIdAsync(token) { r ->
                    if (r.ok) {
                        val parts = r.message.split("|")
                        chatInput.setText(parts[0])
                        save()
                        toast("Trouvé : ${parts.getOrElse(1) { parts[0] }}")
                    } else {
                        toast(r.message)
                    }
                }
            }
        )
        root.addView(chatInput)
        root.addView(
            button("Envoyer un message test") {
                save()
                MiahTelegram.sendMessageAsync(this, "👋 Bonjour ! Ton appli Habitudes est bien connectée.") { r ->
                    toast(if (r.ok) "Message envoyé ✓" else "Échec : ${r.message}")
                }
            }
        )
        root.addView(
            button("Activer Telegram pour toutes les habitudes avec rappel") {
                val component = (applicationContext as HabitsApplication).component
                var n = 0
                for (h in component.habitList) {
                    if (h.hasReminder() && !h.isArchived) {
                        MiahPrefs.setTelegramReminder(this, h.uuid, true)
                        n++
                    }
                }
                toast("$n habitude(s) activée(s)")
            }
        )

        // ---------------- Bilan ----------------
        root.addView(header("Bilan sur Telegram"))
        summaryCheck = CheckBox(this).apply {
            text = "Recevoir un bilan chaque jour"
            setTextColor(Color.WHITE)
            isChecked = MiahPrefs.summaryHour(this@MiahSettingsActivity) >= 0
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    MiahPrefs.setSummaryTime(this@MiahSettingsActivity, 21, 0)
                } else {
                    MiahPrefs.setSummaryTime(this@MiahSettingsActivity, -1, 0)
                }
                updateSummaryButton()
                MiahScheduler.scheduleDaily(this@MiahSettingsActivity)
            }
        }
        root.addView(summaryCheck)
        summaryTimeBtn = button("") {
            val h = MiahPrefs.summaryHour(this).let { if (it < 0) 21 else it }
            TimePickerDialog(this, { _, hour, minute ->
                summaryCheck.isChecked = true
                MiahPrefs.setSummaryTime(this, hour, minute)
                updateSummaryButton()
                MiahScheduler.scheduleDaily(this)
            }, h, MiahPrefs.summaryMinute(this), true).show()
        }
        root.addView(summaryTimeBtn)
        root.addView(
            CheckBox(this).apply {
                text = "Ajouter le bilan de la semaine le dernier jour de la semaine"
                setTextColor(Color.WHITE)
                isChecked = MiahPrefs.weeklySummary(this@MiahSettingsActivity)
                setOnCheckedChangeListener { _, c -> MiahPrefs.setWeeklySummary(this@MiahSettingsActivity, c) }
            }
        )
        root.addView(
            button("Envoyer le bilan maintenant") {
                save()
                val msg = MiahScheduler.buildDailySummary(this, MiahPrefs.weeklySummary(this))
                MiahTelegram.sendMessageAsync(this, msg) { r ->
                    toast(if (r.ok) "Bilan envoyé ✓" else "Échec : ${r.message}")
                }
            }
        )

        // ---------------- Sauvegarde ----------------
        root.addView(header("Sauvegarde automatique sur Telegram"))
        root.addView(text("Le fichier reçu s'importe via Réglages › Importer des données."))
        val choices = listOf("Jamais", "Chaque jour", "Chaque semaine")
        val values = listOf(0, 1, 7)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
        spinner.setBackgroundColor(Color.parseColor("#2C2C33"))
        spinner.setSelection(values.indexOf(MiahPrefs.backupEveryDays(this)).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                (v as? TextView)?.setTextColor(Color.WHITE)
                MiahPrefs.setBackupEveryDays(this@MiahSettingsActivity, values[pos])
                MiahScheduler.scheduleDaily(this@MiahSettingsActivity)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(spinner)
        root.addView(
            button("Envoyer une sauvegarde maintenant") {
                save()
                toast("Envoi…")
                MiahScheduler.sendBackup(this) { r ->
                    toast(if (r.ok) "Sauvegarde envoyée ✓" else "Échec : ${r.message}")
                }
            }
        )

        // ---------------- Alarmes ----------------
        root.addView(header("Alarmes (réveil)"))
        root.addView(
            text(
                "Pour qu'une habitude sonne comme un réveil, ouvre-la, appuie sur Modifier et coche " +
                    "« Sonner comme un réveil » sous le rappel."
            )
        )
        permissionsText = text("")
        root.addView(permissionsText)
        root.addView(
            button("Autoriser l'affichage plein écran") {
                val i = MiahAlarm.fullScreenSettingsIntent(this)
                    ?: Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                tryStart(i)
            }
        )
        root.addView(
            button("Autoriser les alarmes à l'heure exacte") {
                if (Build.VERSION.SDK_INT >= 31) {
                    tryStart(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
            }
        )
        root.addView(
            button("Tester l'alarme maintenant") {
                val component = (applicationContext as HabitsApplication).component
                val first = component.habitList.firstOrNull { !it.isArchived }
                if (first == null) {
                    toast("Crée d'abord une habitude")
                } else {
                    MiahAlarm.show(this, first)
                }
            }
        )

        updateSummaryButton()
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val fs = MiahAlarm.canUseFullScreen(this)
        val am = getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < 31 || (am?.canScheduleExactAlarms() ?: true)
        permissionsText.text =
            "Plein écran : " + (if (fs) "✅ autorisé" else "❌ à autoriser") +
            "\nAlarmes exactes : " + (if (exact) "✅ autorisées" else "❌ à autoriser")
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    private fun save() {
        MiahPrefs.setTelegram(this, tokenInput.text.toString(), chatInput.text.toString())
        MiahScheduler.scheduleDaily(this)
    }

    private fun updateSummaryButton() {
        val h = MiahPrefs.summaryHour(this)
        summaryTimeBtn.text = if (h < 0) {
            "Heure du bilan : désactivé"
        } else {
            String.format(Locale.ROOT, "Heure du bilan : %02d:%02d", h, MiahPrefs.summaryMinute(this))
        }
    }

    private fun tryStart(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            tryStartAppSettings()
        }
    }

    private fun tryStartAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
        }
    }

    private fun header(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#FF8A65"))
        setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setPadding(0, dp(24), 0, dp(8))
    }

    private fun text(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.LTGRAY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, 0, 0, dp(8))
    }

    private fun field(hint: String, value: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Color.GRAY)
        setText(value)
        setTextColor(Color.WHITE)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#37474F"))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, dp(6)) }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
