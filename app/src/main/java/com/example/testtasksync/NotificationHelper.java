package com.example.testtasksync;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.Date;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "task_reminders";
    private static final String CHANNEL_NAME = "Task Reminders";

    /**
     * Create notification channel (required for Android 8.0+)
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for task reminders");
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "✅ Notification channel created");
            }
        }
    }

    /**
     * Schedule a notification for a todo task
     */
    public static void scheduleTodoNotification(Context context, String taskId,
                                                String taskTitle, String taskText,
                                                Date scheduleDate, String scheduleTime,
                                                int reminderMinutes) {
        if (scheduleDate == null) {
            Log.d(TAG, "⚠️ No schedule date, skipping notification");
            return;
        }

        Calendar notificationTime = Calendar.getInstance();
        notificationTime.setTime(scheduleDate);

        // If time is specified, set it
        if (scheduleTime != null && !scheduleTime.isEmpty()) {
            try {
                String[] timeParts = scheduleTime.split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);
                notificationTime.set(Calendar.HOUR_OF_DAY, hour);
                notificationTime.set(Calendar.MINUTE, minute);
                notificationTime.set(Calendar.SECOND, 0);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing time", e);
            }
        }

        // Subtract reminder minutes
        notificationTime.add(Calendar.MINUTE, -reminderMinutes);

        // Don't schedule if time is in the past
        if (notificationTime.getTimeInMillis() <= System.currentTimeMillis()) {
            Log.d(TAG, "⚠️ Notification time is in the past, skipping");
            return;
        }

        scheduleNotification(context, taskId, taskTitle, taskText,
                notificationTime.getTimeInMillis(), "todo");
    }

    /**
     * Schedule a notification for a weekly task
     */
    public static void scheduleWeeklyTaskNotification(Context context, String taskId,
                                                      String planTitle, String taskText,
                                                      String day, Date startDate,
                                                      String time, int reminderMinutes) {
        if (startDate == null || time == null || time.isEmpty()) {
            Log.d(TAG, "⚠️ Missing date or time for weekly task");
            return;
        }

        // Calculate which day of the week
        Calendar taskDate = Calendar.getInstance();
        taskDate.setTime(startDate);

        // Map day string to Calendar constant
        int dayOfWeek = getDayOfWeek(day);

        // Find the next occurrence of this day in the week
        while (taskDate.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            taskDate.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Set time
        try {
            String[] timeParts = time.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            taskDate.set(Calendar.HOUR_OF_DAY, hour);
            taskDate.set(Calendar.MINUTE, minute);
            taskDate.set(Calendar.SECOND, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time", e);
            return;
        }

        // Subtract reminder minutes
        taskDate.add(Calendar.MINUTE, -reminderMinutes);

        // Don't schedule if time is in the past
        if (taskDate.getTimeInMillis() <= System.currentTimeMillis()) {
            Log.d(TAG, "⚠️ Weekly notification time is in the past, skipping");
            return;
        }

        String notificationTitle = planTitle + " - " + day;
        scheduleNotification(context, taskId, notificationTitle, taskText,
                taskDate.getTimeInMillis(), "weekly");
    }

    /**
     * Schedule notification using AlarmManager
     */
    private static void scheduleNotification(Context context, String id,
                                             String title, String message,
                                             long triggerTime, String type) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra("notification_id", id.hashCode());
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        intent.putExtra("type", type);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                id.hashCode(), // Use hashCode as unique request code
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            // Use setExactAndAllowWhileIdle for precise timing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            }

            Log.d(TAG, "✅ Notification scheduled for: " + new Date(triggerTime));
            Log.d(TAG, "   Title: " + title);
            Log.d(TAG, "   Message: " + message);
        }
    }

    /**
     * Cancel a scheduled notification
     */
    public static void cancelNotification(Context context, String taskId) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d(TAG, "❌ Notification cancelled for task: " + taskId);
        }
    }

    /**
     * Map day string to Calendar day constant
     */
    private static int getDayOfWeek(String day) {
        switch (day) {
            case "Mon": return Calendar.MONDAY;
            case "Tues": return Calendar.TUESDAY;
            case "Wed": return Calendar.WEDNESDAY;
            case "Thur": return Calendar.THURSDAY;
            case "Fri": return Calendar.FRIDAY;
            case "Sat": return Calendar.SATURDAY;
            case "Sun": return Calendar.SUNDAY;
            default: return Calendar.MONDAY;
        }
    }

    /**
     * Get notification channel ID
     */
    public static String getChannelId() {
        return CHANNEL_ID;
    }
}