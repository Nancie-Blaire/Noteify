package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Favorites extends AppCompatActivity {

    private static final String TAG = "FavoritesActivity";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private RecyclerView favoritesRecyclerView;
    private NoteAdapter favoritesAdapter;
    private List<Note> favoritesList;
    private List<Note> noteList;
    private List<Note> todoList;
    private List<Note> weeklyList;
    private ImageView backButton;
    private TextView emptyStateText;

    private NoteAdapter.ItemTypeDetector typeDetector;
    private boolean notesLoaded = false;
    private boolean schedulesLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        // Initialize views
        favoritesRecyclerView = findViewById(R.id.favoritesRecyclerView);
        backButton = findViewById(R.id.backButton);
        emptyStateText = findViewById(R.id.emptyStateText);

        // Setup RecyclerView with 2-column grid
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        favoritesRecyclerView.setLayoutManager(gridLayoutManager);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        // Initialize lists
        favoritesList = new ArrayList<>();
        noteList = new ArrayList<>();
        todoList = new ArrayList<>();
        weeklyList = new ArrayList<>();

        // Create type detector
        typeDetector = note -> {
            if (todoList.contains(note)) return "todo";
            if (weeklyList.contains(note)) return "weekly";
            return "note";
        };

        // Setup adapter
        favoritesAdapter = new NoteAdapter(favoritesList, note -> {
            openItem(note);
        }, true, typeDetector);
        favoritesRecyclerView.setAdapter(favoritesAdapter);

        // Back button
        backButton.setOnClickListener(v -> finish());

        // Load data
        loadNotes(user);
        loadSchedules(user);
    }

    private void loadNotes(FirebaseUser user) {
        db.collection("users")
                .document(user.getUid())
                .collection("notes")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Notes listen failed.", e);
                        notesLoaded = true;
                        updateUI();
                        return;
                    }

                    noteList.clear();

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            // Filter out deleted items
                            if (doc.get("deletedAt") != null) {
                                continue;
                            }

                            Note note = new Note(
                                    doc.getId(),
                                    doc.getString("title"),
                                    doc.getString("content")
                            );

                            try {
                                Timestamp timestamp = doc.getTimestamp("timestamp");
                                if (timestamp != null) {
                                    note.setTimestamp(timestamp.toDate().getTime());
                                }
                            } catch (RuntimeException ex) {
                                Long timestampLong = doc.getLong("timestamp");
                                if (timestampLong != null) {
                                    note.setTimestamp(timestampLong);
                                } else {
                                    note.setTimestamp(System.currentTimeMillis());
                                }
                            }

                            Boolean isStarred = doc.getBoolean("isStarred");
                            if (isStarred != null && isStarred) {
                                note.setStarred(true);
                            }

                            Boolean isLocked = doc.getBoolean("isLocked");
                            if (isLocked != null && isLocked) {
                                note.setLocked(true);
                            }

                            noteList.add(note);
                        }
                    }

                    Log.d(TAG, "Notes loaded: " + noteList.size());
                    notesLoaded = true;
                    updateUI();
                });
    }

    private void loadSchedules(FirebaseUser user) {
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Schedules listen failed: " + e.getMessage());
                        schedulesLoaded = true;
                        updateUI();
                        return;
                    }

                    todoList.clear();
                    weeklyList.clear();

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            // Filter out deleted items
                            if (doc.get("deletedAt") != null) {
                                continue;
                            }

                            String category = doc.getString("category");

                            if ("todo".equals(category)) {
                                Note todoNote = createNoteFromSchedule(doc, "To-Do List");
                                todoList.add(todoNote);
                            } else if ("weekly".equals(category)) {
                                Note weeklyNote = createNoteFromSchedule(doc, "Weekly Plan");
                                weeklyList.add(weeklyNote);
                            }
                        }
                    }

                    Log.d(TAG, "Schedules loaded - Todos: " + todoList.size() + ", Weeklies: " + weeklyList.size());
                    schedulesLoaded = true;
                    updateUI();
                });
    }

    private Note createNoteFromSchedule(QueryDocumentSnapshot doc, String defaultTitle) {
        String id = doc.getId();
        String title = doc.getString("title");
        String description = doc.getString("description");
        String content = description != null ? description : "No description";

        Note note = new Note(id, title != null ? title : defaultTitle, content);

        try {
            Timestamp createdAt = doc.getTimestamp("createdAt");
            if (createdAt != null) {
                note.setTimestamp(createdAt.toDate().getTime());
            } else {
                note.setTimestamp(System.currentTimeMillis());
            }
        } catch (RuntimeException ex) {
            Long timestampLong = doc.getLong("createdAt");
            if (timestampLong != null) {
                note.setTimestamp(timestampLong);
            } else {
                note.setTimestamp(System.currentTimeMillis());
            }
        }

        Boolean isStarred = doc.getBoolean("isStarred");
        if (isStarred != null && isStarred) {
            note.setStarred(true);
        }

        Boolean isLocked = doc.getBoolean("isLocked");
        if (isLocked != null && isLocked) {
            note.setLocked(true);
        }

        return note;
    }

    private void updateUI() {
        if (!notesLoaded || !schedulesLoaded) {
            Log.d(TAG, "Waiting for all data... (notes:" + notesLoaded +
                    ", schedules:" + schedulesLoaded + ")");
            return;
        }

        Log.d(TAG, "All data loaded! Updating UI...");

        // Collect all starred items
        favoritesList.clear();
        for (Note note : noteList) {
            if (note.isStarred()) {
                favoritesList.add(note);
            }
        }
        for (Note todo : todoList) {
            if (todo.isStarred()) {
                favoritesList.add(todo);
            }
        }
        for (Note weekly : weeklyList) {
            if (weekly.isStarred()) {
                favoritesList.add(weekly);
            }
        }

        // Sort by timestamp (newest first)
        Collections.sort(favoritesList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
            }
        });

        Log.d(TAG, "Total favorites: " + favoritesList.size());

        // Show/hide empty state
        if (favoritesList.isEmpty()) {
            favoritesRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
        } else {
            favoritesRecyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
            favoritesAdapter.notifyDataSetChanged();
        }
    }

    private void openItem(Note item) {
        if (todoList.contains(item) || weeklyList.contains(item)) {
            db.collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .collection("schedules")
                    .document(item.getId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String sourceId = documentSnapshot.getString("sourceId");
                            String category = documentSnapshot.getString("category");

                            if (sourceId != null && category != null) {
                                if ("todo".equals(category)) {
                                    Intent intent = new Intent(this, TodoActivity.class);
                                    intent.putExtra("listId", sourceId);
                                    startActivity(intent);
                                } else if ("weekly".equals(category)) {
                                    Intent intent = new Intent(this, WeeklyActivity.class);
                                    intent.putExtra("planId", sourceId);
                                    startActivity(intent);
                                }
                            } else {
                                Toast.makeText(this, "Error: Invalid schedule data", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Error: Schedule not found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get schedule sourceId", e);
                        Toast.makeText(this, "Error opening item", Toast.LENGTH_SHORT).show();
                    });
        } else {
            Intent intent = new Intent(this, NoteActivity.class);
            intent.putExtra("noteId", item.getId());
            startActivity(intent);
        }
    }
}