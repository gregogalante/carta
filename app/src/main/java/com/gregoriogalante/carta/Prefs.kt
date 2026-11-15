package com.gregoriogalante.carta

import android.content.Context
import android.content.SharedPreferences

/** What the user must grant before an effect can be turned on. */
enum class Requirement { NONE, SECURE_SETTINGS, ALL_FILES }

enum class Toggle(
    val key: String,
    val title: Int,
    val description: Int,
    val shortLabel: Int,
    val requirement: Requirement,
) {
    GRAYSCALE(
        "grayscale", R.string.effect_grayscale_title, R.string.effect_grayscale_description,
        R.string.short_grayscale, Requirement.SECURE_SETTINGS,
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
const val PAPER_INTENSITY_SUGGESTED = 35

fun SharedPreferences.isPaperIntensityAuto() = getBoolean(PAPER_INTENSITY_AUTO_KEY, true)

/** Texture strength as a 0..100 percentage: the suggested value while in auto, otherwise the user's. */
fun SharedPreferences.paperIntensity() =
    if (isPaperIntensityAuto()) PAPER_INTENSITY_SUGGESTED else getInt(PAPER_INTENSITY_KEY, PAPER_INTENSITY_SUGGESTED)
