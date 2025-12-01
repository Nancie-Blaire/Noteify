package com.example.testtasksync;
// COMMENT

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;


public class WeeklyActivity extends AppCompatActivity {

    private boolean hasReminder = false;
    private int reminderMinutes = 60;
    private static final int NOTIFICATION_PERMISSION_CODE = 100;
    private static final String TAG = "WeeklyActivity";
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String planId;
    private boolean isNewPlan = false;

    // Week range fields
    private Calendar startDate = null;
    private Calendar endDate = null;

    private EditText weeklyTitle;
    private ImageView saveButton, backButton, scheduleButton;

    private List<String> days = Arrays.asList("Mon", "Tues", "Wed", "Thur", "Fri", "Sat", "Sun");
    private Map<String, RecyclerView> dayContainers = new HashMap<>();
    private Map<String, List<WeeklyTask>> dayTasks = new HashMap<>();
    private String selectedTime = "";
    private ListenerRegistration tasksListener;


    private Map<String, WeeklyTaskAdapter> dayAdapters = new HashMap<>();
    private Map<String, ItemTouchHelper> dayTouchHelpers = new HashMap<>();

    // ✅ Weekly day schedule
    private Map<String, List<DaySchedule>> daySchedules = new HashMap<>();

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
        scheduleButton = findViewById(R.id.scheduleButton);

        NotificationHelper.createNotificationChannel(this);
        requestNotificationPermission();

        // Initialize with current week by default
        setCurrentWeek();

        // Initialize day containers (RecyclerViews)
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

        // Setup RecyclerViews with adapters
        setupRecyclerViews();

        // Set up add task buttons
        findViewById(R.id.addMonTask).setOnClickListener(v -> addTask("Mon"));
        findViewById(R.id.addTuesTask).setOnClickListener(v -> addTask("Tues"));
        findViewById(R.id.addWedTask).setOnClickListener(v -> addTask("Wed"));
        findViewById(R.id.addThurTask).setOnClickListener(v -> addTask("Thur"));
        findViewById(R.id.addFriTask).setOnClickListener(v -> addTask("Fri"));
        findViewById(R.id.addSatTask).setOnClickListener(v -> addTask("Sat"));
        findViewById(R.id.addSunTask).setOnClickListener(v -> addTask("Sun"));

        // ✅ NEW: Setup per-day schedule buttons
        findViewById(R.id.mondayScheduleButton).setOnClickListener(v -> showDayScheduleDialog("Mon"));
        findViewById(R.id.tuesdayScheduleButton).setOnClickListener(v -> showDayScheduleDialog("Tues"));
        findViewById(R.id.wednesdayScheduleButton).setOnClickListener(v -> showDayScheduleDialog("Wed"));
        findViewById(R.id.thursdayScheduleButton).setOnClickListener(v -> showDayScheduleDialog("Thur"));
        findViewById(R.id.fridayScheduleButton).setOnClickListener(v -> showDayScheduleDialog("Fri"));
        findViewById(R.id.saturdayScheduleButton).setOnClickListener(v -> showDayScheduleDialog("Sat"));
        findViewById(R.id.sundayScheduleButton).setOnClickListener(v -> showDayScheduleDialog("Sun"));

        // Set up save, back, and schedule buttons
        saveButton.setOnClickListener(v -> saveWeeklyPlan());
        backButton.setOnClickListener(v -> finish());
        scheduleButton.setOnClickListener(v -> showScheduleDialog());

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
    private void setupRecyclerViews() {
        for (String day : days) {
            RecyclerView recyclerView = dayContainers.get(day);
            List<WeeklyTask> tasks = dayTasks.get(day);

            WeeklyTaskAdapter adapter = new WeeklyTaskAdapter(day, tasks,
                    new WeeklyTaskAdapter.TaskActionListener() {
                        @Override
                        public void onTaskTextChanged(WeeklyTask task, String newText) {
                            // Auto-save functionality can be added here
                        }

                        @Override
                        public void onTaskCompletionChanged(WeeklyTask task, boolean isCompleted) {
                            if (!isNewPlan && task.getId() != null && !task.getId().isEmpty()) {
                                FirebaseUser user = auth.getCurrentUser();
                                if (user != null) {
                                    db.collection("users")
                                            .document(user.getUid())
                                            .collection("weeklyPlans")
                                            .document(planId)
                                            .collection("tasks")
                                            .document(task.getId())
                                            .update("isCompleted", isCompleted)
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "✅ Task completion updated");

                                                // Cancel notification when completed
                                                if (isCompleted) {
                                                    NotificationHelper.cancelNotification(
                                                            WeeklyActivity.this, task.getId());
                                                }
                                            });
                                }
                            }
                        }

                        @Override
                        public void onDeleteClicked(WeeklyTask task, int position) {
                            List<WeeklyTask> dayTaskList = dayTasks.get(day);
                            if (dayTaskList != null && dayTaskList.size() > 1) {
                                dayTaskList.remove(position);

                                WeeklyTaskAdapter currentAdapter = dayAdapters.get(day);
                                if (currentAdapter != null) {
                                    currentAdapter.notifyItemRemoved(position);
                                }

                                updateTaskPositions(day);
                            } else {
                                Toast.makeText(WeeklyActivity.this,
                                        "Keep at least one task per day", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onTaskMoved(int fromPosition, int toPosition) {
                            // Save when order changes (optional)
                        }
                    });

            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
            recyclerView.setNestedScrollingEnabled(false);

            dayAdapters.put(day, adapter);

            // ✅ IMPROVED: Setup drag and drop with BETTER cross-day support
            ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {

                private String draggedFromDay = null;
                private WeeklyTask draggedTask = null;
                private int draggedFromPosition = -1;

                @Override
                public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder) {
                    int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                    int swipeFlags = 0;
                    return makeMovementFlags(dragFlags, swipeFlags);
                }

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder viewHolder,
                                      @NonNull RecyclerView.ViewHolder target) {

                    // ✅ Simply allow all moves - cross-day detection happens in clearView()
                    int fromPos = viewHolder.getAdapterPosition();
                    int toPos = target.getAdapterPosition();

                    WeeklyTaskAdapter currentAdapter = dayAdapters.get(day);
                    if (currentAdapter != null) {
                        currentAdapter.moveItem(fromPos, toPos);
                    }
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                    // No swipe
                }

                @Override
                public boolean isLongPressDragEnabled() {
                    return true;
                }

                @Override
                public boolean isItemViewSwipeEnabled() {
                    return false;
                }

                @Override
                public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                    super.onSelectedChanged(viewHolder, actionState);

                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                        draggedFromDay = day;
                        draggedFromPosition = viewHolder.getAdapterPosition();

                        if (draggedFromPosition >= 0 && draggedFromPosition < tasks.size()) {
                            draggedTask = tasks.get(draggedFromPosition);

                            // ✅ Force update task text from EditText
                            if (viewHolder instanceof WeeklyTaskAdapter.TaskViewHolder) {
                                WeeklyTaskAdapter.TaskViewHolder taskHolder =
                                        (WeeklyTaskAdapter.TaskViewHolder) viewHolder;
                                String currentText = taskHolder.taskText.getText().toString();
                                draggedTask.setTaskText(currentText);

                                Log.d(TAG, "🎯 Started dragging: '" + draggedTask.getTaskText() +
                                        "' from " + day + " at position " + draggedFromPosition);
                            }
                        } else {
                            Log.e(TAG, "❌ Invalid drag position: " + draggedFromPosition);
                            draggedTask = null;
                            return;
                        }

                        // Visual feedback
                        viewHolder.itemView.setAlpha(0.6f);
                        viewHolder.itemView.setScaleX(1.05f);
                        viewHolder.itemView.setScaleY(1.05f);

                        // ✅ CRITICAL: Disable NestedScrollView during drag
                        androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView);
                        if (scrollView != null) {
                            scrollView.requestDisallowInterceptTouchEvent(true);
                            scrollView.setOnTouchListener((v, event) -> true); // Block scroll completely
                        }
                    } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                        // ✅ Re-enable NestedScrollView when drag ends
                        androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView);
                        if (scrollView != null) {
                            scrollView.requestDisallowInterceptTouchEvent(false);
                            scrollView.setOnTouchListener(null); // Restore normal scroll
                        }
                    }
                }
                @Override
                public void clearView(@NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder viewHolder) {
                    super.clearView(recyclerView, viewHolder);

                    // Reset visual
                    viewHolder.itemView.setAlpha(1.0f);
                    viewHolder.itemView.setScaleX(1.0f);
                    viewHolder.itemView.setScaleY(1.0f);

                    // ✅ Re-enable scroll
                    androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView);
                    if (scrollView != null) {
                        scrollView.requestDisallowInterceptTouchEvent(false);
                        scrollView.setOnTouchListener(null); // ⚠️ ADD THIS LINE!
                    }

                    // Check for cross-day drop
                    checkCrossDayDrop(viewHolder);
                }
                private void checkCrossDayDrop(RecyclerView.ViewHolder viewHolder) {
                    if (draggedTask == null || draggedFromDay == null) {
                        resetDragState();
                        return;
                    }

                    // Get the dragged item's position on screen
                    int[] location = new int[2];
                    viewHolder.itemView.getLocationOnScreen(location);
                    int itemTop = location[1];
                    int itemBottom = location[1] + viewHolder.itemView.getHeight();
                    int itemCenterY = location[1] + (viewHolder.itemView.getHeight() / 2);

                    Log.d(TAG, "🎯 Dropped at Y: " + itemCenterY + " (top: " + itemTop + ", bottom: " + itemBottom + ")");

                    LinearLayout daysContainer = findViewById(R.id.daysContainer);

                    // ✅ Increased margin for better detection (especially upward)
                    int margin = (int) (100 * getResources().getDisplayMetrics().density);

                    // ✅ Check ALL days (not just those after current day)
                    for (int i = 0; i < days.size(); i++) {
                        String targetDay = days.get(i);

                        // Skip the day we're dragging from
                        if (targetDay.equals(draggedFromDay)) continue;

                        View daySection = daysContainer.getChildAt(i);

                        if (daySection != null) {
                            int[] daySectionLoc = new int[2];
                            daySection.getLocationOnScreen(daySectionLoc);

                            // ✅ IMPROVED: Use the day section's actual bounds with extended margins
                            int sectionTop = daySectionLoc[1] - margin;
                            int sectionBottom = daySectionLoc[1] + daySection.getHeight() + margin;

                            Log.d(TAG, "📍 " + targetDay + " zone: " + sectionTop + " to " + sectionBottom);

                            // ✅ Check if item center OR any part of item overlaps with target day
                            boolean centerInZone = itemCenterY >= sectionTop && itemCenterY <= sectionBottom;
                            boolean topInZone = itemTop >= sectionTop && itemTop <= sectionBottom;
                            boolean bottomInZone = itemBottom >= sectionTop && itemBottom <= sectionBottom;

                            if (centerInZone || topInZone || bottomInZone) {
                                Log.d(TAG, "✅ Moving to " + targetDay);

                                // Get current position after any reordering
                                List<WeeklyTask> fromTasks = dayTasks.get(draggedFromDay);
                                int currentPosition = fromTasks.indexOf(draggedTask);

                                if (currentPosition >= 0) {
                                    moveTaskToAnotherDay(draggedTask, draggedFromDay, targetDay, currentPosition);
                                } else {
                                    Log.e(TAG, "❌ Task not found in source day list!");
                                }

                                resetDragState();
                                return;
                            }
                        }
                    }

                    // If not moved to another day, just update positions within same day
                    Log.d(TAG, "⚪ Stayed in " + draggedFromDay);
                    updateTaskPositions(draggedFromDay);
                    resetDragState();
                }

                private void resetDragState() {
                    draggedTask = null;
                    draggedFromDay = null;
                    draggedFromPosition = -1;
                }
            };
            ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(recyclerView);
            dayTouchHelpers.put(day, touchHelper);
        }
    }

    // ✅ NEW: Method to move task between days
    // ✅ UPDATED: Method to move task between days
    // ✅ UPDATED: Method to move task between days
    private void moveTaskToAnotherDay(WeeklyTask task, String fromDay, String toDay, int fromPosition) {
        // ✅ DEBUG: Check the state BEFORE moving (dapat nandito sa simula)
        List<WeeklyTask> fromTasks = dayTasks.get(fromDay);
        List<WeeklyTask> toTasks = dayTasks.get(toDay);

        Log.d(TAG, "📊 BEFORE MOVE:");
        Log.d(TAG, "   " + fromDay + " has " + (fromTasks != null ? fromTasks.size() : 0) + " tasks");
        Log.d(TAG, "   " + toDay + " has " + (toTasks != null ? toTasks.size() : 0) + " tasks");
        Log.d(TAG, "   Removing from position: " + fromPosition);
        Log.d(TAG, "   Task text: " + task.getTaskText());

        Log.d(TAG, "🔄 Moving task from " + fromDay + " (pos: " + fromPosition + ") to " + toDay);

        // Remove from source day
        if (fromTasks != null && fromPosition >= 0 && fromPosition < fromTasks.size()) {
            // ✅ IMPORTANT: Remove the exact task object, not by ID
            WeeklyTask removedTask = fromTasks.remove(fromPosition);

            Log.d(TAG, "✅ Removed task: " + removedTask.getTaskText() + " from " + fromDay);

            WeeklyTaskAdapter fromAdapter = dayAdapters.get(fromDay);
            if (fromAdapter != null) {
                fromAdapter.notifyItemRemoved(fromPosition);
                // ✅ Notify range changed to update remaining items
                fromAdapter.notifyItemRangeChanged(fromPosition, fromTasks.size() - fromPosition);
            }
        } else {
            Log.e(TAG, "❌ Failed to remove task - invalid position or list");
            return;
        }

        // Update task's day
        task.setDay(toDay);

        // Add to target day
        if (toTasks != null) {
            toTasks.add(task);
            task.setPosition(toTasks.size() - 1);

            Log.d(TAG, "✅ Added task: " + task.getTaskText() + " to " + toDay + " at position " + task.getPosition());

            WeeklyTaskAdapter toAdapter = dayAdapters.get(toDay);
            if (toAdapter != null) {
                toAdapter.notifyItemInserted(toTasks.size() - 1);
            }
        }

        // Update positions in both days
        updateTaskPositions(fromDay);
        updateTaskPositions(toDay);

        // ✅ DEBUG: Check the state AFTER moving
        Log.d(TAG, "📊 AFTER MOVE:");
        Log.d(TAG, "   " + fromDay + " has " + (fromTasks != null ? fromTasks.size() : 0) + " tasks");
        Log.d(TAG, "   " + toDay + " has " + (toTasks != null ? toTasks.size() : 0) + " tasks");

        Toast.makeText(this, "Moved to " + toDay, Toast.LENGTH_SHORT).show();
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
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

        // ✅ FIRST: Load schedule data (has the date/time info from DayDetails)
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .whereEqualTo("sourceId", planId)
                .whereEqualTo("category", "weekly")
                .get()
                .addOnSuccessListener(scheduleSnapshots -> {
                    if (!scheduleSnapshots.isEmpty()) {
                        // ✅ Load date/time/range from schedule document
                        DocumentSnapshot scheduleDoc = scheduleSnapshots.getDocuments().get(0);

                        // Load time
                        String savedTime = scheduleDoc.getString("time");
                        if (savedTime != null && !savedTime.isEmpty()) {
                            selectedTime = savedTime;
                        }

                        // Try to get date range from schedule
                        Timestamp dateTimestamp = scheduleDoc.getTimestamp("date");
                        if (dateTimestamp != null && startDate == null) {
                            // If no range set, use the date as start of week
                            startDate = Calendar.getInstance();
                            startDate.setTime(dateTimestamp.toDate());
                        }
                    }

                    // ✅ THEN: Load plan details
                    loadWeeklyPlanDetails(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load schedule", e);
                    // Still try to load plan details even if schedule fails
                    loadWeeklyPlanDetails(userId);
                });
    }

    // ✅ NEW: Separate method to load plan details
    private void loadWeeklyPlanDetails(String userId) {
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

                        // ✅ Only use these if schedule didn't have them
                        if (selectedTime == null || selectedTime.isEmpty()) {
                            String savedTime = documentSnapshot.getString("time");
                            if (savedTime != null && !savedTime.isEmpty()) {
                                selectedTime = savedTime;
                            }
                        }

                        // Load week range
                        Timestamp startDateTimestamp = documentSnapshot.getTimestamp("startDate");
                        Timestamp endDateTimestamp = documentSnapshot.getTimestamp("endDate");

                        if (startDateTimestamp != null && endDateTimestamp != null) {
                            if (startDate == null) {
                                startDate = Calendar.getInstance();
                                startDate.setTime(startDateTimestamp.toDate());
                            }

                            endDate = Calendar.getInstance();
                            endDate.setTime(endDateTimestamp.toDate());
                        } else {
                            // If no saved dates, use current week
                            if (startDate == null) {
                                setCurrentWeek();
                            } else {
                                // We have startDate from schedule, calculate endDate
                                endDate = (Calendar) startDate.clone();
                                endDate.add(Calendar.DAY_OF_MONTH, 6);
                            }
                        }

                        // ========================================
                        // ✅ NEW: LOAD NOTIFICATION SETTINGS MULA SA SCHEDULES COLLECTION
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .whereEqualTo("sourceId", planId)
                                .whereEqualTo("category", "weekly")
                                .get()
                                .addOnSuccessListener(scheduleSnapshots -> {
                                    if (!scheduleSnapshots.isEmpty()) {
                                        DocumentSnapshot scheduleDoc = scheduleSnapshots.getDocuments().get(0);
                                        Boolean hasReminderFromSchedule = scheduleDoc.getBoolean("hasReminder");
                                        Long reminderMinutesFromSchedule = scheduleDoc.getLong("reminderMinutes");

                                        if (hasReminderFromSchedule != null) {
                                            // Update the class variable
                                            hasReminder = hasReminderFromSchedule;
                                        }
                                        if (reminderMinutesFromSchedule != null) {
                                            // Update the class variable
                                            reminderMinutes = reminderMinutesFromSchedule.intValue();
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to load schedule notification settings", e);
                                });
                        // END OF NEW CODE
                        // ========================================

                    } // End of if (documentSnapshot.exists())

                    // ✅ Load tasks after plan details and notification settings are initiated
                    loadWeeklyPlanTasks(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load weekly plan", e);
                    Toast.makeText(this, "Failed to load plan", Toast.LENGTH_SHORT).show();
                });
    }
    // ✅ NEW: Separate method to load tasks
    private void loadWeeklyPlanTasks(String userId) {
        db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .orderBy("position")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
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

                    // Refresh all adapters
                    for (String day : days) {
                        List<WeeklyTask> tasks = dayTasks.get(day);
                        if (tasks.isEmpty()) {
                            for (int i = 0; i < 3; i++) {
                                addTask(day);
                            }
                        } else {
                            WeeklyTaskAdapter adapter = dayAdapters.get(day);
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }

                    // ✅ Load day schedules
                    loadDaySchedules(userId);

                    setupTasksRealtimeListener(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load tasks", e);
                    for (String day : days) {
                        for (int i = 0; i < 3; i++) {
                            addTask(day);
                        }
                    }
                });
    }

    // ✅ ADD new method to load day schedules
    private void loadDaySchedules(String userId) {
        db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .collection("daySchedules")
                .orderBy("scheduleNumber")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    daySchedules.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        DaySchedule schedule = doc.toObject(DaySchedule.class);
                        String day = schedule.getDay();

                        // ✅ Add to list for this day
                        if (!daySchedules.containsKey(day)) {
                            daySchedules.put(day, new ArrayList<>());
                        }
                        daySchedules.get(day).add(schedule);

                        Log.d(TAG, "✅ Loaded schedule " + schedule.getScheduleNumber() +
                                " for " + day);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load day schedules", e);
                });
    }

    private void addTask(String day) {
        WeeklyTask task = new WeeklyTask();
        task.setId("");
        task.setDay(day);
        task.setTaskText("");
        task.setCompleted(false);
        task.setPosition(dayTasks.get(day).size());

        dayTasks.get(day).add(task);

        WeeklyTaskAdapter adapter = dayAdapters.get(day);
        if (adapter != null) {
            adapter.notifyItemInserted(dayTasks.get(day).size() - 1);
        }
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

        // Update mainScheduleData with week range in description
        Map<String, Object> mainScheduleData = new HashMap<>();
        mainScheduleData.put("title", title);

        String description = totalTasks + " tasks";
        if (startDate != null && endDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            description += " (" + sdf.format(startDate.getTime()) + " - " +
                    sdf.format(endDate.getTime()) + ")";
        }

        if (completedTasks > 0) {
            description += " • " + completedTasks + " completed";
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

        // âœ… ADD TIME to mainScheduleData
        mainScheduleData.put("time", selectedTime != null ? selectedTime : "");
        mainScheduleData.put("hasReminder", hasReminder);
        mainScheduleData.put("reminderMinutes", reminderMinutes);


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

        // Save to weeklyPlans collection with date range and time
        Map<String, Object> planData = new HashMap<>();
        planData.put("title", finalTitle);
        planData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        planData.put("taskCount", finalTotalTasks);
        planData.put("completedCount", finalCompletedTasks);

        // âœ… ADD TIME to planData
        planData.put("time", selectedTime != null ? selectedTime : "");

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
                        // ✅ Save day schedules
                        saveDaySchedules(userId, planId);
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
                                                String taskId = documentReference.getId();
                                                task.setId(taskId);

                                                savedCount[0]++;

                                                if (savedCount[0] == finalTasksToSave) {
                                                    // All tasks saved
                                                    createOrUpdateMainSchedule(userId, planId, mainScheduleData);

                                                    // ✅ Save day schedules
                                                    saveDaySchedules(userId, planId);

                                                    // ✅ Schedule notifications per day
                                                    schedulePerDayNotifications(userId, planId, planTitle);
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

    // ✅ ADD new method to save day schedules
    private void saveDaySchedules(String userId, String planId) {
        // First, delete existing day schedules
        db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .collection("daySchedules")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().delete();
                    }

                    // ✅ Save new day schedules (now supports multiple per day)
                    for (Map.Entry<String, List<DaySchedule>> entry : daySchedules.entrySet()) {
                        String day = entry.getKey();
                        List<DaySchedule> schedules = entry.getValue();

                        for (int i = 0; i < schedules.size(); i++) {
                            DaySchedule schedule = schedules.get(i);

                            Map<String, Object> scheduleData = new HashMap<>();
                            scheduleData.put("day", schedule.getDay());
                            scheduleData.put("date", schedule.getDate());
                            scheduleData.put("time", schedule.getTime());
                            scheduleData.put("hasReminder", schedule.isHasReminder());
                            scheduleData.put("reminderMinutes", schedule.getReminderMinutes());
                            scheduleData.put("scheduleNumber", i + 1);

                            // ✅ Use auto-generated ID instead of day name (to support multiple)
                            int finalI = i;
                            db.collection("users")
                                    .document(userId)
                                    .collection("weeklyPlans")
                                    .document(planId)
                                    .collection("daySchedules")
                                    .add(scheduleData)
                                    .addOnSuccessListener(documentReference -> {
                                        Log.d(TAG, "✅ Day schedule saved for " + day +
                                                " (Schedule " + (finalI + 1) + ")");
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to save day schedule for " + day, e);
                                    });
                        }
                    }
                });
    }

    // ✅ ADD new method to schedule per-day notifications
    private void schedulePerDayNotifications(String userId, String planId, String planTitle) {
        for (String day : days) {
            List<DaySchedule> dayScheduleList = daySchedules.get(day);

            if (dayScheduleList != null && !dayScheduleList.isEmpty()) {
                for (DaySchedule daySchedule : dayScheduleList) {
                    if (daySchedule.isHasReminder()) {
                        List<WeeklyTask> dayTaskList = dayTasks.get(day);

                        if (dayTaskList != null) {
                            for (WeeklyTask task : dayTaskList) {
                                if (!task.getTaskText().trim().isEmpty() && !task.isCompleted()) {
                                    // ✅ Create unique notification ID with schedule number
                                    String notificationId = task.getId() + "_sched_" +
                                            daySchedule.getScheduleNumber();

                                    NotificationHelper.scheduleWeeklyTaskNotification(
                                            this,
                                            notificationId,
                                            planTitle,
                                            task.getTaskText(),
                                            day,
                                            daySchedule.getDate().toDate(),
                                            daySchedule.getTime(),
                                            daySchedule.getReminderMinutes()
                                    );

                                    Log.d(TAG, "📢 Scheduled notification for " + day +
                                            " (Schedule " + daySchedule.getScheduleNumber() +
                                            "): " + task.getTaskText());
                                }
                            }
                        }
                    }
                }
            }
        }
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
                                    Toast.makeText(this, "Weekly plan saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update schedule", e);
                                    Toast.makeText(this, "Weekly plan saved", Toast.LENGTH_SHORT).show();
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
                                    Toast.makeText(this, "Weekly plan saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create schedule", e);
                                    Toast.makeText(this, " Weekly plan saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check existing schedule", e);
                    Toast.makeText(this, "Weekly plan saved", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

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
    }

    // ✅ ADD this new method for per-day schedule dialog
    private void showDayScheduleDialog(String day) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_weekly_day_schedule, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Get views
        TextView dayNameTitle = dialogView.findViewById(R.id.dayNameTitle);
        LinearLayout timeSchedulesContainer = dialogView.findViewById(R.id.timeSchedulesContainer);
        Button addTimeScheduleButton = dialogView.findViewById(R.id.addTimeScheduleButton);
        Button clearScheduleButton = dialogView.findViewById(R.id.clearScheduleButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveDayScheduleButton = dialogView.findViewById(R.id.saveDayScheduleButton);

        // Set title
        String fullDayName = getFullDayName(day);
        dayNameTitle.setText(fullDayName + " Schedule");

        // ✅ Initialize schedules list for this day if needed
        if (!daySchedules.containsKey(day)) {
            daySchedules.put(day, new ArrayList<>());
        }

        List<DaySchedule> existingSchedules = daySchedules.get(day);

        // ✅ Load existing schedules or add first one
        if (existingSchedules.isEmpty()) {
            addTimeScheduleView(timeSchedulesContainer, day, null, 1);
        } else {
            int scheduleNum = 1;
            for (DaySchedule schedule : existingSchedules) {
                addTimeScheduleView(timeSchedulesContainer, day, schedule, scheduleNum);
                scheduleNum++;
            }
            clearScheduleButton.setVisibility(View.VISIBLE);
        }

        // ✅ Add time schedule button
        addTimeScheduleButton.setOnClickListener(v -> {
            int newScheduleNum = timeSchedulesContainer.getChildCount() + 1;
            addTimeScheduleView(timeSchedulesContainer, day, null, newScheduleNum);
            clearScheduleButton.setVisibility(View.VISIBLE);
        });

        // Clear all schedules button
        clearScheduleButton.setOnClickListener(v -> {
            daySchedules.get(day).clear();
            Toast.makeText(this, fullDayName + " schedules cleared", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Save button
        saveDayScheduleButton.setOnClickListener(v -> {
            List<DaySchedule> newSchedules = new ArrayList<>();
            boolean allValid = true;

            // ✅ Collect all schedules from views
            for (int i = 0; i < timeSchedulesContainer.getChildCount(); i++) {
                View scheduleView = timeSchedulesContainer.getChildAt(i);

                TextView selectedDateText = scheduleView.findViewById(R.id.selectedDateText);
                TextView selectedTimeText = scheduleView.findViewById(R.id.selectedTimeText);
                CheckBox notificationCheckbox = scheduleView.findViewById(R.id.notificationCheckbox);
                android.widget.Spinner notificationTimeSpinner = scheduleView.findViewById(R.id.notificationTimeSpinner);

                // Get stored data from view tag
                Object tag = scheduleView.getTag();
                if (tag instanceof DayScheduleViewData) {
                    DayScheduleViewData data = (DayScheduleViewData) tag;

                    if (data.selectedDate == null) {
                        Toast.makeText(this, "Please select date for Schedule " + (i + 1),
                                Toast.LENGTH_SHORT).show();
                        allValid = false;
                        break;
                    }

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

                    DaySchedule schedule = new DaySchedule(
                            day,
                            new Timestamp(data.selectedDate.getTime()),
                            data.selectedTime,
                            notificationCheckbox.isChecked(),
                            reminderMinutes,
                            i + 1
                    );

                    newSchedules.add(schedule);
                }
            }

            if (allValid && !newSchedules.isEmpty()) {
                daySchedules.put(day, newSchedules);

                String message = fullDayName + ": " + newSchedules.size() +
                        " schedule(s) saved";

                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    // ✅ NEW: Helper class to store view data
    private static class DayScheduleViewData {
        Calendar selectedDate;
        String selectedTime;
    }

    // ✅ NEW: Method to add a time schedule view
    private void addTimeScheduleView(LinearLayout container, String day,
                                     DaySchedule existingSchedule, int scheduleNumber) {
        View scheduleView = LayoutInflater.from(this)
                .inflate(R.layout.item_weekly_day_time_schedule, container, false);

        TextView scheduleNumberText = scheduleView.findViewById(R.id.scheduleNumberText);
        LinearLayout datePickerButton = scheduleView.findViewById(R.id.datePickerButton);
        TextView selectedDateText = scheduleView.findViewById(R.id.selectedDateText);
        LinearLayout timePickerButton = scheduleView.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = scheduleView.findViewById(R.id.selectedTimeText);
        CheckBox notificationCheckbox = scheduleView.findViewById(R.id.notificationCheckbox);
        LinearLayout notificationTimeSection = scheduleView.findViewById(R.id.notificationTimeSection);
        android.widget.Spinner notificationTimeSpinner = scheduleView.findViewById(R.id.notificationTimeSpinner);
        ImageView removeScheduleButton = scheduleView.findViewById(R.id.removeScheduleButton);

        // Set schedule number
        scheduleNumberText.setText("Schedule " + scheduleNumber);

        // Setup notification spinner
        String[] notificationTimes = {"5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "1 hour", "2 hours", "1 day"};
        int[] notificationMinutes = {5, 10, 15, 30, 60, 120, 1440};

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, notificationTimes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTimeSpinner.setAdapter(adapter);

        // Create view data holder
        DayScheduleViewData viewData = new DayScheduleViewData();
        scheduleView.setTag(viewData);

        // Load existing data if available
        if (existingSchedule != null) {
            if (existingSchedule.getDate() != null) {
                viewData.selectedDate = Calendar.getInstance();
                viewData.selectedDate.setTime(existingSchedule.getDate().toDate());
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
                selectedDateText.setText(sdf.format(viewData.selectedDate.getTime()));
            }

            if (existingSchedule.getTime() != null && !existingSchedule.getTime().isEmpty()) {
                viewData.selectedTime = existingSchedule.getTime();
                selectedTimeText.setText(viewData.selectedTime);
            }

            notificationCheckbox.setChecked(existingSchedule.isHasReminder());
            notificationTimeSection.setVisibility(existingSchedule.isHasReminder() ?
                    View.VISIBLE : View.GONE);

            for (int i = 0; i < notificationMinutes.length; i++) {
                if (notificationMinutes[i] == existingSchedule.getReminderMinutes()) {
                    notificationTimeSpinner.setSelection(i);
                    break;
                }
            }
        } else {
            // Set default date for new schedule
            if (startDate != null) {
                Calendar defaultDate = getDateForDayInWeek(day, startDate);
                if (defaultDate != null) {
                    viewData.selectedDate = defaultDate;
                    SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
                    selectedDateText.setText(sdf.format(defaultDate.getTime()));
                }
            }
        }

        // Date picker
        datePickerButton.setOnClickListener(v -> {
            Calendar initialDate = viewData.selectedDate != null ?
                    viewData.selectedDate : Calendar.getInstance();

            DatePickerDialog dateDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        viewData.selectedDate = Calendar.getInstance();
                        viewData.selectedDate.set(year, month, dayOfMonth);
                        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
                        selectedDateText.setText(sdf.format(viewData.selectedDate.getTime()));
                    },
                    initialDate.get(Calendar.YEAR),
                    initialDate.get(Calendar.MONTH),
                    initialDate.get(Calendar.DAY_OF_MONTH)
            );
            dateDialog.show();
        });

        // Time picker
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

        // Notification checkbox
        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Remove button
        removeScheduleButton.setOnClickListener(v -> {
            if (container.getChildCount() > 1) {
                container.removeView(scheduleView);
                // Update schedule numbers
                updateScheduleNumbers(container);
            } else {
                Toast.makeText(this, "Keep at least one schedule", Toast.LENGTH_SHORT).show();
            }
        });

        container.addView(scheduleView);
    }

    // ✅ NEW: Update schedule numbers after removal
    private void updateScheduleNumbers(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View scheduleView = container.getChildAt(i);
            TextView scheduleNumberText = scheduleView.findViewById(R.id.scheduleNumberText);
            scheduleNumberText.setText("Schedule " + (i + 1));
        }
    }

    // ✅ ADD helper method to get date for specific day in week
    private Calendar getDateForDayInWeek(String day, Calendar weekStart) {
        int targetDayOfWeek = getDayOfWeekFromName(day);
        if (targetDayOfWeek == -1) return null;

        Calendar result = (Calendar) weekStart.clone();

        // Move to the target day
        while (result.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek) {
            result.add(Calendar.DAY_OF_MONTH, 1);
        }

        return result;
    }

    // ✅ ADD helper method to get full day name
    private String getFullDayName(String shortDay) {
        switch (shortDay) {
            case "Mon": return "Monday";
            case "Tues": return "Tuesday";
            case "Wed": return "Wednesday";
            case "Thur": return "Thursday";
            case "Fri": return "Friday";
            case "Sat": return "Saturday";
            case "Sun": return "Sunday";
            default: return shortDay;
        }
    }

    // ✅ KEEP the existing getDayOfWeekFromName method but ensure it exists:
    private int getDayOfWeekFromName(String dayName) {
        if (dayName == null) return -1;

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

    // SCHEDULE DIALOG METHODS

    private void showScheduleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_weekly_schedule, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Get views from dialog
        LinearLayout weekRangePickerButton = dialogView.findViewById(R.id.weekRangePickerButton);
        TextView weekRangeText = dialogView.findViewById(R.id.weekRangeText);
        ImageView clearWeekButton = dialogView.findViewById(R.id.clearWeekButton);

        LinearLayout timePickerButton = dialogView.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = dialogView.findViewById(R.id.selectedTimeText);

        CheckBox notificationCheckbox = dialogView.findViewById(R.id.notificationCheckbox);
        LinearLayout notificationTimeSection = dialogView.findViewById(R.id.notificationTimeSection);
        android.widget.Spinner notificationTimeSpinner = dialogView.findViewById(R.id.notificationTimeSpinner);

        android.widget.Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        android.widget.Button saveScheduleButton = dialogView.findViewById(R.id.saveScheduleButton);

        // ✅ NEW: Setup notification spinner
        String[] notificationTimes = {"5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "1 hour", "2 hours", "1 day"};
        int[] notificationMinutes = {5, 10, 15, 30, 60, 120, 1440};

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                notificationTimes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTimeSpinner.setAdapter(adapter);

        // Initialize with current week range if already set
        if (startDate != null && endDate != null) {
            updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
        } else {
            setCurrentWeek();
            updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
        }

        // ✅ NEW: Load existing notification settings
        notificationCheckbox.setChecked(hasReminder);
        notificationTimeSection.setVisibility(hasReminder ? View.VISIBLE : View.GONE);

        // Set spinner to saved notification time
        for (int i = 0; i < notificationMinutes.length; i++) {
            if (notificationMinutes[i] == reminderMinutes) {
                notificationTimeSpinner.setSelection(i);
                break;
            }
        }

        // Week range picker
        weekRangePickerButton.setOnClickListener(v ->
                showQuickWeekSelectorDialog(weekRangeText, clearWeekButton)
        );

        // Clear week button
        clearWeekButton.setOnClickListener(v -> {
            setCurrentWeek();
            updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
        });

        // Time picker
        timePickerButton.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            android.app.TimePickerDialog timeDialog = new android.app.TimePickerDialog(
                    this,
                    (view, hourOfDay, minuteOfHour) -> {
                        String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
                        selectedTimeText.setText(time);
                    },
                    hour, minute, false
            );
            timeDialog.show();
        });

        // ✅ NEW: Notification checkbox listener
        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // ✅ UPDATED: Save button with notification handling
        saveScheduleButton.setOnClickListener(v -> {
            String selectedTimeValue = selectedTimeText.getText().toString();

            // ✅ Save notification settings
            hasReminder = notificationCheckbox.isChecked();
            if (hasReminder) {
                int selectedPos = notificationTimeSpinner.getSelectedItemPosition();
                reminderMinutes = notificationMinutes[selectedPos];
            }

            // Save the time
            if (!selectedTimeValue.equals("Select time")) {
                selectedTime = selectedTimeValue;
            } else {
                selectedTime = "";
            }

            // Week range is already saved in startDate and endDate
            String message = "Schedule set for " +
                    new SimpleDateFormat("MMM dd", Locale.getDefault()).format(startDate.getTime()) +
                    " - " + new SimpleDateFormat("MMM dd", Locale.getDefault()).format(endDate.getTime());

            if (!selectedTime.isEmpty()) {
                message += " at " + selectedTime;
            }

            if (hasReminder) {
                message += "\nReminder: " + notificationTimes[notificationTimeSpinner.getSelectedItemPosition()];
            }

            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });

        dialog.show();
    }
    private void showQuickWeekSelectorDialog(TextView weekRangeText, ImageView clearWeekButton) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Week");

        // Calculate week ranges
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
                    updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
                    break;

                case 1: // Next Week
                    cal.add(Calendar.DAY_OF_MONTH, -toMonday + 7);
                    setWeekFromStartDate(cal);
                    updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
                    break;

                case 2: // Week After Next
                    cal.add(Calendar.DAY_OF_MONTH, -toMonday + 14);
                    setWeekFromStartDate(cal);
                    updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
                    break;

                case 3: // Custom
                    showCustomWeekPickerDialog(weekRangeText, clearWeekButton);
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
    }

    private void showCustomWeekPickerDialog(TextView weekRangeText, ImageView clearWeekButton) {
        Calendar initialDate = startDate != null ? startDate : Calendar.getInstance();

        DatePickerDialog startDatePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    startDate = Calendar.getInstance();
                    startDate.set(year, month, dayOfMonth);
                    showEndDatePickerDialog(weekRangeText, clearWeekButton);
                },
                initialDate.get(Calendar.YEAR),
                initialDate.get(Calendar.MONTH),
                initialDate.get(Calendar.DAY_OF_MONTH)
        );

        startDatePicker.setTitle("Select START date");
        startDatePicker.show();
    }

    private void showEndDatePickerDialog(TextView weekRangeText, ImageView clearWeekButton) {
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

                    updateWeekRangeDisplayInDialog(weekRangeText, clearWeekButton);
                },
                defaultEnd.get(Calendar.YEAR),
                defaultEnd.get(Calendar.MONTH),
                defaultEnd.get(Calendar.DAY_OF_MONTH)
        );

        endDatePicker.getDatePicker().setMinDate(startDate.getTimeInMillis());
        endDatePicker.setTitle("Select END date");
        endDatePicker.show();
    }

    private void updateWeekRangeDisplayInDialog(TextView weekRangeText, ImageView clearWeekButton) {
        if (startDate != null && endDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());

            String startStr = sdf.format(startDate.getTime());
            String endStr = sdf.format(endDate.getTime());
            String year = yearFormat.format(startDate.getTime());

            weekRangeText.setText(startStr + " - " + endStr + ", " + year);
            clearWeekButton.setVisibility(View.VISIBLE);
        } else {
            weekRangeText.setText("Select week");
            clearWeekButton.setVisibility(View.GONE);
        }
    }

    private void setupTasksRealtimeListener(String userId) {
        if (tasksListener != null) {
            tasksListener.remove();
        }

        tasksListener = db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(planId)
                .collection("tasks")
                .orderBy("position")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Tasks listener error", e);
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) return;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String taskId = doc.getId();
                        String day = doc.getString("day");
                        Boolean isCompleted = doc.getBoolean("isCompleted");

                        List<WeeklyTask> tasks = dayTasks.get(day);
                        if (tasks != null) {
                            for (WeeklyTask task : tasks) {
                                if (task.getId().equals(taskId)) {
                                    boolean newStatus = isCompleted != null && isCompleted;
                                    if (task.isCompleted() != newStatus) {
                                        task.setCompleted(newStatus);
                                        WeeklyTaskAdapter adapter = dayAdapters.get(day);
                                        if (adapter != null) {
                                            adapter.notifyDataSetChanged();
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksListener != null) {
            tasksListener.remove();
        }

    }
    // ✅ ADD this method in your WeeklyActivity class
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        // ✅ IMPROVED: Better drag detection
        boolean isDragging = false;

        // Check if we're in a drag motion
        if (ev.getAction() == android.view.MotionEvent.ACTION_MOVE) {
            // Check if any RecyclerView is currently being dragged
            for (String day : days) {
                RecyclerView recyclerView = dayContainers.get(day);
                if (recyclerView != null && recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
                    isDragging = true;
                    break;
                }
            }
        }

        if (isDragging) {
            // Prevent NestedScrollView from intercepting
            androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView);
            if (scrollView != null) {
                scrollView.requestDisallowInterceptTouchEvent(true);
            }
        }

        return super.dispatchTouchEvent(ev);
    }

}