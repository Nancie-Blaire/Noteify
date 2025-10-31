package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

public class NoteActivity extends AppCompatActivity {

    private EditText noteTitle, noteContent;
    private ImageView checkBtn, backBtn, addSubpageBtn;
    private LinearLayout addSubpageContainer;
    private RecyclerView subpagesRecyclerView;
    private SubpageAdapter subpageAdapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String noteId = null;
    private boolean hasSubpages = false; // Track if note has subpages

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);

        // Initialize views
        noteTitle = findViewById(R.id.noteTitle);
        noteContent = findViewById(R.id.noteContent);
        checkBtn = findViewById(R.id.checkBtn);
        backBtn = findViewById(R.id.backBtn);
        addSubpageBtn = findViewById(R.id.addSubpageBtn);
        addSubpageContainer = findViewById(R.id.addSubpageContainer);
        subpagesRecyclerView = findViewById(R.id.subpagesRecyclerView);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        noteId = getIntent().getStringExtra("noteId");

        // If new note, generate noteId in advance
        if (noteId == null) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                noteId = db.collection("users").document(user.getUid())
                        .collection("notes")
                        .document().getId();
            }
        }

        // Setup RecyclerView
        setupRecyclerView();

        // Button listeners - both save and exit
        checkBtn.setOnClickListener(v -> saveAndExit());
        backBtn.setOnClickListener(v -> saveAndExit());
        addSubpageBtn.setOnClickListener(v -> openSubpage());

        if (noteId != null) {
            loadNote();
            loadSubpages();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload subpages when returning to this activity
        if (noteId != null) {
            loadSubpages();
        }
    }

    private void setupRecyclerView() {
        subpageAdapter = new SubpageAdapter(this, noteId);
        subpagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        subpagesRecyclerView.setAdapter(subpageAdapter);
    }

    private void loadNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes")
                .document(noteId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        noteTitle.setText(doc.getString("title"));
                        noteContent.setText(doc.getString("content"));
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error loading note", Toast.LENGTH_SHORT).show()
                );
    }

    private void loadSubpages() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages")
                .orderBy("timestamp")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading subpages", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null) {
                        List<Subpage> subpages = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            Subpage subpage = doc.toObject(Subpage.class);
                            subpage.setId(doc.getId());
                            subpages.add(subpage);
                        }

                        // Update adapter
                        subpageAdapter.setSubpages(subpages);
                        subpageAdapter.setNoteId(noteId);

                        // Track if note has subpages
                        hasSubpages = !subpages.isEmpty();

                        // Show/hide RecyclerView based on whether there are subpages
                        if (subpages.isEmpty()) {
                            subpagesRecyclerView.setVisibility(View.GONE);
                        } else {
                            subpagesRecyclerView.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private void saveAndExit() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        String title = noteTitle.getText().toString().trim();
        String content = noteContent.getText().toString().trim();

        // If both title and content are empty AND no subpages, just exit without saving
        if (title.isEmpty() && content.isEmpty() && !hasSubpages) {
            finish();
            return;
        }

        // Allow empty title OR empty content (basta may laman kahit isa OR may subpages)
        Map<String, Object> noteData = new HashMap<>();
        noteData.put("title", title);
        noteData.put("content", content);
        noteData.put("timestamp", System.currentTimeMillis());

        // Exit immediately, don't wait for Firebase
        finish();

        // Save in background using the pre-generated noteId
        db.collection("users").document(user.getUid())
                .collection("notes")
                .document(noteId)
                .set(noteData)
                .addOnSuccessListener(aVoid -> {
                    // Note saved successfully (pero user na naka-exit na)
                })
                .addOnFailureListener(e -> {
                    // Handled by Firebase offline persistence
                });
    }

    private void openSubpage() {
        // Auto-save current changes before opening subpage (if may content OR may subpages)
        saveCurrentNote();

        // Open subpage directly using the pre-generated noteId
        Intent intent = new Intent(NoteActivity.this, SubpageActivity.class);
        intent.putExtra("noteId", noteId);
        startActivity(intent);
    }

    private void saveCurrentNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String title = noteTitle.getText().toString().trim();
        String content = noteContent.getText().toString().trim();

        // Save even if both are empty (because user is creating subpages)
        Map<String, Object> noteData = new HashMap<>();
        noteData.put("title", title);
        noteData.put("content", content);
        noteData.put("timestamp", System.currentTimeMillis());

        // Save silently in background using pre-generated noteId
        db.collection("users").document(user.getUid())
                .collection("notes")
                .document(noteId)
                .set(noteData);
    }
}