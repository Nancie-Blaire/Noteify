package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Notes extends Fragment {

    private static final String TAG = "NotesFragment";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private List<Note> noteList;
    private List<Note> todoList;
    private List<Note> weeklyList;
    private List<Note> starredList;
    private List<Note> combinedList;

    private NoteAdapter starredAdapter;
    private RecyclerView prioNotesRecyclerView;
    private RecyclerView notesRecyclerView;
    private EditText editText;

    private NoteAdapter.ItemTypeDetector typeDetector;

    // Track which data sources have loaded
    private boolean notesLoaded = false;
    private boolean schedulesLoaded = false; // ✅ One listener for both todo & weekly



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.d(TAG, "onViewCreated started");

        // Initialize RecyclerViews
        prioNotesRecyclerView = view.findViewById(R.id.prioNotesRecyclerView);
        notesRecyclerView = view.findViewById(R.id.notesRecyclerView);

        // Set layout managers
        prioNotesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        notesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();

        // Initialize search bar
        editText = view.findViewById(R.id.searchBar);
        editText.clearFocus();

        editText.setOnClickListener(v -> {
            editText.setFocusable(true);
            editText.setFocusableInTouchMode(true);
            editText.requestFocus();
        });

        // Check if user is logged in
        if (user == null) {
            startActivity(new Intent(getContext(), Login.class));
            requireActivity().finish();
            return;
        }

        // Initialize lists
        noteList = new ArrayList<>();
        todoList = new ArrayList<>();
        weeklyList = new ArrayList<>();
        starredList = new ArrayList<>();
        combinedList = new ArrayList<>();

        // Create type detector
        typeDetector = note -> {
            if (todoList.contains(note)) return "todo";
            if (weeklyList.contains(note)) return "weekly";
            return "note";
        };

        // Adapter for starred items (grid)
        starredAdapter = new NoteAdapter(starredList, note -> {
            openItem(note);
        }, true, typeDetector);

        // Load data from Firebase
        loadNotes(user);
        loadSchedules(user); // ✅ Load both todo & weekly from schedules collection
    }

    private void openItem(Note item) {
        // For todo and weekly items, we need to get the sourceId from Firestore
        // because the Note object contains the schedule ID, not the actual list/plan ID
        if (todoList.contains(item) || weeklyList.contains(item)) {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;

            // Get the schedule document to find the sourceId
            db.collection("users")
                    .document(user.getUid())
                    .collection("schedules")
                    .document(item.getId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String sourceId = documentSnapshot.getString("sourceId");
                            String category = documentSnapshot.getString("category");

                            if (sourceId != null && category != null) {
                                if ("todo".equals(category)) {
                                    Intent intent = new Intent(getContext(), TodoActivity.class);
                                    intent.putExtra("listId", sourceId);  // Use sourceId, not schedule ID
                                    startActivity(intent);
                                } else if ("weekly".equals(category)) {
                                    Intent intent = new Intent(getContext(), WeeklyActivity.class);
                                    intent.putExtra("planId", sourceId);  // Use sourceId, not schedule ID
                                    startActivity(intent);
                                }
                            } else {
                                Toast.makeText(getContext(), "Error: Invalid schedule data", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "Error: Schedule not found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get schedule sourceId", e);
                        Toast.makeText(getContext(), "Error opening item", Toast.LENGTH_SHORT).show();
                    });
        } else {
            // Regular note - just open normally
            Intent intent = new Intent(getContext(), NoteActivity.class);
            intent.putExtra("noteId", item.getId());
            startActivity(intent);
        }
    }

    private void loadNotes(FirebaseUser user) {
        db.collection("users")
                .document(user.getUid())
                .collection("notes")
                .orderBy("timestamp", Query.Direction.DESCENDING)
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
                            Note note = new Note(
                                    doc.getId(),
                                    doc.getString("title"),
                                    doc.getString("content")
                            );

                            // SAFE timestamp handling
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
                                Log.w(TAG, "Timestamp not in Firestore format, using fallback", ex);
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

                    Log.d(TAG, "✅ Notes loaded: " + noteList.size());
                    notesLoaded = true;
                    updateUI();
                });
    }

    // ✅ Load BOTH todo and weekly from schedules collection in ONE query
    private void loadSchedules(FirebaseUser user) {
        Log.d(TAG, "📋 Loading schedules (todo & weekly)...");

        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "❌ Schedules listen failed: " + e.getMessage());
                        schedulesLoaded = true;
                        updateUI();
                        return;
                    }

                    // Clear both lists
                    todoList.clear();
                    weeklyList.clear();

                    if (snapshots != null && !snapshots.isEmpty()) {
                        Log.d(TAG, "📦 Found " + snapshots.size() + " schedule items");

                        for (QueryDocumentSnapshot doc : snapshots) {
                            String category = doc.getString("category");

                            // ✅ Check if item was added from DayDetails
                            Boolean addedFromDayDetails = doc.getBoolean("addedFromDayDetails");

                            // ✅ Skip items that were added from DayDetails
                            if (addedFromDayDetails != null && addedFromDayDetails) {
                                Log.d(TAG, "  ⭐️ Skipping DayDetails item: " + doc.getString("title"));
                                continue;
                            }

                            // ✅ Filter by category and add to appropriate list
                            if ("todo".equals(category)) {
                                Note todoNote = createNoteFromSchedule(doc, "To-Do List");
                                todoList.add(todoNote);
                                Log.d(TAG, "  ✅ Added todo: " + todoNote.getTitle());

                            } else if ("weekly".equals(category)) {
                                Note weeklyNote = createNoteFromSchedule(doc, "Weekly Plan");
                                weeklyList.add(weeklyNote);
                                Log.d(TAG, "  ✅ Added weekly: " + weeklyNote.getTitle());
                            }
                            // Ignore other categories (event, holiday, etc.)
                        }
                    } else {
                        Log.d(TAG, "🔭 No schedules found");
                    }

                    Log.d(TAG, "✅ Schedules loaded - Todos: " + todoList.size() + ", Weeklies: " + weeklyList.size());
                    schedulesLoaded = true;
                    updateUI();
                });
    }

    // ✅ Helper method to create Note from schedule document
    private Note createNoteFromSchedule(QueryDocumentSnapshot doc, String defaultTitle) {
        String id = doc.getId();
        String title = doc.getString("title");
        String description = doc.getString("description");
        String content = description != null ? description : "No description";

        Note note = new Note(id, title != null ? title : defaultTitle, content);

        // Get timestamp
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

        // Get starred state
        Boolean isStarred = doc.getBoolean("isStarred");
        if (isStarred != null && isStarred) {
            note.setStarred(true);
        }

        // Get locked state
        Boolean isLocked = doc.getBoolean("isLocked");
        if (isLocked != null && isLocked) {
            note.setLocked(true);
        }

        return note;
    }

    private void updateUI() {
        // ✅ Only update UI when ALL data is loaded
        if (!notesLoaded || !schedulesLoaded) {
            Log.d(TAG, "⏳ Waiting for all data... (notes:" + notesLoaded +
                    ", schedules:" + schedulesLoaded + ")");
            return;
        }

        Log.d(TAG, "🎨 All data loaded! Updating UI...");
        Log.d(TAG, "   Notes: " + noteList.size());
        Log.d(TAG, "   Todos: " + todoList.size());
        Log.d(TAG, "   Weeklies: " + weeklyList.size());

        // Collect all starred items
        starredList.clear();
        for (Note note : noteList) {
            if (note.isStarred()) {
                starredList.add(note);
            }
        }
        for (Note todo : todoList) {
            if (todo.isStarred()) {
                starredList.add(todo);
            }
        }
        for (Note weekly : weeklyList) {
            if (weekly.isStarred()) {
                starredList.add(weekly);
            }
        }

        // Handle Starred/Prios Section
        if (starredList.isEmpty()) {
            Log.d(TAG, "No starred items - showing welcome card");
            defaultCardAdapter prioWelcomeAdapter = new defaultCardAdapter(true);
            prioNotesRecyclerView.setAdapter(prioWelcomeAdapter);
        } else {
            Log.d(TAG, "Starred items found: " + starredList.size());
            starredAdapter.setTypeDetector(typeDetector);
            prioNotesRecyclerView.setAdapter(starredAdapter);
            starredAdapter.notifyDataSetChanged();
        }

        // Combine all items and sort by timestamp (newest first)
        combinedList.clear();
        combinedList.addAll(noteList);
        combinedList.addAll(todoList);
        combinedList.addAll(weeklyList);

        Log.d(TAG, "Combined list size: " + combinedList.size());

        // Sort by timestamp (descending - newest first)
        Collections.sort(combinedList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
            }
        });

        // Handle Combined Recent Section
        if (combinedList.isEmpty()) {
            Log.d(TAG, "No items - showing welcome card");
            defaultCardAdapter recentWelcomeAdapter = new defaultCardAdapter(false);
            notesRecyclerView.setAdapter(recentWelcomeAdapter);
        } else {
            Log.d(TAG, "Combined items found: " + combinedList.size());

            // Create unified adapter WITH typeDetector
            NoteAdapter unifiedAdapter = new NoteAdapter(combinedList, note -> {
                openItem(note);
            }, false, typeDetector);

            notesRecyclerView.setAdapter(unifiedAdapter);
            unifiedAdapter.notifyDataSetChanged();

            Log.d(TAG, "✅ UI Updated successfully!");
        }
    }
}