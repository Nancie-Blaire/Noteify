package com.example.testtasksync;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
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
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.content.Context.INPUT_METHOD_SERVICE;

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
    private ImageButton prioToolbar;
    private ImageButton recentsToolbar;
    private View blurOverlay;
    private View searchContainer;
    private View mainContent;

    private NoteAdapter.ItemTypeDetector typeDetector;

    private boolean notesLoaded = false;
    private boolean schedulesLoaded = false;

    // Layout modes
    private enum LayoutMode { GRID, LIST }
    private LayoutMode prioLayoutMode = LayoutMode.GRID;
    private LayoutMode recentsLayoutMode = LayoutMode.LIST;

    // View
    private static final String PREFS_NAME = "NotesPreferences";
    private static final String PREF_PRIO_LAYOUT = "prioLayoutMode";
    private static final String PREF_RECENTS_LAYOUT = "recentsLayoutMode";

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
        prioToolbar = view.findViewById(R.id.prioToolbar);
        recentsToolbar = view.findViewById(R.id.recentsToolbar);
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

        // Calling methods
        loadLayoutPreferences();

        FirebaseUser user = auth.getCurrentUser();

        // Check if user is logged in
        if (user == null) {
            startActivity(new Intent(getContext(), Login.class));
            requireActivity().finish();
            return;
        }

        // Setup toolbar click listeners
        prioToolbar.setOnClickListener(v -> showPrioToolbarMenu(v));
        recentsToolbar.setOnClickListener(v -> showRecentsToolbarMenu(v));

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

        // Initialize time card views
        TextView timeText = view.findViewById(R.id.timeText);
        TextView amPmText = view.findViewById(R.id.amPmText);
        TextView dateText = view.findViewById(R.id.dateText);

        // Update time and date
        updateTimeAndDate(timeText, amPmText, dateText);
    }

    // Add this method to load saved preferences
    private void loadLayoutPreferences() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load prio layout mode
        String savedPrioLayout = prefs.getString(PREF_PRIO_LAYOUT, "GRID");
        prioLayoutMode = savedPrioLayout.equals("LIST") ? LayoutMode.LIST : LayoutMode.GRID;

        // Load recents layout mode
        String savedRecentsLayout = prefs.getString(PREF_RECENTS_LAYOUT, "LIST");
        recentsLayoutMode = savedRecentsLayout.equals("GRID") ? LayoutMode.GRID : LayoutMode.LIST;

        Log.d(TAG, "Loaded preferences - Prio: " + prioLayoutMode + ", Recents: " + recentsLayoutMode);
    }

    // Add this method to save preferences
    private void saveLayoutPreference(String key, LayoutMode mode) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, mode.name());
        editor.apply();
        Log.d(TAG, "Saved preference - " + key + ": " + mode.name());
    }

    private void showPrioToolbarMenu(View anchor) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View popupView = inflater.inflate(R.layout.menu_toolbar_layout, null);

        final PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setElevation(8);

        View gridView = popupView.findViewById(R.id.menu_grid_view);
        View listView = popupView.findViewById(R.id.menu_list_view);
        View sortNewest = popupView.findViewById(R.id.menu_sort_newest);
        View sortOldest = popupView.findViewById(R.id.menu_sort_oldest);

        gridView.setOnClickListener(v -> {
            prioLayoutMode = LayoutMode.GRID;
            saveLayoutPreference(PREF_PRIO_LAYOUT, prioLayoutMode);
            updatePrioLayout();
            Toast.makeText(getContext(), "Grid view", Toast.LENGTH_SHORT).show();
            popupWindow.dismiss();
        });

        listView.setOnClickListener(v -> {
            prioLayoutMode = LayoutMode.LIST;
            saveLayoutPreference(PREF_PRIO_LAYOUT, prioLayoutMode);
            updatePrioLayout();
            //Toast.makeText(getContext(), "List view", Toast.LENGTH_SHORT).show();
            popupWindow.dismiss();
        });

        sortNewest.setOnClickListener(v -> {
            sortPriosByNewest();
            //Toast.makeText(getContext(), "Sorted by newest", Toast.LENGTH_SHORT).show();
            popupWindow.dismiss();
        });

        sortOldest.setOnClickListener(v -> {
            sortPriosByOldest();
            //Toast.makeText(getContext(), "Sorted by oldest", Toast.LENGTH_SHORT).show();
            popupWindow.dismiss();
        });

        // Measure the popup view first
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        // Position popup aligned to right edge of anchor, slightly below
        popupWindow.showAsDropDown(anchor, anchor.getWidth() - popupView.getMeasuredWidth(), 8);
    }

    // Replace the showRecentsToolbarMenu method with this:
    private void showRecentsToolbarMenu(View anchor) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View popupView = inflater.inflate(R.layout.menu_toolbar_layout, null);

        final PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setElevation(8);

        View gridView = popupView.findViewById(R.id.menu_grid_view);
        View listView = popupView.findViewById(R.id.menu_list_view);
        View sortNewest = popupView.findViewById(R.id.menu_sort_newest);
        View sortOldest = popupView.findViewById(R.id.menu_sort_oldest);

        gridView.setOnClickListener(v -> {
            recentsLayoutMode = LayoutMode.GRID;
            saveLayoutPreference(PREF_RECENTS_LAYOUT, recentsLayoutMode);
            updateRecentsLayout();
           // Toast.makeText(getContext(), "Grid view", Toast.LENGTH_SHORT).show();
            popupWindow.dismiss();
        });

        listView.setOnClickListener(v -> {
            recentsLayoutMode = LayoutMode.LIST;
            saveLayoutPreference(PREF_RECENTS_LAYOUT, recentsLayoutMode);
            updateRecentsLayout();
           // Toast.makeText(getContext(), "List view", Toast.LENGTH_SHORT).show();
            popupWindow.dismiss();
        });

        sortNewest.setOnClickListener(v -> {
            sortRecentsByNewest();
           // Toast.makeText(getContext(), "Sorted by newest", Toast.LENGTH_SHORT).show();
            popupWindow.dismiss();
        });

        sortOldest.setOnClickListener(v -> {
            sortRecentsByOldest();
           // Toast.makeText(getContext(), "Sorted by oldest", Toast.LENGTH_SHORT).show();
            popupWindow.dismiss();
        });

        // Measure the popup view first
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        // Position popup aligned to right edge of anchor, slightly below
        popupWindow.showAsDropDown(anchor, anchor.getWidth() - popupView.getMeasuredWidth(), 8);
    }

    private void sortPriosByNewest() {
        Collections.sort(starredList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp()); // Newest first
            }
        });
        starredAdapter.notifyDataSetChanged();
        Log.d(TAG, "Prios sorted by newest");
    }
    private void sortPriosByOldest() {
        Collections.sort(starredList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n1.getTimestamp(), n2.getTimestamp()); // Oldest first
            }
        });
        starredAdapter.notifyDataSetChanged();
        Log.d(TAG, "Prios sorted by oldest");
    }

    // ✅ NEW: Sort Recents by Newest (Descending - newest first)
    private void sortRecentsByNewest() {
        Collections.sort(combinedList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp()); // Newest first
            }
        });

        NoteAdapter currentAdapter = (NoteAdapter) notesRecyclerView.getAdapter();
        if (currentAdapter != null) {
            currentAdapter.notifyDataSetChanged();
        }
        Log.d(TAG, "Recents sorted by newest");
    }

    // ✅ NEW: Sort Recents by Oldest (Ascending - oldest first)
    private void sortRecentsByOldest() {
        Collections.sort(combinedList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n1.getTimestamp(), n2.getTimestamp()); // Oldest first
            }
        });

        NoteAdapter currentAdapter = (NoteAdapter) notesRecyclerView.getAdapter();
        if (currentAdapter != null) {
            currentAdapter.notifyDataSetChanged();
        }
        Log.d(TAG, "Recents sorted by oldest");
    }

    private void updatePrioLayout() {
        if (starredList.isEmpty()) {
            return;
        }

        if (prioLayoutMode == LayoutMode.GRID) {
            prioNotesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
            starredAdapter = new NoteAdapter(starredList, note -> openItem(note), true, typeDetector);
        } else {
            prioNotesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            starredAdapter = new NoteAdapter(starredList, note -> openItem(note), false, typeDetector);
        }

        prioNotesRecyclerView.setAdapter(starredAdapter);
        starredAdapter.notifyDataSetChanged();
    }

    private void updateRecentsLayout() {
        if (combinedList.isEmpty()) {
            return;
        }

        if (recentsLayoutMode == LayoutMode.GRID) {
            notesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        } else {
            notesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        }

        NoteAdapter unifiedAdapter = new NoteAdapter(combinedList, note -> openItem(note),
                recentsLayoutMode == LayoutMode.GRID, typeDetector);
        notesRecyclerView.setAdapter(unifiedAdapter);
        unifiedAdapter.notifyDataSetChanged();
    }

    private void sortPriosByDate() {
        Collections.sort(starredList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
            }
        });
        starredAdapter.notifyDataSetChanged();
    }

    private void sortRecentsByDate() {
        Collections.sort(combinedList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
            }
        });

        NoteAdapter currentAdapter = (NoteAdapter) notesRecyclerView.getAdapter();
        if (currentAdapter != null) {
            currentAdapter.notifyDataSetChanged();
        }
    }

    private void updateTimeAndDate(TextView timeText, TextView amPmText, TextView dateText) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();

        // Get time format preference
        String timeFormat = Settings.getTimeFormat(requireContext());

        // Format time based on preference
        if (timeFormat.equals("military")) {
            // Military time (24-hour format)
            int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = calendar.get(java.util.Calendar.MINUTE);
            String time = String.format("%02d:%02d", hour, minute);
            timeText.setText(time);
            amPmText.setVisibility(View.GONE); // Hide AM/PM in military time
        } else {
            // Civilian time (12-hour format)
            int hour = calendar.get(java.util.Calendar.HOUR);
            if (hour == 0) hour = 12;
            int minute = calendar.get(java.util.Calendar.MINUTE);
            String time = String.format("%d:%02d", hour, minute);
            timeText.setText(time);

            // Format AM/PM
            int hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY);
            String amPm = hourOfDay < 12 ? "A.M." : "P.M.";
            amPmText.setText(amPm);
            amPmText.setVisibility(View.VISIBLE); // Show AM/PM in civilian time
        }

        // Format date
        String[] months = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
                "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
        int month = calendar.get(java.util.Calendar.MONTH);
        int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);
        String date = months[month] + " " + day;
        dateText.setText(date);
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

        for (Note note : noteList) {
            if (matchesQuery(note, lowerQuery)) {
                searchResults.add(note);
            }
        }

        for (Note todo : todoList) {
            if (matchesQuery(todo, lowerQuery)) {
                searchResults.add(todo);
            }
        }

        for (Note weekly : weeklyList) {
            if (matchesQuery(weekly, lowerQuery)) {
                searchResults.add(weekly);
            }
        }

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

// Replace the loadNotes method in Notes.java - BALIK SA ORIGINAL NA MAY MANUAL CHECK

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
                            // ✅ MANUAL CHECK: Skip deleted items
                            if (doc.get("deletedAt") != null) {
                                Log.d(TAG, "Skipping deleted note: " + doc.getId());
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
                                Log.w(TAG, "Timestamp not in Firestore format, using fallback", ex);
                            }

                            Boolean isStarred = doc.getBoolean("isStarred");
                            note.setStarred(isStarred != null && isStarred);

                            Log.d(TAG, "📄 Loaded note: " + note.getTitle() + " | starred=" + note.isStarred());

                            Boolean isLocked = doc.getBoolean("isLocked");
                            note.setLocked(isLocked != null && isLocked);

                            noteList.add(note);
                        }

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

// Replace the loadSchedules method in Notes.java - BALIK SA ORIGINAL NA MAY MANUAL CHECK

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
                            // ✅ MANUAL CHECK: Skip deleted items
                            if (doc.get("deletedAt") != null) {
                                Log.d(TAG, "Skipping deleted schedule: " + doc.getId());
                                continue;
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

        // ✅ CRITICAL FIX: Always explicitly set starred state from Firebase
        Boolean isStarred = doc.getBoolean("isStarred");
        note.setStarred(isStarred != null && isStarred);

        Log.d(TAG, "📅 Loaded schedule: " + note.getTitle() + " | starred=" + note.isStarred());

        Boolean isLocked = doc.getBoolean("isLocked");
        note.setLocked(isLocked != null && isLocked);

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
            updatePrioLayout();
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
            updateRecentsLayout();
            Log.d(TAG, "✅ UI Updated successfully!");
        }
    }//
}