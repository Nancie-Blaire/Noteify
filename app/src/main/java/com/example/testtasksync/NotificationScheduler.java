package com.example.testtasksync;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * ✅ Utility class to reschedule all notifications
 * Called when user enables notifications in Settings
 */
public class NotificationScheduler {
    private static final String TAG = "NotificationScheduler";

    public static void rescheduleAllNotifications(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No user logged in, cannot reschedule");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Log.d(TAG, "🔄 Starting to reschedule all notifications...");

        // Reschedule TODO lists
        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int count = 0;
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        if (doc.get("deletedAt") != null) continue;

                        String listId = doc.getId();
                        String title = doc.getString("title");
                        Date scheduleDate = doc.getDate("scheduleDate");
                        String scheduleTime = doc.getString("scheduleTime");

                        Long reminderMinutesLong = doc.getLong("reminderMinutes");
                        int reminderMinutes = reminderMinutesLong != null ? reminderMinutesLong.intValue() : 15;

                        if (scheduleDate != null) {
                            NotificationHelper.scheduleTodoListNotification(
                                    context, listId, title, scheduleDate,
                                    scheduleTime, reminderMinutes
                            );
                            count++;
                        }

                        // Also check for individual task notifications
                        List<Map<String, Object>> tasks = (List<Map<String, Object>>) doc.get("tasks");
                        if (tasks != null) {
                            for (int i = 0; i < tasks.size(); i++) {
                                Map<String, Object> task = tasks.get(i);
                                String taskId = (String) task.get("id");
                                String taskText = (String) task.get("text");

                                if (task.get("scheduleDate") instanceof com.google.firebase.Timestamp) {
                                    Date taskScheduleDate = ((com.google.firebase.Timestamp) task.get("scheduleDate")).toDate();
                                    String taskScheduleTime = (String) task.get("scheduleTime");

                                    Long taskReminderLong = (Long) task.get("reminderMinutes");
                                    int taskReminder = taskReminderLong != null ? taskReminderLong.intValue() : 15;

                                    NotificationHelper.scheduleTodoTaskNotification(
                                            context, listId, taskId, taskText,
                                            taskScheduleDate, taskScheduleTime, taskReminder
                                    );
                                    count++;
                                }
                            }
                        }
                    }
                    Log.d(TAG, "✅ Rescheduled " + count + " TODO notifications");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load todo lists", e));

        // Reschedule Weekly plans
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int count = 0;
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        if (doc.get("deletedAt") != null) continue;

                        String planId = doc.getId();
                        String planTitle = doc.getString("title");
                        Date startDate = doc.getDate("startDate");

                        Long reminderMinutesLong = doc.getLong("reminderMinutes");
                        int reminderMinutes = reminderMinutesLong != null ? reminderMinutesLong.intValue() : 15;

                        List<Map<String, Object>> tasks = (List<Map<String, Object>>) doc.get("tasks");
                        if (tasks != null && startDate != null) {
                            for (Map<String, Object> task : tasks) {
                                String day = (String) task.get("day");
                                String time = (String) task.get("time");
                                String taskText = (String) task.get("text");
                                String taskId = (String) task.get("id");

                                if (day != null && time != null && !time.isEmpty()) {
                                    NotificationHelper.scheduleWeeklyTaskNotification(
                                            context, taskId, planTitle, taskText,
                                            day, startDate, time, reminderMinutes
                                    );
                                    count++;
                                }
                            }
                        }
                    }
                    Log.d(TAG, "✅ Rescheduled " + count + " WEEKLY notifications");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load weekly plans", e));
    }
}