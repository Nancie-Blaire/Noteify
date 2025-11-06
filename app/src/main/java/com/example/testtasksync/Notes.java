package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
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

public class Notes extends Fragment {

    private static final String TAG = "NotesFragment";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private List<Note> noteList;
    private List<Note> starredNoteList;
    private List<Note> combinedList; // Notes + Todo + Weekly combined

    private NoteAdapter adapter;
    private NoteAdapter starredAdapter;
    private NoteAdapter combinedAdapter;

    // RecyclerViews
    private RecyclerView prioNotesRecyclerView;
    private RecyclerView notesRecyclerView;

    // UI Elements
    private SearchView searchView;

    // Store todo and weekly list IDs to track them
    private Map<String, String> todoListMap = new HashMap<>();
    private Map<String, String> weeklyPlanMap = new HashMap<>();

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
        searchView = view.findViewById(R.id.searchBar);
        searchView.clearFocus();

        searchView.setOnClickListener(v -> {
            searchView.setFocusable(true);
            searchView.setFocusableInTouchMode(true);
            searchView.requestFocus();
        });

        // Check if user is logged in
        if (user == null) {
            startActivity(new Intent(getContext(), Login.class));
            requireActivity().finish();
            return;
        }

        // Initialize lists
        noteList = new ArrayList<>();
        starredNoteList = new ArrayList<>();
        combinedList = new ArrayList<>();

        // Adapter for starred notes
        starredAdapter = new NoteAdapter(starredNoteList, note -> {
            Intent intent = new Intent(getContext(), NoteActivity.class);
            intent.putExtra("noteId", note.getId());
            startActivity(intent);
        }, true);

        // Adapter for combined list (notes + todo + weekly)
        combinedAdapter = new NoteAdapter(combinedList, note -> {
            // Check if it's a todo list
            if (todoListMap.containsKey(note.getId())) {
                Intent intent = new Intent(getContext(), TodoActivity.class);
                intent.putExtra("listId", note.getId());
                intent.putExtra("listTitle", note.getTitle());
                startActivity(intent);
                return;
            }

            // Check if it's a weekly plan
            if (weeklyPlanMap.containsKey(note.getId())) {
                Intent intent = new Intent(getContext(), WeeklyActivity.class);
                intent.putExtra("planId", note.getId());
                intent.putExtra("planTitle", note.getTitle());
                startActivity(intent);
                return;
            }

            // Otherwise, it's a regular note
            Intent intent = new Intent(getContext(), NoteActivity.class);
            intent.putExtra("noteId", note.getId());
            startActivity(intent);
        });

        // Load data from Firebase
        loadNotes(user);
        loadTodoLists(user);
        loadWeeklyPlans(user);
    }

    private void loadNotes(FirebaseUser user) {
        db.collection("users")
                .document(user.getUid())
                .collection("notes")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }

                    noteList.clear();
                    starredNoteList.clear();

                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Note note = new Note(
                                    doc.getId(),
                                    doc.getString("title"),
                                    doc.getString("content")
                            );

                            // Get starred state from Firebase
                            Boolean isStarred = doc.getBoolean("isStarred");
                            if (isStarred != null && isStarred) {
                                note.setStarred(true);
                                starredNoteList.add(note);
                            }

                            // Get locked state from Firebase
                            Boolean isLocked = doc.getBoolean("isLocked");
                            if (isLocked != null && isLocked) {
                                note.setLocked(true);
                            }

                            // Add all notes to regular list
                            noteList.add(note);
                        }
                    }

                    updateUI();
                });
    }

    private void loadTodoLists(FirebaseUser user) {
        db.collection("users")
                .document(user.getUid())
                .collection("todoLists")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Todo lists listen failed.", e);
                        return;
                    }

                    todoListMap.clear();

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            String id = doc.getId();
                            String title = doc.getString("title");
                            Long taskCount = doc.getLong("taskCount");
                            Long completedCount = doc.getLong("completedCount");

                            todoListMap.put(id, title != null ? title : "To-Do List");

                            // Create preview content
                            String content = String.format("%d/%d tasks completed",
                                    completedCount != null ? completedCount : 0,
                                    taskCount != null ? taskCount : 0);

                            Note todoNote = new Note(id, title, content);
                            // Don't add to noteList to avoid duplication
                        }
                    }

                    updateUI();
                });
    }

    private void loadWeeklyPlans(FirebaseUser user) {
        db.collection("users")
                .document(user.getUid())
                .collection("weeklyPlans")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Weekly plans listen failed.", e);
                        return;
                    }

                    weeklyPlanMap.clear();

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            String id = doc.getId();
                            String title = doc.getString("title");
                            Long taskCount = doc.getLong("taskCount");
                            Long completedCount = doc.getLong("completedCount");

                            weeklyPlanMap.put(id, title != null ? title : "Weekly Plan");

                            // Create preview content
                            String content = String.format("%d/%d tasks completed",
                                    completedCount != null ? completedCount : 0,
                                    taskCount != null ? taskCount : 0);

                            Note weeklyNote = new Note(id, title, content);
                            // Don't add to noteList to avoid duplication
                        }
                    }

                    updateUI();
                });
    }

    private void updateUI() {
        // Handle Starred/Prios Section
        if (starredNoteList.isEmpty()) {
            Log.d(TAG, "No starred notes - showing welcome card in prio section");
            defaultCardAdapter prioWelcomeAdapter = new defaultCardAdapter(true);
            prioNotesRecyclerView.setAdapter(prioWelcomeAdapter);
        } else {
            Log.d(TAG, "Starred notes found: " + starredNoteList.size());
            prioNotesRecyclerView.setAdapter(starredAdapter);
            starredAdapter.notifyDataSetChanged();
        }

        // Combine all items (notes + todo + weekly)
        combinedList.clear();

        // Add todo lists as cards
        for (Map.Entry<String, String> entry : todoListMap.entrySet()) {
            Note todoCard = new Note(entry.getKey(), entry.getValue(), "To-Do List");
            combinedList.add(todoCard);
        }

        // Add weekly plans as cards
        for (Map.Entry<String, String> entry : weeklyPlanMap.entrySet()) {
            Note weeklyCard = new Note(entry.getKey(), entry.getValue(), "Weekly Plan");
            combinedList.add(weeklyCard);
        }

        // Add regular notes
        combinedList.addAll(noteList);

        // Handle Combined Recent Section
        if (combinedList.isEmpty()) {
            Log.d(TAG, "No items - showing welcome card in recent section");
            defaultCardAdapter recentWelcomeAdapter = new defaultCardAdapter(false);
            notesRecyclerView.setAdapter(recentWelcomeAdapter);
        } else {
            Log.d(TAG, "Combined items found: " + combinedList.size());
            notesRecyclerView.setAdapter(combinedAdapter);
            combinedAdapter.notifyDataSetChanged();
        }
    }
}