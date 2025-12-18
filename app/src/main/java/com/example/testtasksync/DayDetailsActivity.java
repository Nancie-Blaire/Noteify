package com.example.testtasksync;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DayDetailsActivity extends AppCompatActivity {

    private static final String TAG = "DayDetailsActivity";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration scheduleListener;
    private ListenerRegistration weeklyPlansListener; // ✅ NEW: Real-time listener for weekly plans
    private ListenerRegistration todoTasksListener;
    private TextView selectedDateText;
    private LinearLayout holidayBanner;
    private TextView holidayNameText;
    private RecyclerView schedulesRecyclerView;
    private LinearLayout emptyStateLayout;
    private ImageView addScheduleButton, backButton, deleteButton;

    private List<Schedule> scheduleList;
    private DayScheduleAdapter adapter;
    private String dateKey;
    private Calendar selectedDate;

    private List<Schedule> selectedSchedules = new ArrayList<>();

    private boolean isDeleteMode = false;

    private Map<String, List<TaskScheduleData>> weeklyTaskSchedules = new HashMap<>();

    private static class WeeklyDialogTask {
        String taskText = "";
        Calendar scheduleDate = null;
        String scheduleTime = null;
        boolean hasNotification = false;
        int notificationMinutes = 60;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_day_details);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        dateKey = getIntent().getStringExtra("dateKey");
        if (dateKey == null) {
            finish();
            return;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            selectedDate = Calendar.getInstance();
            selectedDate.setTime(sdf.parse(dateKey));
        } catch (Exception e) {
            Log.e(TAG, "Error parsing date", e);
            finish();
            return;
        }

        selectedDateText = findViewById(R.id.selectedDateText);
        holidayBanner = findViewById(R.id.holidayBanner);
        holidayNameText = findViewById(R.id.holidayNameText);
        schedulesRecyclerView = findViewById(R.id.schedulesRecyclerView);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        addScheduleButton = findViewById(R.id.addScheduleButton);
        backButton = findViewById(R.id.backButton);
        deleteButton = findViewById(R.id.deleteButton);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d", Locale.getDefault());
        selectedDateText.setText(dateFormat.format(selectedDate.getTime()));

        checkIfHoliday();

        scheduleList = new ArrayList<>();

        // ✅✅✅ CRITICAL FIX: Create adapter ONCE with ALL listeners set IMMEDIATELY
        adapter = new DayScheduleAdapter(scheduleList, new DayScheduleAdapter.OnScheduleClickListener() {
            @Override
            public void onScheduleClick(Schedule schedule) {
                if (isDeleteMode) {
                    toggleScheduleSelection(schedule);
                } else {
                    openScheduleSource(schedule);
                }
            }

            @Override
            public void onScheduleLongClick(Schedule schedule) {
                enterDeleteMode();
                toggleScheduleSelection(schedule);
            }
        });

        // ✅✅✅ Set completion listener RIGHT AFTER creating adapter
        adapter.setCompletionListener(new DayScheduleAdapter.OnTaskCompletionListener() {
            @Override
            public void onTaskCompleted(Schedule schedule) {
                Log.d(TAG, "🎯 onCreate: completionListener TRIGGERED for: " + schedule.getTitle());
                handleTaskCompletion(schedule);
            }
        });

        Log.d(TAG, "✅ Adapter created and completion listener set!");

        schedulesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        schedulesRecyclerView.setAdapter(adapter);

        backButton.setOnClickListener(v -> finish());

        addScheduleButton.setOnClickListener(v -> {
            showAddScheduleDialog();
        });

        deleteButton.setOnClickListener(v -> {
            if (isDeleteMode) {
                showDeleteConfirmation();
            }
        });

        // ✅ Load schedules AFTER adapter is fully configured
        loadSchedulesForDate();
    }
    private static class TaskScheduleData {
        Calendar date;
        String time;
        boolean hasNotification;
        int reminderMinutes;

        TaskScheduleData(Calendar date, String time, boolean hasNotification, int reminderMinutes) {
            this.date = date;
            this.time = time;
            this.hasNotification = hasNotification;
            this.reminderMinutes = reminderMinutes;
        }
    }
    private static final long REMOVAL_DELAY_MS = 600;

    private void handleTaskCompletion(Schedule schedule) {
        Log.d(TAG, "🔥 handleTaskCompletion CALLED for: " + schedule.getTitle());

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Log.e(TAG, "❌ User is null!");
            return;
        }

        String category = schedule.getCategory();
        String sourceId = schedule.getSourceId();

        // ✅ CRITICAL FIX: Find and remove from list IMMEDIATELY
        int positionIndex = -1;
        for (int i = 0; i < scheduleList.size(); i++) {
            if (scheduleList.get(i).getId().equals(schedule.getId())) {
                positionIndex = i;
                break;
            }
        }

        if (positionIndex == -1) {
            Log.e(TAG, "❌ Item not found in list!");
            return;
        }

        // ✅ Make final for lambda
        final int finalPosition = positionIndex;

        // ✅ IMPORTANT: Use Handler.post to ensure UI update happens after current pass
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            // Remove from list
            scheduleList.remove(finalPosition);

            // Notify adapter
            adapter.notifyItemRemoved(finalPosition);

            // ✅ CRITICAL: Update all remaining items
            if (finalPosition < scheduleList.size()) {
                adapter.notifyItemRangeChanged(finalPosition, scheduleList.size());
            }

            updateScheduleDisplay();

            Log.d(TAG, "📊 After removal, list size: " + scheduleList.size());
        });

        // ✅ Update Firestore in parallel
        if ("weekly".equals(category)) {
            String taskId = schedule.getId().replace(sourceId + "_", "");

            db.collection("users")
                    .document(user.getUid())
                    .collection("weeklyPlans")
                    .document(sourceId)
                    .collection("tasks")
                    .document(taskId)
                    .update("isCompleted", true)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ Weekly task marked as completed");
                        Toast.makeText(this, "✓ Task completed", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to complete weekly task", e);
                        Toast.makeText(this, "Failed to complete task", Toast.LENGTH_SHORT).show();

                        // ✅ Rollback: Re-add the item
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            scheduleList.add(finalPosition, schedule);
                            adapter.notifyItemInserted(finalPosition);
                            updateScheduleDisplay();
                        });
                    });

        } else if ("todo_task".equals(category)) {
            String taskId = schedule.getId().replace(sourceId + "_task_", "");

            db.collection("users")
                    .document(user.getUid())
                    .collection("todoLists")
                    .document(sourceId)
                    .collection("tasks")
                    .document(taskId)
                    .update("isCompleted", true)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ Todo task marked as completed");
                        Toast.makeText(this, "✓ Task completed", Toast.LENGTH_SHORT).show();
                        updateTodoListCompletionCount(user.getUid(), sourceId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to complete todo task", e);
                        Toast.makeText(this, "Failed to complete task", Toast.LENGTH_SHORT).show();

                        // ✅ Rollback: Re-add the item
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            scheduleList.add(finalPosition, schedule);
                            adapter.notifyItemInserted(finalPosition);
                            updateScheduleDisplay();
                        });
                    });
        } else {
            Log.e(TAG, "❌ Unknown category: " + category);
        }
    }

    private void updateTodoListCompletionCount(String userId, String listId) {
        db.collection("users")
                .document(userId)
                .collection("todoLists")
                .document(listId)
                .collection("tasks")
                .get()
                .addOnSuccessListener(taskSnapshots -> {
                    int totalTasks = 0;
                    int completedTasks = 0;

                    for (QueryDocumentSnapshot doc : taskSnapshots) {
                        String taskText = doc.getString("taskText");
                        if (taskText != null && !taskText.trim().isEmpty()) {
                            totalTasks++;
                            Boolean isCompleted = doc.getBoolean("isCompleted");
                            if (isCompleted != null && isCompleted) {
                                completedTasks++;
                            }
                        }
                    }

                    // Update the list metadata
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("taskCount", totalTasks);
                    updates.put("completedCount", completedTasks);

                    db.collection("users")
                            .document(userId)
                            .collection("todoLists")
                            .document(listId)
                            .update(updates);
                });
    }
    private void checkIfHoliday() {
        int year = selectedDate.get(Calendar.YEAR);
        int month = selectedDate.get(Calendar.MONTH);
        int day = selectedDate.get(Calendar.DAY_OF_MONTH);

        if (PhilippineHolidays.isHoliday(year, month, day)) {
            String holidayName = PhilippineHolidays.getHolidayName(year, month, day);
            holidayNameText.setText("***" + holidayName);
            holidayBanner.setVisibility(View.VISIBLE);
        } else {
            holidayBanner.setVisibility(View.GONE);
        }
    }

    private void loadSchedulesForDate() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ✅ Remove old listeners
        if (scheduleListener != null) {
            scheduleListener.remove();
            scheduleListener = null;
        }
        if (weeklyPlansListener != null) {
            weeklyPlansListener.remove();
            weeklyPlansListener = null;
        }

        Calendar startOfDay = (Calendar) selectedDate.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);

        Calendar endOfDay = (Calendar) selectedDate.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);

        Timestamp startTimestamp = new Timestamp(startOfDay.getTime());
        Timestamp endTimestamp = new Timestamp(endOfDay.getTime());

        scheduleListener = db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .whereGreaterThanOrEqualTo("date", startTimestamp)
                .whereLessThanOrEqualTo("date", endTimestamp)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }

                    // ✅ Clear only non-weekly items
                    for (int i = scheduleList.size() - 1; i >= 0; i--) {
                        if (!"weekly".equals(scheduleList.get(i).getCategory())) {
                            scheduleList.remove(i);
                        }
                    }

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            // ✅ SKIP DELETED ITEMS
                            if (doc.get("deletedAt") != null) {
                                continue;
                            }

                            Schedule schedule = doc.toObject(Schedule.class);
                            schedule.setId(doc.getId());

                            if (!"weekly".equals(schedule.getCategory())) {
                                scheduleList.add(schedule);
                            }
                        }
                    }

                    // Sort by time
                    Collections.sort(scheduleList, (s1, s2) -> {
                        String t1 = s1.getTime() != null ? s1.getTime() : "";
                        String t2 = s2.getTime() != null ? s2.getTime() : "";
                        return t1.compareTo(t2);
                    });

                    // ✅ Load weekly plans (will add to existing schedules)
                    loadWeeklyPlansForDate();
                    //indiv to do
                    loadScheduledTodoTasksForDate();
                });
    }

    private void loadScheduledTodoTasksForDate() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Remove old listener
        if (todoTasksListener != null) {
            todoTasksListener.remove();
            todoTasksListener = null;
        }

        Calendar startOfDay = (Calendar) selectedDate.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);

        Calendar endOfDay = (Calendar) selectedDate.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);

        Timestamp startTimestamp = new Timestamp(startOfDay.getTime());
        Timestamp endTimestamp = new Timestamp(endOfDay.getTime());

        Log.d(TAG, "Loading scheduled todo tasks for date");

        todoTasksListener = db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .addSnapshotListener((listSnapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Todo lists listen failed.", e);
                        return;
                    }

                    // Clear old
                    for (int i = scheduleList.size() - 1; i >= 0; i--) {
                        if ("todo_task".equals(scheduleList.get(i).getCategory())) {
                            scheduleList.remove(i);
                        }
                    }

                    if (listSnapshots == null || listSnapshots.isEmpty()) {
                        updateScheduleDisplay();
                        return;
                    }

                    AtomicInteger completedQueries = new AtomicInteger(0);
                    int totalLists = listSnapshots.size();

                    for (QueryDocumentSnapshot listDoc : listSnapshots) {
                        // ✅ SKIP DELETED TODO LISTS
                        if (listDoc.get("deletedAt") != null) {
                            Log.d(TAG, "Skipping deleted todo list: " + listDoc.getId());
                            int completed = completedQueries.incrementAndGet();
                            if (completed == totalLists) {
                                updateScheduleDisplay();
                            }
                            continue;
                        }

                        String listId = listDoc.getId();
                        String listTitle = listDoc.getString("title");

                        db.collection("users")
                                .document(user.getUid())
                                .collection("todoLists")
                                .document(listId)
                                .collection("tasks")
                                .whereGreaterThanOrEqualTo("scheduleDate", startTimestamp)
                                .whereLessThanOrEqualTo("scheduleDate", endTimestamp)
                                .get()
                                .addOnSuccessListener(taskSnapshots -> {
                                    for (QueryDocumentSnapshot taskDoc : taskSnapshots) {
                                        Timestamp scheduleDate = taskDoc.getTimestamp("scheduleDate");
                                        String taskText = taskDoc.getString("taskText");
                                        Boolean isCompleted = taskDoc.getBoolean("isCompleted");
                                        String scheduleTime = taskDoc.getString("scheduleTime");

                                        if (taskText != null && !taskText.trim().isEmpty()) {
                                            // ✅ Skip completed tasks
                                            if (isCompleted != null && isCompleted) {
                                                Log.d(TAG, "Skipping completed task: " + taskText);
                                                continue;
                                            }

                                            Schedule taskSchedule = new Schedule();
                                            taskSchedule.setId(listId + "_task_" + taskDoc.getId());
                                            taskSchedule.setTitle(taskText);
                                            taskSchedule.setDescription("From: " + listTitle);
                                            taskSchedule.setCategory("todo_task");
                                            taskSchedule.setSourceId(listId);
                                            taskSchedule.setCompleted(false);
                                            taskSchedule.setDate(scheduleDate);

                                            if (scheduleTime != null && !scheduleTime.isEmpty()) {
                                                taskSchedule.setTime(scheduleTime);
                                            }

                                            boolean exists = false;
                                            for (Schedule s : scheduleList) {
                                                if (s.getId().equals(taskSchedule.getId())) {
                                                    exists = true;
                                                    break;
                                                }
                                            }

                                            if (!exists) {
                                                scheduleList.add(taskSchedule);
                                            }
                                        }
                                    }

                                    int completed = completedQueries.incrementAndGet();
                                    if (completed == totalLists) {
                                        updateScheduleDisplay();
                                    }
                                })
                                .addOnFailureListener(err -> {
                                    int completed = completedQueries.incrementAndGet();
                                    if (completed == totalLists) {
                                        updateScheduleDisplay();
                                    }
                                });
                    }
                });
    }
    private void loadWeeklyPlansForDate() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy (EEE)", Locale.getDefault());
        Log.d(TAG, "📅 Loading weekly plans for date: " + sdf.format(selectedDate.getTime()));
        Log.d(TAG, "🔢 Day of week: " + getDayNameFromCalendar(selectedDate));

        // ✅ Add real-time listener for weekly plans
        weeklyPlansListener = db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Weekly plans listen failed.", e);
                        return;
                    }

                    // ✅ Clear only weekly items from list
                    for (int i = scheduleList.size() - 1; i >= 0; i--) {
                        if ("weekly".equals(scheduleList.get(i).getCategory())) {
                            scheduleList.remove(i);
                        }
                    }

                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "⚠️ No weekly plans found");
                        updateScheduleDisplay();
                        return;
                    }

                    Log.d(TAG, "📋 Found " + queryDocumentSnapshots.size() + " weekly plan(s)");

                    List<String> plansInRange = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        // ✅ SKIP DELETED WEEKLY PLANS
                        if (doc.get("deletedAt") != null) {
                            Log.d(TAG, "⭕️ Skipping deleted weekly plan: " + doc.getId());
                            continue;
                        }

                        Timestamp startDateTimestamp = doc.getTimestamp("startDate");
                        Timestamp endDateTimestamp = doc.getTimestamp("endDate");

                        if (startDateTimestamp != null && endDateTimestamp != null) {
                            Calendar planStart = Calendar.getInstance();
                            planStart.setTime(startDateTimestamp.toDate());
                            planStart.set(Calendar.HOUR_OF_DAY, 0);
                            planStart.set(Calendar.MINUTE, 0);
                            planStart.set(Calendar.SECOND, 0);
                            planStart.set(Calendar.MILLISECOND, 0);

                            Calendar planEnd = Calendar.getInstance();
                            planEnd.setTime(endDateTimestamp.toDate());
                            planEnd.set(Calendar.HOUR_OF_DAY, 23);
                            planEnd.set(Calendar.MINUTE, 59);
                            planEnd.set(Calendar.SECOND, 59);
                            planEnd.set(Calendar.MILLISECOND, 999);

                            SimpleDateFormat planSdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                            Log.d(TAG, "📅 Plan '" + doc.getId() + "' range: " +
                                    planSdf.format(planStart.getTime()) + " - " + planSdf.format(planEnd.getTime()));

                            Calendar selectedDateNormalized = (Calendar) selectedDate.clone();
                            selectedDateNormalized.set(Calendar.HOUR_OF_DAY, 0);
                            selectedDateNormalized.set(Calendar.MINUTE, 0);
                            selectedDateNormalized.set(Calendar.SECOND, 0);
                            selectedDateNormalized.set(Calendar.MILLISECOND, 0);

                            boolean isInRange = !selectedDateNormalized.before(planStart) &&
                                    !selectedDateNormalized.after(planEnd);

                            Log.d(TAG, "🎯 Selected date: " + planSdf.format(selectedDateNormalized.getTime()));
                            Log.d(TAG, "🎯 Is in range? " + isInRange);

                            if (isInRange) {
                                plansInRange.add(doc.getId());
                            } else {
                                Log.d(TAG, "⭕️ Selected date not in plan range - skipping");
                            }
                        } else {
                            Log.e(TAG, "❌ Plan missing start/end date");
                        }
                    }

                    if (plansInRange.isEmpty()) {
                        Log.d(TAG, "⚠️ No plans in range for selected date");
                        updateScheduleDisplay();
                    } else {
                        Log.d(TAG, "✅ " + plansInRange.size() + " plan(s) in range");
                        String selectedDayName = getDayNameFromCalendar(selectedDate);

                        AtomicInteger completedQueries = new AtomicInteger(0);
                        int totalQueries = plansInRange.size();

                        for (String planId : plansInRange) {
                            loadWeeklyPlanTasksForSpecificDay(planId, selectedDayName, () -> {
                                int completed = completedQueries.incrementAndGet();
                                Log.d(TAG, "✅ Query " + completed + "/" + totalQueries + " completed");

                                if (completed == totalQueries) {
                                    Log.d(TAG, "🔄 All queries completed, updating UI");
                                    updateScheduleDisplay();
                                }
                            });
                        }
                    }
                });
    }

    private void loadWeeklyPlanTasksForSpecificDay(String planId, String dayName, Runnable onComplete) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        Log.d(TAG, "🔎 Loading tasks for plan: " + planId + ", day: " + dayName);

        // ✅ STEP 1: Load day-specific schedules first (highest priority)
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .collection("daySchedules")
                .whereEqualTo("day", dayName)
                .get()
                .addOnSuccessListener(dayScheduleSnapshots -> {
                    // Store day schedules with their times
                    java.util.Map<Integer, String> dayScheduleTimes = new java.util.HashMap<>();

                    for (QueryDocumentSnapshot dayScheduleDoc : dayScheduleSnapshots) {
                        Long scheduleNumber = dayScheduleDoc.getLong("scheduleNumber");
                        String dayTime = dayScheduleDoc.getString("time");

                        if (scheduleNumber != null && dayTime != null && !dayTime.isEmpty()) {
                            dayScheduleTimes.put(scheduleNumber.intValue(), dayTime);
                            Log.d(TAG, "📅 Found day schedule " + scheduleNumber + " for " + dayName + ": " + dayTime);
                        }
                    }

                    // ✅ STEP 2: Get the weekly plan details (time AND title)
                    db.collection("users")
                            .document(user.getUid())
                            .collection("weeklyPlans")
                            .document(planId)
                            .get()
                            .addOnSuccessListener(planDoc -> {
                                String weeklyPlanTime = "";
                                String weeklyPlanTitle = "Weekly Plan";

                                if (planDoc.exists()) {
                                    weeklyPlanTime = planDoc.getString("time");
                                    weeklyPlanTitle = planDoc.getString("title");
                                    Log.d(TAG, "📅 Weekly plan global time: " + weeklyPlanTime);
                                    Log.d(TAG, "📅 Weekly plan title: " + weeklyPlanTitle);
                                }

                                final String planTime = weeklyPlanTime != null ? weeklyPlanTime : "";
                                final String finalPlanTitle = weeklyPlanTitle;

                                // ✅ STEP 3: Load tasks and apply the correct time
                                db.collection("users")
                                        .document(user.getUid())
                                        .collection("weeklyPlans")
                                        .document(planId)
                                        .collection("tasks")
                                        .whereEqualTo("day", dayName)
                                        .get()
                                        .addOnSuccessListener(taskSnapshots -> {
                                            if (taskSnapshots.isEmpty()) {
                                                Log.d(TAG, "⚠️ No tasks found for " + dayName + " in plan " + planId);
                                            } else {
                                                Log.d(TAG, "📋 Found " + taskSnapshots.size() + " task(s) for " + dayName);
                                            }

                                            for (QueryDocumentSnapshot taskDoc : taskSnapshots) {
                                                String taskText = taskDoc.getString("taskText");
                                                Boolean isCompleted = taskDoc.getBoolean("isCompleted");
                                                String taskDay = taskDoc.getString("day");

                                                Log.d(TAG, "📝 Task: '" + taskText + "', Day: " + taskDay + ", Completed: " + isCompleted);

                                                if (taskText == null || taskText.trim().isEmpty()) {
                                                    Log.d(TAG, "⏭️ Skipping empty task");
                                                    continue;
                                                }

                                                // ✅ SKIP COMPLETED TASKS
                                                if (isCompleted != null && isCompleted) {
                                                    Log.d(TAG, "⏭️ Skipping completed task: " + taskText);
                                                    continue;
                                                }

                                                // ✅ Determine which time to use
                                                // ✅✅✅ CRITICAL FIX: Better time resolution with proper fallback
                                                String timeToUse = null;

                                                // Priority 1: Use day schedule time if available
                                                if (!dayScheduleTimes.isEmpty() && dayScheduleTimes.containsKey(1)) {
                                                    timeToUse = dayScheduleTimes.get(1);
                                                    Log.d(TAG, "⏰ Using day schedule 1 time: " + timeToUse);
                                                }
                                                // Priority 2: Use weekly plan's global time
                                                else if (planTime != null && !planTime.isEmpty()) {
                                                    timeToUse = planTime;
                                                    Log.d(TAG, "⏰ Using weekly plan time: " + timeToUse);
                                                }
                                                // Priority 3: Check if task has its own scheduleTime
                                                else {
                                                    String taskScheduleTime = taskDoc.getString("scheduleTime");
                                                    if (taskScheduleTime != null && !taskScheduleTime.isEmpty()) {
                                                        timeToUse = taskScheduleTime;
                                                        Log.d(TAG, "⏰ Using task's own time: " + timeToUse);
                                                    }
                                                }

                                                Schedule taskSchedule = new Schedule();
                                                taskSchedule.setId(planId + "_" + taskDoc.getId());
                                                taskSchedule.setTitle(taskText);
                                                taskSchedule.setDescription("From: " + finalPlanTitle);
                                                taskSchedule.setCategory("weekly");
                                                taskSchedule.setSourceId(planId);
                                                taskSchedule.setCompleted(false);
                                                taskSchedule.setDate(new Timestamp(selectedDate.getTime()));

                                                // ✅ Set the time (will always have a value if any source has time)
                                                if (timeToUse != null && !timeToUse.isEmpty()) {
                                                    taskSchedule.setTime(timeToUse);
                                                    Log.d(TAG, "✅ Time set for task '" + taskText + "': " + timeToUse);
                                                } else {
                                                    Log.d(TAG, "⚠️ No time available for task '" + taskText + "'");
                                                }

                                                // Check if already exists
                                                boolean exists = false;
                                                for (Schedule s : scheduleList) {
                                                    if (s.getId().equals(taskSchedule.getId())) {
                                                        exists = true;
                                                        break;
                                                    }
                                                }

                                                if (!exists) {
                                                    scheduleList.add(taskSchedule);
                                                    Log.d(TAG, "✅ Added task to schedule list with time: " + timeToUse);
                                                } else {
                                                    Log.d(TAG, "⚠️ Task already in schedule list");
                                                }
                                            }

                                            if (onComplete != null) {
                                                onComplete.run();
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to load weekly plan tasks for " + dayName, e);
                                            if (onComplete != null) {
                                                onComplete.run();
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to load weekly plan details", e);
                                if (onComplete != null) {
                                    onComplete.run();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load day schedules for " + dayName, e);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }
    private String getDayNameFromCalendar(Calendar calendar) {
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        switch (dayOfWeek) {
            case Calendar.SUNDAY: return "Sun";
            case Calendar.MONDAY: return "Mon";
            case Calendar.TUESDAY: return "Tues";
            case Calendar.WEDNESDAY: return "Wed";
            case Calendar.THURSDAY: return "Thur";
            case Calendar.FRIDAY: return "Fri";
            case Calendar.SATURDAY: return "Sat";
            default:
                Log.e(TAG, "❌ Unknown day of week: " + dayOfWeek);
                return "Mon";
        }
    }

    private void updateScheduleDisplay() {
        adapter.notifyDataSetChanged();

        if (scheduleList.isEmpty()) {
            schedulesRecyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            schedulesRecyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    // Add this method to DayDetailsActivity.java
// Replace the existing showScheduleDetailsDialog method with this version:
    private void openScheduleSource(Schedule schedule) {
        String category = schedule.getCategory();
        String sourceId = schedule.getSourceId();

        if (sourceId == null || sourceId.isEmpty()) {
            Toast.makeText(this, "Cannot open this item", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("todo".equals(category)) {
            // Navigate to TodoActivity
            Intent intent = new Intent(this, TodoActivity.class);
            intent.putExtra("listId", sourceId);
            startActivity(intent);
        } else if ("todo_task".equals(category)) {
            // Navigate to TodoActivity (for individual scheduled tasks)
            Intent intent = new Intent(this, TodoActivity.class);
            intent.putExtra("listId", sourceId);
            startActivity(intent);
        } else if ("weekly".equals(category)) {
            // Navigate to WeeklyActivity
            Intent intent = new Intent(this, WeeklyActivity.class);
            intent.putExtra("planId", sourceId);
            startActivity(intent);
        }
    }

    private void enterDeleteMode() {
        isDeleteMode = true;
        deleteButton.setVisibility(View.VISIBLE);
        selectedSchedules.clear();
        adapter.setDeleteMode(true);
    }

    private void exitDeleteMode() {
        isDeleteMode = false;
        deleteButton.setVisibility(View.GONE);
        selectedSchedules.clear();
        adapter.setDeleteMode(false);
    }

    private void toggleScheduleSelection(Schedule schedule) {
        if (selectedSchedules.contains(schedule)) {
            selectedSchedules.remove(schedule);
        } else {
            selectedSchedules.add(schedule);
        }

        if (selectedSchedules.isEmpty()) {
            exitDeleteMode();
        }

        adapter.setSelectedSchedules(selectedSchedules);
        adapter.notifyDataSetChanged();
    }

    private void showDeleteConfirmation() {
        if (selectedSchedules.isEmpty()) return;

        String message = "Delete " + selectedSchedules.size() + " item(s)?";

        new AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> deleteSelectedSchedules())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSelectedSchedules() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        int deleteCount = selectedSchedules.size();

        // ✅ Create a copy to avoid concurrent modification
        List<Schedule> schedulesToDelete = new ArrayList<>(selectedSchedules);

        for (Schedule schedule : schedulesToDelete) {
            String category = schedule.getCategory();

            if ("weekly".equals(category)) {
                // ✅ Delete weekly task
                String sourceId = schedule.getSourceId();
                if (sourceId != null) {
                    String taskId = schedule.getId().replace(sourceId + "_", "");

                    db.collection("users")
                            .document(user.getUid())
                            .collection("weeklyPlans")
                            .document(sourceId)
                            .collection("tasks")
                            .document(taskId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "✅ Weekly task deleted");
                                // ✅ Remove from adapter using the new method
                                adapter.removeSchedule(schedule);
                                updateScheduleDisplay();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ Failed to delete weekly task", e);
                                Toast.makeText(this, "Failed to delete some items", Toast.LENGTH_SHORT).show();
                            });
                }
            } else if ("todo_task".equals(category)) {
                // ✅ Delete individual todo task
                String sourceId = schedule.getSourceId();
                if (sourceId != null) {
                    String taskId = schedule.getId().replace(sourceId + "_task_", "");

                    db.collection("users")
                            .document(user.getUid())
                            .collection("todoLists")
                            .document(sourceId)
                            .collection("tasks")
                            .document(taskId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "✅ Todo task deleted");
                                // ✅ Remove from adapter using the new method
                                adapter.removeSchedule(schedule);
                                updateScheduleDisplay();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ Failed to delete todo task", e);
                                Toast.makeText(this, "Failed to delete some items", Toast.LENGTH_SHORT).show();
                            });
                }
            } else {
                // ✅ Soft delete for "todo" category and others
                db.collection("users")
                        .document(user.getUid())
                        .collection("schedules")
                        .document(schedule.getId())
                        .update("deletedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Schedule soft-deleted (sent to Bin)");
                            // ✅ Remove from adapter using the new method
                            adapter.removeSchedule(schedule);
                            updateScheduleDisplay();

                            // ✅ Also soft delete the source todoList if it exists
                            String sourceId = schedule.getSourceId();
                            if (sourceId != null && !sourceId.isEmpty()) {
                                db.collection("users")
                                        .document(user.getUid())
                                        .collection("todoLists")
                                        .document(sourceId)
                                        .update("deletedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
                                        .addOnSuccessListener(aVoid2 -> {
                                            Log.d(TAG, "✅ TodoList source also soft-deleted");
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to soft-delete todoList source", e);
                                        });
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Failed to soft-delete schedule", e);
                            Toast.makeText(this, "Failed to delete some items", Toast.LENGTH_SHORT).show();
                        });
            }
        }

        Toast.makeText(this, "🗑️ " + deleteCount + " item(s) deleted", Toast.LENGTH_SHORT).show();
        exitDeleteMode();
    }
    private String getCategoryDisplayName(String category) {
        switch (category) {
            case "todo": return "To-Do";
            case "weekly": return "Weekly";
            case "holiday": return "Holiday";
            default: return "To-Do";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ✅ Remove both listeners
        if (scheduleListener != null) {
            scheduleListener.remove();
            scheduleListener = null;
        }
        if (weeklyPlansListener != null) {
            weeklyPlansListener.remove();
            weeklyPlansListener = null;
        }
        if (todoTasksListener != null) {
            todoTasksListener.remove();
            todoTasksListener = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedDate != null) {
            loadSchedulesForDate();
        }
    }

    private void showAddScheduleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_schedule, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        EditText titleInput = dialogView.findViewById(R.id.scheduleTitleInput);
        LinearLayout datePickerButton = dialogView.findViewById(R.id.datePickerButton);
        TextView selectedDateText = dialogView.findViewById(R.id.selectedDateText);
        RadioGroup categoryRadioGroup = dialogView.findViewById(R.id.categoryRadioGroup);
        RadioButton todoRadio = dialogView.findViewById(R.id.todoRadio);
        RadioButton weeklyRadio = dialogView.findViewById(R.id.weeklyRadio);
        LinearLayout timePickerButton = dialogView.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = dialogView.findViewById(R.id.selectedTimeText);
        ImageView clearTimeButton = dialogView.findViewById(R.id.clearTimeButton);
        CheckBox notificationCheckbox = dialogView.findViewById(R.id.notificationCheckbox);
        Spinner reminderTimeSpinner = dialogView.findViewById(R.id.reminderTimeSpinner);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveButton);

        // Dynamic sections
        LinearLayout todoTasksSection = dialogView.findViewById(R.id.todoTasksSection);
        LinearLayout todoTasksContainer = dialogView.findViewById(R.id.todoTasksContainer);
        LinearLayout addTodoTaskButton = dialogView.findViewById(R.id.addTodoTaskButton);
        LinearLayout weeklyTasksSection = dialogView.findViewById(R.id.weeklyTasksSection);
        LinearLayout weeklyDaysContainer = dialogView.findViewById(R.id.weeklyDaysContainer);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        selectedDateText.setText(dateFormat.format(selectedDate.getTime()));

        final String[] selectedTime = {null};
        final Calendar[] selectedScheduleDate = {(Calendar) selectedDate.clone()};
        final Calendar[] customStartDate = {null};
        final Calendar[] customEndDate = {null};
        final String[] selectedCategory = {"todo"};

        // Store task data
        final List<String> todoTasks = new ArrayList<>();
        final Map<String, List<WeeklyDialogTask>> weeklyTasks = new HashMap<>();
        weeklyTasks.put("Mon", new ArrayList<>());
        weeklyTasks.put("Tues", new ArrayList<>());
        weeklyTasks.put("Wed", new ArrayList<>());
        weeklyTasks.put("Thur", new ArrayList<>());
        weeklyTasks.put("Fri", new ArrayList<>());
        weeklyTasks.put("Sat", new ArrayList<>());
        weeklyTasks.put("Sun", new ArrayList<>());

        todoRadio.setChecked(true);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.reminder_times, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderTimeSpinner.setAdapter(adapter);

        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            reminderTimeSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Category change listener
        categoryRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.todoRadio) {
                selectedCategory[0] = "todo";
                todoTasksSection.setVisibility(View.VISIBLE);
                weeklyTasksSection.setVisibility(View.GONE);
                selectedDateText.setText(dateFormat.format(selectedDate.getTime()));
                customStartDate[0] = null;
                customEndDate[0] = null;
            } else if (checkedId == R.id.weeklyRadio) {
                selectedCategory[0] = "weekly";
                todoTasksSection.setVisibility(View.GONE);
                weeklyTasksSection.setVisibility(View.VISIBLE);

                // ✅ SHOW week selection prompt (NOT auto-set)
                selectedDateText.setText("Select week range");

                // ✅ Build weekly UI with schedule buttons
                buildWeeklyDaysUIWithScheduleButtons(weeklyDaysContainer, weeklyTasks, customStartDate, customEndDate);
            }
        });

        // Add task button for To-Do
        addTodoTaskButton.setOnClickListener(v -> {
            addTaskInputField(todoTasksContainer, todoTasks, null);
        });

        // ✅ Date picker - different behavior for To-Do vs Weekly
        datePickerButton.setOnClickListener(v -> {
            if ("todo".equals(selectedCategory[0])) {
                showSingleDatePicker(selectedScheduleDate, selectedDateText, dateFormat);
            } else if ("weekly".equals(selectedCategory[0])) {
                // ✅ Show week range picker (user must select)
                showWeekSelectionDialog(customStartDate, customEndDate, selectedDateText);
            }
        });

        // Time picker
        timePickerButton.setOnClickListener(v -> {
            Calendar currentTime = Calendar.getInstance();
            int hour = currentTime.get(Calendar.HOUR_OF_DAY);
            int minute = currentTime.get(Calendar.MINUTE);

            TimePickerDialog timePicker = new TimePickerDialog(this,
                    (view, hourOfDay, minuteOfHour) -> {
                        selectedTime[0] = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
                        SimpleDateFormat input = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        SimpleDateFormat output = new SimpleDateFormat("h:mm a", Locale.getDefault());
                        try {
                            Date d = input.parse(selectedTime[0]);
                            selectedTimeText.setText(output.format(d));
                            selectedTimeText.setTextColor(getResources().getColor(android.R.color.black));
                            clearTimeButton.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            selectedTimeText.setText(selectedTime[0]);
                        }
                    }, hour, minute, false);
            timePicker.show();
        });

        clearTimeButton.setOnClickListener(v -> {
            selectedTime[0] = null;
            selectedTimeText.setText("Select Time");
            selectedTimeText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            clearTimeButton.setVisibility(View.GONE);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ Collect tasks
            if ("todo".equals(selectedCategory[0])) {
                todoTasks.clear();
                for (int i = 0; i < todoTasksContainer.getChildCount(); i++) {
                    View taskView = todoTasksContainer.getChildAt(i);
                    EditText taskInput = taskView.findViewById(R.id.taskInput);
                    String taskText = taskInput.getText().toString().trim();
                    if (!taskText.isEmpty()) {
                        todoTasks.add(taskText);
                    }
                }
            } else if ("weekly".equals(selectedCategory[0])) {
                // ✅ Collect weekly tasks with their schedule data
                for (String day : weeklyTasks.keySet()) {
                    weeklyTasks.get(day).clear();
                }

                for (int i = 0; i < weeklyDaysContainer.getChildCount(); i++) {
                    View daySection = weeklyDaysContainer.getChildAt(i);
                    TextView dayLabel = daySection.findViewById(R.id.dayLabel);
                    LinearLayout tasksContainer = daySection.findViewById(R.id.dayTasksContainer);
                    String day = dayLabel.getText().toString();

                    for (int j = 0; j < tasksContainer.getChildCount(); j++) {
                        View taskView = tasksContainer.getChildAt(j);
                        EditText taskInput = taskView.findViewById(R.id.taskInput);
                        String taskText = taskInput.getText().toString().trim();

                        if (!taskText.isEmpty()) {
                            Object tag = taskView.getTag();
                            WeeklyDialogTask taskData;

                            if (tag instanceof WeeklyDialogTask) {
                                taskData = (WeeklyDialogTask) tag;
                                taskData.taskText = taskText;
                            } else {
                                taskData = new WeeklyDialogTask();
                                taskData.taskText = taskText;
                            }

                            weeklyTasks.get(day).add(taskData);
                        }
                    }
                }
            }

            // ✅ Validation for weekly
            if ("weekly".equals(selectedCategory[0])) {
                if (customStartDate[0] == null || customEndDate[0] == null) {
                    Toast.makeText(this, "Please select week range", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;

            if ("todo".equals(selectedCategory[0])) {
                saveTodoScheduleWithTasks(user, title, "", selectedScheduleDate[0], selectedTime[0],
                        notificationCheckbox.isChecked(), reminderTimeSpinner, todoTasks, dialog);
            } else if ("weekly".equals(selectedCategory[0])) {
                // ✅ NOW PASSING TIME AND NOTIFICATION SETTINGS!
                saveWeeklyScheduleWithTasksNew(user, title, customStartDate[0], customEndDate[0],
                        selectedTime[0], notificationCheckbox.isChecked(), reminderTimeSpinner,
                        weeklyTasks, dialog);
            }
        });

        dialog.show();
    }
    private void buildWeeklyDaysUIWithScheduleButtons(LinearLayout container,
                                                      Map<String, List<WeeklyDialogTask>> weeklyTasks,
                                                      Calendar[] weekStart, Calendar[] weekEnd) {
        container.removeAllViews();
        String[] days = {"Mon", "Tues", "Wed", "Thur", "Fri", "Sat", "Sun"};

        for (String day : days) {
            View daySection = LayoutInflater.from(this).inflate(R.layout.item_weekly_day_section, container, false);

            TextView dayLabel = daySection.findViewById(R.id.dayLabel);
            LinearLayout tasksContainer = daySection.findViewById(R.id.dayTasksContainer);
            ImageView addTaskButton = daySection.findViewById(R.id.addDayTaskButton);

            dayLabel.setText(day);

            addTaskButton.setOnClickListener(v -> {
                // ✅ Add task WITH schedule button
                addWeeklyTaskWithScheduleButton(tasksContainer, day, weekStart, weekEnd);
            });

            container.addView(daySection);
        }
    }
    private void addWeeklyTaskWithScheduleButton(LinearLayout container, String day,
                                                 Calendar[] weekStart, Calendar[] weekEnd) {
        View taskView = LayoutInflater.from(this).inflate(R.layout.item_task_input, container, false);
        EditText taskInput = taskView.findViewById(R.id.taskInput);
        ImageView deleteButton = taskView.findViewById(R.id.deleteTaskButton);

        // Create task data
        WeeklyDialogTask taskData = new WeeklyDialogTask();
        taskView.setTag(taskData);

        // ✅ Add schedule button (calendar icon)
        ImageView scheduleButton = new ImageView(this);
        scheduleButton.setImageResource(R.drawable.ic_calendar);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) (32 * getResources().getDisplayMetrics().density),
                (int) (32 * getResources().getDisplayMetrics().density)
        );
        params.setMargins(8, 0, 8, 0);
        scheduleButton.setLayoutParams(params);
        scheduleButton.setPadding(4, 4, 4, 4);
        scheduleButton.setContentDescription("Set schedule");
        scheduleButton.setBackgroundResource(android.R.drawable.btn_default);

        // Add schedule button to layout (before delete button)
        if (taskView instanceof LinearLayout) {
            ((LinearLayout) taskView).addView(scheduleButton,
                    ((LinearLayout) taskView).getChildCount() - 1);
        }

        // ✅ Schedule button click - opens time/notification dialog
        scheduleButton.setOnClickListener(v -> {
            if (weekStart[0] == null || weekEnd[0] == null) {
                Toast.makeText(this, "Please select week range first", Toast.LENGTH_SHORT).show();
                return;
            }
            showTaskScheduleDialog(taskData, day, weekStart[0], weekEnd[0], taskInput);
        });

        deleteButton.setOnClickListener(v -> {
            container.removeView(taskView);
        });

        container.addView(taskView);
        taskInput.requestFocus();
    }
    private void showTaskScheduleDialog(WeeklyDialogTask taskData, String day,
                                        Calendar weekStart, Calendar weekEnd,
                                        EditText taskInput) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_weekly_schedule, null);
        builder.setView(dialogView);
        AlertDialog scheduleDialog = builder.create();

        LinearLayout weekRangePickerButton = dialogView.findViewById(R.id.weekRangePickerButton);
        TextView weekRangeText = dialogView.findViewById(R.id.weekRangeText);
        ImageView clearWeekButton = dialogView.findViewById(R.id.clearWeekButton);
        LinearLayout timePickerButton = dialogView.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = dialogView.findViewById(R.id.selectedTimeText);
        CheckBox notificationCheckbox = dialogView.findViewById(R.id.notificationCheckbox);
        LinearLayout notificationTimeSection = dialogView.findViewById(R.id.notificationTimeSection);
        Spinner notificationTimeSpinner = dialogView.findViewById(R.id.notificationTimeSpinner);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveScheduleButton = dialogView.findViewById(R.id.saveScheduleButton);

        // ✅ DISABLE week range picker - it's already set
        weekRangePickerButton.setEnabled(false);
        weekRangePickerButton.setAlpha(0.5f);
        clearWeekButton.setVisibility(View.GONE);

        // ✅ Calculate and display the specific date for this day
        Calendar taskDate = getDateForDayInWeek(day, weekStart);
        if (taskDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
            weekRangeText.setText(sdf.format(taskDate.getTime()));
        } else {
            weekRangeText.setText("Date not available");
        }

        // Setup notification spinner
        String[] notificationTimes = {"5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "1 hour", "2 hours", "1 day"};
        int[] notificationMinutes = {5, 10, 15, 30, 60, 120, 1440};

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, notificationTimes);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTimeSpinner.setAdapter(spinnerAdapter);

        // Load existing values
        if (taskData.scheduleTime != null && !taskData.scheduleTime.isEmpty()) {
            selectedTimeText.setText(taskData.scheduleTime);
        }

        notificationCheckbox.setChecked(taskData.hasNotification);
        notificationTimeSection.setVisibility(taskData.hasNotification ? View.VISIBLE : View.GONE);

        for (int i = 0; i < notificationMinutes.length; i++) {
            if (notificationMinutes[i] == taskData.notificationMinutes) {
                notificationTimeSpinner.setSelection(i);
                break;
            }
        }

        // Time picker
        timePickerButton.setOnClickListener(v -> {
            int hour = 9;
            int minute = 0;

            if (taskData.scheduleTime != null && !taskData.scheduleTime.isEmpty()) {
                try {
                    String[] timeParts = taskData.scheduleTime.split(":");
                    hour = Integer.parseInt(timeParts[0]);
                    minute = Integer.parseInt(timeParts[1]);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing time", e);
                }
            }

            TimePickerDialog timePicker = new TimePickerDialog(
                    this,
                    (view, hourOfDay, minuteOfHour) -> {
                        String timeStr = String.format(Locale.getDefault(), "%02d:%02d",
                                hourOfDay, minuteOfHour);
                        selectedTimeText.setText(timeStr);
                    },
                    hour, minute, false
            );
            timePicker.show();
        });

        // Notification checkbox
        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> scheduleDialog.dismiss());

        // ✅ Save button
        saveScheduleButton.setOnClickListener(v -> {
            if (taskDate == null) {
                Toast.makeText(this, "Date not available", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedTimeText.getText().equals("Select time")) {
                Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save to task data
            taskData.scheduleDate = taskDate;
            taskData.scheduleTime = selectedTimeText.getText().toString();
            taskData.hasNotification = notificationCheckbox.isChecked();

            if (notificationCheckbox.isChecked()) {
                int selectedPos = notificationTimeSpinner.getSelectedItemPosition();
                taskData.notificationMinutes = notificationMinutes[selectedPos];
            }

            // Update input hint
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            taskInput.setHint("Task (📅 " + sdf.format(taskDate.getTime()) + " " + taskData.scheduleTime + ")");

            Toast.makeText(this, "✓ Schedule set", Toast.LENGTH_SHORT).show();
            scheduleDialog.dismiss();
        });

        scheduleDialog.show();
    }

    // ✅ NEW: Simplified version for the Add Schedule dialog (no individual task scheduling)
    private void buildWeeklyDaysUISimple(LinearLayout container, Map<String, List<String>> weeklyTasks,
                                         Calendar[] weekStart, Calendar[] weekEnd) {
        container.removeAllViews();
        String[] days = {"Mon", "Tues", "Wed", "Thur", "Fri", "Sat", "Sun"};

        for (String day : days) {
            View daySection = LayoutInflater.from(this).inflate(R.layout.item_weekly_day_section, container, false);

            TextView dayLabel = daySection.findViewById(R.id.dayLabel);
            LinearLayout tasksContainer = daySection.findViewById(R.id.dayTasksContainer);
            ImageView addTaskButton = daySection.findViewById(R.id.addDayTaskButton);

            dayLabel.setText(day);

            addTaskButton.setOnClickListener(v -> {
                addTaskInputField(tasksContainer, weeklyTasks.get(day), null);
            });

            container.addView(daySection);
        }
    }
    private void saveTodoScheduleWithTasks(FirebaseUser user, String title, String description,
                                           Calendar scheduleDate, String time, boolean hasReminder,
                                           Spinner reminderSpinner, List<String> tasks, AlertDialog dialog) {

        Map<String, Object> todoListData = new HashMap<>();
        todoListData.put("title", title);
        todoListData.put("description", description);
        todoListData.put("createdAt", Timestamp.now());

        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .add(todoListData)
                .addOnSuccessListener(todoListRef -> {
                    String todoListId = todoListRef.getId();

                    AtomicInteger tasksAdded = new AtomicInteger(0);
                    if (tasks.isEmpty()) {
                        createTodoSchedule(user, title, description, scheduleDate, time, hasReminder, reminderSpinner, todoListId, dialog);
                    } else {
                        for (int i = 0; i < tasks.size(); i++) {
                            String taskText = tasks.get(i);
                            Map<String, Object> taskData = new HashMap<>();
                            taskData.put("taskText", taskText);
                            taskData.put("isCompleted", false);
                            taskData.put("position", i);

                            // ✅✅✅ CRITICAL FIX: Save schedule info sa bawat task!
                            taskData.put("scheduleDate", new Timestamp(scheduleDate.getTime()));
                            taskData.put("scheduleTime", time != null ? time : "");

                            db.collection("users")
                                    .document(user.getUid())
                                    .collection("todoLists")
                                    .document(todoListId)
                                    .collection("tasks")
                                    .add(taskData)
                                    .addOnSuccessListener(taskRef -> {
                                        if (tasksAdded.incrementAndGet() == tasks.size()) {
                                            createTodoSchedule(user, title, description, scheduleDate, time, hasReminder, reminderSpinner, todoListId, dialog);
                                        }
                                    });
                        }
                    }
                });
    }
    // New method to build weekly days UI with individual schedule buttons
    private void buildWeeklyDaysUIForDialog(LinearLayout container, Map<String, List<WeeklyDialogTask>> weeklyTasks,
                                            Calendar[] weekStart, Calendar[] weekEnd) {
        container.removeAllViews();
        String[] days = {"Mon", "Tues", "Wed", "Thur", "Fri", "Sat", "Sun"};

        for (String day : days) {
            View daySection = LayoutInflater.from(this).inflate(R.layout.item_weekly_day_section, container, false);

            TextView dayLabel = daySection.findViewById(R.id.dayLabel);
            LinearLayout tasksContainer = daySection.findViewById(R.id.dayTasksContainer);
            ImageView addTaskButton = daySection.findViewById(R.id.addDayTaskButton);

            dayLabel.setText(day);

            addTaskButton.setOnClickListener(v -> {
                addWeeklyTaskWithSchedule(tasksContainer, day, weekStart, weekEnd);
            });

            container.addView(daySection);
        }
    }

    // New method to add weekly task input with schedule button
    private void addWeeklyTaskWithSchedule(LinearLayout container, String day, Calendar[] weekStart, Calendar[] weekEnd) {
        View taskView = LayoutInflater.from(this).inflate(R.layout.item_task_input, container, false);
        EditText taskInput = taskView.findViewById(R.id.taskInput);
        ImageView deleteButton = taskView.findViewById(R.id.deleteTaskButton);

        // Create task data
        WeeklyDialogTask taskData = new WeeklyDialogTask();
        taskView.setTag(taskData);

        // Add schedule button next to delete button
        ImageView scheduleButton = new ImageView(this);
        scheduleButton.setImageResource(R.drawable.ic_calendar);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) (32 * getResources().getDisplayMetrics().density),
                (int) (32 * getResources().getDisplayMetrics().density)
        );
        params.setMargins(8, 0, 8, 0);
        scheduleButton.setLayoutParams(params);
        scheduleButton.setPadding(4, 4, 4, 4);
        scheduleButton.setContentDescription("Set schedule");
        scheduleButton.setBackgroundResource(android.R.drawable.btn_default);

        // Add to layout
        if (taskView instanceof LinearLayout) {
            ((LinearLayout) taskView).addView(scheduleButton,
                    ((LinearLayout) taskView).getChildCount() - 1);
        }

        scheduleButton.setOnClickListener(v -> {
            showWeeklyTaskScheduleDialogInAddDialog(taskData, day, weekStart[0], weekEnd[0], taskInput);
        });

        deleteButton.setOnClickListener(v -> {
            container.removeView(taskView);
        });

        container.addView(taskView);
        taskInput.requestFocus();
    }
    // New method to show schedule dialog for individual weekly task
    private void showWeeklyTaskScheduleDialogInAddDialog(WeeklyDialogTask taskData, String day,
                                                         Calendar weekStart, Calendar weekEnd,
                                                         EditText taskInput) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_weekly_schedule, null);
        builder.setView(dialogView);
        AlertDialog scheduleDialog = builder.create();

        LinearLayout weekRangePickerButton = dialogView.findViewById(R.id.weekRangePickerButton);
        TextView weekRangeText = dialogView.findViewById(R.id.weekRangeText);
        ImageView clearWeekButton = dialogView.findViewById(R.id.clearWeekButton);

        LinearLayout timePickerButton = dialogView.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = dialogView.findViewById(R.id.selectedTimeText);

        CheckBox notificationCheckbox = dialogView.findViewById(R.id.notificationCheckbox);
        LinearLayout notificationTimeSection = dialogView.findViewById(R.id.notificationTimeSection);
        android.widget.Spinner notificationTimeSpinner = dialogView.findViewById(R.id.notificationTimeSpinner);

        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveScheduleButton = dialogView.findViewById(R.id.saveScheduleButton);

        // ✅ DISABLE week range picker - it's auto-calculated
        weekRangePickerButton.setEnabled(false);
        weekRangePickerButton.setAlpha(0.5f);
        clearWeekButton.setVisibility(View.GONE);

        // ✅ Display the fixed date based on day and week range
        Calendar taskScheduleDate = getDateForDayInWeek(day, weekStart);
        if (taskScheduleDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            weekRangeText.setText(day + " - " + sdf.format(taskScheduleDate.getTime()));
        } else {
            weekRangeText.setText("Date not available");
        }

        // Setup notification spinner
        String[] notificationTimes = {"5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "1 hour", "2 hours", "1 day"};
        int[] notificationMinutes = {5, 10, 15, 30, 60, 120, 1440};

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, notificationTimes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTimeSpinner.setAdapter(adapter);

        // Load existing values
        if (taskData.scheduleTime != null && !taskData.scheduleTime.isEmpty()) {
            selectedTimeText.setText(taskData.scheduleTime);
        }

        notificationCheckbox.setChecked(taskData.hasNotification);
        notificationTimeSection.setVisibility(taskData.hasNotification ? View.VISIBLE : View.GONE);

        for (int i = 0; i < notificationMinutes.length; i++) {
            if (notificationMinutes[i] == taskData.notificationMinutes) {
                notificationTimeSpinner.setSelection(i);
                break;
            }
        }

        // Time picker
        timePickerButton.setOnClickListener(v -> {
            int hour = 9;
            int minute = 0;

            if (taskData.scheduleTime != null && !taskData.scheduleTime.isEmpty()) {
                try {
                    String[] timeParts = taskData.scheduleTime.split(":");
                    hour = Integer.parseInt(timeParts[0]);
                    minute = Integer.parseInt(timeParts[1]);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing time", e);
                }
            }

            android.app.TimePickerDialog timePicker = new android.app.TimePickerDialog(
                    this,
                    (view, hourOfDay, minuteOfHour) -> {
                        String timeStr = String.format(Locale.getDefault(), "%02d:%02d",
                                hourOfDay, minuteOfHour);
                        selectedTimeText.setText(timeStr);
                    },
                    hour, minute, false
            );
            timePicker.show();
        });

        // Notification checkbox
        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> scheduleDialog.dismiss());

        // Save button
        saveScheduleButton.setOnClickListener(v -> {
            if (taskScheduleDate == null) {
                Toast.makeText(this, "Date not available", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedTimeText.getText().equals("Select time")) {
                Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save to task data
            taskData.scheduleDate = taskScheduleDate;
            taskData.scheduleTime = selectedTimeText.getText().toString();
            taskData.hasNotification = notificationCheckbox.isChecked();

            if (notificationCheckbox.isChecked()) {
                int selectedPos = notificationTimeSpinner.getSelectedItemPosition();
                taskData.notificationMinutes = notificationMinutes[selectedPos];
            }

            // Update input hint
            taskInput.setHint("Task (scheduled: " + taskData.scheduleTime + ")");

            Toast.makeText(this, "✓ Schedule set for " + day, Toast.LENGTH_SHORT).show();
            scheduleDialog.dismiss();
        });

        scheduleDialog.show();
    }

    // New save method for weekly with task-level schedules
    private void saveWeeklyScheduleWithTasksNew(FirebaseUser user, String title,
                                                Calendar weekStart, Calendar weekEnd,
                                                String time, boolean hasReminder, Spinner reminderSpinner,
                                                Map<String, List<WeeklyDialogTask>> weeklyTasks,
                                                AlertDialog dialog) {
        Toast.makeText(this, "💾 Saving weekly plan...", Toast.LENGTH_SHORT).show();

        Map<String, Object> weeklyPlanData = new HashMap<>();
        weeklyPlanData.put("title", title);
        weeklyPlanData.put("description", "");
        weeklyPlanData.put("startDate", new Timestamp(weekStart.getTime()));
        weeklyPlanData.put("endDate", new Timestamp(weekEnd.getTime()));
        weeklyPlanData.put("time", time != null ? time : "");  // ✅ NOW SAVES TIME!
        weeklyPlanData.put("createdAt", Timestamp.now());
        weeklyPlanData.put("hasReminder", hasReminder);  // ✅ NOW SAVES REMINDER FLAG!

        // ✅ Save reminder minutes
        if (hasReminder) {
            String selectedReminderText = reminderSpinner.getSelectedItem().toString();
            int reminderMinutes = parseReminderMinutes(selectedReminderText);
            weeklyPlanData.put("reminderMinutes", reminderMinutes);
        } else {
            weeklyPlanData.put("reminderMinutes", 0);
        }

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .add(weeklyPlanData)
                .addOnSuccessListener(weeklyPlanRef -> {
                    String planId = weeklyPlanRef.getId();

                    int totalTasks = 0;
                    for (Map.Entry<String, List<WeeklyDialogTask>> entry : weeklyTasks.entrySet()) {
                        totalTasks += entry.getValue().size();
                    }

                    final int finalTotalTasks = totalTasks;

                    if (finalTotalTasks == 0) {
                        createWeeklyScheduleInBackground(user, title, weekStart, time, hasReminder, reminderSpinner, planId);
                        dialog.dismiss();
                        return;
                    }

                    dialog.dismiss();
                    Toast.makeText(this, "✅ Weekly plan created! Saving " + finalTotalTasks + " tasks...", Toast.LENGTH_SHORT).show();

                    AtomicInteger tasksAdded = new AtomicInteger(0);
                    int position = 0;

                    for (Map.Entry<String, List<WeeklyDialogTask>> entry : weeklyTasks.entrySet()) {
                        String day = entry.getKey();
                        List<WeeklyDialogTask> tasks = entry.getValue();

                        for (WeeklyDialogTask task : tasks) {
                            Map<String, Object> taskData = new HashMap<>();
                            taskData.put("taskText", task.taskText);
                            taskData.put("day", day);
                            taskData.put("isCompleted", false);
                            taskData.put("position", position++);

                            // Save task-level schedule
                            if (task.scheduleDate != null) {
                                taskData.put("scheduleDate", new Timestamp(task.scheduleDate.getTime()));
                            }
                            taskData.put("scheduleTime", task.scheduleTime != null ? task.scheduleTime : "");
                            taskData.put("hasNotification", task.hasNotification);
                            taskData.put("notificationMinutes", task.notificationMinutes);

                            db.collection("users")
                                    .document(user.getUid())
                                    .collection("weeklyPlans")
                                    .document(planId)
                                    .collection("tasks")
                                    .add(taskData)
                                    .addOnSuccessListener(taskRef -> {
                                        if (tasksAdded.incrementAndGet() == finalTotalTasks) {
                                            createWeeklyScheduleInBackground(user, title, weekStart,
                                                    time, hasReminder, reminderSpinner, planId);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to save task", e);
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "❌ Failed to create weekly plan", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error creating weekly plan", e);
                });
    }
    private void createTodoSchedule(FirebaseUser user, String title, String description,
                                    Calendar scheduleDate, String time, boolean hasReminder,
                                    Spinner reminderSpinner, String todoListId, AlertDialog dialog) {
        Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("title", title);
        scheduleData.put("description", description);
        scheduleData.put("date", new Timestamp(scheduleDate.getTime()));
        scheduleData.put("time", time != null ? time : "");
        scheduleData.put("category", "todo");
        scheduleData.put("sourceId", todoListId);
        scheduleData.put("isCompleted", false);
        scheduleData.put("createdAt", Timestamp.now());
        scheduleData.put("hasReminder", hasReminder);
        scheduleData.put("addedFromDayDetails", true);

        if (hasReminder) {
            String selectedReminderText = reminderSpinner.getSelectedItem().toString();
            int reminderMinutes = parseReminderMinutes(selectedReminderText);
            scheduleData.put("reminderMinutes", reminderMinutes);
        } else {
            scheduleData.put("reminderMinutes", 0);
        }

        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .add(scheduleData)
                .addOnSuccessListener(scheduleRef -> {
                    Toast.makeText(this, "To-Do schedule added", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
    }

    private void saveWeeklyScheduleWithTasks(FirebaseUser user, String title,
                                             Calendar weekStart, Calendar weekEnd, String time,
                                             boolean hasReminder, Spinner reminderSpinner,
                                             Map<String, List<String>> weeklyTasks, AlertDialog dialog) {

        // ✅ Show loading immediately
        Toast.makeText(this, "💾 Saving weekly plan...", Toast.LENGTH_SHORT).show();

        Map<String, Object> weeklyPlanData = new HashMap<>();
        weeklyPlanData.put("title", title);
        weeklyPlanData.put("description", "");
        weeklyPlanData.put("startDate", new Timestamp(weekStart.getTime()));
        weeklyPlanData.put("endDate", new Timestamp(weekEnd.getTime()));
        weeklyPlanData.put("time", time != null ? time : "");
        weeklyPlanData.put("createdAt", Timestamp.now());
        weeklyPlanData.put("hasReminder", hasReminder);

        if (hasReminder) {
            String selectedReminderText = reminderSpinner.getSelectedItem().toString();
            int reminderMinutes = parseReminderMinutes(selectedReminderText);
            weeklyPlanData.put("reminderMinutes", reminderMinutes);
        } else {
            weeklyPlanData.put("reminderMinutes", 0);
        }

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .add(weeklyPlanData)
                .addOnSuccessListener(weeklyPlanRef -> {
                    String planId = weeklyPlanRef.getId();

                    // ✅ Count total tasks
                    int totalTasks = 0;
                    for (Map.Entry<String, List<String>> entry : weeklyTasks.entrySet()) {
                        totalTasks += entry.getValue().size();
                    }

                    final int finalTotalTasks = totalTasks;

                    // ✅ If no tasks, save day schedules and create schedule immediately
                    if (finalTotalTasks == 0) {
                        // ✅✅ CRITICAL FIX: Save day schedules even with no tasks
                        saveDaySchedulesFromDialog(user.getUid(), planId, weekStart);
                        createWeeklySchedule(user, title, weekStart, time, hasReminder, reminderSpinner, planId, dialog);
                        return;
                    }

                    // ✅ DISMISS DIALOG IMMEDIATELY - don't wait for tasks to save
                    dialog.dismiss();
                    Toast.makeText(this, "✅ Weekly plan created! Saving " + finalTotalTasks + " tasks...", Toast.LENGTH_SHORT).show();

                    // ✅ Save tasks in background
                    AtomicInteger tasksAdded = new AtomicInteger(0);
                    int position = 0;

                    for (Map.Entry<String, List<String>> entry : weeklyTasks.entrySet()) {
                        String day = entry.getKey();
                        List<String> tasks = entry.getValue();

                        for (String taskText : tasks) {
                            Map<String, Object> taskData = new HashMap<>();
                            taskData.put("taskText", taskText);
                            taskData.put("day", day);
                            taskData.put("isCompleted", false);
                            taskData.put("position", position++);

                            db.collection("users")
                                    .document(user.getUid())
                                    .collection("weeklyPlans")
                                    .document(planId)
                                    .collection("tasks")
                                    .add(taskData)
                                    .addOnSuccessListener(taskRef -> {
                                        int completed = tasksAdded.incrementAndGet();
                                        if (completed == finalTotalTasks) {
                                            // ✅✅ CRITICAL FIX: Save day schedules after all tasks saved
                                            saveDaySchedulesFromDialog(user.getUid(), planId, weekStart);
                                            // ✅ All tasks saved - now create schedule reference
                                            createWeeklyScheduleInBackground(user, title, weekStart, time, hasReminder, reminderSpinner, planId);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to save task", e);
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "❌ Failed to create weekly plan", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error creating weekly plan", e);
                });
    }
    private void createWeeklySchedule(FirebaseUser user, String title, Calendar weekStart,
                                      String time, boolean hasReminder, Spinner reminderSpinner,
                                      String planId, AlertDialog dialog) {
        Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("title", title);
        scheduleData.put("description", "");
        scheduleData.put("date", new Timestamp(weekStart.getTime()));
        scheduleData.put("time", time != null ? time : "");
        scheduleData.put("category", "weekly");
        scheduleData.put("sourceId", planId);
        scheduleData.put("isCompleted", false);
        scheduleData.put("createdAt", Timestamp.now());
        scheduleData.put("hasReminder", hasReminder);
        scheduleData.put("addedFromDayDetails", true);

        if (hasReminder) {
            String selectedReminderText = reminderSpinner.getSelectedItem().toString();
            int reminderMinutes = parseReminderMinutes(selectedReminderText);
            scheduleData.put("reminderMinutes", reminderMinutes);
        } else {
            scheduleData.put("reminderMinutes", 0);
        }

        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .add(scheduleData)
                .addOnSuccessListener(scheduleRef -> {
                    Toast.makeText(this, "Weekly schedule added", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
    }
    // ✅ New method - creates schedule in background (no dialog to dismiss)
    private void createWeeklyScheduleInBackground(FirebaseUser user, String title, Calendar weekStart,
                                                  String time, boolean hasReminder, Spinner reminderSpinner,
                                                  String planId) {
        Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("title", title);
        scheduleData.put("description", "");
        scheduleData.put("date", new Timestamp(weekStart.getTime()));
        scheduleData.put("time", time != null ? time : "");
        scheduleData.put("category", "weekly");
        scheduleData.put("sourceId", planId);
        scheduleData.put("isCompleted", false);
        scheduleData.put("createdAt", Timestamp.now());
        scheduleData.put("hasReminder", hasReminder);
        scheduleData.put("addedFromDayDetails", true);

        if (hasReminder) {
            String selectedReminderText = reminderSpinner.getSelectedItem().toString();
            int reminderMinutes = parseReminderMinutes(selectedReminderText);
            scheduleData.put("reminderMinutes", reminderMinutes);
        } else {
            scheduleData.put("reminderMinutes", 0);
        }

        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .add(scheduleData)
                .addOnSuccessListener(scheduleRef -> {
                    Toast.makeText(this, "✅ All tasks saved!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create schedule reference", e);
                });
    }
    private void addTaskInputField(LinearLayout container, List<String> tasksList, String existingText) {
        View taskView = LayoutInflater.from(this).inflate(R.layout.item_task_input, container, false);
        EditText taskInput = taskView.findViewById(R.id.taskInput);
        ImageView deleteButton = taskView.findViewById(R.id.deleteTaskButton);

        if (existingText != null) {
            taskInput.setText(existingText);
        }

        deleteButton.setOnClickListener(v -> {
            container.removeView(taskView);
        });

        // ✅ Removed TextWatcher - tasks will be collected when Save button is clicked

        container.addView(taskView);
        taskInput.requestFocus();
    }
    // ✅ FIXED: Replace your buildWeeklyDaysUI method with this version

    private void buildWeeklyDaysUI(LinearLayout container, Map<String, List<String>> weeklyTasks,
                                   Calendar[] weekStart, Calendar[] weekEnd) {
        container.removeAllViews();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};

        for (String day : days) {
            View daySection = LayoutInflater.from(this).inflate(R.layout.item_weekly_day_section, container, false);

            TextView dayLabel = daySection.findViewById(R.id.dayLabel);
            LinearLayout tasksContainer = daySection.findViewById(R.id.dayTasksContainer);
            ImageView addTaskButton = daySection.findViewById(R.id.addDayTaskButton);


            dayLabel.setText(day);

            if (!weeklyTaskSchedules.containsKey(day)) {
                weeklyTaskSchedules.put(day, new ArrayList<>());
            }

            addTaskButton.setOnClickListener(v -> {
                addTaskInputField(tasksContainer, weeklyTasks.get(day), null);
            });

            container.addView(daySection);
        }
    }

    // 4. Add this new method to show the schedule dialog
    private void showWeeklyTaskScheduleDialog(String dayName, Calendar weekStart, Calendar weekEnd) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_weekly_task_schedule, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        TextView dayNameTitle = dialogView.findViewById(R.id.dayNameTitle);
        LinearLayout timeSchedulesContainer = dialogView.findViewById(R.id.timeSchedulesContainer);
        Button addTimeScheduleButton = dialogView.findViewById(R.id.addTimeScheduleButton);
        Button clearScheduleButton = dialogView.findViewById(R.id.clearScheduleButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveScheduleButton = dialogView.findViewById(R.id.saveScheduleButton);

        String fullDayName = getFullDayName(dayName);
        dayNameTitle.setText(fullDayName + " Schedule");

        // ✅ Calculate the specific date for this day within the week range
        Calendar specificDate = calculateDateForDay(dayName, weekStart, weekEnd);

        List<TaskScheduleData> existingSchedules = weeklyTaskSchedules.get(dayName);

        if (existingSchedules == null || existingSchedules.isEmpty()) {
            addScheduleTimeView(timeSchedulesContainer, dayName, null, 1, specificDate);
        } else {
            int scheduleNum = 1;
            for (TaskScheduleData schedule : existingSchedules) {
                addScheduleTimeView(timeSchedulesContainer, dayName, schedule, scheduleNum, specificDate);
                scheduleNum++;
            }
            clearScheduleButton.setVisibility(View.VISIBLE);
        }

        addTimeScheduleButton.setOnClickListener(v -> {
            int newScheduleNum = timeSchedulesContainer.getChildCount() + 1;
            addScheduleTimeView(timeSchedulesContainer, dayName, null, newScheduleNum, specificDate);
            clearScheduleButton.setVisibility(View.VISIBLE);
        });

        clearScheduleButton.setOnClickListener(v -> {
            weeklyTaskSchedules.get(dayName).clear();
            Toast.makeText(this, fullDayName + " schedules cleared", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveScheduleButton.setOnClickListener(v -> {
            List<TaskScheduleData> newSchedules = new ArrayList<>();
            boolean allValid = true;

            for (int i = 0; i < timeSchedulesContainer.getChildCount(); i++) {
                View scheduleView = timeSchedulesContainer.getChildAt(i);
                TextView selectedTimeText = scheduleView.findViewById(R.id.selectedTimeText);
                CheckBox notificationCheckbox = scheduleView.findViewById(R.id.notificationCheckbox);
                android.widget.Spinner notificationTimeSpinner = scheduleView.findViewById(R.id.notificationTimeSpinner);

                Object tag = scheduleView.getTag();
                if (tag instanceof ScheduleViewData) {
                    ScheduleViewData data = (ScheduleViewData) tag;

                    if (data.selectedTime == null || data.selectedTime.isEmpty()) {
                        Toast.makeText(this, "Please select time for Schedule " + (i + 1),
                                Toast.LENGTH_SHORT).show();
                        allValid = false;
                        break;
                    }

                    int reminderMinutes = 60;
                    if (notificationCheckbox.isChecked()) {
                        int[] notificationMinutesArray = {5, 10, 15, 30, 60, 120, 1440};
                        reminderMinutes = notificationMinutesArray[notificationTimeSpinner.getSelectedItemPosition()];
                    }

                    TaskScheduleData schedule = new TaskScheduleData(
                            specificDate, // Use the auto-calculated date
                            data.selectedTime,
                            notificationCheckbox.isChecked(),
                            reminderMinutes
                    );

                    newSchedules.add(schedule);
                }
            }

            if (allValid && !newSchedules.isEmpty()) {
                weeklyTaskSchedules.put(dayName, newSchedules);
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                String message = fullDayName + " (" + sdf.format(specificDate.getTime()) + "): " +
                        newSchedules.size() + " schedule(s) saved";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }


    // 5. Add helper class for view data
    private static class ScheduleViewData {
        Calendar selectedDate;
        String selectedTime;
    }
    // 6. Add method to create schedule time views
    private void addScheduleTimeView(LinearLayout container, String dayName,
                                     TaskScheduleData existingSchedule, int scheduleNumber,
                                     Calendar autoDate) {
        View scheduleView = LayoutInflater.from(this)
                .inflate(R.layout.item_schedule_time, container, false);

        TextView scheduleNumberText = scheduleView.findViewById(R.id.scheduleNumberText);
        LinearLayout datePickerButton = scheduleView.findViewById(R.id.datePickerButton);
        TextView selectedDateText = scheduleView.findViewById(R.id.selectedDateText);
        LinearLayout timePickerButton = scheduleView.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = scheduleView.findViewById(R.id.selectedTimeText);
        CheckBox notificationCheckbox = scheduleView.findViewById(R.id.notificationCheckbox);
        LinearLayout notificationTimeSection = scheduleView.findViewById(R.id.notificationTimeSection);
        android.widget.Spinner notificationTimeSpinner = scheduleView.findViewById(R.id.notificationTimeSpinner);
        ImageView removeScheduleButton = scheduleView.findViewById(R.id.removeScheduleButton);

        scheduleNumberText.setText("Schedule " + scheduleNumber);

        // ✅ HIDE DATE PICKER BUTTON - date is auto-set
        datePickerButton.setVisibility(View.GONE);

        // ✅ SHOW THE AUTO-CALCULATED DATE
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
        selectedDateText.setText(sdf.format(autoDate.getTime()));
        selectedDateText.setVisibility(View.VISIBLE);

        String[] notificationTimes = {"5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "1 hour", "2 hours", "1 day"};
        int[] notificationMinutes = {5, 10, 15, 30, 60, 120, 1440};

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, notificationTimes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTimeSpinner.setAdapter(adapter);

        ScheduleViewData viewData = new ScheduleViewData();
        viewData.selectedDate = autoDate; // Auto-set date
        scheduleView.setTag(viewData);

        if (existingSchedule != null) {
            viewData.selectedTime = existingSchedule.time;
            selectedTimeText.setText(existingSchedule.time);
            notificationCheckbox.setChecked(existingSchedule.hasNotification);
            notificationTimeSection.setVisibility(existingSchedule.hasNotification ?
                    View.VISIBLE : View.GONE);

            for (int i = 0; i < notificationMinutes.length; i++) {
                if (notificationMinutes[i] == existingSchedule.reminderMinutes) {
                    notificationTimeSpinner.setSelection(i);
                    break;
                }
            }
        }

        timePickerButton.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            android.app.TimePickerDialog timeDialog = new android.app.TimePickerDialog(
                    this,
                    (view, hourOfDay, minuteOfHour) -> {
                        viewData.selectedTime = String.format(Locale.getDefault(),
                                "%02d:%02d", hourOfDay, minuteOfHour);
                        selectedTimeText.setText(viewData.selectedTime);
                    },
                    hour, minute, false
            );
            timeDialog.show();
        });

        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        removeScheduleButton.setOnClickListener(v -> {
            if (container.getChildCount() > 1) {
                container.removeView(scheduleView);
                updateScheduleNumbers(container);
            } else {
                Toast.makeText(this, "Keep at least one schedule", Toast.LENGTH_SHORT).show();
            }
        });

        container.addView(scheduleView);
    }

    // 7. Add helper method to update schedule numbers
    private void updateScheduleNumbers(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View scheduleView = container.getChildAt(i);
            TextView scheduleNumberText = scheduleView.findViewById(R.id.scheduleNumberText);
            scheduleNumberText.setText("Schedule " + (i + 1));
        }
    }

    // 8. Add helper method to get full day name
    private String getFullDayName(String shortDay) {
        switch (shortDay) {
            case "MONDAY": return "Monday";
            case "TUESDAY": return "Tuesday";
            case "WEDNESDAY": return "Wednesday";
            case "THURSDAY": return "Thursday";
            case "FRIDAY": return "Friday";
            case "SATURDAY": return "Saturday";
            case "SUNDAY": return "Sunday";
            default: return shortDay;
        }
    }

    // 9. Update saveWeeklyScheduleWithTasks to save task schedules
    private void saveWeeklyScheduleWithTasksUpdated(FirebaseUser user, String title,
                                                    Calendar weekStart, Calendar weekEnd, String time,
                                                    boolean hasReminder, Spinner reminderSpinner,
                                                    Map<String, List<String>> weeklyTasks, AlertDialog dialog) {

        Toast.makeText(this, "💾 Saving weekly plan...", Toast.LENGTH_SHORT).show();

        Map<String, Object> weeklyPlanData = new HashMap<>();
        weeklyPlanData.put("title", title);
        weeklyPlanData.put("description", "");
        weeklyPlanData.put("startDate", new Timestamp(weekStart.getTime()));
        weeklyPlanData.put("endDate", new Timestamp(weekEnd.getTime()));
        weeklyPlanData.put("time", time != null ? time : "");
        weeklyPlanData.put("createdAt", Timestamp.now());
        weeklyPlanData.put("hasReminder", hasReminder);

        if (hasReminder) {
            String selectedReminderText = reminderSpinner.getSelectedItem().toString();
            int reminderMinutes = parseReminderMinutes(selectedReminderText);
            weeklyPlanData.put("reminderMinutes", reminderMinutes);
        } else {
            weeklyPlanData.put("reminderMinutes", 0);
        }

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .add(weeklyPlanData)
                .addOnSuccessListener(weeklyPlanRef -> {
                    String planId = weeklyPlanRef.getId();

                    int totalTasks = 0;
                    for (Map.Entry<String, List<String>> entry : weeklyTasks.entrySet()) {
                        totalTasks += entry.getValue().size();
                    }

                    final int finalTotalTasks = totalTasks;

                    dialog.dismiss();
                    Toast.makeText(this, "✅ Weekly plan created! Saving " + finalTotalTasks + " tasks...", Toast.LENGTH_SHORT).show();

                    AtomicInteger tasksAdded = new AtomicInteger(0);
                    int position = 0;

                    for (Map.Entry<String, List<String>> entry : weeklyTasks.entrySet()) {
                        String day = entry.getKey();
                        List<String> tasks = entry.getValue();

                        for (String taskText : tasks) {
                            Map<String, Object> taskData = new HashMap<>();
                            taskData.put("taskText", taskText);
                            taskData.put("day", day);
                            taskData.put("isCompleted", false);
                            taskData.put("position", position++);

                            db.collection("users")
                                    .document(user.getUid())
                                    .collection("weeklyPlans")
                                    .document(planId)
                                    .collection("tasks")
                                    .add(taskData)
                                    .addOnSuccessListener(taskRef -> {
                                        int completed = tasksAdded.incrementAndGet();
                                        if (completed == finalTotalTasks) {
                                            // Save day schedules
                                            saveDaySchedulesFromDialog(user.getUid(), planId, weekStart);
                                            createWeeklyScheduleInBackground(user, title, weekStart, time, hasReminder, reminderSpinner, planId);
                                        }
                                    });
                        }
                    }
                });
    }

    // 10. Add method to save day schedules
    // 10. Add method to save day schedules
    private void saveDaySchedulesFromDialog(String userId, String planId, Calendar weekStart) {
        for (Map.Entry<String, List<TaskScheduleData>> entry : weeklyTaskSchedules.entrySet()) {
            String day = entry.getKey();
            List<TaskScheduleData> schedules = entry.getValue();

            for (int i = 0; i < schedules.size(); i++) {
                TaskScheduleData schedule = schedules.get(i);
                final int scheduleNumber = i + 1; // ✅ Make it final

                Map<String, Object> scheduleData = new HashMap<>();
                scheduleData.put("day", day);
                scheduleData.put("date", new Timestamp(schedule.date.getTime()));
                scheduleData.put("time", schedule.time);
                scheduleData.put("hasReminder", schedule.hasNotification);
                scheduleData.put("reminderMinutes", schedule.reminderMinutes);
                scheduleData.put("scheduleNumber", scheduleNumber); // ✅ Use final variable

                db.collection("users")
                        .document(userId)
                        .collection("weeklyPlans")
                        .document(planId)
                        .collection("daySchedules")
                        .add(scheduleData)
                        .addOnSuccessListener(documentReference -> {
                            Log.d(TAG, "✅ Day schedule saved for " + day + " (Schedule " + scheduleNumber + ")"); // ✅ Use final variable
                        });
            }
        }
    }

    // Helper method to calculate the specific date for a day name within a week range
    private Calendar calculateDateForDay(String dayName, Calendar weekStart, Calendar weekEnd) {
        Calendar date = (Calendar) weekStart.clone();

        // Map day names to Calendar day constants
        int targetDay;
        switch (dayName) {
            case "Sun": targetDay = Calendar.SUNDAY; break;
            case "Mon": targetDay = Calendar.MONDAY; break;
            case "Tues": targetDay = Calendar.TUESDAY; break;
            case "Wed": targetDay = Calendar.WEDNESDAY; break;
            case "Thur": targetDay = Calendar.THURSDAY; break;
            case "Fri": targetDay = Calendar.FRIDAY; break;
            case "Sat": targetDay = Calendar.SATURDAY; break;
            default: return date;
        }

        // Find the date within the week range that matches the target day
        while (!date.after(weekEnd)) {
            if (date.get(Calendar.DAY_OF_WEEK) == targetDay) {
                return date;
            }
            date.add(Calendar.DAY_OF_MONTH, 1);
        }

        return weekStart; // Fallback
    }

    private void showCustomRangePicker(Calendar[] startDate, Calendar[] endDate, TextView dateText) {
        if (startDate[0] == null) {
            // Step 1: Select START date
            Calendar cal = Calendar.getInstance();
            DatePickerDialog startPicker = new DatePickerDialog(this,
                    (view, year, month, day) -> {
                        Calendar selected = Calendar.getInstance();
                        selected.set(year, month, day);
                        selected.set(Calendar.HOUR_OF_DAY, 0);
                        selected.set(Calendar.MINUTE, 0);
                        selected.set(Calendar.SECOND, 0);

                        startDate[0] = selected;

                        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                        dateText.setText("Start: " + sdf.format(selected.getTime()) + " → Select end date");

                        // Automatically show end date picker
                        showCustomRangePicker(startDate, endDate, dateText);
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH));

            startPicker.setTitle("Select Start Date");
            startPicker.show();

        } else if (endDate[0] == null) {
            // Step 2: Select END date
            Calendar cal = (Calendar) startDate[0].clone();
            DatePickerDialog endPicker = new DatePickerDialog(this,
                    (view, year, month, day) -> {
                        Calendar selected = Calendar.getInstance();
                        selected.set(year, month, day);
                        selected.set(Calendar.HOUR_OF_DAY, 23);
                        selected.set(Calendar.MINUTE, 59);
                        selected.set(Calendar.SECOND, 59);

                        // Validate: end must be after start
                        if (selected.before(startDate[0])) {
                            Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        endDate[0] = selected;

                        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                        dateText.setText(sdf.format(startDate[0].getTime()) + " - " + sdf.format(endDate[0].getTime()));
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH));

            endPicker.setTitle("Select End Date");
            endPicker.getDatePicker().setMinDate(startDate[0].getTimeInMillis());
            endPicker.show();

        } else {
            // Already selected both - reset and start over
            startDate[0] = null;
            endDate[0] = null;
            dateText.setText("Select date range");
            showCustomRangePicker(startDate, endDate, dateText);
        }
    }
    // Single date picker for To-Do
    private void showSingleDatePicker(Calendar[] selectedDate, TextView dateText, SimpleDateFormat dateFormat) {
        Calendar cal = selectedDate[0] != null ? selectedDate[0] : Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    Calendar newDate = Calendar.getInstance();
                    newDate.set(selectedYear, selectedMonth, selectedDay);
                    selectedDate[0] = newDate;
                    dateText.setText(dateFormat.format(newDate.getTime()));
                }, year, month, day);
        datePickerDialog.show();
    }

    // pwededlt
    private void showWeekRangePicker(Calendar[] weekStart, Calendar[] weekEnd, TextView dateText) {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(selectedYear, selectedMonth, selectedDay);

                    // Calculate week start (Sunday)
                    Calendar start = (Calendar) selectedDate.clone();
                    start.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                    start.set(Calendar.HOUR_OF_DAY, 0);
                    start.set(Calendar.MINUTE, 0);
                    start.set(Calendar.SECOND, 0);

                    // Calculate week end (Saturday)
                    Calendar end = (Calendar) start.clone();
                    end.add(Calendar.DAY_OF_MONTH, 6);
                    end.set(Calendar.HOUR_OF_DAY, 23);
                    end.set(Calendar.MINUTE, 59);
                    end.set(Calendar.SECOND, 59);

                    weekStart[0] = start;
                    weekEnd[0] = end;

                    SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
                    String rangeText = sdf.format(start.getTime()) + " - " + sdf.format(end.getTime());
                    dateText.setText(rangeText);
                }, year, month, day);
        datePickerDialog.setTitle("Select any day in the week");
        datePickerDialog.show();
    }

    private void saveTodoSchedule(FirebaseUser user, String title, String description,
                                  Calendar scheduleDate, String time, boolean hasReminder,
                                  Spinner reminderSpinner, AlertDialog dialog) {

        Map<String, Object> todoListData = new HashMap<>();
        todoListData.put("title", title);
        todoListData.put("description", description);
        todoListData.put("createdAt", Timestamp.now());

        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .add(todoListData)
                .addOnSuccessListener(todoListRef -> {
                    String todoListId = todoListRef.getId();

                    // Step 2: Create the schedule with sourceId reference
                    Map<String, Object> scheduleData = new HashMap<>();
                    scheduleData.put("title", title);
                    scheduleData.put("description", description);
                    scheduleData.put("date", new Timestamp(scheduleDate.getTime()));
                    scheduleData.put("time", time != null ? time : "");
                    scheduleData.put("category", "todo");
                    scheduleData.put("sourceId", todoListId); // Critical: Link to todoList
                    scheduleData.put("isCompleted", false);
                    scheduleData.put("createdAt", Timestamp.now());
                    scheduleData.put("hasReminder", hasReminder);
                    scheduleData.put("addedFromDayDetails", true);

                    if (hasReminder) {
                        String selectedReminderText = reminderSpinner.getSelectedItem().toString();
                        int reminderMinutes = parseReminderMinutes(selectedReminderText);
                        scheduleData.put("reminderMinutes", reminderMinutes);
                    } else {
                        scheduleData.put("reminderMinutes", 0);
                    }

                    db.collection("users")
                            .document(user.getUid())
                            .collection("schedules")
                            .add(scheduleData)
                            .addOnSuccessListener(scheduleRef -> {
                                Toast.makeText(this, "To-Do schedule added", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to add schedule", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "Error adding schedule", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to create todo list", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error creating todo list", e);
                });
    }

    private void saveWeeklySchedule(FirebaseUser user, String title, String description,
                                    Calendar weekStart, Calendar weekEnd, String time,
                                    boolean hasReminder, Spinner reminderSpinner, AlertDialog dialog) {

        // Step 1: Create the weekly plan document first
        Map<String, Object> weeklyPlanData = new HashMap<>();
        weeklyPlanData.put("title", title);
        weeklyPlanData.put("description", description);
        weeklyPlanData.put("startDate", new Timestamp(weekStart.getTime()));
        weeklyPlanData.put("endDate", new Timestamp(weekEnd.getTime()));
        weeklyPlanData.put("time", time != null ? time : "");
        weeklyPlanData.put("createdAt", Timestamp.now());
        weeklyPlanData.put("hasReminder", hasReminder);

        if (hasReminder) {
            String selectedReminderText = reminderSpinner.getSelectedItem().toString();
            int reminderMinutes = parseReminderMinutes(selectedReminderText);
            weeklyPlanData.put("reminderMinutes", reminderMinutes);
        } else {
            weeklyPlanData.put("reminderMinutes", 0);
        }

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .add(weeklyPlanData)
                .addOnSuccessListener(weeklyPlanRef -> {
                    String planId = weeklyPlanRef.getId();

                    // Step 2: Create tasks for the weekly plan
                    List<String> uniqueDays = new ArrayList<>();
                    Calendar current = (Calendar) weekStart.clone();

                    while (!current.after(weekEnd)) {
                        String dayName = getDayNameFromCalendar(current);
                        if (!uniqueDays.contains(dayName)) {
                            uniqueDays.add(dayName);
                        }
                        current.add(Calendar.DAY_OF_MONTH, 1);
                    }

                    Log.d(TAG, "Creating tasks for unique days: " + uniqueDays);

                    AtomicInteger tasksCreated = new AtomicInteger(0);
                    for (int i = 0; i < uniqueDays.size(); i++) {
                        String dayName = uniqueDays.get(i);

                        Map<String, Object> taskData = new HashMap<>();
                        taskData.put("taskText", title);
                        taskData.put("day", dayName);
                        taskData.put("isCompleted", false);
                        taskData.put("position", i);

                        db.collection("users")
                                .document(user.getUid())
                                .collection("weeklyPlans")
                                .document(planId)
                                .collection("tasks")
                                .add(taskData)
                                .addOnSuccessListener(taskRef -> {
                                    int count = tasksCreated.incrementAndGet();
                                    Log.d(TAG, "Created task for " + dayName + " (" + count + "/" + uniqueDays.size() + ")");

                                    if (count == uniqueDays.size()) {
                                        // Step 3: Create schedule reference with sourceId
                                        Map<String, Object> scheduleData = new HashMap<>();
                                        scheduleData.put("title", title);
                                        scheduleData.put("description", description);
                                        scheduleData.put("date", new Timestamp(weekStart.getTime()));
                                        scheduleData.put("time", time != null ? time : "");
                                        scheduleData.put("category", "weekly");
                                        scheduleData.put("sourceId", planId); // Critical: Link to weeklyPlan
                                        scheduleData.put("isCompleted", false);
                                        scheduleData.put("createdAt", Timestamp.now());
                                        scheduleData.put("hasReminder", hasReminder);
                                        scheduleData.put("addedFromDayDetails", true);

                                        if (hasReminder) {
                                            String selectedReminderText = reminderSpinner.getSelectedItem().toString();
                                            int reminderMinutes = parseReminderMinutes(selectedReminderText);
                                            scheduleData.put("reminderMinutes", reminderMinutes);
                                        } else {
                                            scheduleData.put("reminderMinutes", 0);
                                        }

                                        db.collection("users")
                                                .document(user.getUid())
                                                .collection("schedules")
                                                .add(scheduleData)
                                                .addOnSuccessListener(scheduleRef -> {
                                                    Toast.makeText(this, "Weekly schedule added for " + uniqueDays.size() + " day(s)", Toast.LENGTH_SHORT).show();
                                                    dialog.dismiss();
                                                })
                                                .addOnFailureListener(e -> {
                                                    Toast.makeText(this, "Failed to add schedule reference", Toast.LENGTH_SHORT).show();
                                                    Log.e(TAG, "Error adding schedule reference", e);
                                                });
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error adding task for " + dayName, e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to add weekly schedule", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error adding weekly schedule", e);
                });
    }

    private int parseReminderMinutes(String reminderText) {
        if (reminderText.contains("5 minutes")) return 5;
        if (reminderText.contains("10 minutes")) return 10;
        if (reminderText.contains("15 minutes")) return 15;
        if (reminderText.contains("30 minutes")) return 30;
        if (reminderText.contains("1 hour")) return 60;
        if (reminderText.contains("1 day")) return 1440;
        return 15;
    }

    // Add this method to replace showCustomRangePicker
    // Replace the showWeekSelectionDialog method in DayDetailsActivity.java with this fixed version:

    // UPDATED: Replace the existing showWeekSelectionDialog with this version
    private void showWeekSelectionDialog(Calendar[] startDate, Calendar[] endDate, TextView dateText) {
        String[] weekOptions = new String[5];

        // Calculate current week starting from MONDAY
        Calendar today = Calendar.getInstance();
        int currentDayOfWeek = today.get(Calendar.DAY_OF_WEEK);

        // Calculate days to Monday (Monday = 2 in Calendar)
        int daysToMonday = (currentDayOfWeek == Calendar.SUNDAY) ? 6 : currentDayOfWeek - Calendar.MONDAY;

        // This Week (Monday to Sunday)
        Calendar thisWeekStart = (Calendar) today.clone();
        thisWeekStart.add(Calendar.DAY_OF_MONTH, -daysToMonday);
        thisWeekStart.set(Calendar.HOUR_OF_DAY, 0);
        thisWeekStart.set(Calendar.MINUTE, 0);
        thisWeekStart.set(Calendar.SECOND, 0);

        Calendar thisWeekEnd = (Calendar) thisWeekStart.clone();
        thisWeekEnd.add(Calendar.DAY_OF_MONTH, 6);
        thisWeekEnd.set(Calendar.HOUR_OF_DAY, 23);
        thisWeekEnd.set(Calendar.MINUTE, 59);
        thisWeekEnd.set(Calendar.SECOND, 59);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());

        weekOptions[0] = "This Week (" + sdf.format(thisWeekStart.getTime()) + " - " + sdf.format(thisWeekEnd.getTime()) + ")";

        Calendar nextWeekStart = (Calendar) thisWeekStart.clone();
        nextWeekStart.add(Calendar.DAY_OF_MONTH, 7);
        Calendar nextWeekEnd = (Calendar) thisWeekEnd.clone();
        nextWeekEnd.add(Calendar.DAY_OF_MONTH, 7);
        weekOptions[1] = "Next Week (" + sdf.format(nextWeekStart.getTime()) + " - " + sdf.format(nextWeekEnd.getTime()) + ")";

        Calendar weekAfterStart = (Calendar) nextWeekStart.clone();
        weekAfterStart.add(Calendar.DAY_OF_MONTH, 7);
        Calendar weekAfterEnd = (Calendar) nextWeekEnd.clone();
        weekAfterEnd.add(Calendar.DAY_OF_MONTH, 7);
        weekOptions[2] = "Week After Next (" + sdf.format(weekAfterStart.getTime()) + " - " + sdf.format(weekAfterEnd.getTime()) + ")";

        Calendar thirdWeekStart = (Calendar) weekAfterStart.clone();
        thirdWeekStart.add(Calendar.DAY_OF_MONTH, 7);
        Calendar thirdWeekEnd = (Calendar) weekAfterEnd.clone();
        thirdWeekEnd.add(Calendar.DAY_OF_MONTH, 7);
        weekOptions[3] = "Third Week Ahead (" + sdf.format(thirdWeekStart.getTime()) + " - " + sdf.format(thirdWeekEnd.getTime()) + ")";

        weekOptions[4] = "Custom Date Range...";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Week");
        builder.setItems(weekOptions, (dialog, which) -> {
            switch (which) {
                case 0:
                    startDate[0] = thisWeekStart;
                    endDate[0] = thisWeekEnd;
                    dateText.setText(sdf.format(thisWeekStart.getTime()) + " - " + sdf.format(thisWeekEnd.getTime()));
                    break;
                case 1:
                    startDate[0] = nextWeekStart;
                    endDate[0] = nextWeekEnd;
                    dateText.setText(sdf.format(nextWeekStart.getTime()) + " - " + sdf.format(nextWeekEnd.getTime()));
                    break;
                case 2:
                    startDate[0] = weekAfterStart;
                    endDate[0] = weekAfterEnd;
                    dateText.setText(sdf.format(weekAfterStart.getTime()) + " - " + sdf.format(weekAfterEnd.getTime()));
                    break;
                case 3:
                    startDate[0] = thirdWeekStart;
                    endDate[0] = thirdWeekEnd;
                    dateText.setText(sdf.format(thirdWeekStart.getTime()) + " - " + sdf.format(thirdWeekEnd.getTime()));
                    break;
                case 4:
                    showCustomRangePicker(startDate, endDate, dateText);
                    break;
            }
        });
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }
    // ✅ NEW METHOD: Auto-calculate week range based on selected date
    private void autoSetWeekRangeForSelectedDate(Calendar[] startDate, Calendar[] endDate, TextView dateText) {
        // Use the selectedDate from DayDetailsActivity (the date clicked in calendar)
        Calendar clickedDate = (Calendar) selectedDate.clone();

        int currentDayOfWeek = clickedDate.get(Calendar.DAY_OF_WEEK);

        // Calculate days to Monday (Monday = 2 in Calendar)
        int daysToMonday = (currentDayOfWeek == Calendar.SUNDAY) ? 6 : currentDayOfWeek - Calendar.MONDAY;

        // Calculate week start (Monday)
        Calendar weekStart = (Calendar) clickedDate.clone();
        weekStart.add(Calendar.DAY_OF_MONTH, -daysToMonday);
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);

        // Calculate week end (Sunday)
        Calendar weekEnd = (Calendar) weekStart.clone();
        weekEnd.add(Calendar.DAY_OF_MONTH, 6); // Monday + 6 = Sunday
        weekEnd.set(Calendar.HOUR_OF_DAY, 23);
        weekEnd.set(Calendar.MINUTE, 59);
        weekEnd.set(Calendar.SECOND, 59);

        // Set the arrays
        startDate[0] = weekStart;
        endDate[0] = weekEnd;

        // Update the TextView
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
        dateText.setText(sdf.format(weekStart.getTime()) + " - " + sdf.format(weekEnd.getTime()));

        Log.d(TAG, "✅ Auto-set week range: " + sdf.format(weekStart.getTime()) + " - " + sdf.format(weekEnd.getTime()));
    }
    private Calendar getDateForDayInWeek(String day, Calendar weekStart) {
        if (weekStart == null) return null;

        int targetDayOfWeek = getDayOfWeekFromName(day);
        if (targetDayOfWeek == -1) return null;

        Calendar result = (Calendar) weekStart.clone();

        // Move to the target day
        while (result.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek) {
            result.add(Calendar.DAY_OF_MONTH, 1);
        }

        return result;
    }

    private int getDayOfWeekFromName(String dayName) {
        switch (dayName) {
            case "Sun": return Calendar.SUNDAY;
            case "Mon": return Calendar.MONDAY;
            case "Tues": return Calendar.TUESDAY;
            case "Wed": return Calendar.WEDNESDAY;
            case "Thur": return Calendar.THURSDAY;
            case "Fri": return Calendar.FRIDAY;
            case "Sat": return Calendar.SATURDAY;
            default: return -1;
        }
    }

}