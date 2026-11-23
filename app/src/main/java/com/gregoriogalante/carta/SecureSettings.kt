package com.gregoriogalante.carta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * System-wide tweaks written through WRITE_SECURE_SETTINGS. The user's original values are backed
 * up on enable and restored on disable, so turning a toggle off leaves the phone as it was.
 */
class SecureSettings(private val context: Context) {

    private enum class Table { SECURE, GLOBAL }

    /**
     * [systemDefault] is what to restore when the key was never set; null removes it again, for keys
     * whose default lives in the device config. [onValue] receives the current value.
     */
    private class Tweak(
        val table: Table,
        val key: String,
        val systemDefault: String?,
        val onValue: (current: String?) -> String,
    ) {
        constructor(table: Table, key: String, onValue: String, systemDefault: String) :
            this(table, key, systemDefault, { onValue })
    }

    private val resolver = context.contentResolver
    private val backup = context.getSharedPreferences("settings_backup", Context.MODE_PRIVATE)

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Returns false when Android refuses a write, so the effect is reported as not applied. */
    fun set(toggle: Toggle, on: Boolean): Boolean = try {
        TWEAKS[toggle]?.forEach { if (on) enable(it) else restore(it) }
        true
    } catch (e: SecurityException) {
        false
    }

    private fun enable(tweak: Tweak) {
        val backupKey = "${tweak.table}/${tweak.key}"
        val current = read(tweak)
        if (!backup.contains(backupKey)) {
            backup.edit().putString(backupKey, current ?: tweak.systemDefault ?: UNSET).apply()
        }
        write(tweak, tweak.onValue(current))
    }

    private fun restore(tweak: Tweak) {
        val backupKey = "${tweak.table}/${tweak.key}"
        val original = backup.getString(backupKey, null) ?: return
        write(tweak, original.takeUnless { it == UNSET })
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

        /** Kelvin; paper under a reading lamp. Android clamps it to the device's supported range. */
        const val WARM_TEMPERATURE = 3400

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
                // A Night Light already warmer than paper is the user's choice: keep it.
                Tweak(Table.SECURE, NIGHT_DISPLAY_TEMPERATURE, null) { current ->
                    minOf(current?.toIntOrNull() ?: WARM_TEMPERATURE, WARM_TEMPERATURE).toString()
                },
                // Manual mode, or a sunrise schedule would switch it off while Carta is on.
                Tweak(Table.SECURE, NIGHT_DISPLAY_AUTO_MODE, "0", "0"),
                Tweak(Table.SECURE, NIGHT_DISPLAY_ACTIVATED, "1", "0"),
            ),
            Toggle.EXTRA_DIM to listOf(Tweak(Table.SECURE, EXTRA_DIM_ACTIVATED, "1", "0")),
            Toggle.BOLD_TEXT to listOf(Tweak(Table.SECURE, FONT_WEIGHT_ADJUSTMENT, BOLD_WEIGHT, "0")),
            Toggle.NO_ANIMATIONS to listOf(
                Settings.Global.ANIMATOR_DURATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.WINDOW_ANIMATION_SCALE,
            ).map { Tweak(Table.GLOBAL, it, "0", "1") },
        )
    }
}
