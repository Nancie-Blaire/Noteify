package com.example.testtasksync;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class Bin extends AppCompatActivity {

    private static final String TAG = "BinActivity";
    private static final long DAYS_30_IN_MILLIS = 30L * 24 * 60 * 60 * 1000;

    private RecyclerView binRecyclerView;
    private BinAdapter binAdapter;
    private List<Note> deletedNotes;
    private List<Note> deletedSchedules;
    private List<Note> allDeletedItems;

    private View actionBar;
    private TextView selectedCountText;
    private Button btnRestore;
    private Button btnDelete;
    private TextView emptyStateText;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bin);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        binRecyclerView = findViewById(R.id.binRecyclerView);
        actionBar = findViewById(R.id.actionBar);
        selectedCountText = findViewById(R.id.selectedCountText);
        btnRestore = findViewById(R.id.btnRestore);
        btnDelete = findViewById(R.id.btnDelete);
        emptyStateText = findViewById(R.id.emptyStateText);

        // Setup RecyclerView
        binRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        deletedNotes = new ArrayList<>();
        deletedSchedules = new ArrayList<>();
        allDeletedItems = new ArrayList<>();

        // Setup adapter with selection callback
        binAdapter = new BinAdapter(allDeletedItems, new BinAdapter.OnSelectionChangeListener() {
            @Override
            public void onSelectionChanged(int selectedCount) {
                updateActionBar(selectedCount);
            }
        });
        binRecyclerView.setAdapter(binAdapter);

        // Setup action buttons
        btnRestore.setOnClickListener(v -> restoreSelected());
        btnDelete.setOnClickListener(v -> confirmPermanentDelete());

        // Load deleted items
        loadDeletedItems();

        // Auto-cleanup items older than 30 days
        autoCleanupOldItems();
    }

    private void updateActionBar(int selectedCount) {
        if (selectedCount > 0) {
            actionBar.setVisibility(View.VISIBLE);
            selectedCountText.setText(selectedCount + " selected");
        } else {
            actionBar.setVisibility(View.GONE);
        }
    }

    private void loadDeletedItems() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();

        // Load deleted notes
        db.collection("users")
                .document(userId)
                .collection("notes")
                .whereNotEqualTo("deletedAt", null)
                .orderBy("deletedAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error loading deleted notes", e);
                        return;
                    }

                    deletedNotes.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Note note = new Note(
                                    doc.getId(),
                                    doc.getString("title"),
                                    doc.getString("content")
                            );

                            Timestamp deletedAt = doc.getTimestamp("deletedAt");
                            if (deletedAt != null) {
                                note.setDeletedAt(deletedAt.toDate().getTime());
                            }

                            // Set other properties
                            Boolean isStarred = doc.getBoolean("isStarred");
                            if (isStarred != null && isStarred) {
                                note.setStarred(true);
                            }

                            deletedNotes.add(note);
                        }
                    }
                    updateCombinedList();
                });

        // Load deleted schedules
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .whereNotEqualTo("deletedAt", null)
                .orderBy("deletedAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error loading deleted schedules", e);
                        return;
                    }

                    deletedSchedules.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            String title = doc.getString("title");
                            String description = doc.getString("description");
                            String category = doc.getString("category");

                            Note note = new Note(
                                    doc.getId(),
                                    title != null ? title : ("todo".equals(category) ? "To-Do List" : "Weekly Plan"),
                                    description != null ? description : "No description"
                            );

                            Timestamp deletedAt = doc.getTimestamp("deletedAt");
                            if (deletedAt != null) {
                                note.setDeletedAt(deletedAt.toDate().getTime());
                            }

                            // Store category info
                            note.setCategory(category);

                            Boolean isStarred = doc.getBoolean("isStarred");
                            if (isStarred != null && isStarred) {
                                note.setStarred(true);
                            }

                            deletedSchedules.add(note);
                        }
                    }
                    updateCombinedList();
                });
    }

    private void updateCombinedList() {
        allDeletedItems.clear();
        allDeletedItems.addAll(deletedNotes);
        allDeletedItems.addAll(deletedSchedules);

        // ✅ Sort by deletion date (newest first) - API 23 compatible
        Collections.sort(allDeletedItems, new Comparator<Note>() {
            @Override
            public int compare(Note n1, Note n2) {
                return Long.compare(n2.getDeletedAt(), n1.getDeletedAt());
            }
        });

        binAdapter.notifyDataSetChanged();

        // Show/hide empty state
        if (allDeletedItems.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            binRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            binRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void confirmPermanentDelete() {
        List<Note> selectedItems = binAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) return;

        new AlertDialog.Builder(this)
                .setTitle("Permanently Delete?")
                .setMessage("This will permanently delete " + selectedItems.size() +
                        " item(s). This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> permanentlyDeleteSelected())
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void restoreSelected() {
        List<Note> selectedItems = binAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) return;

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();

        // Separate notes and schedules
        List<String> noteIds = new ArrayList<>();
        List<String> scheduleIds = new ArrayList<>();

        for (Note note : selectedItems) {
            if (note.getCategory() != null) {
                scheduleIds.add(note.getId());
            } else {
                noteIds.add(note.getId());
            }
        }

        // Restore notes directly
        if (!noteIds.isEmpty()) {
            WriteBatch noteBatch = db.batch();
            for (String noteId : noteIds) {
                noteBatch.update(
                        db.collection("users")
                                .document(userId)
                                .collection("notes")
                                .document(noteId),
                        "deletedAt", null
                );
            }
            noteBatch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Restored " + noteIds.size() + " notes");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to restore notes", e);
                    });
        }

        // Restore schedules and their sources
        if (!scheduleIds.isEmpty()) {
            restoreSchedulesWithSource(userId, scheduleIds, selectedItems.size());
        } else if (noteIds.isEmpty()) {
            Toast.makeText(this,
                    selectedItems.size() + " item(s) restored",
                    Toast.LENGTH_SHORT).show();
            binAdapter.clearSelection();
        }
    }

    private void restoreSchedulesWithSource(String userId, List<String> scheduleIds, int totalCount) {
        List<ScheduleSourcePair> pairs = new ArrayList<>();

        for (String scheduleId : scheduleIds) {
            db.collection("users")
                    .document(userId)
                    .collection("schedules")
                    .document(scheduleId)
                    .get()
                    .addOnSuccessListener(scheduleDoc -> {
                        if (scheduleDoc.exists()) {
                            String sourceId = scheduleDoc.getString("sourceId");
                            String category = scheduleDoc.getString("category");

                            pairs.add(new ScheduleSourcePair(scheduleId, sourceId, category));

                            if (pairs.size() == scheduleIds.size()) {
                                performBatchRestoreWithSource(userId, pairs, totalCount);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get schedule", e);
                    });
        }
    }

    // ✅ NEW METHOD: Perform the actual batch restore
    private void performBatchRestoreWithSource(String userId, List<ScheduleSourcePair> pairs, int totalCount) {
        WriteBatch batch = db.batch();

        for (ScheduleSourcePair pair : pairs) {
            // Restore the schedule reference
            batch.update(
                    db.collection("users")
                            .document(userId)
                            .collection("schedules")
                            .document(pair.scheduleId),
                    "deletedAt", null
            );

            // Restore the source document if it exists
            if (pair.sourceId != null && !pair.sourceId.isEmpty()) {
                String sourceCollection = "todo".equals(pair.category) ? "todoLists" : "weeklyPlans";

                batch.update(
                        db.collection("users")
                                .document(userId)
                                .collection(sourceCollection)
                                .document(pair.sourceId),
                        "deletedAt", null
                );

                Log.d(TAG, "Will restore " + sourceCollection + "/" + pair.sourceId);
            }
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            totalCount + " item(s) restored",
                            Toast.LENGTH_SHORT).show();
                    binAdapter.clearSelection();
                    Log.d(TAG, "Successfully restored schedules and sources");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to restore items", e);
                    Toast.makeText(this, "Failed to restore items", Toast.LENGTH_SHORT).show();
                });
    }

    private void permanentlyDeleteSelected() {
        List<Note> selectedItems = binAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) return;

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();

        // Separate items by type
        List<String> scheduleIds = new ArrayList<>();
        List<String> noteIds = new ArrayList<>();

        for (Note note : selectedItems) {
            if (note.getCategory() != null) {
                scheduleIds.add(note.getId());
            } else {
                noteIds.add(note.getId());
            }
        }

        int totalItems = selectedItems.size();
        final int[] completedOperations = {0};

        // Delete notes with subpages
        if (!noteIds.isEmpty()) {
            for (String noteId : noteIds) {
                deleteNoteWithSubpages(userId, noteId, () -> {
                    completedOperations[0]++;
                    if (completedOperations[0] == totalItems) {
                        Toast.makeText(this,
                                totalItems + " item(s) permanently deleted",
                                Toast.LENGTH_SHORT).show();
                        binAdapter.clearSelection();
                    }
                });
            }
        }

        // Delete schedules with source
        if (!scheduleIds.isEmpty()) {
            for (String scheduleId : scheduleIds) {
                deleteScheduleWithSource(userId, scheduleId, () -> {
                    completedOperations[0]++;
                    if (completedOperations[0] == totalItems) {
                        Toast.makeText(this,
                                totalItems + " item(s) permanently deleted",
                                Toast.LENGTH_SHORT).show();
                        binAdapter.clearSelection();
                    }
                });
            }
        }
    }

    // ✅ Delete a single note with all its subpages
    private void deleteNoteWithSubpages(String userId, String noteId, Runnable onComplete) {
        Log.d(TAG, "🗑️ Deleting note: " + noteId);

        // Step 1: Get all subpages - CHANGE "pages" to "subpages"
        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .collection("subpages")  // ✅ CHANGED FROM "pages"
                .get()
                .addOnSuccessListener(pageSnapshots -> {
                    Log.d(TAG, "📄 Found " + pageSnapshots.size() + " subpages for note: " + noteId);

                    WriteBatch batch = db.batch();

                    // Delete all subpages
                    for (QueryDocumentSnapshot pageDoc : pageSnapshots) {
                        batch.delete(pageDoc.getReference());
                        Log.d(TAG, "🗑️ Queued subpage for deletion: " + pageDoc.getId());
                    }

                    // Delete the note itself
                    batch.delete(
                            db.collection("users")
                                    .document(userId)
                                    .collection("notes")
                                    .document(noteId)
                    );

                    // Commit the batch
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "✅ Deleted note " + noteId + " with " + pageSnapshots.size() + " subpages");
                                if (onComplete != null) onComplete.run();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ Failed to delete note with subpages: " + noteId, e);
                                if (onComplete != null) onComplete.run();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to get subpages for note: " + noteId, e);
                    // Still try to delete the note itself
                    db.collection("users")
                            .document(userId)
                            .collection("notes")
                            .document(noteId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "⚠️ Deleted note " + noteId + " (couldn't access subpages)");
                                if (onComplete != null) onComplete.run();
                            })
                            .addOnFailureListener(e2 -> {
                                Log.e(TAG, "❌ Failed to delete note: " + noteId, e2);
                                if (onComplete != null) onComplete.run();
                            });
                });
    }
    // ✅ Delete a single schedule with its source document
    private void deleteScheduleWithSource(String userId, String scheduleId, Runnable onComplete) {
        Log.d(TAG, "🗑️ Deleting schedule: " + scheduleId);

        // Step 1: Get the schedule to find sourceId
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(scheduleId)
                .get()
                .addOnSuccessListener(scheduleDoc -> {
                    if (scheduleDoc.exists()) {
                        String sourceId = scheduleDoc.getString("sourceId");
                        String category = scheduleDoc.getString("category");

                        WriteBatch batch = db.batch();

                        // Delete the schedule
                        batch.delete(scheduleDoc.getReference());

                        // Delete the source document if it exists
                        if (sourceId != null && !sourceId.isEmpty() && category != null) {
                            String sourceCollection = "todo".equals(category) ? "todoLists" : "weeklyPlans";

                            batch.delete(
                                    db.collection("users")
                                            .document(userId)
                                            .collection(sourceCollection)
                                            .document(sourceId)
                            );

                            Log.d(TAG, "🗑️ Queued source for deletion: " + sourceCollection + "/" + sourceId);
                        }

                        batch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✅ Deleted schedule " + scheduleId + " with source");
                                    if (onComplete != null) onComplete.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Failed to delete schedule with source: " + scheduleId, e);
                                    if (onComplete != null) onComplete.run();
                                });
                    } else {
                        Log.e(TAG, "❌ Schedule not found: " + scheduleId);
                        if (onComplete != null) onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to get schedule: " + scheduleId, e);
                    if (onComplete != null) onComplete.run();
                });
    }
private void performBatchDeleteWithSource(String userId, List<ScheduleSourcePair> pairs, int totalCount) {
        WriteBatch batch = db.batch();

        for (ScheduleSourcePair pair : pairs) {
            // Delete the schedule reference
            batch.delete(
                    db.collection("users")
                            .document(userId)
                            .collection("schedules")
                            .document(pair.scheduleId)
            );

            // Delete the source document if it exists
            if (pair.sourceId != null && !pair.sourceId.isEmpty()) {
                String sourceCollection = "todo".equals(pair.category) ? "todoLists" : "weeklyPlans";

                batch.delete(
                        db.collection("users")
                                .document(userId)
                                .collection(sourceCollection)
                                .document(pair.sourceId)
                );

                Log.d(TAG, "Will delete " + sourceCollection + "/" + pair.sourceId);
            }
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            totalCount + " item(s) permanently deleted",
                            Toast.LENGTH_SHORT).show();
                    binAdapter.clearSelection();
                    Log.d(TAG, "Successfully deleted schedules and sources");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete items", e);
                    Toast.makeText(this, "Failed to delete items", Toast.LENGTH_SHORT).show();
                });
    }

    // ✅ NEW HELPER CLASS
    private static class ScheduleSourcePair {
        String scheduleId;
        String sourceId;
        String category;

        ScheduleSourcePair(String scheduleId, String sourceId, String category) {
            this.scheduleId = scheduleId;
            this.sourceId = sourceId;
            this.category = category;
        }
    }

    private void autoCleanupOldItems() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date thirtyDaysAgo = cal.getTime();

        // ✅ Cleanup old deleted notes WITH subpages
        db.collection("users")
                .document(user.getUid())
                .collection("notes")
                .whereLessThan("deletedAt", new Timestamp(thirtyDaysAgo))
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) return;

                    for (QueryDocumentSnapshot noteDoc : snapshots) {
                        String noteId = noteDoc.getId();

                        // Delete subpages first - CHANGE "pages" to "subpages"
                        db.collection("users")
                                .document(user.getUid())
                                .collection("notes")
                                .document(noteId)
                                .collection("subpages")  // ✅ CHANGED FROM "pages"
                                .get()
                                .addOnSuccessListener(pageSnapshots -> {
                                    WriteBatch batch = db.batch();

                                    // Delete all subpages
                                    for (QueryDocumentSnapshot pageDoc : pageSnapshots) {
                                        batch.delete(pageDoc.getReference());
                                    }

                                    // Delete the note itself
                                    batch.delete(noteDoc.getReference());

                                    batch.commit()
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "Auto-cleaned note " + noteId + " with " + pageSnapshots.size() + " subpages");
                                            });
                                });
                    }

                    Log.d(TAG, "Auto-cleaning " + snapshots.size() + " old notes");
                });
    }
}