package com.gregoriogalante.carta

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews

/** Home screen switch: the same ink/paper look as the app, toggling every effect at once. */
class EinkWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            // No activity to start from here: a live wallpaper cannot be brought back, the rest is restored.
            EinkMode(context).toggle()
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.gregoriogalante.carta.action.TOGGLE"

        // Below this width (dp) only the square glyph + switch fits.
        private const val WIDE_MIN_WIDTH_DP = 110f
        private const val MIN_SIZE_DP = 40f

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EinkWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        /** Android 12+ picks the layout per size on its own; Android 11 always gets the wide one. */
        private fun views(context: Context): RemoteViews {
            val active = EinkMode(context).isActive()
            val wide = layout(context, R.layout.widget_wide, active)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return wide
            return RemoteViews(
                mapOf(
                    SizeF(MIN_SIZE_DP, MIN_SIZE_DP) to layout(context, R.layout.widget_square, active),
                    SizeF(WIDE_MIN_WIDTH_DP, MIN_SIZE_DP) to wide,
                ),
            )
        }

        /**
         * Colors live in the layouts as day/night resources, so the launcher follows the system theme by
         * itself. Only the switch and the state label reflect whether Carta is on.
         */
        private fun layout(context: Context, layoutId: Int, active: Boolean): RemoteViews {
            val state = context.getString(if (active) R.string.widget_state_on else R.string.widget_state_off)
            return RemoteViews(context.packageName, layoutId).apply {
                setContentDescription(android.R.id.background, "${context.getString(R.string.app_name)}: $state")
                setOnClickPendingIntent(android.R.id.background, togglePendingIntent(context))
                setInt(R.id.widget_track, "setBackgroundResource", if (active) R.drawable.bg_widget_track_on else R.drawable.bg_widget_track_off)
                setViewVisibility(R.id.widget_knob_on, if (active) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_knob_off, if (active) View.GONE else View.VISIBLE)
                if (layoutId == R.layout.widget_wide) setTextViewText(R.id.widget_state, state)
            }
        }

        private fun togglePendingIntent(context: Context) = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, EinkWidget::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
