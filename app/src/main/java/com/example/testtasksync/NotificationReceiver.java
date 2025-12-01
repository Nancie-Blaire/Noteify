package com.example.testtasksync;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "📬 Notification received!");

        int notificationId = intent.getIntExtra("notification_id", 0);
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        String type = intent.getStringExtra("type");
        String sourceId = intent.getStringExtra("sourceId");

        Log.d(TAG, "Type: " + type + ", SourceId: " + sourceId);

        // ✅ Create proper back stack navigation
        PendingIntent pendingIntent = createPendingIntentWithBackStack(context, type, sourceId, notificationId);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, NotificationHelper.getChannelId())
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        // Show the notification
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "✅ Notification displayed!");
        }
    }

    /**
     * ✅ Create PendingIntent with proper back stack so back button goes to MainActivity
     */
    private PendingIntent createPendingIntentWithBackStack(Context context, String type,
                                                           String sourceId, int requestCode) {
        Intent resultIntent;

        if ("weekly".equals(type)) {
            // Open WeeklyActivity with planId
            resultIntent = new Intent(context, WeeklyActivity.class);
            resultIntent.putExtra("planId", sourceId);
            resultIntent.putExtra("fromNotification", true); // ✅ Flag to load data
            Log.d(TAG, "📱 Opening WeeklyActivity with planId: " + sourceId);
        } else {
            // Open TodoActivity with listId
            resultIntent = new Intent(context, TodoActivity.class);
            resultIntent.putExtra("listId", sourceId);
            resultIntent.putExtra("fromNotification", true); // ✅ Flag to load data
            Log.d(TAG, "📱 Opening TodoActivity with listId: " + sourceId);
        }

        // ✅ Create back stack: MainActivity → TodoActivity/WeeklyActivity
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

        // Add MainActivity as the parent
        stackBuilder.addNextIntentWithParentStack(new Intent(context, MainActivity.class));

        // Add the target activity
        stackBuilder.addNextIntent(resultIntent);

        // Create PendingIntent with back stack
        return stackBuilder.getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}