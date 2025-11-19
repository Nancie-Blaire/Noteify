package com.example.testtasksync;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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

public class TodoActivity extends AppCompatActivity {

    private static final String TAG = "TodoActivity";
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String listId;
    private boolean isNewList = false;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Get list ID from intent
        listId = getIntent().getStringExtra("listId");
        isNewList = (listId == null || listId.isEmpty());

        // Initialize views
        todoTitle = findViewById(R.id.todoTitle);
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        dueDateButton = findViewById(R.id.dueDateButton); // ✅ NEW
        tasksContainer = findViewById(R.id.tasksContainer);
        addTaskButton = findViewById(R.id.addTaskButton);
        dueDateDisplay = findViewById(R.id.dueDateDisplay); // ✅ NEW
        dueDateText = findViewById(R.id.dueDateText);
        clearDateButton = findViewById(R.id.clearDateButton);

        // Set up buttons
        saveButton.setOnClickListener(v -> saveTodoList());
        backButton.setOnClickListener(v -> finish());
        addTaskButton.setOnClickListener(v -> addTask());

        // ✅ NEW: Due date button opens dialog
        dueDateButton.setOnClickListener(v -> showDueDateDialog());
        clearDateButton.setOnClickListener(v -> clearDueDate());

        // Load existing list or add default tasks
        if (isNewList) {
            addTask();
            addTask();
        } else {
            loadTodoList();
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
                    hour, minute, true
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

        final String finalTitle = title;
        final int finalTotalTasks = totalTasks;
        final int finalCompletedTasks = completedTasks;

        final Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("title", finalTitle);
        scheduleData.put("description", finalTotalTasks + " tasks (" + finalCompletedTasks + " completed)");
        scheduleData.put("category", "todo");
        scheduleData.put("isCompleted", finalCompletedTasks == finalTotalTasks && finalTotalTasks > 0);
        scheduleData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        if (dueDate != null) {
            scheduleData.put("date", new Timestamp(dueDate.getTime()));
            scheduleData.put("hasReminder", hasReminder);
            scheduleData.put("reminderMinutes", reminderMinutes);
        } else {
            scheduleData.put("date", null);
            scheduleData.put("hasReminder", false);
        }

        scheduleData.put("time", dueTime != null ? dueTime : "");

        Map<String, Object> listData = new HashMap<>();
        listData.put("title", finalTitle);
        listData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        listData.put("taskCount", finalTotalTasks);
        listData.put("completedCount", finalCompletedTasks);

        if (dueDate != null) {
            listData.put("dueDate", new Timestamp(dueDate.getTime()));
        } else {
            listData.put("dueDate", null);
        }

        listData.put("dueTime", dueTime);

        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .document(listId)
                .set(listData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Todo list saved successfully");
                    saveTasksWithoutFinish(user.getUid(), listId, finalTitle, finalTotalTasks,
                            finalCompletedTasks, scheduleData);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save todo list", e);
                    Toast.makeText(this, "Failed to save list", Toast.LENGTH_SHORT).show();
                });
    }

    // ========================================
// SAVE TASKS WITHOUT FINISHING
// ========================================
    private void saveTasksWithoutFinish(String userId, String listId, String listTitle, int totalTasks,
                                        int completedTasks, Map<String, Object> scheduleData) {
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
                        createOrUpdateScheduleWithoutFinish(userId, listId, scheduleData);
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
                                            createOrUpdateScheduleWithoutFinish(userId, listId, scheduleData);
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
                    Toast.makeText(this, "Failed to save tasks", Toast.LENGTH_SHORT).show();
                });
    }

    // ========================================
// CREATE OR UPDATE SCHEDULE WITHOUT FINISHING
// ========================================
    private void createOrUpdateScheduleWithoutFinish(String userId, String listId, Map<String, Object> scheduleData) {
        scheduleData.put("sourceId", listId);

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
                                    // ✅ DON'T call finish() here!
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
                                    // ✅ DON'T call finish() here!
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
    // ========================================
    // UPDATE DUE DATE DISPLAY
    // ========================================
    private void updateDueDateDisplay() {
        if (dueDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            String displayText = "Due: " + sdf.format(dueDate.getTime());

            if (dueTime != null && !dueTime.isEmpty()) {
                displayText += ", " + dueTime;
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
        hasReminder = false; // ✅ Also reset reminder
        updateDueDateDisplay();

        // ✅ CHANGED: Use the new method that doesn't finish
        if (!isNewList) {
            saveTodoListWithoutFinish(); // ✅ Save but stay in activity
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
                    tasksContainer.removeAllViews();
                    taskList.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        TodoTask task = new TodoTask();
                        task.setId(doc.getId());
                        task.setTaskText(doc.getString("taskText"));
                        task.setCompleted(Boolean.TRUE.equals(doc.getBoolean("isCompleted")));
                        task.setPosition(doc.getLong("position").intValue());

                        // Load schedule data
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
                        addTaskView(task);
                    }

                    if (taskList.isEmpty()) {
                        addTask();
                        addTask();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load tasks", e);
                    addTask();
                    addTask();
                });
        setupTasksRealtimeListener(userId);
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
        addTaskView(task);
    }

    // ========================================
    // ADD TASK VIEW (UI + LISTENERS)
    // ========================================
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

// ✅ Apply strikethrough based on completion status (MUST be AFTER setText!)
        if (task.isCompleted()) {
            taskText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            taskText.setTextColor(getResources().getColor(android.R.color.black));
            taskText.setPaintFlags(taskText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }

        updateTaskScheduleDisplay(task, scheduleDisplay, scheduleText, notificationIcon);

// Replace the existing checkbox listener with this:
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            task.setCompleted(isChecked);
            if (isChecked) {
                taskText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                taskText.setTextColor(getResources().getColor(android.R.color.black));
                taskText.setPaintFlags(taskText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            }

            // Update in Firebase immediately
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

        tasksContainer.addView(taskView);
    }

    private void updateTaskCompletionInFirebase(TodoTask task) {
        if (isNewList || task.getId().isEmpty()) {
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // ✅ FIXED: Find task by ID instead of text/position
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

                            db.collection("users")
                                    .document(user.getUid())
                                    .collection("todoLists")
                                    .document(listId)
                                    .collection("tasks")
                                    .document(taskDocId)
                                    .update("isCompleted", task.isCompleted())
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "✅ Task completion status updated: " + task.isCompleted());
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
                displayText += ", " + task.getScheduleTime();
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
                    hour, minute, true
            );
            timePicker.show();
        });

        notificationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationTimeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveScheduleButton.setOnClickListener(v -> {
            if (selectedDateText.getText().equals("Select date")) {
                Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show();
                return;
            }

            task.setScheduleDate(taskSchedule.getTime());

            if (!selectedTimeText.getText().equals("Select time")) {
                task.setScheduleTime(selectedTimeText.getText().toString());
            }

            task.setHasNotification(notificationCheckbox.isChecked());
            if (notificationCheckbox.isChecked()) {
                int selectedPos = notificationTimeSpinner.getSelectedItemPosition();
                task.setNotificationMinutes(notificationMinutes[selectedPos]);
            }

            updateTaskScheduleDisplay(task, scheduleDisplay, scheduleText, notificationIcon);

            if (!isNewList) {
                saveTodoList();
            }

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

        final Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("title", finalTitle);
        scheduleData.put("description", finalTotalTasks + " tasks (" + finalCompletedTasks + " completed)");
        scheduleData.put("category", "todo");
        scheduleData.put("isCompleted", finalCompletedTasks == finalTotalTasks && finalTotalTasks > 0);
        scheduleData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        if (dueDate != null) {
            scheduleData.put("date", new Timestamp(dueDate.getTime()));
            scheduleData.put("hasReminder", true);
            scheduleData.put("reminderMinutes", 60);
        } else {
            scheduleData.put("date", null);
            scheduleData.put("hasReminder", false);
        }

        // ✅ NEW: Save due time
        scheduleData.put("time", dueTime != null ? dueTime : "");

        Map<String, Object> listData = new HashMap<>();
        listData.put("title", finalTitle);
        listData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        listData.put("taskCount", finalTotalTasks);
        listData.put("completedCount", finalCompletedTasks);

        if (dueDate != null) {
            listData.put("dueDate", new Timestamp(dueDate.getTime()));
        } else {
            listData.put("dueDate", null);
        }

        // ✅ NEW: Save due time in list data
        listData.put("dueTime", dueTime);

        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .document(finalListId)
                .set(listData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Todo list saved successfully");
                    saveTasks(user.getUid(), finalListId, finalTitle, finalTotalTasks,
                            finalCompletedTasks, scheduleData);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save todo list", e);
                    Toast.makeText(this, "Failed to save list", Toast.LENGTH_SHORT).show();
                });
    }

    // ========================================
    // SAVE TASKS TO FIREBASE
    // ========================================
    private void saveTasks(String userId, String listId, String listTitle, int totalTasks,
                           int completedTasks, Map<String, Object> scheduleData) {
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
                        createOrUpdateSchedule(userId, listId, scheduleData);
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
                                            createOrUpdateSchedule(userId, listId, scheduleData);
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
                    Toast.makeText(this, "Failed to save tasks", Toast.LENGTH_SHORT).show();
                });
    }

    // ========================================
    // CREATE OR UPDATE SCHEDULE
    // ========================================
    private void createOrUpdateSchedule(String userId, String listId, Map<String, Object> scheduleData) {
        scheduleData.put("sourceId", listId);

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
                                    Log.d(TAG, "Schedule updated for todo list");
                                    Toast.makeText(this, "✓ To-Do list saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update schedule", e);
                                    Toast.makeText(this, "✓ To-Do list saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    } else {
                        db.collection("users")
                                .document(userId)
                                .collection("schedules")
                                .add(scheduleData)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d(TAG, "Schedule created for todo list");
                                    Toast.makeText(this, "✓ To-Do list saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create schedule", e);
                                    Toast.makeText(this, "✓ To-Do list saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check existing schedule", e);
                    Toast.makeText(this, "✓ To-Do list saved", Toast.LENGTH_SHORT).show();
                    finish();
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

                            // ✅ Match by ID if available, otherwise by text and position
                            boolean matches = false;
                            if (!task.getId().isEmpty() && task.getId().equals(taskId)) {
                                matches = true;
                            } else if (task.getTaskText().equals(taskText) &&
                                    position != null && task.getPosition() == position.intValue()) {
                                matches = true;
                                task.setId(taskId); // ✅ Update task ID
                            }

                            if (matches) {
                                boolean newStatus = isCompleted != null && isCompleted;
                                if (task.isCompleted() != newStatus) {
                                    Log.d(TAG, "✅ Updating UI for task: " + taskText);
                                    task.setCompleted(newStatus);

                                    // Update the UI
                                    if (i < tasksContainer.getChildCount()) {
                                        View taskView = tasksContainer.getChildAt(i);
                                        if (taskView != null) {
                                            CheckBox checkbox = taskView.findViewById(R.id.todoCheckbox);
                                            EditText taskTextView = taskView.findViewById(R.id.todoEditText);

                                            // Remove listener temporarily
                                            checkbox.setOnCheckedChangeListener(null);
                                            checkbox.setChecked(newStatus);

                                            // Apply strikethrough
                                            if (newStatus) {
                                                taskTextView.setTextColor(getResources().getColor(android.R.color.darker_gray));
                                                taskTextView.setPaintFlags(taskTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                                            } else {
                                                taskTextView.setTextColor(getResources().getColor(android.R.color.black));
                                                taskTextView.setPaintFlags(taskTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                                            }

                                            // Re-attach listener
                                            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                                task.setCompleted(isChecked);
                                                if (isChecked) {
                                                    taskTextView.setTextColor(getResources().getColor(android.R.color.darker_gray));
                                                    taskTextView.setPaintFlags(taskTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                                                } else {
                                                    taskTextView.setTextColor(getResources().getColor(android.R.color.black));
                                                    taskTextView.setPaintFlags(taskTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                                                }
                                                updateTaskCompletionInFirebase(task);
                                            });
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                });
    }
}