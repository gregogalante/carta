package com.gregoriogalante.carta

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Warm light through the day: close to paper-neutral by day, warmer like a reading lamp in the
 * evening, when blue light disturbs sleep the most. Changes are gradual, in small steps.
 */
object WarmLightSchedule {

    const val DAY_KELVIN = 4000
    const val NIGHT_KELVIN = 3000

    private val EVENING_START: LocalTime = LocalTime.of(19, 0)
    private val MORNING_START: LocalTime = LocalTime.of(6, 0)
    private val RAMP: Duration = Duration.ofHours(2)
    private const val KELVIN_STEP = 100
    private val CHECK_STEP: Duration = Duration.ofMinutes(15)
    private const val ACTION_REFRESH = "com.gregoriogalante.carta.action.REFRESH_WARM_LIGHT"

    // Plain set() lets Android delay the alarm by up to an hour; a short window keeps the steps even.
    private val ALARM_WINDOW: Duration = Duration.ofMinutes(10)

    fun kelvinAt(time: LocalTime): Int {
        val warmth = when {
            time in EVENING_START..EVENING_START + RAMP -> fraction(EVENING_START, time)
            time in MORNING_START..MORNING_START + RAMP -> 1 - fraction(MORNING_START, time)
            time > EVENING_START || time < MORNING_START -> 1f
            else -> 0f
        }
        val kelvin = DAY_KELVIN - (DAY_KELVIN - NIGHT_KELVIN) * warmth
        return (kelvin / KELVIN_STEP).roundToInt() * KELVIN_STEP
    }

    /** The first moment after [from] at which [kelvinAt] gives a different value. */
    fun nextChange(from: LocalDateTime): LocalDateTime {
        val current = kelvinAt(from.toLocalTime())
        // Checks fall on the quarter hour, so evening and morning ramps get even steps.
        var next = from.withSecond(0).withNano(0).plusMinutes(CHECK_STEP.toMinutes() - from.minute % CHECK_STEP.toMinutes())
        while (kelvinAt(next.toLocalTime()) == current) next += CHECK_STEP
        return next
    }

    /**
     * Wakes Carta at the next change. The alarm is not a wakeup one: if the phone sleeps, it fires
     * when the screen next turns on, which is the only time the color matters.
     */
    fun schedule(context: Context) {
        val at = nextChange(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC, at, ALARM_WINDOW.toMillis(), pendingIntent(context))
    }

    fun cancel(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))

    private fun fraction(start: LocalTime, time: LocalTime) =
        Duration.between(start, time).toMinutes().toFloat() / RAMP.toMinutes()

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, Receiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Moves the warm light to the current time of day; also after a reboot, which drops alarms. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_REFRESH && intent.action != Intent.ACTION_BOOT_COMPLETED) return
            if (!context.einkPrefs().isOn(Toggle.WARM_LIGHT)) return
            val settings = SecureSettings(context)
            if (settings.hasPermission()) settings.set(Toggle.WARM_LIGHT, true)
            schedule(context)
        }
    }
}
