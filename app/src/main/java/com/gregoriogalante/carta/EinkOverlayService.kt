package com.gregoriogalante.carta

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service used only for its overlay window type: TYPE_ACCESSIBILITY_OVERLAY covers
 * system bars and needs no "display over other apps" permission.
 */
class EinkOverlayService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs by lazy { einkPrefs() }
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var overlay: PaperOverlayView? = null

    override fun onServiceConnected() {
        prefs.registerOnSharedPreferenceChangeListener(this)
        syncOverlay()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) = syncOverlay()

    // The service is the long-lived component, so it keeps the wallpaper in step with dark/light switches.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        NeutralWallpaper(this).followTheme(isDarkTheme())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        removeOverlay()
        super.onDestroy()
    }

    private fun syncOverlay() {
        if (!prefs.isOn(Toggle.PAPER)) return removeOverlay()
        val view = overlay ?: PaperOverlayView(this).also {
            windowManager.addView(it, overlayParams())
            overlay = it
        }
        view.intensity = prefs.paperIntensity() / 100f
    }

    private fun removeOverlay() {
        overlay?.let(windowManager::removeView)
        overlay = null
    }

    private fun overlayParams() = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT,
        LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        LayoutParams.FLAG_NOT_TOUCHABLE or
            LayoutParams.FLAG_NOT_FOCUSABLE or
            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
}
