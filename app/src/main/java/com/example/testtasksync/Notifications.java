package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Notifications extends Fragment {
    private static final String TAG = "NotificationsFragment";

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private RecyclerView upcomingRecyclerView;
    private RecyclerView overdueRecyclerView;
    private TextView upcomingEmptyText;
    private TextView overdueEmptyText;

    private List<NotificationItem> upcomingList = new ArrayList<>();
    private List<NotificationItem> overdueList = new ArrayList<>();

    private NotificationAdapter upcomingAdapter;
    private NotificationAdapter overdueAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Initialize views
        upcomingRecyclerView = view.findViewById(R.id.upcomingRecyclerView);
        overdueRecyclerView = view.findViewById(R.id.overdueRecyclerView);
        upcomingEmptyText = view.findViewById(R.id.upcomingEmptyText);
        overdueEmptyText = view.findViewById(R.id.overdueEmptyText);

        // Setup RecyclerViews
        upcomingRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        overdueRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Setup adapters
        upcomingAdapter = new NotificationAdapter(upcomingList, item -> openTask(item));
        overdueAdapter = new NotificationAdapter(overdueList, item -> openTask(item));

        upcomingRecyclerView.setAdapter(upcomingAdapter);
        overdueRecyclerView.setAdapter(overdueAdapter);

        // Load notifications
        loadNotifications();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh notifications when fragment becomes visible
        loadNotifications();
    }

    private void loadNotifications() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        upcomingList.clear();
        overdueList.clear();

        Calendar now = Calendar.getInstance();
        Date currentDate = now.getTime();

        // Load Todo tasks with schedules
        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .get()
                .addOnSuccessListener(todoSnapshots -> {
                    for (QueryDocumentSnapshot todoDoc : todoSnapshots) {
                        String listId = todoDoc.getId();
                        String listTitle = todoDoc.getString("title");

                        // Get tasks from this todo list
                        db.collection("users")
                                .document(user.getUid())
                                .collection("todoLists")
                                .document(listId)
                                .collection("tasks")
                                .get()
                                .addOnSuccessListener(taskSnapshots -> {
                                    for (QueryDocumentSnapshot taskDoc : taskSnapshots) {
                                        Boolean isCompleted = taskDoc.getBoolean("isCompleted");
                                        Boolean hasNotification = taskDoc.getBoolean("hasNotification");

                                        // Only show tasks that are not completed and have notifications
                                        if (Boolean.TRUE.equals(isCompleted) ||
                                                !Boolean.TRUE.equals(hasNotification)) {
                                            continue;
                                        }

                                        Timestamp scheduleTimestamp = taskDoc.getTimestamp("scheduleDate");
                                        String scheduleTime = taskDoc.getString("scheduleTime");
                                        String taskText = taskDoc.getString("taskText");

                                        if (scheduleTimestamp != null) {
                                            Date scheduleDate = scheduleTimestamp.toDate();
                                            Calendar taskCalendar = Calendar.getInstance();
                                            taskCalendar.setTime(scheduleDate);

                                            // Set time if available
                                            if (scheduleTime != null && !scheduleTime.isEmpty()) {
                                                try {
                                                    String[] timeParts = scheduleTime.split(":");
                                                    taskCalendar.set(Calendar.HOUR_OF_DAY,
                                                            Integer.parseInt(timeParts[0]));
                                                    taskCalendar.set(Calendar.MINUTE,
                                                            Integer.parseInt(timeParts[1]));
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Error parsing time", e);
                                                }
                                            }

                                            NotificationItem item = new NotificationItem(
                                                    listId,
                                                    listTitle,
                                                    taskText,
                                                    taskCalendar.getTime(),
                                                    scheduleTime,
                                                    "todo"
                                            );

                                            // Categorize as upcoming or overdue
                                            if (taskCalendar.getTimeInMillis() < now.getTimeInMillis()) {
                                                overdueList.add(item);
                                            } else {
                                                upcomingList.add(item);
                                            }
                                        }
                                    }
                                    updateUI();
                                });
                    }
                });

        // Load Weekly tasks with schedules
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .get()
                .addOnSuccessListener(weeklySnapshots -> {
                    for (QueryDocumentSnapshot weeklyDoc : weeklySnapshots) {
                        String planId = weeklyDoc.getId();
                        String planTitle = weeklyDoc.getString("title");
                        Timestamp startTimestamp = weeklyDoc.getTimestamp("startDate");
                        String time = weeklyDoc.getString("time");

                        if (startTimestamp == null || time == null || time.isEmpty()) {
                            continue;
                        }

                        // Get tasks from this weekly plan
                        db.collection("users")
                                .document(user.getUid())
                                .collection("weeklyPlans")
                                .document(planId)
                                .collection("tasks")
                                .get()
                                .addOnSuccessListener(taskSnapshots -> {
                                    for (QueryDocumentSnapshot taskDoc : taskSnapshots) {
                                        Boolean isCompleted = taskDoc.getBoolean("isCompleted");

                                        // Only show incomplete tasks
                                        if (Boolean.TRUE.equals(isCompleted)) {
                                            continue;
                                        }

                                        String day = taskDoc.getString("day");
                                        String taskText = taskDoc.getString("taskText");

                                        if (day != null) {
                                            // Calculate the date for this day
                                            Calendar taskDate = Calendar.getInstance();
                                            taskDate.setTime(startTimestamp.toDate());

                                            int targetDay = getDayOfWeek(day);
                                            while (taskDate.get(Calendar.DAY_OF_WEEK) != targetDay) {
                                                taskDate.add(Calendar.DAY_OF_MONTH, 1);
                                            }

                                            // Set time
                                            try {
                                                String[] timeParts = time.split(":");
                                                taskDate.set(Calendar.HOUR_OF_DAY,
                                                        Integer.parseInt(timeParts[0]));
                                                taskDate.set(Calendar.MINUTE,
                                                        Integer.parseInt(timeParts[1]));
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error parsing time", e);
                                            }

                                            NotificationItem item = new NotificationItem(
                                                    planId,
                                                    planTitle + " - " + day,
                                                    taskText,
                                                    taskDate.getTime(),
                                                    time,
                                                    "weekly"
                                            );

                                            // Categorize as upcoming or overdue
                                            if (taskDate.getTimeInMillis() < now.getTimeInMillis()) {
                                                overdueList.add(item);
                                            } else {
                                                upcomingList.add(item);
                                            }
                                        }
                                    }
                                    updateUI();
                                });
                    }
                });
    }

    private void updateUI() {
        // Sort by date (closest first)
        upcomingList.sort((a, b) -> a.getDueDate().compareTo(b.getDueDate()));
        overdueList.sort((a, b) -> b.getDueDate().compareTo(a.getDueDate())); // Most overdue first

        // Update adapters
        upcomingAdapter.notifyDataSetChanged();
        overdueAdapter.notifyDataSetChanged();

        // Show/hide empty states
        if (upcomingList.isEmpty()) {
            upcomingRecyclerView.setVisibility(View.GONE);
            upcomingEmptyText.setVisibility(View.VISIBLE);
        } else {
            upcomingRecyclerView.setVisibility(View.VISIBLE);
            upcomingEmptyText.setVisibility(View.GONE);
        }

        if (overdueList.isEmpty()) {
            overdueRecyclerView.setVisibility(View.GONE);
            overdueEmptyText.setVisibility(View.VISIBLE);
        } else {
            overdueRecyclerView.setVisibility(View.VISIBLE);
            overdueEmptyText.setVisibility(View.GONE);
        }

        Log.d(TAG, "✅ Notifications updated - Upcoming: " + upcomingList.size() +
                ", Overdue: " + overdueList.size());
    }

    private void openTask(NotificationItem item) {
        if ("todo".equals(item.getType())) {
            Intent intent = new Intent(getContext(), TodoActivity.class);
            intent.putExtra("listId", item.getSourceId());
            startActivity(intent);
        } else if ("weekly".equals(item.getType())) {
            Intent intent = new Intent(getContext(), WeeklyActivity.class);
            intent.putExtra("planId", item.getSourceId());
            startActivity(intent);
        }
    }

    private int getDayOfWeek(String day) {
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
}