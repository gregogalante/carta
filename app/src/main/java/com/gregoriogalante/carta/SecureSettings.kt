package com.gregoriogalante.carta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import java.time.Clock
import java.time.LocalTime

/**
 * System-wide tweaks written through WRITE_SECURE_SETTINGS. The user's original values are backed
 * up on enable and restored on disable, so turning a toggle off leaves the phone as it was.
 */
class SecureSettings(private val context: Context, private val clock: Clock = Clock.systemDefaultZone()) {

    private enum class Table { SECURE, GLOBAL }

    /**
     * [systemDefault] is what to restore when the key was never set; null removes it again, for keys
     * whose default lives in the device config. [onValue] receives the user's original value (null
     * if unknown) and the local time, for values that follow the day. [restoreAs] rewrites the
     * original into a form Android reads back.
     */
    private class Tweak(
        val table: Table,
        val key: String,
        val systemDefault: String?,
        val restoreAs: (String) -> String = { it },
        val onValue: (original: String?, now: LocalTime) -> String,
    ) {
        constructor(table: Table, key: String, onValue: String, systemDefault: String) :
            this(table, key, systemDefault, onValue = { _, _ -> onValue })
    }

    private val resolver = context.contentResolver
    private val backup = context.getSharedPreferences("settings_backup", Context.MODE_PRIVATE)

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Applies or restores [toggle]. Applying again while on refreshes values that follow the day.
     * Returns false when Android refuses a write, so the effect is reported as not applied.
     */
    fun set(toggle: Toggle, on: Boolean): Boolean = try {
        TWEAKS[toggle]?.forEach { if (on) enable(it) else restore(it) }
        true
    } catch (e: SecurityException) {
        false
    }

    private fun enable(tweak: Tweak) {
        val backupKey = "${tweak.table}/${tweak.key}"
        if (!backup.contains(backupKey)) {
            backup.edit().putString(backupKey, read(tweak) ?: tweak.systemDefault ?: UNSET).apply()
        }
        val original = backup.getString(backupKey, null)?.takeUnless { it == UNSET }
        write(tweak, tweak.onValue(original, LocalTime.now(clock)))
    }

    private fun restore(tweak: Tweak) {
        val backupKey = "${tweak.table}/${tweak.key}"
        val original = backup.getString(backupKey, null) ?: return
        write(tweak, original.takeUnless { it == UNSET }?.let(tweak.restoreAs))
        backup.edit().remove(backupKey).apply()
    }

    // From Android 12, apps may write hidden keys but not read those Android has not marked
    // readable (Extra dim, for one). Their original value is then taken to be the system default.
    private fun read(tweak: Tweak): String? = try {
        when (tweak.table) {
            Table.SECURE -> Settings.Secure.getString(resolver, tweak.key)
            Table.GLOBAL -> Settings.Global.getString(resolver, tweak.key)
        }
    } catch (e: SecurityException) {
        null
    }

    private fun write(tweak: Tweak, value: String?) {
        when (tweak.table) {
            Table.SECURE -> Settings.Secure.putString(resolver, tweak.key, value)
            Table.GLOBAL -> Settings.Global.putString(resolver, tweak.key, value)
        }
    }

    private companion object {
        // Hidden Settings.Secure keys; daltonizer mode 0 = monochromacy (grayscale).
        const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val DALTONIZER_MODE = "accessibility_display_daltonizer"
        const val MONOCHROMACY = "0"

        // Hidden Settings.Secure keys of Night Light. Its color matrix multiplies each channel, so
        // blue drops while blacks stay black; it runs after grayscale, turning it into a warm sepia.
        const val NIGHT_DISPLAY_ACTIVATED = "night_display_activated"
        const val NIGHT_DISPLAY_TEMPERATURE = "night_display_color_temperature"
        const val NIGHT_DISPLAY_AUTO_MODE = "night_display_auto_mode"

        // Hidden Settings.Secure keys (Android 12+). Extra dim lowers whites below the minimum
        // brightness, at the strength set in Android's settings; paper reflects far less light than
        // a backlit white. Font weight 300 is Android's "bold text", for full, print-like strokes.
        const val EXTRA_DIM_ACTIVATED = "reduce_bright_colors_activated"
        const val FONT_WEIGHT_ADJUSTMENT = "font_weight_adjustment"
        const val BOLD_WEIGHT = "300"

        // Marks a key that had no value, so restoring removes it instead of inventing a default.
        const val UNSET = ""

        val TWEAKS = mapOf(
            Toggle.GRAYSCALE to listOf(
                Tweak(Table.SECURE, DALTONIZER_MODE, MONOCHROMACY, "12"),
                Tweak(Table.SECURE, DALTONIZER_ENABLED, "1", "0"),
            ),
            Toggle.WARM_LIGHT to listOf(
                // A Night Light already warmer than the schedule is the user's choice: keep it.
                Tweak(Table.SECURE, NIGHT_DISPLAY_TEMPERATURE, null) { original, now ->
                    val scheduled = WarmLightSchedule.kelvinAt(now)
                    minOf(original?.toIntOrNull() ?: scheduled, scheduled).toString()
                },
                // Manual mode, or a sunrise schedule would switch it off while Carta is on.
                Tweak(Table.SECURE, NIGHT_DISPLAY_AUTO_MODE, "0", "0"),
                Tweak(Table.SECURE, NIGHT_DISPLAY_ACTIVATED, "1", "0"),
            ),
            Toggle.EXTRA_DIM to listOf(Tweak(Table.SECURE, EXTRA_DIM_ACTIVATED, "1", "0")),
            Toggle.BOLD_TEXT to listOf(
                // Some phones store "0.0", but Android reads this key as an integer: written back as
                // is, it would be ignored and the text would stay bold.
                Tweak(
                    Table.SECURE, FONT_WEIGHT_ADJUSTMENT, "0",
                    restoreAs = { it.toFloatOrNull()?.toInt()?.toString() ?: it },
                ) { _, _ -> BOLD_WEIGHT },
            ),
            Toggle.NO_ANIMATIONS to listOf(
                Settings.Global.ANIMATOR_DURATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.WINDOW_ANIMATION_SCALE,
            ).map { Tweak(Table.GLOBAL, it, "0", "1") },
        )
    }
}
