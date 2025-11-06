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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TodoActivity extends AppCompatActivity {

    private static final String TAG = "TodoActivity";
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String listId;
    private boolean isNewList = false;

    private EditText todoTitle;
    private ImageView saveButton, backButton;
    private LinearLayout tasksContainer, addTaskButton;

    private List<TodoTask> taskList = new ArrayList<>();

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
        tasksContainer = findViewById(R.id.tasksContainer);
        addTaskButton = findViewById(R.id.addTaskButton);

        // Set up buttons
        saveButton.setOnClickListener(v -> saveTodoList());
        backButton.setOnClickListener(v -> finish());
        addTaskButton.setOnClickListener(v -> addTask());

        // Load existing list or add default tasks
        if (isNewList) {
            // Add 2 default tasks
            addTask();
            addTask();
        } else {
            loadTodoList();
        }
    }

    private void loadTodoList() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Load list details
        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .document(listId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        if (title != null && !title.isEmpty()) {
                            todoTitle.setText(title);
                        }
                    }

                    // Load tasks
                    loadTasks();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load todo list", e);
                    Toast.makeText(this, "Failed to load list", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadTasks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .document(listId)
                .collection("tasks")
                .orderBy("position")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Clear existing views and list
                    tasksContainer.removeAllViews();
                    taskList.clear();

                    // Load tasks from Firebase
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        TodoTask task = new TodoTask();
                        task.setId(doc.getId());
                        task.setTaskText(doc.getString("taskText"));
                        task.setCompleted(Boolean.TRUE.equals(doc.getBoolean("isCompleted")));
                        task.setPosition(doc.getLong("position").intValue());

                        taskList.add(task);
                        addTaskView(task);
                    }

                    // If no tasks exist, add default ones
                    if (taskList.isEmpty()) {
                        addTask();
                        addTask();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load tasks", e);
                    // Add default tasks if loading fails
                    addTask();
                    addTask();
                });
    }

    private void addTask() {
        TodoTask task = new TodoTask();
        task.setId(""); // Will be generated on save
        task.setTaskText("");
        task.setCompleted(false);
        task.setPosition(taskList.size());

        taskList.add(task);
        addTaskView(task);
    }

    private void addTaskView(TodoTask task) {
        View taskView = LayoutInflater.from(this).inflate(R.layout.item_todo, tasksContainer, false);

        CheckBox checkbox = taskView.findViewById(R.id.todoCheckbox);
        EditText taskText = taskView.findViewById(R.id.todoEditText);
        ImageView deleteButton = taskView.findViewById(R.id.deleteTodoButton);

        // Set initial values
        checkbox.setChecked(task.isCompleted());
        if (task.getTaskText() != null && !task.getTaskText().isEmpty()) {
            taskText.setText(task.getTaskText());
        }

        // Apply completed style if checked
        if (task.isCompleted()) {
            taskText.setTextColor(getResources().getColor(android.R.color.darker_gray));
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
            if (taskList.size() > 1) { // Keep at least 1 task
                taskList.remove(task);
                tasksContainer.removeView(taskView);
                updateTaskPositions();
            } else {
                Toast.makeText(this, "Keep at least one task", Toast.LENGTH_SHORT).show();
            }
        });

        tasksContainer.addView(taskView);
    }

    private void updateTaskPositions() {
        for (int i = 0; i < taskList.size(); i++) {
            taskList.get(i).setPosition(i);
        }
    }

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

        // Calculate total and completed tasks
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

        // Create or update list document
        Map<String, Object> listData = new HashMap<>();
        listData.put("title", title);
        listData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        listData.put("taskCount", totalTasks);
        listData.put("completedCount", completedTasks);

        // Generate new ID if this is a new list
        if (isNewList) {
            listId = db.collection("users")
                    .document(user.getUid())
                    .collection("todoLists")
                    .document().getId();
        }

        final String finalTitle = title;
        final String finalListId = listId;

        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .document(listId)
                .set(listData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Todo list saved successfully");
                    saveTasks(user.getUid(), finalListId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save todo list", e);
                    Toast.makeText(this, "Failed to save list", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveTasks(String userId, String listId) {
        // First, delete all existing tasks
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

                    // Count non-empty tasks
                    int tasksToSave = 0;
                    for (TodoTask task : taskList) {
                        if (!task.getTaskText().trim().isEmpty()) {
                            tasksToSave++;
                        }
                    }

                    if (tasksToSave == 0) {
                        Toast.makeText(this, "✓ To-Do list saved", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    final int[] savedCount = {0};
                    for (TodoTask task : taskList) {
                        if (!task.getTaskText().trim().isEmpty()) {
                            Map<String, Object> taskData = new HashMap<>();
                            taskData.put("taskText", task.getTaskText());
                            taskData.put("isCompleted", task.isCompleted());
                            taskData.put("position", task.getPosition());

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
                                            Toast.makeText(this, "✓ To-Do list saved", Toast.LENGTH_SHORT).show();
                                            finish();
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
}