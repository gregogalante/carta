package com.gregoriogalante.carta

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val prefs by lazy { einkPrefs() }
    private val mode by lazy { EinkMode(this) }
    private val wallpaper by lazy { NeutralWallpaper(this) }
    private val switches = mutableMapOf<Toggle, Switch>()
    private lateinit var hero: View

    // Keeps the setup section current while the user toggles the service in system settings.
    private val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshSetup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hero = findViewById<View>(R.id.hero).apply {
            setOnClickListener { toggleCarta() }
            accessibilityDelegate = SwitchAccessibility { mode.isActive() }
        }
        val effects = findViewById<LinearLayout>(R.id.effects)
        val intensityRow = findViewById<View>(R.id.intensity_row)
        (intensityRow.parent as ViewGroup).removeView(intensityRow)
        Toggle.entries.forEach {
            effects.addView(effectRow(effects, it))
            // The slider belongs right under the effect it tunes.
            if (it == Toggle.PAPER) effects.addView(intensityRow)
        }
        bindIntensity()

        findViewById<TextView>(R.id.secure_command).text = getString(R.string.secure_command, packageName)
        findViewById<View>(R.id.service_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.files_button).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null)),
            )
        }
        findViewById<TextView>(R.id.version).text =
            getString(R.string.version, packageManager.getPackageInfo(packageName, 0).versionName)
    }

    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, accessibilityObserver,
        )
    }

    override fun onResume() {
        super.onResume()
        // Covers theme switches while the overlay service (which also follows them) is off.
        wallpaper.followTheme(isDarkTheme())
        refreshEffects() // The widget may have changed things while the app was in the background.
        refreshSetup()
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(accessibilityObserver)
        super.onStop()
    }

    private fun effectRow(parent: LinearLayout, toggle: Toggle): View {
        val row = layoutInflater.inflate(R.layout.row_effect, parent, false)
        row.findViewById<TextView>(R.id.title).setText(toggle.title)
        row.findViewById<TextView>(R.id.description).setText(toggle.description)
        val switch = row.findViewById<Switch>(R.id.toggle).apply {
            // Every row shares this id: restoring saved state on recreation (theme, language, rotation)
            // would push one row's state into all and fire the listeners. Prefs are the source of truth.
            isSaveEnabled = false
            tag = toggle
            setOnCheckedChangeListener(::onSwitchChanged)
        }
        row.findViewById<View>(R.id.row).setOnClickListener { switch.toggle() }
        switches[toggle] = switch
        return row
    }

    /** Auto uses the suggested intensity; dragging the slider switches to manual. */
    private fun bindIntensity() {
        val value = findViewById<TextView>(R.id.intensity_value)
        val autoChip = findViewById<TextView>(R.id.intensity_auto)
        val slider = findViewById<SeekBar>(R.id.intensity)
        val show = {
            slider.progress = prefs.paperIntensity()
            value.text = getString(R.string.paper_intensity_value, slider.progress)
            autoChip.isActivated = prefs.isPaperIntensityAuto()
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                prefs.edit()
                    .putBoolean(PAPER_INTENSITY_AUTO_KEY, false)
                    .putInt(PAPER_INTENSITY_KEY, progress)
                    .apply()
                show()
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        autoChip.setOnClickListener {
            prefs.edit().putBoolean(PAPER_INTENSITY_AUTO_KEY, true).apply()
            show()
        }
        show()
    }

    /** The switches edit the configuration; it is applied right away only while Carta is on. */
    private fun onSwitchChanged(button: CompoundButton, selected: Boolean) {
        val toggle = button.tag as Toggle
        if (!mode.select(toggle, selected, ::startActivity)) showMissing(toggle.requirement)
        refreshEffects()
    }

    private fun toggleCarta() {
        val turningOn = !mode.isActive()
        val skipped = mode.toggle(::startActivity)
        if (turningOn) skipped.firstOrNull()?.let { showMissing(it.requirement) }
        refreshEffects()
    }

    private fun showMissing(requirement: Requirement) {
        val message = when (requirement) {
            Requirement.ALL_FILES -> R.string.missing_files_access
            else -> R.string.missing_permission
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun refreshEffects() {
        Toggle.entries.forEach { showSwitch(it, mode.isSelected(it)) }
        refreshHero()
        // Tunable while Carta is off too, as long as the texture is part of the configuration.
        val paperSelected = mode.isSelected(Toggle.PAPER)
        findViewById<View>(R.id.intensity_row).alpha = if (paperSelected) 1f else DISABLED_ALPHA
        findViewById<View>(R.id.intensity).isEnabled = paperSelected
    }

    private fun refreshHero() {
        val active = mode.isActive()
        // Activation propagates to the children: colors, icon tint and pill all follow it.
        hero.isActivated = active
        val knob = findViewById<View>(R.id.hero_knob)
        knob.layoutParams = (knob.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.CENTER_VERTICAL or if (active) Gravity.END else Gravity.START
        }
        findViewById<TextView>(R.id.hero_title).setText(if (active) R.string.hero_title_on else R.string.hero_title_off)
        findViewById<TextView>(R.id.hero_summary).text =
            if (active) {
                mode.selectedToggles().joinToString(getString(R.string.summary_separator)) { getString(it.shortLabel) }
            } else {
                getString(R.string.hero_summary_off)
            }
    }

    private fun showSwitch(toggle: Toggle, checked: Boolean) {
        val switch = switches.getValue(toggle)
        if (switch.isChecked == checked) return
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = checked
        switch.setOnCheckedChangeListener(::onSwitchChanged)
    }

    private fun refreshSetup() {
        val secureGranted = mode.isGranted(Requirement.SECURE_SETTINGS)
        showMarker(R.id.secure_marker, secureGranted, 1)
        findViewById<TextView>(R.id.secure_body)
            .setText(if (secureGranted) R.string.setup_secure_done else R.string.setup_secure_pending)
        findViewById<View>(R.id.secure_command).visibility = if (secureGranted) View.GONE else View.VISIBLE

        val serviceOn = isOverlayServiceEnabled()
        showMarker(R.id.service_marker, serviceOn, 2)
        findViewById<TextView>(R.id.service_body)
            .setText(if (serviceOn) R.string.setup_service_done else R.string.setup_service_pending)
        findViewById<View>(R.id.service_button).visibility = if (serviceOn) View.GONE else View.VISIBLE

        val filesGranted = mode.isGranted(Requirement.ALL_FILES)
        showMarker(R.id.files_marker, filesGranted, 3)
        findViewById<TextView>(R.id.files_body)
            .setText(if (filesGranted) R.string.setup_files_done else R.string.setup_files_pending)
        findViewById<View>(R.id.files_button).visibility = if (filesGranted) View.GONE else View.VISIBLE
    }

    /** Step number while pending, an accent check once done. */
    private fun showMarker(id: Int, done: Boolean, step: Int) {
        findViewById<TextView>(id).apply {
            text = if (done) "✓" else "$step."
            setTextColor(getColor(if (done) R.color.accent else R.color.ink))
        }
    }

    // Reads the same setting the observer watches; AccessibilityManager's list proved unreliable
    // right after (re)installs on Motorola.
    private fun isOverlayServiceEnabled(): Boolean {
        val self = ComponentName(this, EinkOverlayService::class.java)
        return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .any { ComponentName.unflattenFromString(it) == self }
    }

    /** Announces the hero card as a switch to TalkBack. */
    private class SwitchAccessibility(private val isOn: () -> Boolean) : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = Switch::class.java.name
            info.isCheckable = true
            info.isChecked = isOn()
        }
    }

    private companion object {
        const val DISABLED_ALPHA = 0.4f
    }
}
