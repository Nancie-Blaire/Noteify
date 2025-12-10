package com.example.testtasksync;
// COMMENT

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task; // <-- ADDED
import com.google.android.gms.tasks.Tasks; // <-- ADDED
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot; // <-- ADDED

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // Track deleted schedules
    private Set<String> deletedTodoLists = new HashSet<>();
    private Set<String> deletedWeeklyPlans = new HashSet<>();

    // Counter to track the main loading operations (Todo and Weekly) <-- ADDED
    private int pendingLoads = 0;

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
        upcomingAdapter = new NotificationAdapter(upcomingList, new NotificationAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(NotificationItem item) {
                openTask(item);
            }
        });

        overdueAdapter = new NotificationAdapter(overdueList, new NotificationAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(NotificationItem item) {
                openTask(item);
            }
        });

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

    /**
     * Decrements the pendingLoads counter and calls updateUI() when all main loads are complete.
     */
    private void checkAndCallUpdateUI() { // <-- ADDED
        pendingLoads--;
        if (pendingLoads <= 0) {
            // Ensure UI update happens only once all asynchronous tasks are done
            updateUI();
        }
    }


    private void loadNotifications() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Clear lists before loading
        upcomingList.clear();
        overdueList.clear();
        deletedTodoLists.clear();
        deletedWeeklyPlans.clear();

        // Reset counter and set number of main tasks to wait for (loadTodoTasks and loadWeeklyTasks) <-- CHANGED
        pendingLoads = 2;

        Calendar now = Calendar.getInstance();

        // First, get list of deleted schedules
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .get()
                .addOnSuccessListener(scheduleSnapshots -> {
                    // Build list of deleted source IDs
                    for (QueryDocumentSnapshot doc : scheduleSnapshots) {
                        if (doc.get("deletedAt") != null) {
                            String category = doc.getString("category");
                            String sourceId = doc.getString("sourceId");

                            if (sourceId != null) {
                                if ("todo".equals(category)) {
                                    deletedTodoLists.add(sourceId);
                                    Log.d(TAG, "Marking todo as deleted: " + sourceId);
                                } else if ("weekly".equals(category)) {
                                    deletedWeeklyPlans.add(sourceId);
                                    Log.d(TAG, "Marking weekly as deleted: " + sourceId);
                                }
                            }
                        }
                    }

                    // Now load tasks, coordinated by pendingLoads
                    loadTodoTasks(user, now);
                    loadWeeklyTasks(user, now);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading schedules", e);
                    // Continue anyway, but still wait for the two main loads
                    loadTodoTasks(user, now);
                    loadWeeklyTasks(user, now);
                });
    }

    private void loadTodoTasks(FirebaseUser user, Calendar now) {
        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .get()
                .addOnSuccessListener(todoSnapshots -> {

                    // List to hold all async task fetches for individual todo lists <-- ADDED
                    final List<Task<QuerySnapshot>> taskFetchTasks = new ArrayList<>();

                    if (todoSnapshots.isEmpty()) {
                        checkAndCallUpdateUI(); // No lists, signal completion immediately
                        return;
                    }

                    for (QueryDocumentSnapshot todoDoc : todoSnapshots) {
                        String listId = todoDoc.getId();

                        // Skip if this todo list is marked as deleted
                        if (deletedTodoLists.contains(listId)) {
                            Log.d(TAG, "Skipping deleted todo list: " + listId);
                            continue;
                        }

                        String listTitle = todoDoc.getString("title");

                        // Get tasks from this todo list and add the Task to the list <-- CHANGED
                        Task<QuerySnapshot> fetchTasksTask = db.collection("users")
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
                                                // Ang .contains() ay gumagana na ng tama dahil sa pagbabago sa NotificationItem.java
                                                if (!overdueList.contains(item)) {
                                                    overdueList.add(item);
                                                }
                                            } else {
                                                if (!upcomingList.contains(item)) {
                                                    upcomingList.add(item);
                                                }
                                            }
                                        }
                                    }
                                    // REMOVED: updateUI();
                                });

                        taskFetchTasks.add(fetchTasksTask);
                    }

                    // Wait for all inner tasks (task fetches) to complete before signaling loadTodoTasks completion <-- ADDED
                    Tasks.whenAllComplete(taskFetchTasks)
                            .addOnCompleteListener(task -> {
                                checkAndCallUpdateUI(); // loadTodoTasks is complete
                            });

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading todo lists", e);
                    checkAndCallUpdateUI(); // Signal completion even on failure
                });
    }

    private void loadWeeklyTasks(FirebaseUser user, Calendar now) {
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .get()
                .addOnSuccessListener(weeklySnapshots -> {

                    final List<Task<QuerySnapshot>> taskFetchTasks = new ArrayList<>();

                    if (weeklySnapshots.isEmpty()) {
                        checkAndCallUpdateUI();
                        return;
                    }

                    for (QueryDocumentSnapshot weeklyDoc : weeklySnapshots) {
                        String planId = weeklyDoc.getId();

                        if (deletedWeeklyPlans.contains(planId)) {
                            Log.d(TAG, "Skipping deleted weekly plan: " + planId);
                            continue;
                        }

                        String planTitle = weeklyDoc.getString("title");
                        Timestamp startTimestamp = weeklyDoc.getTimestamp("startDate");

                        if (startTimestamp == null) {
                            continue;
                        }

                        // ✅ Load day schedules for this plan (NOW SUPPORTS MULTIPLE PER DAY)
                        Task<QuerySnapshot> fetchTasksTask = db.collection("users")
                                .document(user.getUid())
                                .collection("weeklyPlans")
                                .document(planId)
                                .collection("tasks")
                                .get()
                                .continueWithTask(taskResult -> {
                                    // Load ALL day schedules (not just one per day)
                                    return db.collection("users")
                                            .document(user.getUid())
                                            .collection("weeklyPlans")
                                            .document(planId)
                                            .collection("daySchedules")
                                            .orderBy("scheduleNumber")
                                            .get()
                                            .addOnSuccessListener(dayScheduleSnapshots -> {
                                                // ✅ Group schedules by day (List instead of single object)
                                                Map<String, List<DaySchedule>> daySchedulesMap = new HashMap<>();

                                                for (QueryDocumentSnapshot dayDoc : dayScheduleSnapshots) {
                                                    DaySchedule schedule = dayDoc.toObject(DaySchedule.class);
                                                    String day = schedule.getDay();

                                                    if (!daySchedulesMap.containsKey(day)) {
                                                        daySchedulesMap.put(day, new ArrayList<>());
                                                    }
                                                    daySchedulesMap.get(day).add(schedule);
                                                }

                                                // Process tasks with day schedules
                                                QuerySnapshot taskSnapshots = taskResult.getResult();
                                                if (taskSnapshots != null) {
                                                    for (QueryDocumentSnapshot taskDoc : taskSnapshots) {
                                                        Boolean isCompleted = taskDoc.getBoolean("isCompleted");

                                                        if (Boolean.TRUE.equals(isCompleted)) {
                                                            continue;
                                                        }

                                                        String day = taskDoc.getString("day");
                                                        String taskText = taskDoc.getString("taskText");

                                                        if (day != null) {
                                                            // ✅ Check if this day has specific schedules (plural)
                                                            List<DaySchedule> daySchedules = daySchedulesMap.get(day);

                                                            if (daySchedules != null && !daySchedules.isEmpty()) {
                                                                // ✅ Create notification item for EACH schedule
                                                                for (DaySchedule daySchedule : daySchedules) {
                                                                    if (daySchedule.getDate() != null) {
                                                                        Calendar taskDate = Calendar.getInstance();
                                                                        taskDate.setTime(daySchedule.getDate().toDate());
                                                                        String time = daySchedule.getTime();

                                                                        // Set time if available
                                                                        if (time != null && !time.isEmpty()) {
                                                                            try {
                                                                                String[] timeParts = time.split(":");
                                                                                taskDate.set(Calendar.HOUR_OF_DAY,
                                                                                        Integer.parseInt(timeParts[0]));
                                                                                taskDate.set(Calendar.MINUTE,
                                                                                        Integer.parseInt(timeParts[1]));
                                                                            } catch (Exception e) {
                                                                                Log.e(TAG, "Error parsing time", e);
                                                                            }
                                                                        }

                                                                        // ✅ Include schedule number in title
                                                                        String itemTitle = planTitle + " - " + day;
                                                                        if (daySchedules.size() > 1) {
                                                                            itemTitle += " (Schedule " +
                                                                                    daySchedule.getScheduleNumber() + ")";
                                                                        }

                                                                        NotificationItem item = new NotificationItem(
                                                                                planId,
                                                                                itemTitle,
                                                                                taskText,
                                                                                taskDate.getTime(),
                                                                                time,
                                                                                "weekly"
                                                                        );

                                                                        // ✅ FIX: Compare actual task time, not notification time
                                                                        // Task is overdue only if the scheduled time has passed
                                                                        if (taskDate.getTimeInMillis() < now.getTimeInMillis()) {
                                                                            if (!overdueList.contains(item)) {
                                                                                overdueList.add(item);
                                                                            }
                                                                        } else {
                                                                            if (!upcomingList.contains(item)) {
                                                                                upcomingList.add(item);
                                                                            }
                                                                        }

                                                                        Log.d(TAG, "✅ Added notification for " + day +
                                                                                " - Schedule " + daySchedule.getScheduleNumber());
                                                                    }
                                                                }
                                                            } else {
                                                                // ✅ No specific schedules - use default start date
                                                                Calendar taskDate = Calendar.getInstance();
                                                                taskDate.setTime(startTimestamp.toDate());
                                                                int targetDay = getDayOfWeek(day);

                                                                while (taskDate.get(Calendar.DAY_OF_WEEK) != targetDay) {
                                                                    taskDate.add(Calendar.DAY_OF_MONTH, 1);
                                                                }

                                                                // ✅ FIX: Set time from task's scheduleTime if available
                                                                Timestamp taskScheduleTimestamp = taskDoc.getTimestamp("scheduleDate");
                                                                String taskScheduleTime = taskDoc.getString("scheduleTime");

                                                                if (taskScheduleTime != null && !taskScheduleTime.isEmpty()) {
                                                                    try {
                                                                        String[] timeParts = taskScheduleTime.split(":");
                                                                        taskDate.set(Calendar.HOUR_OF_DAY,
                                                                                Integer.parseInt(timeParts[0]));
                                                                        taskDate.set(Calendar.MINUTE,
                                                                                Integer.parseInt(timeParts[1]));
                                                                    } catch (Exception e) {
                                                                        Log.e(TAG, "Error parsing task time", e);
                                                                    }
                                                                }

                                                                NotificationItem item = new NotificationItem(
                                                                        planId,
                                                                        planTitle + " - " + day,
                                                                        taskText,
                                                                        taskDate.getTime(),
                                                                        taskScheduleTime != null ? taskScheduleTime : "",
                                                                        "weekly"
                                                                );

                                                                if (taskDate.getTimeInMillis() < now.getTimeInMillis()) {
                                                                    if (!overdueList.contains(item)) {
                                                                        overdueList.add(item);
                                                                    }
                                                                } else {
                                                                    if (!upcomingList.contains(item)) {
                                                                        upcomingList.add(item);
                                                                    }
                                                                }

                                                                Log.d(TAG, "✅ Added notification for " + day +
                                                                        " (using default schedule)");
                                                            }
                                                        }
                                                    }
                                                }
                                            });
                                });

                        taskFetchTasks.add(fetchTasksTask);
                    }

                    Tasks.whenAllComplete(taskFetchTasks)
                            .addOnCompleteListener(task -> {
                                checkAndCallUpdateUI();
                            });

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading weekly plans", e);
                    checkAndCallUpdateUI();
                });
    }

    private void updateUI() {
        // Sort by date (closest first)
        Collections.sort(upcomingList, new Comparator<NotificationItem>() {
            @Override
            public int compare(NotificationItem a, NotificationItem b) {
                return a.getDueDate().compareTo(b.getDueDate());
            }
        });

        // Most overdue first
        Collections.sort(overdueList, new Comparator<NotificationItem>() {
            @Override
            public int compare(NotificationItem a, NotificationItem b) {
                return b.getDueDate().compareTo(a.getDueDate());
            }
        });

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