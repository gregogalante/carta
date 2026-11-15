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

    private class Tweak(val table: Table, val key: String, val onValue: String, val systemDefault: String)

    private val resolver = context.contentResolver
    private val backup = context.getSharedPreferences("settings_backup", Context.MODE_PRIVATE)

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun set(toggle: Toggle, on: Boolean) {
        TWEAKS[toggle]?.forEach { if (on) enable(it) else restore(it) }
    }

    private fun enable(tweak: Tweak) {
        val backupKey = "${tweak.table}/${tweak.key}"
        if (!backup.contains(backupKey)) {
            backup.edit().putString(backupKey, read(tweak) ?: tweak.systemDefault).apply()
        }
        write(tweak, tweak.onValue)
    }

    private fun restore(tweak: Tweak) {
        val backupKey = "${tweak.table}/${tweak.key}"
        val original = backup.getString(backupKey, null) ?: return
        write(tweak, original)
        backup.edit().remove(backupKey).apply()
    }

    private fun read(tweak: Tweak): String? = when (tweak.table) {
        Table.SECURE -> Settings.Secure.getString(resolver, tweak.key)
        Table.GLOBAL -> Settings.Global.getString(resolver, tweak.key)
    }

    private fun write(tweak: Tweak, value: String) {
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

        val TWEAKS = mapOf(
            Toggle.GRAYSCALE to listOf(
                Tweak(Table.SECURE, DALTONIZER_MODE, MONOCHROMACY, "12"),
                Tweak(Table.SECURE, DALTONIZER_ENABLED, "1", "0"),
            ),
            Toggle.NO_ANIMATIONS to listOf(
                Settings.Global.ANIMATOR_DURATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.WINDOW_ANIMATION_SCALE,
            ).map { Tweak(Table.GLOBAL, it, "0", "1") },
        )
    }
}
