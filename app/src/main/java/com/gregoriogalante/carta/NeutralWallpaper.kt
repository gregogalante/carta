package com.gregoriogalante.carta

import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream

fun Context.isDarkTheme() =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/**
 * Swaps the home and lock wallpapers for a flat paper (light theme) or ink (dark theme) color,
 * backing up the user's originals first. Not pure white/black: e-ink panels are neither, and the
 * warm neutrals also steer Material You toward a calm system palette.
 *
 * Reading the current wallpaper on Android 14+ requires "All files access".
 */
class NeutralWallpaper(private val context: Context) {

    private val manager = WallpaperManager.getInstance(context)
    private val state = context.getSharedPreferences("wallpaper_backup", Context.MODE_PRIVATE)
    private val systemBackup = File(context.filesDir, "wallpaper_system")
    private val lockBackup = File(context.filesDir, "wallpaper_lock")

    fun hasPermission() = Environment.isExternalStorageManager()

    /** Backs up the user's wallpapers once, then applies the color matching the theme. */
    fun apply(dark: Boolean): Boolean = runCatching {
        if (!isActive()) backUp()
        val color = context.getColor(if (dark) R.color.ink else R.color.paper)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        manager.setBitmap(bitmap, null, true, FLAG_SYSTEM or FLAG_LOCK)
        state.edit().putBoolean(KEY_ACTIVE, true).putBoolean(KEY_DARK, dark).apply()
    }.onFailure { Log.w(TAG, "Could not apply neutral wallpaper", it) }.isSuccess

    /** Re-applies when the system theme flipped since the last apply. */
    fun followTheme(dark: Boolean) {
        if (isActive() && state.getBoolean(KEY_DARK, false) != dark) apply(dark)
    }

    /**
     * Puts the user's wallpapers back. A live wallpaper cannot be set silently: the returned intent
     * opens the system preview so the user confirms it.
     */
    fun restore(): Intent? {
        if (!isActive()) return null
        val live = state.getString(KEY_LIVE, null)?.let(ComponentName::unflattenFromString)
        val restored = runCatching {
            when {
                systemBackup.exists() -> {
                    // Without a separate lock backup, the lock screen was mirroring the home one.
                    val flags = if (lockBackup.exists()) FLAG_SYSTEM else FLAG_SYSTEM or FLAG_LOCK
                    systemBackup.inputStream().use { manager.setStream(it, null, true, flags) }
                }
                live == null -> manager.clear(FLAG_SYSTEM or FLAG_LOCK) // Was the built-in default.
            }
            if (lockBackup.exists()) lockBackup.inputStream().use { manager.setStream(it, null, true, FLAG_LOCK) }
        }.onFailure { Log.w(TAG, "Could not restore wallpaper", it) }.isSuccess
        // Keep the backup for the next attempt rather than losing the user's wallpaper.
        if (!restored) return null
        systemBackup.delete()
        lockBackup.delete()
        state.edit().clear().apply()
        return live?.let {
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, it)
        }
    }

    private fun isActive() = state.getBoolean(KEY_ACTIVE, false)

    private fun backUp() {
        manager.wallpaperInfo?.let { state.edit().putString(KEY_LIVE, it.component.flattenToString()).apply() }
        copyWallpaper(FLAG_SYSTEM, systemBackup)
        copyWallpaper(FLAG_LOCK, lockBackup)
    }

    // Null when that slot has no image of its own (live wallpaper, built-in default, mirrored lock).
    private fun copyWallpaper(which: Int, target: File) {
        manager.getWallpaperFile(which)?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
    }

    private companion object {
        const val TAG = "NeutralWallpaper"
        const val SIZE = 64
        const val KEY_ACTIVE = "active"
        const val KEY_DARK = "dark"
        const val KEY_LIVE = "live_component"
    }
}
