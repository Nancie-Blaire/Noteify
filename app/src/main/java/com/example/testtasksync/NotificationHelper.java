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
    private static final String APP_NAME = "Noteify"; // ✅ Your app name

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
     * ✅ IMPORTANT: Use this ONLY when scheduling the ENTIRE todo list
     * (when the list itself has a due date, not individual tasks)
     */
    public static void scheduleTodoListNotification(Context context, String listId,
                                                    String listTitle,
                                                    Date scheduleDate, String scheduleTime,
                                                    int reminderMinutes) {
        if (scheduleDate == null) {
            Log.d(TAG, "⚠️ No schedule date, skipping notification");
            return;
        }

        // ✅ Skip if title is empty
        if (listTitle == null || listTitle.trim().isEmpty()) {
            Log.d(TAG, "⚠️ Skipping notification with empty title");
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

        Log.d(TAG, "✅ Scheduling TODO LIST notification: " + listTitle);

        // ✅ FIXED: Title is always "Noteify", message is the list title
        scheduleNotification(context, listId, APP_NAME, listTitle,
                notificationTime.getTimeInMillis(), "todo");
    }

    /**
     * Schedule notification for an INDIVIDUAL TODO TASK
     */
    public static void scheduleTodoTaskNotification(Context context, String taskId,
                                                    String taskText,
                                                    Date scheduleDate, String scheduleTime,
                                                    int reminderMinutes) {
        if (scheduleDate == null) {
            Log.d(TAG, "⚠️ No schedule date, skipping notification");
            return;
        }

        // ✅ Skip if task text is empty
        if (taskText == null || taskText.trim().isEmpty()) {
            Log.d(TAG, "⚠️ Skipping notification with empty task text");
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

        Log.d(TAG, "✅ Scheduling individual TASK notification: " + taskText);

        // ✅ FIXED: Title is always "Noteify", message is the task text
        scheduleNotification(context, taskId, APP_NAME, taskText,
                notificationTime.getTimeInMillis(), "todo_task");
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

        // ✅ Skip if task text is empty
        if (taskText == null || taskText.trim().isEmpty()) {
            Log.d(TAG, "⚠️ Skipping notification with empty task text");
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

        // ✅ FIXED: Title is always "Noteify", message shows task text
        String notificationMessage = taskText + " (" + planTitle + " - " + day + ")";
        scheduleNotification(context, taskId, APP_NAME, notificationMessage,
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