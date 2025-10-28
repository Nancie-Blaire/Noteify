package com.example.testtasksync;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class NoteActivity extends AppCompatActivity {

    private EditText noteTitle, noteContent;
    private Button saveBtn;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String noteId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);

        noteTitle = findViewById(R.id.noteTitle);
        noteContent = findViewById(R.id.noteContent);
        saveBtn = findViewById(R.id.saveBtn);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        noteId = getIntent().getStringExtra("noteId");

        saveBtn.setOnClickListener(v -> saveNote());

        if (noteId != null) {
            loadNote();
        }
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

    private void saveNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String title = noteTitle.getText().toString().trim();
        String content = noteContent.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("title", title);
        noteData.put("content", content);

        if (noteId == null) {
            // New note
            db.collection("users").document(user.getUid())
                    .collection("notes")
                    .add(noteData)
                    .addOnSuccessListener(doc -> {
                        Toast.makeText(this, "Note added!", Toast.LENGTH_SHORT).show();
                        finish(); // ✅ Moved inside success callback
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        } else {
            // Update existing note
            db.collection("users").document(user.getUid())
                    .collection("notes")
                    .document(noteId)
                    .set(noteData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Note updated!", Toast.LENGTH_SHORT).show();
                        finish(); // ✅ Added finish here too
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        }
    }
}