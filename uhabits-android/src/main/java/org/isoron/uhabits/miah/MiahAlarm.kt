/*
 * Ajouts Miah — rappels en mode « réveil ».
 * Notification d'alarme plein écran qui sonne en boucle jusqu'à ce qu'on l'arrête.
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.isoron.platform.time.getToday
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.habits.show.ShowHabitActivity
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit

object MiahAlarm {
    const val CHANNEL_ID = "MIAH_ALARM"
    private const val NOTIF_BASE = 0x4D0000

    fun notificationId(habitId: Long): Int = NOTIF_BASE + (habitId % 10000).toInt()

    fun alarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarmes (réveil)",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Rappels qui sonnent comme un réveil"
        channel.setSound(
            alarmUri(),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
        channel.setBypassDnd(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(channel)
    }

    fun show(context: Context, habit: Habit) {
        val id = habit.id ?: return
        createChannel(context)
        val intent = Intent(context, MiahAlarmActivity::class.java).apply {
            putExtra("habitId", id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = habit.question.ifBlank { "C'est l'heure !" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ ${habit.name}")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        try {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(notificationId(id), notification)
        } catch (e: SecurityException) {
            Log.e("MiahAlarm", "Notification refusée", e)
        }
    }

    fun cancel(context: Context, habitId: Long) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(habitId))
    }

    /** Android 14+ : l'autorisation « notifications plein écran » peut être désactivée. */
    fun canUseFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return true
        return nm.canUseFullScreenIntent()
    }

    fun fullScreenSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 34) return null
        return Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.parse("package:" + context.packageName))
    }
}

/** Écran de réveil : affiché par-dessus l'écran de verrouillage, sonne en boucle. */
class MiahAlarmActivity : Activity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var habit: Habit? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val component = (applicationContext as HabitsApplication).component
        val habitId = intent.getLongExtra("habitId", -1L)
        habit = component.habitList.getById(habitId)
        val h = habit
        if (h == null) {
            finish()
            return
        }
        // La notification elle-même ne doit plus sonner : c'est l'écran qui prend le relais.
        MiahAlarm.cancel(this, habitId)

        buildUi(h)
        startRinging()
        // Sécurité : arrêt automatique après 5 minutes.
        handler.postDelayed({ stopAndFinish() }, 5 * 60 * 1000L)
    }

    private fun buildUi(h: Habit) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        root.addView(
            TextView(this).apply {
                text = "⏰"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
                gravity = Gravity.CENTER
            }
        )
        root.addView(
            TextView(this).apply {
                text = h.name
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(8))
            }
        )
        root.addView(
            TextView(this).apply {
                text = h.question.ifBlank { "C'est l'heure !" }
                setTextColor(Color.LTGRAY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(40))
            }
        )

        if (h.isNumerical) {
            root.addView(
                button("Ouvrir pour saisir", "#2E7D32") {
                    val i = Intent(this, ShowHabitActivity::class.java)
                    i.data = ContentUris.withAppendedId(Uri.parse("content://org.isoron.uhabits/habit"), h.id!!)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                    stopAndFinish()
                }
            )
        } else {
            root.addView(
                button("C'est fait ✓", "#2E7D32") {
                    markDone(h)
                    stopAndFinish()
                }
            )
        }
        root.addView(
            button("Rappeler dans 10 min", "#455A64") {
                val component = (applicationContext as HabitsApplication).component
                component.reminderScheduler.snoozeReminder(h, 10L)
                component.notificationTray.cancel(h)
                stopAndFinish()
            }
        )
        root.addView(
            button("Arrêter", "#B71C1C") { stopAndFinish() }
        )
        setContentView(root)
    }

    private fun button(label: String, color: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setBackgroundColor(Color.parseColor(color))
            isAllCaps = false
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            ).apply { setMargins(0, dp(8), 0, dp(8)) }
        }

    private fun markDone(h: Habit) {
        val component = (applicationContext as HabitsApplication).component
        component.commandRunner.run(
            CreateRepetitionCommand(component.habitList, h, getToday(), Entry.YES_MANUAL, "")
        )
        component.notificationTray.cancel(h)
    }

    private fun startRinging() {
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@MiahAlarmActivity, MiahAlarm.alarmUri())
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("MiahAlarm", "Impossible de jouer la sonnerie", e)
        }
        try {
            @Suppress("DEPRECATION")
            vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        } catch (e: Exception) {
            Log.e("MiahAlarm", "Vibration impossible", e)
        }
    }

    private fun stopRinging() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopAndFinish() {
        handler.removeCallbacksAndMessages(null)
        stopRinging()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopRinging()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        stopAndFinish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
