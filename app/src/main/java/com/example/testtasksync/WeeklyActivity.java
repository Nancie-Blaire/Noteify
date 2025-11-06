package com.example.testtasksync;

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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeeklyActivity extends AppCompatActivity {

    private static final String TAG = "WeeklyActivity";
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String planId;
    private boolean isNewPlan = false;

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

        // Load plan details
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        if (title != null && !title.isEmpty()) {
                            weeklyTitle.setText(title);
                        }
                    }

                    // Load tasks
                    loadTasks();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load weekly plan", e);
                    Toast.makeText(this, "Failed to load plan", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadTasks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
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

        // Create or update plan document
        Map<String, Object> planData = new HashMap<>();
        planData.put("title", title);
        planData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        planData.put("taskCount", totalTasks);
        planData.put("completedCount", completedTasks);

        // Generate new ID if this is a new plan
        if (isNewPlan) {
            planId = db.collection("users")
                    .document(user.getUid())
                    .collection("weeklyPlans")
                    .document().getId();
        }

        final String finalTitle = title;
        final String finalPlanId = planId;

        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .document(planId)
                .set(planData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Weekly plan saved successfully");
                    saveTasks(user.getUid(), finalPlanId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save weekly plan", e);
                    Toast.makeText(this, "Failed to save plan", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveTasks(String userId, String planId) {
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

                    // Now save all current tasks
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

                    if (tasksToSave == 0) {
                        Toast.makeText(this, "✓ Weekly plan saved", Toast.LENGTH_SHORT).show();
                        finish();
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
                                                    Toast.makeText(this, "✓ Weekly plan saved", Toast.LENGTH_SHORT).show();
                                                    finish();
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
}