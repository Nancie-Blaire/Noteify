package com.example.testtasksync;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.animation.ObjectAnimator;
import android.view.MotionEvent;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

// FEATURES
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.graphics.Color;



public class TodoActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_CODE = 100;
    private static final String TAG = "TodoActivity";
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String listId;
    private boolean isNewList = false;

    private RecyclerView tasksRecyclerView;
    private TodoTaskAdapter taskAdapter;
    private ItemTouchHelper itemTouchHelper;

    private EditText todoTitle;
    private ImageView saveButton, backButton, dueDateButton;
    private LinearLayout tasksContainer, addTaskButton;
    private LinearLayout dueDateDisplay;
    private TextView dueDateText;
    private ImageView clearDateButton;

    private List<TodoTask> taskList = new ArrayList<>();
    private Calendar dueDate = null;
    private String dueTime = null; // ✅ NEW: Store time separately
    private boolean hasReminder = false; // ✅ NEW: Store reminder flag
    private int reminderMinutes = 60; // ✅ NEW: Default 60 minutes before

    // Auto-save handler
    private Handler autoSaveHandler = new Handler();
    private Runnable autoSaveRunnable;
    private ListenerRegistration tasksListener;
    private View draggedView = null;
    private int draggedPosition = -1;
    private float initialY = 0;
    private float lastY = 0;


    private LinearLayout mainLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo);
        //TOP BAR COLOR THEME
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#8daaa6")); // Same as your top bar
        }

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // ✅ FIX 1: Get listId FIRST before any loading
        listId = getIntent().getStringExtra("listId");
        isNewList = (listId == null || listId.isEmpty());

        Log.d(TAG, "🎯 TodoActivity opened:");
        Log.d(TAG, "   listId: " + listId);
        Log.d(TAG, "   isNewList: " + isNewList);

        // Initialize views
        todoTitle = findViewById(R.id.todoTitle);
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        dueDateButton = findViewById(R.id.dueDateButton);
        tasksRecyclerView = findViewById(R.id.tasksContainer);
        addTaskButton = findViewById(R.id.addTaskButton);
        dueDateDisplay = findViewById(R.id.dueDateDisplay);
        dueDateText = findViewById(R.id.dueDateText);
        clearDateButton = findViewById(R.id.clearDateButton);


        // Setup RecyclerView
        setupRecyclerView();

        // Set up buttons
        saveButton.setOnClickListener(v -> saveTodoList());
        backButton.setOnClickListener(v -> saveTodoList());
        addTaskButton.setOnClickListener(v -> addTask());
        dueDateButton.setOnClickListener(v -> showDueDateDialog());
        clearDateButton.setOnClickListener(v -> clearDueDate());

        // Load list or create new
        if (isNewList) {
            addTask();
            addTask();
        } else {
            loadTodoList();
        }

        NotificationHelper.createNotificationChannel(this);
        requestNotificationPermission();
    }

    private void setupRecyclerView() {
        taskAdapter = new TodoTaskAdapter(taskList, new TodoTaskAdapter.TaskActionListener() {
            @Override
            public void onTaskTextChanged(TodoTask task, String newText) {
                if (autoSaveRunnable != null) {
                    autoSaveHandler.removeCallbacks(autoSaveRunnable);
                }
                autoSaveRunnable = () -> {
                    if (!isNewList && !task.getId().isEmpty()) {
                        saveTodoListWithoutFinish(); // ✅ CHANGED: Use new method
                    }
                };
                autoSaveHandler.postDelayed(autoSaveRunnable, 1000);
            }

            @Override
            public void onTaskCompletionChanged(TodoTask task, boolean isCompleted) {
                updateTaskCompletionInFirebase(task);
            }

            @Override
            public void onScheduleClicked(TodoTask task, int position) {
                View taskView = tasksRecyclerView.getLayoutManager()
                        .findViewByPosition(position);
                if (taskView != null) {
                    LinearLayout scheduleDisplay = taskView.findViewById(R.id.scheduleDisplay);
                    TextView scheduleText = taskView.findViewById(R.id.scheduleText);
                    ImageView notificationIcon = taskView.findViewById(R.id.notificationIcon);
                    showTaskScheduleDialog(task, scheduleDisplay, scheduleText, notificationIcon);
                }
            }

            @Override
            public void onClearScheduleClicked(TodoTask task) {
                task.setScheduleDate(null);
                task.setScheduleTime(null);
                task.setHasNotification(false);
                taskAdapter.notifyDataSetChanged();
                if (!isNewList) {
                    saveTodoListWithoutFinish(); // ✅ CHANGED: Use new method
                }
            }

            @Override
            public void onDeleteClicked(TodoTask task, int position) {
                if (taskList.size() > 1) {
                    taskList.remove(position);
                    taskAdapter.notifyItemRemoved(position);
                    updateTaskPositions();
                    if (!isNewList) {
                        saveTodoListWithoutFinish(); // ✅ CHANGED: Use new method
                    }
                } else {
                    Toast.makeText(TodoActivity.this, "Keep at least one task",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onTaskMoved(int fromPosition, int toPosition) {
                if (!isNewList) {
                    saveTodoListWithoutFinish(); // ✅ CHANGED: Use new method instead of saveTodoList()
                }
            }
        });

        tasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tasksRecyclerView.setAdapter(taskAdapter);

        // Setup drag and drop
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                taskAdapter.moveItem(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe action
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(tasksRecyclerView);
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


    // ========================================
    // ✅ NEW: SHOW DUE DATE DIALOG (same style as task schedule)
    // ========================================
    private void showDueDateDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_schedule);
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        // Get dialog views
        LinearLayout datePickerButton = dialog.findViewById(R.id.datePickerButton);
        TextView selectedDateText = dialog.findViewById(R.id.selectedDateText);
        LinearLayout timePickerButton = dialog.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = dialog.findViewById(R.id.selectedTimeText);
        CheckBox notificationCheckbox = dialog.findViewById(R.id.notificationCheckbox);
        LinearLayout notificationTimeSection = dialog.findViewById(R.id.notificationTimeSection);
        Spinner notificationTimeSpinner = dialog.findViewById(R.id.notificationTimeSpinner); // ✅ NOW USED
        Button cancelButton = dialog.findViewById(R.id.cancelButton);
        Button saveScheduleButton = dialog.findViewById(R.id.saveScheduleButton);

        // ✅ NEW: Setup notification spinner
        String[] notificationTimes = {"5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "1 hour", "2 hours", "1 day"};
        int[] notificationMinutes = {5, 10, 15, 30, 60, 120, 1440};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, notificationTimes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTimeSpinner.setAdapter(adapter);

        // Load existing values
        Calendar listDueDate = dueDate != null ? dueDate : Calendar.getInstance();

        if (dueDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            selectedDateText.setText(sdf.format(dueDate.getTime()));
        }

        if (dueTime != null && !dueTime.isEmpty()) {
            selectedTimeText.setText(dueTime);
        }

        // ✅ NEW: Load notification settings
        notificationCheckbox.setChecked(hasReminder);
        notificationTimeSection.setVisibility(hasReminder ? View.VISIBLE : View.GONE);

        // Set spinner to saved notification time
        for (int i = 0; i < notificationMinutes.length; i++) {
            if (notificationMinutes[i] == reminderMinutes) {
                notificationTimeSpinner.setSelection(i);
                break;
            }
        }

        // Date picker
        datePickerButton.setOnClickListener(v -> {
            DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        listDueDate.set(year, month, dayOfMonth);
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        selectedDateText.setText(sdf.format(listDueDate.getTime()));
                    },
                    listDueDate.get(Calendar.YEAR),
                    listDueDate.get(Calendar.MONTH),
                    listDueDate.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.show();
        });

        // Time picker
        timePickerButton.setOnClickListener(v -> {
            int hour = 9;
            int minute = 0;

            if (dueTime != null && !dueTime.isEmpty()) {
                try {
                    String[] timeParts = dueTime.split(":");
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
                    hour, minute, false  // ✅ Changed to false for 12-hour format
            );
            timePicker.show();
        });

        // ✅ NEW: Notification checkbox listener
        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Save button
        // Save button
        saveScheduleButton.setOnClickListener(v -> {
            if (selectedDateText.getText().equals("Select date")) {
                Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show();
                return;
            }

            dueDate = listDueDate;

            if (!selectedTimeText.getText().equals("Select time")) {
                dueTime = selectedTimeText.getText().toString();
            } else {
                dueTime = null;
            }

            // ✅ Save notification settings
            hasReminder = notificationCheckbox.isChecked();
            if (hasReminder) {
                int selectedPos = notificationTimeSpinner.getSelectedItemPosition();
                reminderMinutes = notificationMinutes[selectedPos];
            }

            updateDueDateDisplay();

            // ✅ CHANGED: Only auto-save if not new list, but DON'T finish!
            if (!isNewList) {
                saveTodoListWithoutFinish(); // ✅ New method that doesn't call finish()
            }

            dialog.dismiss();
        });
        dialog.show();
    }

    // ========================================
// SAVE TODO LIST WITHOUT FINISHING ACTIVITY
// ========================================
    private void saveTodoListWithoutFinish() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String title = todoTitle.getText().toString().trim();
        if (title.isEmpty()) {
            title = "To-Do List";
        }

        int totalTasks = 0;
        int completedTasks = 0;
        for (TodoTask task : taskList) {
            if (!task.getTaskText().trim().isEmpty()) {
                totalTasks++;
                if (task.isCompleted()) {
                    completedTasks++;
                }
            }
        }

        final String finalTitle = title;
        final int finalTotalTasks = totalTasks;
        final int finalCompletedTasks = completedTasks;

        Map<String, Object> listData = new HashMap<>();
        listData.put("title", finalTitle);
        listData.put("timestamp", new Timestamp(new Date()));
        listData.put("taskCount", finalTotalTasks);
        listData.put("completedCount", finalCompletedTasks);

        if (dueDate != null) {
            listData.put("dueDate", new Timestamp(dueDate.getTime()));
        } else {
            listData.put("dueDate", null);
        }
        listData.put("dueTime", dueTime);

        // ✅ FIX: Also create/update schedule immediately
        String description = finalTotalTasks + " task" + (finalTotalTasks != 1 ? "s" : "");
        if (dueDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            description += " (" + sdf.format(dueDate.getTime()) + ")";
        }
        description += " • " + finalCompletedTasks + " completed";

        Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("title", finalTitle);
        scheduleData.put("description", description);
        scheduleData.put("category", "todo");
        scheduleData.put("sourceId", listId);
        scheduleData.put("isCompleted", finalCompletedTasks == finalTotalTasks && finalTotalTasks > 0);
        scheduleData.put("createdAt", new Timestamp(new Date()));
        scheduleData.put("createdAtServer", com.google.firebase.firestore.FieldValue.serverTimestamp());

        if (dueDate != null) {
            scheduleData.put("date", new Timestamp(dueDate.getTime()));
            scheduleData.put("hasReminder", hasReminder);
            scheduleData.put("reminderMinutes", reminderMinutes);
        } else {
            scheduleData.put("date", null);
            scheduleData.put("hasReminder", false);
        }

        scheduleData.put("time", dueTime != null ? dueTime : "");
        scheduleData.put("taskCount", finalTotalTasks);
        scheduleData.put("completedCount", finalCompletedTasks);

        // ✅ Update schedule immediately
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .document(listId)
                .set(scheduleData, com.google.firebase.firestore.SetOptions.merge());

        // Update list data
        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .document(listId)
                .set(listData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Todo list saved (drag reorder)");
                    saveTasksPositionsOnly(user.getUid(), listId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save todo list", e);
                });
    }

    private void saveTasksPositionsOnly(String userId, String listId) {
        for (TodoTask task : taskList) {
            if (!task.getId().isEmpty() && !task.getTaskText().trim().isEmpty()) {
                db.collection("users")
                        .document(userId)
                        .collection("todoLists")
                        .document(listId)
                        .collection("tasks")
                        .document(task.getId())
                        .update("position", task.getPosition())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Position updated for: " + task.getTaskText());
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to update position", e);
                        });
            }
        }
    }

    // ========================================
// SAVE TASKS WITHOUT FINISHING
// ========================================
    private void saveTasksWithoutFinish(String userId, String listId, String listTitle, int totalTasks,
                                        int completedTasks, Map<String, Object> scheduleData) {

        // ✅ FIX: Create schedule FIRST
        scheduleData.put("sourceId", listId);
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(listId)
                .set(scheduleData, com.google.firebase.firestore.SetOptions.merge());

        db.collection("users")
                .document(userId)
                .collection("todoLists")
                .document(listId)
                .collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().delete();
                    }

                    int tasksToSave = 0;
                    for (TodoTask task : taskList) {
                        if (!task.getTaskText().trim().isEmpty()) {
                            tasksToSave++;
                        }
                    }

                    if (tasksToSave == 0) {
                        // ✅ Just schedule notification, schedule already created
                        scheduleListNotificationIfNeeded(userId, listId, listTitle);
                        return;
                    }

                    final int[] savedCount = {0};
                    for (TodoTask task : taskList) {
                        if (!task.getTaskText().trim().isEmpty()) {
                            Map<String, Object> taskData = new HashMap<>();
                            taskData.put("taskText", task.getTaskText());
                            taskData.put("isCompleted", task.isCompleted());
                            taskData.put("position", task.getPosition());

                            if (task.getScheduleDate() != null) {
                                taskData.put("scheduleDate", new Timestamp(task.getScheduleDate()));
                            }
                            taskData.put("scheduleTime", task.getScheduleTime());
                            taskData.put("hasNotification", task.hasNotification());
                            taskData.put("notificationMinutes", task.getNotificationMinutes());

                            int finalTasksToSave = tasksToSave;
                            db.collection("users")
                                    .document(userId)
                                    .collection("todoLists")
                                    .document(listId)
                                    .collection("tasks")
                                    .add(taskData)
                                    .addOnSuccessListener(documentReference -> {
                                        savedCount[0]++;
                                        if (savedCount[0] == finalTasksToSave) {
                                            scheduleListNotificationIfNeeded(userId, listId, listTitle);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to save task", e);
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete old tasks", e);
                });
    }

    // ========================================
// CREATE OR UPDATE SCHEDULE WITHOUT FINISHING
// ========================================

    private void createOrUpdateScheduleWithoutFinish(String userId, String listId, Map<String, Object> scheduleData) {
        scheduleData.put("sourceId", listId);

        // ✅ Get the list title from scheduleData
        String listTitle = (String) scheduleData.get("title");

        db.collection("users")
                .document(userId)
                .collection("schedules")
                .whereEqualTo("sourceId", listId)
                .whereEqualTo("category", "todo")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String scheduleId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .document(scheduleId)
                                .update(scheduleData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Schedule updated - staying in activity");

                                    // ✅ Schedule list-level notification
                                    scheduleListNotificationIfNeeded(userId, listId, listTitle);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update schedule", e);
                                });
                    } else {
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .add(scheduleData)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d(TAG, "Schedule created - staying in activity");

                                    // ✅ Schedule list-level notification
                                    scheduleListNotificationIfNeeded(userId, listId, listTitle);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create schedule", e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check existing schedule", e);
                });
    }
    // ✅ ADD this helper method to your TodoActivity class (add anywhere in the class)

    private String formatTimeForDisplay(String time24) {
        if (time24 == null || time24.isEmpty()) {
            return "";
        }

        // Get user's time format preference
        String timeFormat = Settings.getTimeFormat(this);

        try {
            // Parse the 24-hour time
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            if ("civilian".equals(timeFormat)) {
                // Convert to 12-hour format with AM/PM
                String period = (hour >= 12) ? "PM" : "AM";
                int hour12 = (hour == 0) ? 12 : (hour > 12) ? hour - 12 : hour;
                return String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, period);
            } else {
                // Keep 24-hour format
                return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time", e);
            return time24;
        }
    }

    // ✅ REPLACE your existing updateDueDateDisplay() method with this:
    private void updateDueDateDisplay() {
        if (dueDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            String displayText = "Due: " + sdf.format(dueDate.getTime());

            if (dueTime != null && !dueTime.isEmpty()) {
                // ✅ Format time according to user preference
                String formattedTime = formatTimeForDisplay(dueTime);
                displayText += ", " + formattedTime;
            }

            dueDateText.setText(displayText);
            dueDateDisplay.setVisibility(View.VISIBLE);
        } else {
            dueDateDisplay.setVisibility(View.GONE);
        }
    }

    // ========================================
    // CLEAR DUE DATE
    // ========================================
    private void clearDueDate() {
        dueDate = null;
        dueTime = null;
        hasReminder = false;
        updateDueDateDisplay();

        if (!isNewList) {
            saveTodoListWithoutFinish(); // ✅ CHANGED: Use new method
        }
    }

    private void loadTodoList() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String userId = user.getUid();

        // ✅ FIRST: Load schedule data (has the date/time info)
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .whereEqualTo("sourceId", listId)
                .whereEqualTo("category", "todo")
                .get()
                .addOnSuccessListener(scheduleSnapshots -> {
                    if (!scheduleSnapshots.isEmpty()) {
                        // ✅ Load date/time from schedule document
                        DocumentSnapshot scheduleDoc = scheduleSnapshots.getDocuments().get(0);

                        Timestamp dateTimestamp = scheduleDoc.getTimestamp("date");
                        if (dateTimestamp != null) {
                            dueDate = Calendar.getInstance();
                            dueDate.setTime(dateTimestamp.toDate());
                        }

                        dueTime = scheduleDoc.getString("time");

                        // ✅ Load reminder settings from schedule
                        Boolean hasReminderFromSchedule = scheduleDoc.getBoolean("hasReminder");
                        if (hasReminderFromSchedule != null) {
                            hasReminder = hasReminderFromSchedule;
                        }

                        Long reminderMinutesFromSchedule = scheduleDoc.getLong("reminderMinutes");
                        if (reminderMinutesFromSchedule != null) {
                            reminderMinutes = reminderMinutesFromSchedule.intValue();
                        }

                        updateDueDateDisplay();
                    }

                    // ✅ THEN: Load list details
                    loadTodoListDetails(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load schedule", e);
                    // Still try to load list details even if schedule fails
                    loadTodoListDetails(userId);
                });
    }
    private void loadTodoListDetails(String userId) {
        db.collection("users")
                .document(userId)
                .collection("todoLists")
                .document(listId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        if (title != null && !title.isEmpty()) {
                            todoTitle.setText(title);
                        }

                        // ✅ Only use these if schedule didn't have them
                        if (dueDate == null) {
                            Timestamp dueDateTimestamp = documentSnapshot.getTimestamp("dueDate");
                            if (dueDateTimestamp != null) {
                                dueDate = Calendar.getInstance();
                                dueDate.setTime(dueDateTimestamp.toDate());
                            }
                        }

                        if (dueTime == null) {
                            dueTime = documentSnapshot.getString("dueTime");
                        }

                        updateDueDateDisplay();
                    }

                    // ✅ Load tasks after list details
                    loadTodoTasks(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load todo list", e);
                    Toast.makeText(this, "Failed to load list", Toast.LENGTH_SHORT).show();
                });
    }

    // ✅ NEW: Separate method to load tasks
    private void loadTodoTasks(String userId) {
        db.collection("users")
                .document(userId)
                .collection("todoLists")
                .document(listId)
                .collection("tasks")
                .orderBy("position")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    taskList.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        TodoTask task = new TodoTask();
                        task.setId(doc.getId());
                        task.setTaskText(doc.getString("taskText"));
                        task.setCompleted(Boolean.TRUE.equals(doc.getBoolean("isCompleted")));
                        task.setPosition(doc.getLong("position").intValue());

                        Timestamp scheduleTimestamp = doc.getTimestamp("scheduleDate");
                        if (scheduleTimestamp != null) {
                            task.setScheduleDate(scheduleTimestamp.toDate());
                        }
                        task.setScheduleTime(doc.getString("scheduleTime"));
                        task.setHasNotification(Boolean.TRUE.equals(doc.getBoolean("hasNotification")));
                        Long notifMinutes = doc.getLong("notificationMinutes");
                        if (notifMinutes != null) {
                            task.setNotificationMinutes(notifMinutes.intValue());
                        }

                        taskList.add(task);
                    }

                    if (taskList.isEmpty()) {
                        addTask();
                        addTask();
                    }

                    taskAdapter.notifyDataSetChanged();
                    setupTasksRealtimeListener(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load tasks", e);
                    addTask();
                    addTask();
                });
    }
    // ========================================
    // ADD NEW TASK
    // ========================================
    private void addTask() {
        TodoTask task = new TodoTask();
        task.setId("");
        task.setTaskText("");
        task.setCompleted(false);
        task.setPosition(taskList.size());

        taskList.add(task);
        taskAdapter.notifyItemInserted(taskList.size() - 1);
    }

    // ========================================
    // ADD TASK VIEW (UI + LISTENERS)
    // ========================================
    // Replace your addTaskView() method with this updated version:
    private void addTaskView(TodoTask task) {
        View taskView = LayoutInflater.from(this).inflate(R.layout.item_todo, tasksContainer, false);

        CheckBox checkbox = taskView.findViewById(R.id.todoCheckbox);
        EditText taskText = taskView.findViewById(R.id.todoEditText);
        ImageView scheduleButton = taskView.findViewById(R.id.scheduleButton);
        ImageView deleteButton = taskView.findViewById(R.id.deleteTodoButton);
        LinearLayout scheduleDisplay = taskView.findViewById(R.id.scheduleDisplay);
        TextView scheduleText = taskView.findViewById(R.id.scheduleText);
        ImageView notificationIcon = taskView.findViewById(R.id.notificationIcon);
        ImageView clearScheduleButton = taskView.findViewById(R.id.clearScheduleButton);

        // Set task text
        if (task.getTaskText() != null && !task.getTaskText().isEmpty()) {
            taskText.setText(task.getTaskText());
        }

        // Set checkbox state
        checkbox.setChecked(task.isCompleted());

        // Apply strikethrough based on completion status
        if (task.isCompleted()) {
            taskText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            taskText.setTextColor(getResources().getColor(android.R.color.black));
            taskText.setPaintFlags(taskText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }

        updateTaskScheduleDisplay(task, scheduleDisplay, scheduleText, notificationIcon);

        // Checkbox listener
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            task.setCompleted(isChecked);
            if (isChecked) {
                taskText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                taskText.setTextColor(getResources().getColor(android.R.color.black));
                taskText.setPaintFlags(taskText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            }
            updateTaskCompletionInFirebase(task);
        });

        // Text change listener + auto-save
        taskText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                task.setTaskText(s.toString());

                if (autoSaveRunnable != null) {
                    autoSaveHandler.removeCallbacks(autoSaveRunnable);
                }

                autoSaveRunnable = () -> {
                    if (!isNewList && !task.getId().isEmpty()) {
                        saveTodoList();
                    }
                };

                autoSaveHandler.postDelayed(autoSaveRunnable, 1000);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Schedule button
        scheduleButton.setOnClickListener(v ->
                showTaskScheduleDialog(task, scheduleDisplay, scheduleText, notificationIcon)
        );

        // Clear schedule button
        clearScheduleButton.setOnClickListener(v -> {
            task.setScheduleDate(null);
            task.setScheduleTime(null);
            task.setHasNotification(false);
            updateTaskScheduleDisplay(task, scheduleDisplay, scheduleText, notificationIcon);
            if (!isNewList) {
                saveTodoList();
            }
        });

        // Focus listener
        taskText.setOnFocusChangeListener((v, hasFocus) -> {
            deleteButton.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
        });

        // Delete button
        deleteButton.setOnClickListener(v -> {
            if (taskList.size() > 1) {
                taskList.remove(task);
                tasksContainer.removeView(taskView);
                updateTaskPositions();
            } else {
                Toast.makeText(this, "Keep at least one task", Toast.LENGTH_SHORT).show();
            }
        });

        // ✅ IMPROVED: Add drag and drop functionality
        taskView.setOnLongClickListener(v -> {
            // Don't allow dragging while editing
            if (taskText.isFocused()) {
                return false;
            }

            startDragging(v, task);
            return true;
        });

        taskView.setOnTouchListener(new View.OnTouchListener() {
            private boolean isDragging = false;
            private float touchStartY = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (draggedView != v) {
                    return false;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        if (draggedView != null) {
                            float rawY = event.getRawY();

                            // Move the dragged view
                            v.setY(rawY - v.getHeight() / 2 - getStatusBarHeight());

                            // Check for reordering
                            int currentIndex = tasksContainer.indexOfChild(v);
                            int targetIndex = findTargetPosition(rawY);

                            if (targetIndex != -1 && targetIndex != currentIndex) {
                                swapTaskPositions(currentIndex, targetIndex);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (draggedView != null) {
                            stopDragging();
                        }
                        return true;
                }
                return false;
            }
        });

        tasksContainer.addView(taskView);
    }

    private void swapTaskPositions(int fromPos, int toPos) {
        if (fromPos == toPos) return;

        // Update data model
        TodoTask movedTask = taskList.remove(fromPos);
        taskList.add(toPos, movedTask);

        // Update view positions
        View movedView = tasksContainer.getChildAt(fromPos);
        tasksContainer.removeViewAt(fromPos);
        tasksContainer.addView(movedView, toPos);
    }

    // ✅ NEW: Get status bar height
    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }
    private void startDragging(View view, TodoTask task) {
        draggedView = view;
        draggedPosition = tasksContainer.indexOfChild(view);

        // Bring to front
        view.bringToFront();

        // Visual feedback
        view.setAlpha(0.7f);
        view.setScaleX(1.05f);
        view.setScaleY(1.05f);
        view.setElevation(16f);

        // Make container allow overlapping
        tasksContainer.setClipChildren(false);
        tasksContainer.setClipToPadding(false);
    }

    // ✅ NEW: Stop dragging
    private void stopDragging() {
        if (draggedView == null) return;

        View view = draggedView;
        draggedView = null;

        // Reset visual
        view.setAlpha(1.0f);
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.setElevation(0f);

        // Animate back to position
        int finalIndex = tasksContainer.indexOfChild(view);
        view.animate()
                .y(0) // Reset Y since LinearLayout will handle positioning
                .setDuration(200)
                .withEndAction(() -> {
                    // Force layout refresh
                    tasksContainer.requestLayout();

                    // Save new order
                    updateTaskPositions();
                    if (!isNewList) {
                        saveTodoList();
                    }
                })
                .start();

        // Restore normal clipping
        tasksContainer.setClipChildren(true);
        tasksContainer.setClipToPadding(true);
    }

    private int findTargetPosition(float rawY) {
        int[] containerLocation = new int[2];
        tasksContainer.getLocationOnScreen(containerLocation);
        float relativeY = rawY - containerLocation[1];

        float accumulatedHeight = 0;

        for (int i = 0; i < tasksContainer.getChildCount(); i++) {
            View child = tasksContainer.getChildAt(i);

            if (child == draggedView) {
                continue; // Skip the dragged view
            }

            float childMidpoint = accumulatedHeight + (child.getHeight() / 2f);

            if (relativeY < childMidpoint) {
                return i;
            }

            accumulatedHeight += child.getHeight();
        }

        return tasksContainer.getChildCount() - 1;
    }

    // ✅ NEW: Calculate expected Y position for a task at given index
    private float getExpectedYPosition(int index) {
        if (index == 0) {
            return 0;
        }

        float totalHeight = 0;
        for (int i = 0; i < index; i++) {
            View child = tasksContainer.getChildAt(i);
            if (child != null) {
                totalHeight += child.getHeight();
            }
        }
        return totalHeight;
    }

    // ✅ NEW: Get target position based on Y coordinate
    private int getTargetPosition(float rawY) {
        int[] location = new int[2];
        tasksContainer.getLocationOnScreen(location);
        float relativeY = rawY - location[1];

        float cumulativeHeight = 0;
        for (int i = 0; i < tasksContainer.getChildCount(); i++) {
            View child = tasksContainer.getChildAt(i);
            cumulativeHeight += child.getHeight();

            if (relativeY < cumulativeHeight) {
                return i;
            }
        }

        return tasksContainer.getChildCount() - 1;
    }

    // ✅ NEW: Reorder tasks in the list and UI
    private void reorderTasks(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) return;

        // Update data model
        TodoTask movedTask = taskList.remove(fromPosition);
        taskList.add(toPosition, movedTask);

        // Update UI
        View movedView = tasksContainer.getChildAt(fromPosition);
        tasksContainer.removeViewAt(fromPosition);
        tasksContainer.addView(movedView, toPosition);

        // Update positions
        updateTaskPositions();
    }
    private void updateTaskCompletionInFirebase(TodoTask task) {
        if (isNewList || task.getId().isEmpty()) {
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Fixed: Find task by ID instead of text/position
        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .document(listId)
                .collection("tasks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String docTaskText = doc.getString("taskText");
                        Long docPosition = doc.getLong("position");

                        // Match by text AND position
                        if (task.getTaskText().equals(docTaskText) &&
                                task.getPosition() == (docPosition != null ? docPosition.intValue() : -1)) {

                            String taskDocId = doc.getId();
                            task.setId(taskDocId); // ✅ Update task ID

                            // ETO ANG LUGAR KUNG SAAN KA MAG-U-UPDATE SA FIREBASE
                            db.collection("users")
                                    .document(user.getUid())
                                    .collection("todoLists")
                                    .document(listId)
                                    .collection("tasks")
                                    .document(taskDocId)
                                    .update("isCompleted", task.isCompleted())
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "✅ Task completion status updated: " + task.isCompleted());

                                        // ✅ DITO IDADAGDAG ANG NEW CODE: Cancel notification if task is completed
                                        if (task.isCompleted() && task.hasNotification()) {
                                            // Tandaan: Palitan ang 'this' ng tamang Context (e.g., YourActivity.this)
                                            NotificationHelper.cancelNotification(this, task.getId());
                                            Log.d(TAG, "📵 Cancelled notification for completed task");
                                        }

                                        // ⚠️ TANDAAN: Kung ang NotificationHelper.cancelNotification ay nangangailangan ng 'taskDocId'
                                        // bilang parameter imbes na 'task.getId()', gamitin ang 'taskDocId'.
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to update task completion", e);
                                    });
                            break;
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to find task", e);
                });
    }
    // ========================================
    // UPDATE TASK SCHEDULE DISPLAY
    // ========================================
    private void updateTaskScheduleDisplay(TodoTask task, LinearLayout scheduleDisplay,
                                           TextView scheduleText, ImageView notificationIcon) {
        if (task.getScheduleDate() != null) {
            scheduleDisplay.setVisibility(View.VISIBLE);

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            String displayText = dateFormat.format(task.getScheduleDate());

            if (task.getScheduleTime() != null && !task.getScheduleTime().isEmpty()) {
                // ✅ Format time according to user preference
                String formattedTime = formatTimeForDisplay(task.getScheduleTime());
                displayText += ", " + formattedTime;
            }

            scheduleText.setText(displayText);
            notificationIcon.setVisibility(task.hasNotification() ? View.VISIBLE : View.GONE);
        } else {
            scheduleDisplay.setVisibility(View.GONE);
        }
    }

    // ========================================
    // SHOW TASK SCHEDULE DIALOG
    // ========================================
    private void showTaskScheduleDialog(TodoTask task, LinearLayout scheduleDisplay,
                                        TextView scheduleText, ImageView notificationIcon) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_task_schedule);
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        LinearLayout datePickerButton = dialog.findViewById(R.id.datePickerButton);
        TextView selectedDateText = dialog.findViewById(R.id.selectedDateText);
        LinearLayout timePickerButton = dialog.findViewById(R.id.timePickerButton);
        TextView selectedTimeText = dialog.findViewById(R.id.selectedTimeText);
        CheckBox notificationCheckbox = dialog.findViewById(R.id.notificationCheckbox);
        LinearLayout notificationTimeSection = dialog.findViewById(R.id.notificationTimeSection);
        Spinner notificationTimeSpinner = dialog.findViewById(R.id.notificationTimeSpinner);
        Button cancelButton = dialog.findViewById(R.id.cancelButton);
        Button saveScheduleButton = dialog.findViewById(R.id.saveScheduleButton);

        String[] notificationTimes = {"5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "1 hour", "2 hours", "1 day"};
        int[] notificationMinutes = {5, 10, 15, 30, 60, 120, 1440};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, notificationTimes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTimeSpinner.setAdapter(adapter);

        Calendar taskSchedule = Calendar.getInstance();
        if (task.getScheduleDate() != null) {
            taskSchedule.setTime(task.getScheduleDate());
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            selectedDateText.setText(sdf.format(task.getScheduleDate()));
        }

        if (task.getScheduleTime() != null && !task.getScheduleTime().isEmpty()) {
            selectedTimeText.setText(task.getScheduleTime());
        }

        notificationCheckbox.setChecked(task.hasNotification());
        notificationTimeSection.setVisibility(task.hasNotification() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < notificationMinutes.length; i++) {
            if (notificationMinutes[i] == task.getNotificationMinutes()) {
                notificationTimeSpinner.setSelection(i);
                break;
            }
        }

        datePickerButton.setOnClickListener(v -> {
            DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        taskSchedule.set(year, month, dayOfMonth);
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        selectedDateText.setText(sdf.format(taskSchedule.getTime()));
                    },
                    taskSchedule.get(Calendar.YEAR),
                    taskSchedule.get(Calendar.MONTH),
                    taskSchedule.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.show();
        });

        timePickerButton.setOnClickListener(v -> {
            int hour = 9;
            int minute = 0;

            if (task.getScheduleTime() != null && !task.getScheduleTime().isEmpty()) {
                String[] timeParts = task.getScheduleTime().split(":");
                hour = Integer.parseInt(timeParts[0]);
                minute = Integer.parseInt(timeParts[1]);
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

        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveScheduleButton.setOnClickListener(v -> {
            // Tiyakin na may napiling petsa
            if (selectedDateText.getText().equals("Select date")) {
                Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. I-UPDATE ANG TASK OBJECT SA MGA BAGONG VALUE
            task.setScheduleDate(taskSchedule.getTime());

            if (!selectedTimeText.getText().equals("Select time")) {
                task.setScheduleTime(selectedTimeText.getText().toString());
            }

            task.setHasNotification(notificationCheckbox.isChecked());
            if (notificationCheckbox.isChecked()) {
                int selectedPos = notificationTimeSpinner.getSelectedItemPosition();
                task.setNotificationMinutes(notificationMinutes[selectedPos]);
            }

            // 2. I-UPDATE ANG DISPLAY SA UI
            updateTaskScheduleDisplay(task, scheduleDisplay, scheduleText, notificationIcon);

            // 3. I-SAVE SA FIREBASE (Kung hindi ito bagong listahan)
            if (!isNewList) {
                saveTodoList();
            }


            // 4. I-dismiss ang dialog
            dialog.dismiss();
        });

        dialog.show();
    }
    // ========================================
    // UPDATE TASK POSITIONS
    // ========================================
    private void updateTaskPositions() {
        for (int i = 0; i < taskList.size(); i++) {
            taskList.get(i).setPosition(i);
        }
    }


    private void scheduleListNotificationIfNeeded(String userId, String listId, String listTitle) {
        // Only schedule list-level notification if:
        // 1. The LIST has a due date (not individual tasks)
        // 2. The list has reminder enabled

        if (dueDate != null && hasReminder) {
            Log.d(TAG, "📅 Scheduling LIST-LEVEL notification for: " + listTitle);

            // Cancel any existing list-level notification first
            NotificationHelper.cancelNotification(this, listId);

            // Schedule new notification
            NotificationHelper.scheduleTodoListNotification(
                    this,
                    listId,
                    listTitle,
                    dueDate.getTime(),
                    dueTime,
                    reminderMinutes
            );
        } else {
            // If list no longer has due date/reminder, cancel the notification
            NotificationHelper.cancelNotification(this, listId);
            Log.d(TAG, "🔕 List notification cancelled (no due date or reminder)");
        }
    }
    // ========================================
    // SAVE TODO LIST TO FIREBASE
    // ========================================
    private void saveTodoList() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = todoTitle.getText().toString().trim();
        if (title.isEmpty()) {
            title = "To-Do List";
        }

        int totalTasks = 0;
        int completedTasks = 0;
        for (TodoTask task : taskList) {
            if (!task.getTaskText().trim().isEmpty()) {
                totalTasks++;
                if (task.isCompleted()) {
                    completedTasks++;
                }
            }
        }

        if (isNewList) {
            listId = db.collection("users")
                    .document(user.getUid())
                    .collection("todoLists")
                    .document().getId();
        }

        final String finalTitle = title;
        final String finalListId = listId;
        final int finalTotalTasks = totalTasks;
        final int finalCompletedTasks = completedTasks;

        String description = finalTotalTasks + " task" + (finalTotalTasks != 1 ? "s" : "");

        if (dueDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            description += " (" + sdf.format(dueDate.getTime()) + ")";
        }

        description += " • " + finalCompletedTasks + " completed";

        final Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("title", finalTitle);
        scheduleData.put("description", description);
        scheduleData.put("category", "todo");
        scheduleData.put("sourceId", finalListId);  // ✅ ADD IMMEDIATELY
        scheduleData.put("isCompleted", finalCompletedTasks == finalTotalTasks && finalTotalTasks > 0);
        scheduleData.put("createdAt", new Timestamp(new Date()));
        scheduleData.put("createdAtServer", com.google.firebase.firestore.FieldValue.serverTimestamp());

        if (dueDate != null) {
            scheduleData.put("date", new Timestamp(dueDate.getTime()));
            scheduleData.put("hasReminder", hasReminder);
            scheduleData.put("reminderMinutes", reminderMinutes);
        } else {
            scheduleData.put("date", null);
            scheduleData.put("hasReminder", false);
        }

        scheduleData.put("time", dueTime != null ? dueTime : "");
        scheduleData.put("taskCount", finalTotalTasks);
        scheduleData.put("completedCount", finalCompletedTasks);

        Map<String, Object> listData = new HashMap<>();
        listData.put("title", finalTitle);
        listData.put("timestamp", new Timestamp(new Date()));
        listData.put("timestampServer", com.google.firebase.firestore.FieldValue.serverTimestamp());
        listData.put("taskCount", finalTotalTasks);
        listData.put("completedCount", finalCompletedTasks);

        if (dueDate != null) {
            listData.put("dueDate", new Timestamp(dueDate.getTime()));
        } else {
            listData.put("dueDate", null);
        }

        listData.put("dueTime", dueTime);

        // ✅ FIX: Create schedule FIRST before saving tasks
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .document(finalListId)
                .set(scheduleData, com.google.firebase.firestore.SetOptions.merge());

        // Save list data
        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .document(finalListId)
                .set(listData);

        // Save tasks in background
        saveTasks(user.getUid(), finalListId, finalTitle, finalTotalTasks,
                finalCompletedTasks, scheduleData);

        // Show toast and finish
        Toast.makeText(this, "✓ Saving to-do list...", Toast.LENGTH_SHORT).show();
        new android.os.Handler().postDelayed(() -> {
            finish();
        }, 300);
    }

    // ========================================
// SAVE TASKS TO FIREBASE
// ========================================
    private void saveTasks(String userId, String listId, String listTitle, int totalTasks,
                           int completedTasks, Map<String, Object> scheduleData) {

        int tasksToSave = 0;
        for (TodoTask task : taskList) {
            if (!task.getTaskText().trim().isEmpty()) {
                tasksToSave++;
            }
        }

        if (tasksToSave == 0) {
            // ✅ Schedule notification but don't create schedule (already created)
            scheduleListNotificationIfNeeded(userId, listId, listTitle);
            return;
        }

        final int[] savedCount = {0};
        final int finalTasksToSave = tasksToSave;

        for (TodoTask task : taskList) {
            if (!task.getTaskText().trim().isEmpty()) {
                Map<String, Object> taskData = new HashMap<>();
                taskData.put("taskText", task.getTaskText());
                taskData.put("isCompleted", task.isCompleted());
                taskData.put("position", task.getPosition());

                if (task.getScheduleDate() != null) {
                    taskData.put("scheduleDate", new Timestamp(task.getScheduleDate()));
                }
                taskData.put("scheduleTime", task.getScheduleTime());
                taskData.put("hasNotification", task.hasNotification());
                taskData.put("notificationMinutes", task.getNotificationMinutes());

                String taskId = task.getId();
                if (taskId == null || taskId.isEmpty()) {
                    taskId = db.collection("users")
                            .document(userId)
                            .collection("todoLists")
                            .document(listId)
                            .collection("tasks")
                            .document().getId();
                    task.setId(taskId);
                }

                final String finalTaskId = taskId;

                db.collection("users")
                        .document(userId)
                        .collection("todoLists")
                        .document(listId)
                        .collection("tasks")
                        .document(finalTaskId)
                        .set(taskData)
                        .addOnSuccessListener(aVoid -> {
                            savedCount[0]++;

                            if (task.hasNotification() &&
                                    !task.isCompleted() &&
                                    task.getScheduleDate() != null) {

                                NotificationHelper.scheduleTodoTaskNotification(
                                        TodoActivity.this,
                                        listId,
                                        finalTaskId,
                                        task.getTaskText(),
                                        task.getScheduleDate(),
                                        task.getScheduleTime(),
                                        task.getNotificationMinutes()
                                );
                            }

                            if (savedCount[0] == finalTasksToSave) {
                                // ✅ Just schedule notification, schedule already created
                                scheduleListNotificationIfNeeded(userId, listId, listTitle);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to save task", e);
                            savedCount[0]++;
                            if (savedCount[0] == finalTasksToSave) {
                                scheduleListNotificationIfNeeded(userId, listId, listTitle);
                            }
                        });
            }
        }
    }
        // ========================================
    // CREATE OR UPDATE SCHEDULE
    // ========================================
    private void createOrUpdateSchedule(String userId, String listId, Map<String, Object> scheduleData) {
        scheduleData.put("sourceId", listId);

        String listTitle = (String) scheduleData.get("title");

        // ✅ FIXED: Use set() with document ID instead of querying first
        // This works offline because it writes directly to local cache
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(listId)  // ✅ Use listId as document ID
                .set(scheduleData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Schedule saved for todo list (works offline)");
                    scheduleListNotificationIfNeeded(userId, listId, listTitle);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save schedule", e);
                    // Still works offline - will sync when online
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
        // ✅ Add this:
        if (tasksListener != null) {
            tasksListener.remove();
        }
    }

    private void setupTasksRealtimeListener(String userId) {
        if (tasksListener != null) {
            tasksListener.remove();
        }

        tasksListener = db.collection("users")
                .document(userId)
                .collection("todoLists")
                .document(listId)
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
                        String taskText = doc.getString("taskText");
                        Boolean isCompleted = doc.getBoolean("isCompleted");
                        Long position = doc.getLong("position");

                        Log.d(TAG, "🔄 Real-time update: " + taskText + " -> " + isCompleted);

                        // Find and update matching task in UI
                        for (int i = 0; i < taskList.size(); i++) {
                            TodoTask task = taskList.get(i);

                            boolean matches = false;
                            if (!task.getId().isEmpty() && task.getId().equals(taskId)) {
                                matches = true;
                            } else if (task.getTaskText().equals(taskText) &&
                                    position != null && task.getPosition() == position.intValue()) {
                                matches = true;
                                task.setId(taskId);
                            }

                            if (matches) {
                                boolean newStatus = isCompleted != null && isCompleted;
                                if (task.isCompleted() != newStatus) {
                                    Log.d(TAG, "✅ Updating UI for task: " + taskText);
                                    task.setCompleted(newStatus);

                                    // ✅ UPDATE: Use adapter instead of manual view update
                                    taskAdapter.notifyItemChanged(i);
                                }
                                break;
                            }
                        }
                    }
                });
    }

}