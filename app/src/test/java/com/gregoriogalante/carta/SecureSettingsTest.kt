package com.gregoriogalante.carta

import android.content.Context
import android.provider.Settings
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureSettingsTest {

    private val context = RuntimeEnvironment.getApplication() as Context
    private val resolver = context.contentResolver

    private fun settingsAt(hour: Int) =
        SecureSettings(context, Clock.fixed(Instant.parse("2026-11-23T%02d:00:00Z".format(hour)), ZoneOffset.UTC))

    private fun secure(key: String): String? = Settings.Secure.getString(resolver, key)

    @Test
    fun `restores the user's values exactly`() {
        Settings.Secure.putString(resolver, "accessibility_display_daltonizer", "12")
        Settings.Secure.putString(resolver, "accessibility_display_daltonizer_enabled", "0")
        val settings = settingsAt(12)

        assertTrue(settings.set(Toggle.GRAYSCALE, true))
        assertEquals("0", secure("accessibility_display_daltonizer"))
        assertEquals("1", secure("accessibility_display_daltonizer_enabled"))

        assertTrue(settings.set(Toggle.GRAYSCALE, false))
        assertEquals("12", secure("accessibility_display_daltonizer"))
        assertEquals("0", secure("accessibility_display_daltonizer_enabled"))
    }

    @Test
    fun `applying twice keeps the first backup`() {
        Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, "0.5")
        val settings = settingsAt(12)

        settings.set(Toggle.NO_ANIMATIONS, true)
        settings.set(Toggle.NO_ANIMATIONS, true)
        settings.set(Toggle.NO_ANIMATIONS, false)

        assertEquals("0.5", Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE))
    }

    @Test
    fun `warm light follows the time of day and removes a key that was never set`() {
        settingsAt(12).set(Toggle.WARM_LIGHT, true)
        assertEquals("4000", secure("night_display_color_temperature"))
        assertEquals("1", secure("night_display_activated"))

        settingsAt(22).set(Toggle.WARM_LIGHT, true)
        assertEquals("3000", secure("night_display_color_temperature"))

        settingsAt(22).set(Toggle.WARM_LIGHT, false)
        assertNull(secure("night_display_color_temperature"))
        assertEquals("0", secure("night_display_activated"))
    }

    @Test
    fun `keeps a Night Light warmer than the schedule`() {
        Settings.Secure.putString(resolver, "night_display_color_temperature", "2800")

        settingsAt(12).set(Toggle.WARM_LIGHT, true)

        assertEquals("2800", secure("night_display_color_temperature"))
    }

    @Test
    fun `restores a decimal font weight as an integer Android can read`() {
        Settings.Secure.putString(resolver, "font_weight_adjustment", "0.0")
        val settings = settingsAt(12)

        settings.set(Toggle.BOLD_TEXT, true)
        assertEquals("300", secure("font_weight_adjustment"))

        settings.set(Toggle.BOLD_TEXT, false)
        assertEquals("0", secure("font_weight_adjustment"))
    }
}
