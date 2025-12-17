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
import android.widget.Button;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private NoteAdapter recentAdapter;
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

    // Multi-select UI components
    private View multiSelectActionBar;
    private TextView selectedCountText;
    private Button btnCancelSelection;
    private Button btnDeleteSelected;

    // Multi-select state
    private boolean isMultiSelectMode = false;
    private Set<String> selectedItemIds = new HashSet<>();

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

        // Initialize multi-select views
        multiSelectActionBar = view.findViewById(R.id.multiSelectActionBar);
        selectedCountText = view.findViewById(R.id.selectedCountText);
        btnCancelSelection = view.findViewById(R.id.btnCancelSelection);
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected);

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

        // Setup search bar - hide icon when focused
        searchBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                searchIcon.setVisibility(View.GONE);
            } else if (searchBar.getText().toString().isEmpty()) {
                searchIcon.setVisibility(View.VISIBLE);
                hideSearchOverlay();
            }
        });

        // Click on search icon to focus
        searchIcon.setOnClickListener(v -> {
            searchBar.requestFocus();
            showKeyboard();
        });

        // Setup search text watcher
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    showSearchOverlay();
                    performSearch(s.toString());
                } else {
                    hideSearchOverlay();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Click on blur overlay to close search
        blurOverlay.setOnClickListener(v -> clearSearch());

        // Setup multi-select action buttons
        btnCancelSelection.setOnClickListener(v -> exitMultiSelectMode());
        btnDeleteSelected.setOnClickListener(v -> deleteSelectedItems());

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
            handleNoteClick(note);
        }, true, typeDetector);
        starredAdapter.setMultiSelectListener(new NoteAdapter.MultiSelectListener() {
            @Override
            public void onLongPress(Note note) {
                enterMultiSelectMode();
                toggleSelection(note);
            }

            @Override
            public void onItemClick(Note note) {
                if (isMultiSelectMode) {
                    toggleSelection(note);
                } else {
                    openItem(note);
                }
            }

            @Override
            public boolean isMultiSelectMode() {
                return isMultiSelectMode;
            }

            @Override
            public boolean isSelected(Note note) {
                return selectedItemIds.contains(note.getId());
            }
        });

        // Adapter for search results
        searchAdapter = new NoteAdapter(searchResults, note -> {
            openItem(note);
            clearSearch();
        }, false, typeDetector);
        searchRecyclerView.setAdapter(searchAdapter);

        // Load data from Firebase
        loadNotes(user);
        loadSchedules(user);

        // Update greeting card
        updateGreetingCard(view, user);
    }

    // Multi-select methods
    private void enterMultiSelectMode() {
        isMultiSelectMode = true;
        multiSelectActionBar.setVisibility(View.VISIBLE);
        updateMultiSelectUI();

        // Notify adapters
        if (starredAdapter != null) starredAdapter.notifyDataSetChanged();
        if (recentAdapter != null) recentAdapter.notifyDataSetChanged();
    }

    private void exitMultiSelectMode() {
        isMultiSelectMode = false;
        selectedItemIds.clear();
        multiSelectActionBar.setVisibility(View.GONE);

        // Notify adapters
        if (starredAdapter != null) starredAdapter.notifyDataSetChanged();
        if (recentAdapter != null) recentAdapter.notifyDataSetChanged();
    }

    private void toggleSelection(Note note) {
        if (selectedItemIds.contains(note.getId())) {
            selectedItemIds.remove(note.getId());
        } else {
            selectedItemIds.add(note.getId());
        }

        updateMultiSelectUI();

        // Notify adapters
        if (starredAdapter != null) starredAdapter.notifyDataSetChanged();
        if (recentAdapter != null) recentAdapter.notifyDataSetChanged();

        if (selectedItemIds.isEmpty()) {
            exitMultiSelectMode();
        }
    }

    private void updateMultiSelectUI() {
        selectedCountText.setText(selectedItemIds.size() + " selected");
    }

    private void deleteSelectedItems() {
        if (selectedItemIds.isEmpty()) return;

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Delete " + selectedItemIds.size() + " items?")
                .setMessage("These items will be moved to the bin and can be restored within 30 days.")
                .setPositiveButton("Move to Bin", (dialog, which) -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) return;

                    for (String itemId : selectedItemIds) {
                        Note note = findNoteById(itemId);
                        if (note != null) {
                            String itemType = typeDetector.getItemType(note);
                            if (itemType.equals("todo") || itemType.equals("weekly")) {
                                softDeleteSchedule(user.getUid(), itemId, itemType);
                            } else {
                                softDeleteNote(user.getUid(), itemId);
                            }
                        }
                    }

                    Toast.makeText(getContext(), "✓ Items moved to bin", Toast.LENGTH_SHORT).show();
                    exitMultiSelectMode();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Note findNoteById(String id) {
        for (Note note : noteList) {
            if (note.getId().equals(id)) return note;
        }
        for (Note todo : todoList) {
            if (todo.getId().equals(id)) return todo;
        }
        for (Note weekly : weeklyList) {
            if (weekly.getId().equals(id)) return weekly;
        }
        return null;
    }

    private void softDeleteNote(String userId, String noteId) {
        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .update("deletedAt", com.google.firebase.Timestamp.now())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Note soft deleted: " + noteId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to soft delete note", e);
                });
    }

    private void softDeleteSchedule(String userId, String scheduleId, String itemType) {
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(scheduleId)
                .get()
                .addOnSuccessListener(scheduleDoc -> {
                    if (scheduleDoc.exists()) {
                        String sourceId = scheduleDoc.getString("sourceId");
                        String category = scheduleDoc.getString("category");

                        com.google.firebase.firestore.WriteBatch batch = db.batch();

                        batch.update(
                                db.collection("users")
                                        .document(userId)
                                        .collection("schedules")
                                        .document(scheduleId),
                                "deletedAt", com.google.firebase.Timestamp.now()
                        );

                        if (sourceId != null && !sourceId.isEmpty() && category != null) {
                            String sourceCollection = "todo".equals(category) ? "todoLists" : "weeklyPlans";

                            batch.update(
                                    db.collection("users")
                                            .document(userId)
                                            .collection(sourceCollection)
                                            .document(sourceId),
                                    "deletedAt", com.google.firebase.Timestamp.now()
                            );
                        }

                        batch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Schedule soft deleted: " + scheduleId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to soft delete schedule", e);
                                });
                    }
                });
    }

    private void handleNoteClick(Note note) {
        if (isMultiSelectMode) {
            toggleSelection(note);
        } else {
            openItem(note);
        }
    }

    private void updateGreetingCard(View view, FirebaseUser user) {
        TextView greetingText = view.findViewById(R.id.greetingText);
        TextView userNameText = view.findViewById(R.id.userNameText);
        TextView timeText = view.findViewById(R.id.timeText);
        TextView minuteText = view.findViewById(R.id.minuteText);
        TextView amPmText = view.findViewById(R.id.amPmText);

        // Set user name
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            userNameText.setText(displayName + "!");
        } else {
            userNameText.setText("User!");
        }

        // Update time
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        // ... rest of the code
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

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popupWindow.showAsDropDown(anchor, anchor.getWidth() - popupView.getMeasuredWidth(), 8);
    }

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

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popupWindow.showAsDropDown(anchor, anchor.getWidth() - popupView.getMeasuredWidth(), 8);
    }

    private void sortPriosByNewest() {
        Collections.sort(starredList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
            }
        });
        starredAdapter.notifyDataSetChanged();
    }

    private void sortPriosByOldest() {
        Collections.sort(starredList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n1.getTimestamp(), n2.getTimestamp());
            }
        });
        starredAdapter.notifyDataSetChanged();
    }

    private void sortRecentsByNewest() {
        Collections.sort(combinedList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
            }
        });

        if (recentAdapter != null) {
            recentAdapter.notifyDataSetChanged();
        }
    }

    private void sortRecentsByOldest() {
        Collections.sort(combinedList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n1.getTimestamp(), n2.getTimestamp());
            }
        });

        if (recentAdapter != null) {
            recentAdapter.notifyDataSetChanged();
        }
    }

    private void updatePrioLayout() {
        if (starredList.isEmpty()) {
            return;
        }

        if (prioLayoutMode == LayoutMode.GRID) {
            prioNotesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
            starredAdapter = new NoteAdapter(starredList, note -> handleNoteClick(note), true, typeDetector);
        } else {
            prioNotesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            starredAdapter = new NoteAdapter(starredList, note -> handleNoteClick(note), false, typeDetector);
        }

        setupAdapterMultiSelect(starredAdapter);
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

        recentAdapter = new NoteAdapter(combinedList, note -> handleNoteClick(note),
                recentsLayoutMode == LayoutMode.GRID, typeDetector);
        setupAdapterMultiSelect(recentAdapter);
        notesRecyclerView.setAdapter(recentAdapter);
        recentAdapter.notifyDataSetChanged();
    }

    private void setupAdapterMultiSelect(NoteAdapter adapter) {
        adapter.setMultiSelectListener(new NoteAdapter.MultiSelectListener() {
            @Override
            public void onLongPress(Note note) {
                enterMultiSelectMode();
                toggleSelection(note);
            }

            @Override
            public void onItemClick(Note note) {
                if (isMultiSelectMode) {
                    toggleSelection(note);
                } else {
                    openItem(note);
                }
            }

            @Override
            public boolean isMultiSelectMode() {
                return isMultiSelectMode;
            }

            @Override
            public boolean isSelected(Note note) {
                return selectedItemIds.contains(note.getId());
            }
        });
    }

    private void showSearchOverlay() {
        blurOverlay.setVisibility(View.VISIBLE);
        searchContainer.setVisibility(View.VISIBLE);
        mainContent.setAlpha(0.3f);
    }

    private void hideSearchOverlay() {
        blurOverlay.setVisibility(View.GONE);
        searchContainer.setVisibility(View.GONE);
        mainContent.setAlpha(1.0f);
    }

    private void clearSearch() {
        searchBar.setText("");
        searchBar.clearFocus();
        hideKeyboard();
        hideSearchOverlay();
        searchIcon.setVisibility(View.VISIBLE);
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
                            note.setStarred(isStarred != null && isStarred);

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
                        schedulesLoaded = true;
                        updateUI();
                        return;
                    }

                    todoList.clear();
                    weeklyList.clear();

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (QueryDocumentSnapshot doc : snapshots) {
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
                    }

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
        note.setStarred(isStarred != null && isStarred);

        Boolean isLocked = doc.getBoolean("isLocked");
        note.setLocked(isLocked != null && isLocked);

        return note;
    }

    private void updateUI() {
        if (!notesLoaded || !schedulesLoaded) {
            return;
        }

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
            defaultCardAdapter prioWelcomeAdapter = new defaultCardAdapter(true);
            prioNotesRecyclerView.setAdapter(prioWelcomeAdapter);
        } else {
            updatePrioLayout();
        }

        combinedList.clear();
        combinedList.addAll(noteList);
        combinedList.addAll(todoList);
        combinedList.addAll(weeklyList);

        Collections.sort(combinedList, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getTimestamp(), n1.getTimestamp());
            }
        });

        if (combinedList.isEmpty()) {
            defaultCardAdapter recentWelcomeAdapter = new defaultCardAdapter(false);
            notesRecyclerView.setAdapter(recentWelcomeAdapter);
        } else {
            updateRecentsLayout();
        }
    }
}