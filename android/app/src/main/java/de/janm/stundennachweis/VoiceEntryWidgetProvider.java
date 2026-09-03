package de.janm.stundennachweis;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Startbildschirm-Widget, das unmittelbar die Spracheingabe öffnet. */
public final class VoiceEntryWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.voice_entry_widget);
            views.setOnClickPendingIntent(R.id.voice_entry_widget_button, createVoiceIntent(context));
            manager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onEnabled(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, VoiceEntryWidgetProvider.class);
        onUpdate(context, manager, manager.getAppWidgetIds(provider));
    }

    private PendingIntent createVoiceIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra("start_voice", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
