package com.example.testtasksync;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeeklyActivity extends AppCompatActivity {

    private static final String TAG = "WeeklyActivity";
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String planId;
    private boolean isNewPlan = false;

    // STEP 1: Added fields for week range picker
    private Calendar startDate = null;
    private Calendar endDate = null;
    private TextView weekRangeText;
    private LinearLayout weekRangeSection;
    private ImageView clearWeekButton;

    private EditText weeklyTitle;
    private ImageView saveButton, backButton;

    private List<String> days = Arrays.asList("Mon", "Tues", "Wed", "Thur", "Fri", "Sat", "Sun");
    private Map<String, LinearLayout> dayContainers = new HashMap<>();
    private Map<String, List<WeeklyTask>> dayTasks = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weekly);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Get plan ID from intent
        planId = getIntent().getStringExtra("planId");
        isNewPlan = (planId == null || planId.isEmpty());

        // Initialize views
        weeklyTitle = findViewById(R.id.weeklyTitle);
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);

        // STEP 2: Initialize week range views
        weekRangeSection = findViewById(R.id.weekRangeSection);
        weekRangeText = findViewById(R.id.weekRangeText);
        clearWeekButton = findViewById(R.id.clearWeekButton);

        // Set up week range picker with quick selector
        weekRangeSection.setOnClickListener(v -> showQuickWeekSelector());
        clearWeekButton.setOnClickListener(v -> clearWeekRange());

        // Initialize with current week by default
        setCurrentWeek();

        // Initialize day containers
        dayContainers.put("Mon", findViewById(R.id.monTasksContainer));
        dayContainers.put("Tues", findViewById(R.id.tuesTasksContainer));
        dayContainers.put("Wed", findViewById(R.id.wedTasksContainer));
        dayContainers.put("Thur", findViewById(R.id.thurTasksContainer));
        dayContainers.put("Fri", findViewById(R.id.friTasksContainer));
        dayContainers.put("Sat", findViewById(R.id.satTasksContainer));
        dayContainers.put("Sun", findViewById(R.id.sunTasksContainer));

        // Initialize task lists
        for (String day : days) {
            dayTasks.put(day, new ArrayList<>());
        }

        // Set up add task buttons
        findViewById(R.id.addMonTask).setOnClickListener(v -> addTask("Mon"));
        findViewById(R.id.addTuesTask).setOnClickListener(v -> addTask("Tues"));
        findViewById(R.id.addWedTask).setOnClickListener(v -> addTask("Wed"));
        findViewById(R.id.addThurTask).setOnClickListener(v -> addTask("Thur"));
        findViewById(R.id.addFriTask).setOnClickListener(v -> addTask("Fri"));
        findViewById(R.id.addSatTask).setOnClickListener(v -> addTask("Sat"));
        findViewById(R.id.addSunTask).setOnClickListener(v -> addTask("Sun"));

        // Set up save and back buttons
        saveButton.setOnClickListener(v -> saveWeeklyPlan());
        backButton.setOnClickListener(v -> finish());

        // Load existing plan or add default tasks
        if (isNewPlan) {
            // Add 3 default tasks for each day
            for (String day : days) {
                for (int i = 0; i < 3; i++) {
                    addTask(day);
                }
            }
        } else {
            loadWeeklyPlan();
        }
    }

    private void loadWeeklyPlan() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String userId = user.getUid();

        // Query 1: Plan details
        db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        if (title != null && !title.isEmpty()) {
                            weeklyTitle.setText(title);
                        }

                        // STEP 4: Load week range if exists
                        Timestamp startDateTimestamp = documentSnapshot.getTimestamp("startDate");
                        Timestamp endDateTimestamp = documentSnapshot.getTimestamp("endDate");

                        if (startDateTimestamp != null && endDateTimestamp != null) {
                            startDate = Calendar.getInstance();
                            startDate.setTime(startDateTimestamp.toDate());

                            endDate = Calendar.getInstance();
                            endDate.setTime(endDateTimestamp.toDate());

                            updateWeekRangeDisplay();
                        } else {
                            // If no saved dates, use current week
                            setCurrentWeek();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load weekly plan", e);
                    Toast.makeText(this, "Failed to load plan", Toast.LENGTH_SHORT).show();
                });

        // Query 2: Tasks (running in parallel with Query 1)
        db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .orderBy("position")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Clear existing views
                    for (LinearLayout container : dayContainers.values()) {
                        container.removeAllViews();
                    }

                    // Clear task lists
                    for (String day : days) {
                        dayTasks.get(day).clear();
                    }

                    // Load tasks from Firebase
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        WeeklyTask task = new WeeklyTask();
                        task.setId(doc.getId());
                        task.setDay(doc.getString("day"));
                        task.setTaskText(doc.getString("taskText"));
                        task.setCompleted(Boolean.TRUE.equals(doc.getBoolean("isCompleted")));
                        task.setPosition(doc.getLong("position").intValue());

                        List<WeeklyTask> tasks = dayTasks.get(task.getDay());
                        if (tasks != null) {
                            tasks.add(task);
                        }
                    }

                    // Add task views for each day
                    for (String day : days) {
                        List<WeeklyTask> tasks = dayTasks.get(day);
                        if (tasks.isEmpty()) {
                            // Add default tasks if none exist
                            for (int i = 0; i < 3; i++) {
                                addTask(day);
                            }
                        } else {
                            for (WeeklyTask task : tasks) {
                                addTaskView(day, task);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load tasks", e);
                    // Add default tasks if loading fails
                    for (String day : days) {
                        for (int i = 0; i < 3; i++) {
                            addTask(day);
                        }
                    }
                });
    }

    private void addTask(String day) {
        WeeklyTask task = new WeeklyTask();
        task.setId(""); // Will be generated on save
        task.setDay(day);
        task.setTaskText("");
        task.setCompleted(false);
        task.setPosition(dayTasks.get(day).size());

        dayTasks.get(day).add(task);
        addTaskView(day, task);
    }

    private void addTaskView(String day, WeeklyTask task) {
        LinearLayout container = dayContainers.get(day);
        if (container == null) return;

        View taskView = LayoutInflater.from(this).inflate(R.layout.item_weekly_task, container, false);

        CheckBox checkbox = taskView.findViewById(R.id.taskCheckbox);
        EditText taskText = taskView.findViewById(R.id.taskEditText);
        ImageView deleteButton = taskView.findViewById(R.id.deleteTaskButton);

        // Set initial values
        checkbox.setChecked(task.isCompleted());
        if (task.getTaskText() != null && !task.getTaskText().isEmpty()) {
            taskText.setText(task.getTaskText());
        }

        // Checkbox listener
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            task.setCompleted(isChecked);
            if (isChecked) {
                taskText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            } else {
                taskText.setTextColor(getResources().getColor(android.R.color.black));
            }
        });

        // Text change listener
        taskText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                task.setTaskText(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Focus listener to show/hide delete button
        taskText.setOnFocusChangeListener((v, hasFocus) -> {
            deleteButton.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
        });

        // Delete button listener
        deleteButton.setOnClickListener(v -> {
            List<WeeklyTask> tasks = dayTasks.get(day);
            if (tasks != null && tasks.size() > 1) { // Keep at least 1 task
                tasks.remove(task);
                container.removeView(taskView);
                updateTaskPositions(day);
            } else {
                Toast.makeText(this, "Keep at least one task per day", Toast.LENGTH_SHORT).show();
            }
        });

        container.addView(taskView);
    }

    private void updateTaskPositions(String day) {
        List<WeeklyTask> tasks = dayTasks.get(day);
        if (tasks != null) {
            for (int i = 0; i < tasks.size(); i++) {
                tasks.get(i).setPosition(i);
            }
        }
    }

    private void saveWeeklyPlan() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = weeklyTitle.getText().toString().trim();
        if (title.isEmpty()) {
            title = "Weekly Plan";
        }

        // Calculate total and completed tasks
        int totalTasks = 0;
        int completedTasks = 0;
        for (String day : days) {
            List<WeeklyTask> tasks = dayTasks.get(day);
            if (tasks != null) {
                for (WeeklyTask task : tasks) {
                    if (!task.getTaskText().trim().isEmpty()) {
                        totalTasks++;
                        if (task.isCompleted()) {
                            completedTasks++;
                        }
                    }
                }
            }
        }

        // STEP 6: Update mainScheduleData with week range in description
        Map<String, Object> mainScheduleData = new HashMap<>();
        mainScheduleData.put("title", title);

        String description = totalTasks + " tasks";
        if (startDate != null && endDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            description += " (" + sdf.format(startDate.getTime()) + " - " +
                    sdf.format(endDate.getTime()) + ")";
        }
        if (completedTasks > 0) {
            description += " â€¢ " + completedTasks + " completed";
        }

        mainScheduleData.put("description", description);
        mainScheduleData.put("category", "weekly");
        mainScheduleData.put("isCompleted", completedTasks == totalTasks && totalTasks > 0);
        mainScheduleData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        // Set date to start of week (so it appears in calendar on that date)
        if (startDate != null) {
            mainScheduleData.put("date", new Timestamp(startDate.getTime()));
        } else {
            mainScheduleData.put("date", null);
        }

        mainScheduleData.put("time", "");
        mainScheduleData.put("hasReminder", false);

        // Generate new ID if this is a new plan
        if (isNewPlan) {
            planId = db.collection("users")
                    .document(user.getUid())
                    .collection("weeklyPlans")
                    .document().getId();
        }

        final String finalTitle = title;
        final String finalPlanId = planId;
        final int finalTotalTasks = totalTasks;
        final int finalCompletedTasks = completedTasks;

        // STEP 5: Save to weeklyPlans collection with date range
        Map<String, Object> planData = new HashMap<>();
        planData.put("title", finalTitle);
        planData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        planData.put("taskCount", finalTotalTasks);
        planData.put("completedCount", finalCompletedTasks);

        // Add week range dates
        if (startDate != null && endDate != null) {
            planData.put("startDate", new Timestamp(startDate.getTime()));
            planData.put("endDate", new Timestamp(endDate.getTime()));
        } else {
            planData.put("startDate", null);
            planData.put("endDate", null);
        }

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(finalPlanId)
                .set(planData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Weekly plan saved successfully");
                    saveTasks(user.getUid(), finalPlanId, finalTitle, finalTotalTasks,
                            finalCompletedTasks, mainScheduleData);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save weekly plan", e);
                    Toast.makeText(this, "Failed to save plan", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveTasks(String userId, String planId, String planTitle, int totalTasks,
                           int completedTasks, Map<String, Object> mainScheduleData) {
        // First, delete all existing tasks
        db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().delete();
                    }

                    // Count tasks to save
                    int tasksToSave = 0;
                    for (String day : days) {
                        List<WeeklyTask> tasks = dayTasks.get(day);
                        if (tasks != null) {
                            for (WeeklyTask task : tasks) {
                                if (!task.getTaskText().trim().isEmpty()) {
                                    tasksToSave++;
                                }
                            }
                        }
                    }

                    // If no tasks, just create main schedule and finish
                    if (tasksToSave == 0) {
                        createOrUpdateMainSchedule(userId, planId, mainScheduleData);
                        return;
                    }

                    final int[] savedCount = {0};
                    for (String day : days) {
                        List<WeeklyTask> tasks = dayTasks.get(day);
                        if (tasks != null) {
                            for (WeeklyTask task : tasks) {
                                if (!task.getTaskText().trim().isEmpty()) {
                                    Map<String, Object> taskData = new HashMap<>();
                                    taskData.put("day", task.getDay());
                                    taskData.put("taskText", task.getTaskText());
                                    taskData.put("isCompleted", task.isCompleted());
                                    taskData.put("position", task.getPosition());

                                    int finalTasksToSave = tasksToSave;
                                    db.collection("users")
                                            .document(userId)
                                            .collection("weeklyPlans")
                                            .document(planId)
                                            .collection("tasks")
                                            .add(taskData)
                                            .addOnSuccessListener(documentReference -> {
                                                savedCount[0]++;
                                                if (savedCount[0] == finalTasksToSave) {
                                                    // All tasks saved, now create main schedule
                                                    createOrUpdateMainSchedule(userId, planId, mainScheduleData);
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Failed to save task", e);
                                            });
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete old tasks", e);
                    Toast.makeText(this, "Failed to save tasks", Toast.LENGTH_SHORT).show();
                });
    }

    private void createOrUpdateMainSchedule(String userId, String planId,
                                            Map<String, Object> mainScheduleData) {
        // Add sourceId to link back to the weekly plan
        mainScheduleData.put("sourceId", planId);

        // Check if schedule already exists for this weekly plan
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .whereEqualTo("sourceId", planId)
                .whereEqualTo("category", "weekly")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Update existing schedule
                        String scheduleId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .document(scheduleId)
                                .update(mainScheduleData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Main schedule updated for weekly plan");
                                    Toast.makeText(this, "âœ“ Weekly plan saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update schedule", e);
                                    Toast.makeText(this, "âœ“ Weekly plan saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    } else {
                        // Create new schedule
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .add(mainScheduleData)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d(TAG, "Main schedule created for weekly plan");
                                    Toast.makeText(this, "âœ“ Weekly plan saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create schedule", e);
                                    Toast.makeText(this, "âœ“ Weekly plan saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check existing schedule", e);
                    Toast.makeText(this, "âœ“ Weekly plan saved", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private int getDayOfWeekNumber(String day) {
        switch (day) {
            case "Sun": return Calendar.SUNDAY;
            case "Mon": return Calendar.MONDAY;
            case "Tues": return Calendar.TUESDAY;
            case "Wed": return Calendar.WEDNESDAY;
            case "Thur": return Calendar.THURSDAY;
            case "Fri": return Calendar.FRIDAY;
            case "Sat": return Calendar.SATURDAY;
            default: return Calendar.MONDAY;
        }
    }

    // STEP 3: New methods for week range picker functionality

    private void setCurrentWeek() {
        Calendar calendar = Calendar.getInstance();
        int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        // Calculate start of current week (Monday)
        int daysToMonday = (currentDayOfWeek == Calendar.SUNDAY) ? 6 : currentDayOfWeek - Calendar.MONDAY;
        calendar.add(Calendar.DAY_OF_MONTH, -daysToMonday);

        startDate = (Calendar) calendar.clone();

        // Calculate end of week (Sunday)
        calendar.add(Calendar.DAY_OF_MONTH, 6);
        endDate = (Calendar) calendar.clone();

        updateWeekRangeDisplay();
    }

    private void showQuickWeekSelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("ðŸ“… Select Week");

        // Calculate week ranges for display
        Calendar calendar = Calendar.getInstance();
        int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysToMonday = (currentDayOfWeek == Calendar.SUNDAY) ? 6 : currentDayOfWeek - Calendar.MONDAY;

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());

        // This week
        Calendar thisWeekStart = (Calendar) calendar.clone();
        thisWeekStart.add(Calendar.DAY_OF_MONTH, -daysToMonday);
        Calendar thisWeekEnd = (Calendar) thisWeekStart.clone();
        thisWeekEnd.add(Calendar.DAY_OF_MONTH, 6);

        // Next week
        Calendar nextWeekStart = (Calendar) thisWeekStart.clone();
        nextWeekStart.add(Calendar.DAY_OF_MONTH, 7);
        Calendar nextWeekEnd = (Calendar) nextWeekStart.clone();
        nextWeekEnd.add(Calendar.DAY_OF_MONTH, 6);

        // Week after next
        Calendar afterNextStart = (Calendar) thisWeekStart.clone();
        afterNextStart.add(Calendar.DAY_OF_MONTH, 14);
        Calendar afterNextEnd = (Calendar) afterNextStart.clone();
        afterNextEnd.add(Calendar.DAY_OF_MONTH, 6);

        String[] options = {
                "This Week (" + sdf.format(thisWeekStart.getTime()) + " - " + sdf.format(thisWeekEnd.getTime()) + ")",
                "Next Week (" + sdf.format(nextWeekStart.getTime()) + " - " + sdf.format(nextWeekEnd.getTime()) + ")",
                "Week After Next (" + sdf.format(afterNextStart.getTime()) + " - " + sdf.format(afterNextEnd.getTime()) + ")",
                "Custom Date Range..."
        };

        builder.setItems(options, (dialog, which) -> {
            Calendar cal = Calendar.getInstance();
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int toMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;

            switch (which) {
                case 0: // This Week
                    cal.add(Calendar.DAY_OF_MONTH, -toMonday);
                    setWeekFromStartDate(cal);
                    break;

                case 1: // Next Week
                    cal.add(Calendar.DAY_OF_MONTH, -toMonday + 7);
                    setWeekFromStartDate(cal);
                    break;

                case 2: // Week After Next
                    cal.add(Calendar.DAY_OF_MONTH, -toMonday + 14);
                    setWeekFromStartDate(cal);
                    break;

                case 3: // Custom
                    showCustomWeekPicker();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void setWeekFromStartDate(Calendar start) {
        startDate = (Calendar) start.clone();
        endDate = (Calendar) start.clone();
        endDate.add(Calendar.DAY_OF_MONTH, 6);
        updateWeekRangeDisplay();
    }

    private void showCustomWeekPicker() {
        // Step 1: Pick start date
        Calendar initialDate = startDate != null ? startDate : Calendar.getInstance();

        DatePickerDialog startDatePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    startDate = Calendar.getInstance();
                    startDate.set(year, month, dayOfMonth);

                    // Step 2: Now pick end date
                    showEndDatePicker();
                },
                initialDate.get(Calendar.YEAR),
                initialDate.get(Calendar.MONTH),
                initialDate.get(Calendar.DAY_OF_MONTH)
        );

        startDatePicker.setTitle("Select START date");
        startDatePicker.show();
    }

    private void showEndDatePicker() {
        // Default end date: 6 days after start
        Calendar defaultEnd = (Calendar) startDate.clone();
        defaultEnd.add(Calendar.DAY_OF_MONTH, 6);

        DatePickerDialog endDatePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    endDate = Calendar.getInstance();
                    endDate.set(year, month, dayOfMonth);

                    // Validate: end date must be after start date
                    if (endDate.before(startDate)) {
                        Toast.makeText(this, "âš ï¸ End date must be after start date",
                                Toast.LENGTH_SHORT).show();
                        endDate = (Calendar) startDate.clone();
                        endDate.add(Calendar.DAY_OF_MONTH, 6);
                    }

                    updateWeekRangeDisplay();
                },
                defaultEnd.get(Calendar.YEAR),
                defaultEnd.get(Calendar.MONTH),
                defaultEnd.get(Calendar.DAY_OF_MONTH)
        );

        // Set minimum date to start date
        endDatePicker.getDatePicker().setMinDate(startDate.getTimeInMillis());
        endDatePicker.setTitle("Select END date");
        endDatePicker.show();
    }

    private void updateWeekRangeDisplay() {
        if (startDate != null && endDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());

            String startStr = sdf.format(startDate.getTime());
            String endStr = sdf.format(endDate.getTime());
            String year = yearFormat.format(startDate.getTime());

            weekRangeText.setText("Week: " + startStr + " - " + endStr + ", " + year);
            clearWeekButton.setVisibility(View.VISIBLE);
        } else {
            weekRangeText.setText("Select week");
            clearWeekButton.setVisibility(View.GONE);
        }
    }

    private void clearWeekRange() {
        setCurrentWeek(); // Reset to current week instead of null
    }
}