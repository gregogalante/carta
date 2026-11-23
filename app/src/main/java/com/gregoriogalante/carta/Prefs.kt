package com.gregoriogalante.carta

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/** What the user must grant before an effect can be turned on. */
enum class Requirement { NONE, SECURE_SETTINGS, ALL_FILES }

enum class Toggle(
    val key: String,
    val title: Int,
    val description: Int,
    val shortLabel: Int,
    val requirement: Requirement,
    val minSdk: Int = Build.VERSION_CODES.R,
) {
    GRAYSCALE(
        "grayscale", R.string.effect_grayscale_title, R.string.effect_grayscale_description,
        R.string.short_grayscale, Requirement.SECURE_SETTINGS,
    ),
    WARM_LIGHT(
        "warm_light", R.string.effect_warm_light_title, R.string.effect_warm_light_description,
        R.string.short_warm_light, Requirement.SECURE_SETTINGS,
    ),
    EXTRA_DIM(
        "extra_dim", R.string.effect_extra_dim_title, R.string.effect_extra_dim_description,
        R.string.short_extra_dim, Requirement.SECURE_SETTINGS, Build.VERSION_CODES.S,
    ),
    BOLD_TEXT(
        "bold_text", R.string.effect_bold_text_title, R.string.effect_bold_text_description,
        R.string.short_bold_text, Requirement.SECURE_SETTINGS, Build.VERSION_CODES.S,
    ),
    NO_ANIMATIONS(
        "no_animations", R.string.effect_no_animations_title, R.string.effect_no_animations_description,
        R.string.short_no_animations, Requirement.SECURE_SETTINGS,
    ),
    PAPER(
        "paper", R.string.effect_paper_title, R.string.effect_paper_description,
        R.string.short_paper, Requirement.NONE,
    ),
    WALLPAPER(
        "wallpaper", R.string.effect_wallpaper_title, R.string.effect_wallpaper_description,
        R.string.short_wallpaper, Requirement.ALL_FILES,
    ),
    ;

    companion object {
        /** Effects this Android version can apply; the others are never shown. */
        val supported = entries.filter { Build.VERSION.SDK_INT >= it.minSdk }
    }
}

fun Context.einkPrefs(): SharedPreferences =
    getSharedPreferences("eink", Context.MODE_PRIVATE).also { it.migrateLegacyState() }

/** Whether Carta is switched on (hero card, widget). */
const val ACTIVE_KEY = "active"

private val Toggle.selectedKey get() = "${key}_selected"

/** Part of the user's Carta configuration; remembered while Carta is off. */
fun SharedPreferences.isSelected(toggle: Toggle) = getBoolean(toggle.selectedKey, true)

fun SharedPreferences.Editor.putSelected(toggle: Toggle, selected: Boolean): SharedPreferences.Editor =
    putBoolean(toggle.selectedKey, selected)

fun SharedPreferences.isActive() = getBoolean(ACTIVE_KEY, false)

/** Whether the effect is applied right now. */
fun SharedPreferences.isOn(toggle: Toggle) = isActive() && isSelected(toggle)

/**
 * Up to 0.1.0 each effect had a single "on" flag and the master switch turned all of them on.
 * Those flags become the selection, and Carta counts as active if any effect was applied.
 */
private fun SharedPreferences.migrateLegacyState() {
    if (Toggle.entries.none { contains(it.key) }) return
    val wasApplied = Toggle.entries.associateWith { getBoolean(it.key, false) }
    val active = wasApplied.values.any { it }
    edit().apply {
        if (active) wasApplied.forEach { (toggle, on) -> putSelected(toggle, on) }
        putBoolean(ACTIVE_KEY, active)
        Toggle.entries.forEach { remove(it.key) }
        remove("flash") // Effect removed before 0.1.0 shipped.
    }.apply()
}

const val PAPER_INTENSITY_KEY = "paper_intensity"
const val PAPER_INTENSITY_AUTO_KEY = "paper_intensity_auto"
/**
 * A dark theme is mostly black, where the grain cannot show and the lifted blacks read as a gray
 * veil over everything; a light theme is mostly white, where the grain is the paper.
 */
fun suggestedPaperIntensity(darkTheme: Boolean) = if (darkTheme) 30 else 60

fun SharedPreferences.isPaperIntensityAuto() = getBoolean(PAPER_INTENSITY_AUTO_KEY, true)

/** Texture strength as a 0..100 percentage: the suggested value while in auto, otherwise the user's. */
fun SharedPreferences.paperIntensity(darkTheme: Boolean): Int {
    val suggested = suggestedPaperIntensity(darkTheme)
    return if (isPaperIntensityAuto()) suggested else getInt(PAPER_INTENSITY_KEY, suggested)
}
