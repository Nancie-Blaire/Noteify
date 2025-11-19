package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
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

import static android.content.Context.INPUT_METHOD_SERVICE;
import java.util.Collections;
import java.util.Comparator;

public class Notes extends Fragment {

    private static final String TAG = "NotesFragment";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private List<Note> noteList;
    private List<Note> todoList;
    private List<Note> weeklyList;
    private List<Note> starredList;
    private List<Note> combinedList;
    private List<Note> searchResults;

    private NoteAdapter starredAdapter;
    private NoteAdapter searchAdapter;
    private RecyclerView prioNotesRecyclerView;
    private RecyclerView notesRecyclerView;
    private RecyclerView searchRecyclerView;
    private EditText searchBar;
    private ImageView searchIcon;
    private View blurOverlay;
    private View searchContainer;
    private View mainContent;

    private NoteAdapter.ItemTypeDetector typeDetector;

    private boolean notesLoaded = false;
    private boolean schedulesLoaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.d(TAG, "onViewCreated started");

        // Initialize views
        prioNotesRecyclerView = view.findViewById(R.id.prioNotesRecyclerView);
        notesRecyclerView = view.findViewById(R.id.notesRecyclerView);
        searchRecyclerView = view.findViewById(R.id.searchRecyclerView);
        searchBar = view.findViewById(R.id.searchBar);
        searchIcon = view.findViewById(R.id.searchIcon);
        blurOverlay = view.findViewById(R.id.blurOverlay);
        searchContainer = view.findViewById(R.id.searchContainer);
        mainContent = view.findViewById(R.id.mainContent);

        // Set layout managers
        prioNotesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        notesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        searchRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();

        // Check if user is logged in
        if (user == null) {
            startActivity(new Intent(getContext(), Login.class));
            requireActivity().finish();
            return;
        }

        // Setup search bar - NOT focused by default
        searchBar.setFocusable(false);
        searchBar.setFocusableInTouchMode(false);
        searchBar.post(() -> {
            searchBar.clearFocus();
            hideKeyboard();
        });

        // Click on search icon or search bar to activate search
        View.OnClickListener activateSearch = v -> {
            searchBar.setFocusable(true);
            searchBar.setFocusableInTouchMode(true);
            searchBar.requestFocus();
            showKeyboard();
        };

        searchIcon.setOnClickListener(activateSearch);
        searchBar.setOnClickListener(activateSearch);

        // Setup search text watcher
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    searchIcon.setVisibility(View.GONE);
                    showSearchOverlay();
                    performSearch(s.toString());
                } else {
                    searchIcon.setVisibility(View.VISIBLE);
                    hideSearchOverlay();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Setup search bar focus listener
        searchBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && searchBar.getText().toString().isEmpty()) {
                searchIcon.setVisibility(View.VISIBLE);
                hideSearchOverlay();
                searchBar.setFocusable(false);
                searchBar.setFocusableInTouchMode(false);
            }
        });

        // Click on blur overlay to close search
        blurOverlay.setOnClickListener(v -> clearSearch());

        // Initialize lists
        noteList = new ArrayList<>();
        todoList = new ArrayList<>();
        weeklyList = new ArrayList<>();
        starredList = new ArrayList<>();
        combinedList = new ArrayList<>();
        searchResults = new ArrayList<>();

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

        // Adapter for search results
        searchAdapter = new NoteAdapter(searchResults, note -> {
            openItem(note);
            clearSearch();
        }, false, typeDetector);
        searchRecyclerView.setAdapter(searchAdapter);

        // Load data from Firebase
        loadNotes(user);
        loadSchedules(user);
    }

    private void showSearchOverlay() {
        blurOverlay.setVisibility(View.VISIBLE);
        searchContainer.setVisibility(View.VISIBLE);
        mainContent.setAlpha(0.3f);
        Log.d(TAG, "Search overlay shown");
    }

    private void hideSearchOverlay() {
        blurOverlay.setVisibility(View.GONE);
        searchContainer.setVisibility(View.GONE);
        mainContent.setAlpha(1.0f);
        Log.d(TAG, "Search overlay hidden");
    }

    private void clearSearch() {
        searchBar.setText("");
        searchBar.clearFocus();
        hideKeyboard();
        hideSearchOverlay();
        searchIcon.setVisibility(View.VISIBLE);
        searchBar.setFocusable(false);
        searchBar.setFocusableInTouchMode(false);
    }

    private void performSearch(String query) {
        searchResults.clear();
        String lowerQuery = query.toLowerCase();

        // Search in notes
        for (Note note : noteList) {
            if (matchesQuery(note, lowerQuery)) {
                searchResults.add(note);
            }
        }

        // Search in todos
        for (Note todo : todoList) {
            if (matchesQuery(todo, lowerQuery)) {
                searchResults.add(todo);
            }
        }

        // Search in weeklies
        for (Note weekly : weeklyList) {
            if (matchesQuery(weekly, lowerQuery)) {
                searchResults.add(weekly);
            }
        }

        // Sort by timestamp (newest first)
        Collections.sort(searchResults, (n1, n2) ->
                Long.compare(n2.getTimestamp(), n1.getTimestamp()));

        searchAdapter.notifyDataSetChanged();
        Log.d(TAG, "Search results: " + searchResults.size() + " items for query: " + query);
    }

    private boolean matchesQuery(Note note, String query) {
        String title = note.getTitle() != null ? note.getTitle().toLowerCase() : "";
        String content = note.getContent() != null ? note.getContent().toLowerCase() : "";
        return title.contains(query) || content.contains(query);
    }

    private void showKeyboard() {
        if (getContext() == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        if (getContext() == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getView() != null) {
            imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        }
    }

    private void openItem(Note item) {
        if (todoList.contains(item) || weeklyList.contains(item)) {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;

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
                                    intent.putExtra("listId", sourceId);
                                    startActivity(intent);
                                } else if ("weekly".equals(category)) {
                                    Intent intent = new Intent(getContext(), WeeklyActivity.class);
                                    intent.putExtra("planId", sourceId);
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
            Intent intent = new Intent(getContext(), NoteActivity.class);
            intent.putExtra("noteId", item.getId());
            startActivity(intent);
        }
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
                            // ✅ Filter out deleted items in code
                            if (doc.get("deletedAt") != null) {
                                continue;  // Skip deleted items
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

                        // ✅ Sort in code instead of Firestore (API 23 compatible)
                        Collections.sort(noteList, new Comparator<Note>() {
                            @Override
                            public int compare(Note n1, Note n2) {
                                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
                            }
                        });
                    }

                    Log.d(TAG, "✅ Notes loaded: " + noteList.size());
                    notesLoaded = true;
                    updateUI();
                });
    }

    private void loadSchedules(FirebaseUser user) {
        Log.d(TAG, "Loading schedules (todo & weekly)...");

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
                        Log.d(TAG, "Found " + snapshots.size() + " schedule items");

                        for (QueryDocumentSnapshot doc : snapshots) {
                            // ✅ Filter out deleted items in code
                            if (doc.get("deletedAt") != null) {
                                continue;  // Skip deleted items
                            }

                            String category = doc.getString("category");

                            if ("todo".equals(category)) {
                                Note todoNote = createNoteFromSchedule(doc, "To-Do List");
                                todoList.add(todoNote);
                                Log.d(TAG, "  Added todo: " + todoNote.getTitle());

                            } else if ("weekly".equals(category)) {
                                Note weeklyNote = createNoteFromSchedule(doc, "Weekly Plan");
                                weeklyList.add(weeklyNote);
                                Log.d(TAG, "  Added weekly: " + weeklyNote.getTitle());
                            }
                        }

                        // ✅ Sort in code (API 23 compatible)
                        Collections.sort(todoList, new Comparator<Note>() {
                            @Override
                            public int compare(Note n1, Note n2) {
                                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
                            }
                        });

                        Collections.sort(weeklyList, new Comparator<Note>() {
                            @Override
                            public int compare(Note n1, Note n2) {
                                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
                            }
                        });
                    } else {
                        Log.d(TAG, "No schedules found");
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
            Log.d(TAG, "⏳ Waiting for all data... (notes:" + notesLoaded +
                    ", schedules:" + schedulesLoaded + ")");
            return;
        }

        Log.d(TAG, "🎨 All data loaded! Updating UI...");
        Log.d(TAG, "   Notes: " + noteList.size());
        Log.d(TAG, "   Todos: " + todoList.size());
        Log.d(TAG, "   Weeklies: " + weeklyList.size());

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

        combinedList.clear();
        combinedList.addAll(noteList);
        combinedList.addAll(todoList);
        combinedList.addAll(weeklyList);

        Log.d(TAG, "Combined list size: " + combinedList.size());

        Collections.sort(combinedList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
            }
        });

        if (combinedList.isEmpty()) {
            Log.d(TAG, "No items - showing welcome card");
            defaultCardAdapter recentWelcomeAdapter = new defaultCardAdapter(false);
            notesRecyclerView.setAdapter(recentWelcomeAdapter);
        } else {
            Log.d(TAG, "Combined items found: " + combinedList.size());

            NoteAdapter unifiedAdapter = new NoteAdapter(combinedList, note -> {
                openItem(note);
            }, false, typeDetector);

            notesRecyclerView.setAdapter(unifiedAdapter);
            unifiedAdapter.notifyDataSetChanged();

            Log.d(TAG, "✅ UI Updated successfully!");
        }
    }

}