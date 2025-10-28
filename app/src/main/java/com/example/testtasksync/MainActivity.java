package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private List<Task> taskList;
    private TaskAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        // Initialize task list
        taskList = new ArrayList<>();

        // Setup RecyclerView (we'll create this layout next)
        RecyclerView recyclerView = findViewById(R.id.taskRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(taskList);
        recyclerView.setAdapter(adapter);

        // Add task button
        Button addTaskBtn = findViewById(R.id.addTaskBtn);
        addTaskBtn.setOnClickListener(v -> addTaskToFirestore());

        // ✅ START REAL-TIME SYNC
        setupRealtimeSync();
    }

    // 🔥 REAL-TIME SYNC LISTENER
    private void setupRealtimeSync() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .collection("tasks")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w("Firestore", "Listen failed.", e);
                        Toast.makeText(this, "Sync error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Clear and update task list
                    taskList.clear();

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Task task = new Task(
                                    doc.getId(),
                                    doc.getString("title"),
                                    doc.getString("description"),
                                    doc.getString("dueDate")
                            );
                            taskList.add(task);
                        }
                    }

                    // Notify adapter to refresh UI
                    adapter.notifyDataSetChanged();

                    Log.d("SYNC", "Tasks updated: " + taskList.size() + " tasks");
                    Toast.makeText(this, "Synced! " + taskList.size() + " tasks", Toast.LENGTH_SHORT).show();
                });
    }

    // Add a new task
    private void addTaskToFirestore() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> task = new HashMap<>();
        task.put("title", "Task " + System.currentTimeMillis());
        task.put("description", "Auto-generated test task");
        task.put("dueDate", "2025-10-15T18:00:00");

        db.collection("users")
                .document(user.getUid())
                .collection("tasks")
                .add(task)
                .addOnSuccessListener(docRef -> {
                    Log.d("Firestore", "Task added! ID: " + docRef.getId());
                    // No need to manually update UI - the listener will do it automatically!
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}