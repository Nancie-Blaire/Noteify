package com.example.testtasksync;

import android.app.AlertDialog;
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

        adapter = new DayScheduleAdapter(scheduleList, new DayScheduleAdapter.OnScheduleClickListener() {
            @Override
            public void onScheduleClick(Schedule schedule) {
                if (isDeleteMode) {
                    toggleScheduleSelection(schedule);
                } else {
                    showScheduleDetailsDialog(schedule);
                }
            }

            @Override
            public void onScheduleLongClick(Schedule schedule) {
                enterDeleteMode();
                toggleScheduleSelection(schedule);
            }
        });

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

        loadSchedulesForDate();
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
    // ✅ NEW: Load scheduled todo tasks for this specific date

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

        Log.d(TAG, "📅 Loading scheduled todo tasks for date");

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
                                                Log.d(TAG, "⏭️ Skipping completed task: " + taskText);
                                                continue;
                                            }

                                            Schedule taskSchedule = new Schedule();
                                            taskSchedule.setId(listId + "_task_" + taskDoc.getId());
                                            taskSchedule.setTitle(taskText);
                                            taskSchedule.setDescription("From: " + listTitle);
                                            taskSchedule.setCategory("todo_task");
                                            taskSchedule.setSourceId(listId);
                                            taskSchedule.setCompleted(false); // Always false since we filtered completed ones
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
    } private void loadWeeklyPlansForDate() {
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

        // ✅ First, get the weekly plan details (including time)
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .get()
                .addOnSuccessListener(planDoc -> {
                    String weeklyPlanTime = "";

                    if (planDoc.exists()) {
                        weeklyPlanTime = planDoc.getString("time");
                        Log.d(TAG, "📅 Weekly plan time: " + weeklyPlanTime);
                    }

                    final String planTime = weeklyPlanTime != null ? weeklyPlanTime : "";

                    // ✅ Now load the tasks for this specific day
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

                                    Schedule taskSchedule = new Schedule();
                                    taskSchedule.setId(planId + "_" + taskDoc.getId());
                                    taskSchedule.setTitle(taskText);
                                    taskSchedule.setCategory("weekly");
                                    taskSchedule.setSourceId(planId);
                                    taskSchedule.setCompleted(isCompleted != null && isCompleted);
                                    taskSchedule.setDate(new Timestamp(selectedDate.getTime()));

                                    // ✅ Set the time from the weekly plan
                                    if (!planTime.isEmpty()) {
                                        taskSchedule.setTime(planTime);
                                        Log.d(TAG, "⏰ Set time for task: " + planTime);
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
                                        Log.d(TAG, "✅ Added task to schedule list with time: " + planTime);
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

    private void showScheduleDetailsDialog(Schedule schedule) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_schedule_details_v2, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextView timeContainer = dialogView.findViewById(R.id.timeContainer);
        TextView scheduleTime = dialogView.findViewById(R.id.scheduleTime);
        TextView scheduleAmPm = dialogView.findViewById(R.id.scheduleAmPm);
        TextView titleText = dialogView.findViewById(R.id.scheduleDetailTitle);
        TextView descriptionText = dialogView.findViewById(R.id.scheduleDetailDescription);
        TextView dateText = dialogView.findViewById(R.id.scheduleDetailDate);
        TextView categoryBadge = dialogView.findViewById(R.id.categoryBadge);
        TextView reminderText = dialogView.findViewById(R.id.scheduleDetailReminder);
        View tasksDivider = dialogView.findViewById(R.id.tasksDivider);
        LinearLayout tasksSection = dialogView.findViewById(R.id.tasksSection);
        LinearLayout tasksListContainer = dialogView.findViewById(R.id.tasksListContainer);

        titleText.setText(schedule.getTitle());

        if (schedule.getTime() != null && !schedule.getTime().isEmpty()) {
            String formattedTime = schedule.getFormattedTime();
            String[] timeParts = formattedTime.split(" ");
            if (timeParts.length == 2) {
                scheduleTime.setText(timeParts[0]);
                scheduleAmPm.setText(timeParts[1]);
                timeContainer.setVisibility(View.VISIBLE);
            }
        }

        if (schedule.getDescription() != null && !schedule.getDescription().isEmpty()) {
            descriptionText.setText(schedule.getDescription());
            descriptionText.setVisibility(View.VISIBLE);
        } else {
            descriptionText.setVisibility(View.GONE);
        }

        dateText.setText(schedule.getFormattedDate());
        categoryBadge.setText(getCategoryDisplayName(schedule.getCategory()));

        if (schedule.hasReminder()) {
            reminderText.setText("🔔 " + schedule.getReminderMinutes() + " minutes before");
            reminderText.setVisibility(View.VISIBLE);
        }

        if ("weekly".equals(schedule.getCategory()) || "todo".equals(schedule.getCategory())) {
            loadTasksForSchedule(schedule, tasksListContainer, tasksDivider, tasksSection);
        }

        dialog.show();
    }

    private void loadTasksForSchedule(Schedule schedule, LinearLayout container, View divider, LinearLayout section) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String sourceId = schedule.getSourceId();
        if (sourceId == null || sourceId.isEmpty()) return;

        String collection = "weekly".equals(schedule.getCategory()) ? "weeklyPlans" : "todoLists";

        db.collection("users")
                .document(user.getUid())
                .collection(collection)
                .document(sourceId)
                .collection("tasks")
                .orderBy("position")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        divider.setVisibility(View.VISIBLE);
                        section.setVisibility(View.VISIBLE);
                        container.removeAllViews();

                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            View taskView = LayoutInflater.from(this)
                                    .inflate(R.layout.item_dialog_task, container, false);

                            CheckBox checkbox = taskView.findViewById(R.id.taskCheckbox);
                            TextView taskText = taskView.findViewById(R.id.taskText);

                            String text = doc.getString("taskText");
                            boolean isCompleted = Boolean.TRUE.equals(doc.getBoolean("isCompleted"));

                            checkbox.setChecked(isCompleted);
                            checkbox.setEnabled(false); // ✅ Disable checkbox (view only)
                            taskText.setText(text);

                            // ✅ Apply strikethrough if completed
                            if (isCompleted) {
                                taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                                taskText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                            }

                            container.addView(taskView);
                        }
                    }
                });
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

        int deleteCount = 0;

        for (Schedule schedule : selectedSchedules) {
            String category = schedule.getCategory();

            if ("weekly".equals(category)) {
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
                                Log.d(TAG, "Weekly task deleted");
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to delete weekly task", e);
                            });
                    deleteCount++;
                }
            } else if ("todo_task".equals(category)) {
                // ✅ This is for tasks from todo lists
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
                                Log.d(TAG, "Todo task deleted");
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to delete todo task", e);
                            });
                    deleteCount++;
                }
            } else {
                // ✅ This handles regular "todo" schedules and any other category
                // These are stored directly in the schedules collection
                db.collection("users")
                        .document(user.getUid())
                        .collection("schedules")
                        .document(schedule.getId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Schedule deleted");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to delete schedule", e);
                        });
                deleteCount++;
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
        EditText descriptionInput = dialogView.findViewById(R.id.scheduleDescriptionInput);
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

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        selectedDateText.setText(dateFormat.format(selectedDate.getTime()));

        final String[] selectedTime = {null};
        final Calendar[] selectedScheduleDate = {(Calendar) selectedDate.clone()};
        final Calendar[] customStartDate = {null};
        final Calendar[] customEndDate = {null};
        final String[] selectedCategory = {"todo"};

        todoRadio.setChecked(true);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.reminder_times, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderTimeSpinner.setAdapter(adapter);

        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            reminderTimeSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        categoryRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.todoRadio) {
                selectedCategory[0] = "todo";
                selectedDateText.setText(dateFormat.format(selectedDate.getTime()));
                customStartDate[0] = null;
                customEndDate[0] = null;
            } else if (checkedId == R.id.weeklyRadio) {
                selectedCategory[0] = "weekly";
                selectedDateText.setText("Select date range");
                customStartDate[0] = null;
                customEndDate[0] = null;
            }
        });

        // ✅ FIXED: Custom date range picker
        datePickerButton.setOnClickListener(v -> {
            if ("todo".equals(selectedCategory[0])) {
                showSingleDatePicker(selectedScheduleDate, selectedDateText, dateFormat);
            } else if ("weekly".equals(selectedCategory[0])) {
                showCustomRangePicker(customStartDate, customEndDate, selectedDateText);
            }
        });

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
            clearTimeButton.setVisibility(View.GONE);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
                return;
            }

            if ("weekly".equals(selectedCategory[0])) {
                if (customStartDate[0] == null || customEndDate[0] == null) {
                    Toast.makeText(this, "Please select start and end dates", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;

            if ("todo".equals(selectedCategory[0])) {
                saveTodoSchedule(user, title, description, selectedScheduleDate[0], selectedTime[0],
                        notificationCheckbox.isChecked(), reminderTimeSpinner, dialog);
            } else if ("weekly".equals(selectedCategory[0])) {
                saveWeeklySchedule(user, title, description, customStartDate[0], customEndDate[0],
                        selectedTime[0], notificationCheckbox.isChecked(), reminderTimeSpinner, dialog);
            }
        });

        dialog.show();
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

    // Week range picker for Weekly
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
        Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("title", title);
        scheduleData.put("description", description);
        scheduleData.put("date", new Timestamp(scheduleDate.getTime()));
        scheduleData.put("time", time != null ? time : "");
        scheduleData.put("category", "todo");
        scheduleData.put("isCompleted", false);
        scheduleData.put("createdAt", Timestamp.now());
        scheduleData.put("hasReminder", hasReminder);
        scheduleData.put("addedFromDayDetails", true); // ✅ NEW FLAG

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
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "✅ To-Do schedule added", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to add schedule", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error adding schedule", e);
                });
    }

    private void saveWeeklySchedule(FirebaseUser user, String title, String description,
                                    Calendar weekStart, Calendar weekEnd, String time,
                                    boolean hasReminder, Spinner reminderSpinner, AlertDialog dialog) {
        Map<String, Object> weeklyPlanData = new HashMap<>();
        weeklyPlanData.put("title", title);
        weeklyPlanData.put("description", description);
        weeklyPlanData.put("startDate", new Timestamp(weekStart.getTime()));
        weeklyPlanData.put("endDate", new Timestamp(weekEnd.getTime()));
        weeklyPlanData.put("time", time != null ? time : "");
        weeklyPlanData.put("createdAt", Timestamp.now());
        weeklyPlanData.put("hasReminder", hasReminder);
        weeklyPlanData.put("addedFromDayDetails", true); // ✅ NEW FLAG

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
                .addOnSuccessListener(documentReference -> {
                    String planId = documentReference.getId();

                    // ✅ FIX: Get UNIQUE days of week (not all calendar dates)
                    // This prevents duplicate tasks when the distribution logic runs
                    List<String> uniqueDays = new ArrayList<>();
                    Calendar current = (Calendar) weekStart.clone();

                    while (!current.after(weekEnd)) {
                        String dayName = getDayNameFromCalendar(current);
                        if (!uniqueDays.contains(dayName)) {
                            uniqueDays.add(dayName);
                        }
                        current.add(Calendar.DAY_OF_MONTH, 1);
                    }

                    Log.d(TAG, "📋 Creating tasks for unique days: " + uniqueDays);

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
                                    Log.d(TAG, "✅ Created task for " + dayName + " (" + count + "/" + uniqueDays.size() + ")");

                                    if (count == uniqueDays.size()) {
                                        Toast.makeText(this, "✅ Weekly schedule added for " + uniqueDays.size() + " day(s)", Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
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
}