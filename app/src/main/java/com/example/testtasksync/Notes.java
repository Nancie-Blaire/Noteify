package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class Notes extends AppCompatActivity {

    private static final String TAG = "Notes";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private List<Note> noteList;
    private NoteAdapter adapter;
    private FloatingActionButton fabMain, fabNote, fabTodo, fabWeekly;
    private boolean isFabMenuOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        Log.d(TAG, "onCreate started");

        RecyclerView recyclerView = findViewById(R.id.notesRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        noteList = new ArrayList<>();
        adapter = new NoteAdapter(noteList, note -> {
            Intent intent = new Intent(Notes.this, NoteActivity.class);
            intent.putExtra("noteId", note.getId());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        // Setup FAB functionality
        fabMain = findViewById(R.id.fabMain);
        fabNote = findViewById(R.id.fabNote);
        fabTodo = findViewById(R.id.fabTodo);
        fabWeekly = findViewById(R.id.fabWeekly);

        Log.d(TAG, "FABs found: fabMain=" + (fabMain != null) +
                ", fabNote=" + (fabNote != null) +
                ", fabTodo=" + (fabTodo != null) +
                ", fabWeekly=" + (fabWeekly != null));

        if (fabMain == null || fabNote == null || fabTodo == null || fabWeekly == null) {
            Toast.makeText(this, "ERROR: FABs not found!", Toast.LENGTH_LONG).show();
            return;
        }

        // Ensure FABs start hidden
        fabNote.hide();
        fabTodo.hide();
        fabWeekly.hide();

        // Main FAB toggles the menu
        fabMain.setOnClickListener(v -> {
            Log.d(TAG, "FAB Main clicked! Current state: " + isFabMenuOpen);
            Toast.makeText(this, "FAB clicked!", Toast.LENGTH_SHORT).show();
            toggleFabMenu();
        });

        // Note FAB opens NoteActivity
        fabNote.setOnClickListener(v -> {
            Log.d(TAG, "FAB Note clicked");
            Intent intent = new Intent(Notes.this, NoteActivity.class);
            startActivity(intent);
            toggleFabMenu();
        });

        // Todo FAB (disabled for now)
        fabTodo.setOnClickListener(v -> {
            Log.d(TAG, "FAB Todo clicked");
            Toast.makeText(this, "To-do coming soon!", Toast.LENGTH_SHORT).show();
            toggleFabMenu();
        });

        // Weekly FAB (disabled for now)
        fabWeekly.setOnClickListener(v -> {
            Log.d(TAG, "FAB Weekly clicked");
            Toast.makeText(this, "Weekly planner coming soon!", Toast.LENGTH_SHORT).show();
            toggleFabMenu();
        });

        // Firebase listener code
        if (user != null) {
            db.collection("users")
                    .document(user.getUid())
                    .collection("notes")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING) // ✅ Sort by newest first
                    .addSnapshotListener((snapshots, e) -> {
                        noteList.clear();
                        if (snapshots != null) {
                            for (QueryDocumentSnapshot doc : snapshots) {
                                Note note = new Note(
                                        doc.getId(),
                                        doc.getString("title"),
                                        doc.getString("content")
                                );
                                noteList.add(note);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    });
        }
    }

    private void toggleFabMenu() {
        Log.d(TAG, "toggleFabMenu called. isFabMenuOpen: " + isFabMenuOpen);

        if (isFabMenuOpen) {
            // Close menu
            Log.d(TAG, "Closing FAB menu");
            fabNote.hide();
            fabTodo.hide();
            fabWeekly.hide();
        } else {
            // Open menu
            Log.d(TAG, "Opening FAB menu");
            fabNote.show();
            fabTodo.show();
            fabWeekly.show();
        }
        isFabMenuOpen = !isFabMenuOpen;

        Log.d(TAG, "FAB menu state after toggle: " + isFabMenuOpen);
    }
}