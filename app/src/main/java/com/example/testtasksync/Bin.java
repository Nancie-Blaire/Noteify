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

        // Sort by deletion date (newest first)
        allDeletedItems.sort((n1, n2) ->
                Long.compare(n2.getDeletedAt(), n1.getDeletedAt()));

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

    private void restoreSelected() {
        List<Note> selectedItems = binAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) return;

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        WriteBatch batch = db.batch();

        for (Note note : selectedItems) {
            // Determine collection
            String collection = (note.getCategory() != null) ? "schedules" : "notes";

            // Remove deletedAt field to restore
            batch.update(
                    db.collection("users")
                            .document(user.getUid())
                            .collection(collection)
                            .document(note.getId()),
                    "deletedAt", null
            );
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            selectedItems.size() + " item(s) restored",
                            Toast.LENGTH_SHORT).show();
                    binAdapter.clearSelection();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to restore items", e);
                    Toast.makeText(this, "Failed to restore items", Toast.LENGTH_SHORT).show();
                });
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

    private void permanentlyDeleteSelected() {
        List<Note> selectedItems = binAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) return;

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        WriteBatch batch = db.batch();

        for (Note note : selectedItems) {
            String collection = (note.getCategory() != null) ? "schedules" : "notes";

            batch.delete(
                    db.collection("users")
                            .document(user.getUid())
                            .collection(collection)
                            .document(note.getId())
            );
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            selectedItems.size() + " item(s) permanently deleted",
                            Toast.LENGTH_SHORT).show();
                    binAdapter.clearSelection();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete items", e);
                    Toast.makeText(this, "Failed to delete items", Toast.LENGTH_SHORT).show();
                });
    }

    private void autoCleanupOldItems() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date thirtyDaysAgo = cal.getTime();

        // Cleanup old deleted notes
        db.collection("users")
                .document(user.getUid())
                .collection("notes")
                .whereLessThan("deletedAt", new Timestamp(thirtyDaysAgo))
                .get()
                .addOnSuccessListener(snapshots -> {
                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        batch.delete(doc.getReference());
                    }
                    if (!snapshots.isEmpty()) {
                        batch.commit();
                        Log.d(TAG, "Auto-cleaned " + snapshots.size() + " old notes");
                    }
                });

        // Cleanup old deleted schedules
        db.collection("users")
                .document(user.getUid())
                .collection("schedules")
                .whereLessThan("deletedAt", new Timestamp(thirtyDaysAgo))
                .get()
                .addOnSuccessListener(snapshots -> {
                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        batch.delete(doc.getReference());
                    }
                    if (!snapshots.isEmpty()) {
                        batch.commit();
                        Log.d(TAG, "Auto-cleaned " + snapshots.size() + " old schedules");
                    }
                });
    }
}