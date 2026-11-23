package com.gregoriogalante.carta

import android.content.Context
import android.content.Intent

/**
 * Carta has a configuration (which effects the user selected, kept while off) and an on/off state.
 * Switching on applies only the selected effects. Shared by the app screen and the widget.
 *
 * [startActivity] is needed to bring back a live wallpaper; without it that step is skipped.
 */
class EinkMode(private val context: Context) {

    private val prefs = context.einkPrefs()
    private val secureSettings = SecureSettings(context)
    private val wallpaper = NeutralWallpaper(context)

    fun isActive() = prefs.isActive()

    fun isSelected(toggle: Toggle) = prefs.isSelected(toggle)

    fun selectedToggles() = Toggle.supported.filter(prefs::isSelected)

    fun isGranted(requirement: Requirement) = when (requirement) {
        Requirement.NONE -> true
        Requirement.SECURE_SETTINGS -> secureSettings.hasPermission()
        Requirement.ALL_FILES -> wallpaper.hasPermission()
    }

    /**
     * Adds or removes an effect from the configuration, applying it right away while Carta is on.
     * Returns false when it cannot be selected (missing permission or failure).
     */
    fun select(toggle: Toggle, selected: Boolean, startActivity: ((Intent) -> Unit)? = null): Boolean {
        if (selected && !isGranted(toggle.requirement)) return false
        if (isActive() && !apply(toggle, selected, startActivity)) return false
        val editor = prefs.edit().putSelected(toggle, selected)
        // Removing the last effect switches Carta off: "on" with nothing applied would be a lie.
        if (!selected && isActive() && selectedToggles().all { it == toggle }) editor.putBoolean(ACTIVE_KEY, false)
        editor.apply()
        EinkWidget.refreshAll(context)
        return true
    }

    /** Switches Carta on (selected effects only) or off. Returns the selected effects it could not apply. */
    fun toggle(startActivity: ((Intent) -> Unit)? = null): List<Toggle> {
        val turnOn = !isActive()
        // Nothing selected: fall back to the default configuration rather than a no-op "on".
        if (turnOn && selectedToggles().isEmpty()) {
            prefs.edit().apply { Toggle.supported.forEach { putSelected(it, true) } }.apply()
        }
        val skipped = selectedToggles().filterNot { apply(it, turnOn, startActivity) }
        prefs.edit().putBoolean(ACTIVE_KEY, turnOn).apply()
        EinkWidget.refreshAll(context)
        return skipped
    }

    private fun apply(toggle: Toggle, on: Boolean, startActivity: ((Intent) -> Unit)?): Boolean {
        val granted = isGranted(toggle.requirement)
        if (on && !granted) return false
        if (!granted) return true // Nothing was applied, so there is nothing to undo.
        return when (toggle) {
            Toggle.WARM_LIGHT -> secureSettings.set(toggle, on).also { applied ->
                if (on && applied) WarmLightSchedule.schedule(context) else WarmLightSchedule.cancel(context)
            }
            Toggle.GRAYSCALE, Toggle.EXTRA_DIM, Toggle.BOLD_TEXT, Toggle.NO_ANIMATIONS ->
                secureSettings.set(toggle, on)
            Toggle.WALLPAPER ->
                if (on) {
                    wallpaper.apply(context.isDarkTheme())
                } else {
                    wallpaper.restore()?.let { intent -> startActivity?.invoke(intent) }
                    true
                }
            Toggle.PAPER -> true // EinkOverlayService follows the prefs.
        }
    }
}
