package com.example.testtasksync;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.util.List;
import java.util.Locale;

public class DayDetailsActivity extends AppCompatActivity {

    private static final String TAG = "DayDetailsActivity";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration scheduleListener;

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

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get date from intent
        dateKey = getIntent().getStringExtra("dateKey");
        if (dateKey == null) {
            finish();
            return;
        }

        // Parse date
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            selectedDate = Calendar.getInstance();
            selectedDate.setTime(sdf.parse(dateKey));
        } catch (Exception e) {
            Log.e(TAG, "Error parsing date", e);
            finish();
            return;
        }

        // Initialize views
        selectedDateText = findViewById(R.id.selectedDateText);
        holidayBanner = findViewById(R.id.holidayBanner);
        holidayNameText = findViewById(R.id.holidayNameText);
        schedulesRecyclerView = findViewById(R.id.schedulesRecyclerView);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        addScheduleButton = findViewById(R.id.addScheduleButton);
        backButton = findViewById(R.id.backButton);
        deleteButton = findViewById(R.id.deleteButton);

        // Set date text
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d", Locale.getDefault());
        selectedDateText.setText(dateFormat.format(selectedDate.getTime()));

        // Check for holiday
        checkIfHoliday();

        // Initialize schedule list
        scheduleList = new ArrayList<>();

        // Set up adapter with long press listener
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

        // Set up buttons
        backButton.setOnClickListener(v -> finish());
        addScheduleButton.setOnClickListener(v -> {
            // TODO: Open add schedule dialog with pre-selected date
            Toast.makeText(this, "Add schedule feature coming soon", Toast.LENGTH_SHORT).show();
        });

        deleteButton.setOnClickListener(v -> {
            if (isDeleteMode) {
                showDeleteConfirmation();
            }
        });

        // Load schedules
        loadSchedulesForDate();
    }

    private void checkIfHoliday() {
        int year = selectedDate.get(Calendar.YEAR);
        int month = selectedDate.get(Calendar.MONTH);
        int day = selectedDate.get(Calendar.DAY_OF_MONTH);

        if (PhilippineHolidays.isHoliday(year, month, day)) {
            String holidayName = PhilippineHolidays.getHolidayName(year, month, day);
            holidayNameText.setText("Ã°Å¸Å½â€° " + holidayName);
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

        // Remove old listener
        if (scheduleListener != null) {
            scheduleListener.remove();
            scheduleListener = null;
        }

        // Get start and end of selected day
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

                    scheduleList.clear();

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Schedule schedule = doc.toObject(Schedule.class);
                            schedule.setId(doc.getId());
                            scheduleList.add(schedule);
                        }
                    }

                    // Sort by time
                    Collections.sort(scheduleList, (s1, s2) -> {
                        String t1 = s1.getTime() != null ? s1.getTime() : "";
                        String t2 = s2.getTime() != null ? s2.getTime() : "";
                        return t1.compareTo(t2);
                    });

                    // Also load weekly plans
                    loadWeeklyPlansForDate();
                });
    }

    private void loadWeeklyPlansForDate() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy (EEE)", Locale.getDefault());
        Log.d(TAG, "📅 Loading weekly plans for date: " + sdf.format(selectedDate.getTime()));
        Log.d(TAG, "🔢 Day of week: " + getDayNameFromCalendar(selectedDate));

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "⚠️ No weekly plans found");
                        updateScheduleDisplay();
                        return;
                    }

                    Log.d(TAG, "📋 Found " + queryDocumentSnapshots.size() + " weekly plan(s)");

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        // Get the plan's date range
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

                            // Normalize selected date
                            Calendar selectedDateNormalized = (Calendar) selectedDate.clone();
                            selectedDateNormalized.set(Calendar.HOUR_OF_DAY, 0);
                            selectedDateNormalized.set(Calendar.MINUTE, 0);
                            selectedDateNormalized.set(Calendar.SECOND, 0);
                            selectedDateNormalized.set(Calendar.MILLISECOND, 0);

                            // Check if selected date is within the plan's range
                            boolean isInRange = !selectedDateNormalized.before(planStart) &&
                                    !selectedDateNormalized.after(planEnd);

                            Log.d(TAG, "🔍 Selected date: " + planSdf.format(selectedDateNormalized.getTime()));
                            Log.d(TAG, "🔍 Is in range? " + isInRange);

                            if (isInRange) {
                                // Get the day name for the selected date
                                String selectedDayName = getDayNameFromCalendar(selectedDate);
                                Log.d(TAG, "✅ Loading tasks for " + selectedDayName);

                                // Load tasks for this specific day
                                loadWeeklyPlanTasksForSpecificDay(doc.getId(), selectedDayName);
                            } else {
                                Log.d(TAG, "⏭️ Selected date not in plan range - skipping");
                            }
                        } else {
                            Log.e(TAG, "❌ Plan missing start/end date");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load weekly plans", e);
                    updateScheduleDisplay();
                });
    }

    private void loadWeeklyPlanTasksForSpecificDay(String planId, String dayName) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Log.d(TAG, "🔎 Loading tasks for plan: " + planId + ", day: " + dayName);

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

                        // Skip empty tasks
                        if (taskText == null || taskText.trim().isEmpty()) {
                            Log.d(TAG, "⏭️ Skipping empty task");
                            continue;
                        }

                        // Create a schedule entry for this task
                        Schedule taskSchedule = new Schedule();
                        taskSchedule.setId(planId + "_" + taskDoc.getId());
                        taskSchedule.setTitle(taskText);
                        taskSchedule.setCategory("weekly");
                        taskSchedule.setSourceId(planId);
                        taskSchedule.setCompleted(isCompleted != null && isCompleted);
                        taskSchedule.setDate(new Timestamp(selectedDate.getTime()));

                        // Check if not already in the list
                        boolean exists = false;
                        for (Schedule s : scheduleList) {
                            if (s.getId().equals(taskSchedule.getId())) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists) {
                            scheduleList.add(taskSchedule);
                            Log.d(TAG, "✅ Added task to schedule list");
                        } else {
                            Log.d(TAG, "⚠️ Task already in schedule list");
                        }
                    }

                    // Update UI
                    Log.d(TAG, "🔄 Updating UI with " + scheduleList.size() + " total schedules");
                    updateScheduleDisplay();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load weekly plan tasks for " + dayName, e);
                    updateScheduleDisplay();
                });
    }

    private String getDayNameFromCalendar(Calendar calendar) {
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        switch (dayOfWeek) {
            case Calendar.SUNDAY:
                return "Sun";
            case Calendar.MONDAY:
                return "Mon";
            case Calendar.TUESDAY:
                return "Tues";
            case Calendar.WEDNESDAY:
                return "Wed";
            case Calendar.THURSDAY:
                return "Thur";
            case Calendar.FRIDAY:
                return "Fri";
            case Calendar.SATURDAY:
                return "Sat";
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

        // Get views
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

        // Set data
        titleText.setText(schedule.getTitle());

        // Show time if available
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
            reminderText.setText("Ã°Å¸â€â€ " + schedule.getReminderMinutes() + " minutes before");
            reminderText.setVisibility(View.VISIBLE);
        }

        // Load tasks for Weekly/Todo
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

                            String text = "weekly".equals(schedule.getCategory()) ?
                                    doc.getString("taskText") : doc.getString("taskText");
                            boolean isCompleted = Boolean.TRUE.equals(doc.getBoolean("isCompleted"));

                            checkbox.setChecked(isCompleted);
                            taskText.setText(text);

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

        String message = "Delete " + selectedSchedules.size() + " schedule(s)?";

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

        for (Schedule schedule : selectedSchedules) {
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
        }

        Toast.makeText(this, "Ã¢Å“â€œ " + selectedSchedules.size() + " schedule(s) deleted", Toast.LENGTH_SHORT).show();
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
        if (scheduleListener != null) {
            scheduleListener.remove();
            scheduleListener = null;
        }
    }
}