package com.example.testtasksync;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SubpageActivity extends AppCompatActivity {

    private EditText subpageTitle, subpageContent;
    private ImageView checkBtn;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String noteId;
    private String subpageId = null;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subpage);

        // Initialize views
        subpageTitle = findViewById(R.id.subpageTitle);
        subpageContent = findViewById(R.id.subpageContent);
        checkBtn = findViewById(R.id.checkBtn);
        rootView = findViewById(R.id.subpageLayout);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Get parent note ID
        noteId = getIntent().getStringExtra("noteId");
        subpageId = getIntent().getStringExtra("subpageId");

        if (noteId == null) {
            Toast.makeText(this, "Error: No parent note found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Button listeners - both save and exit
        checkBtn.setOnClickListener(v -> saveAndExit());

        if (subpageId != null) {
            loadSubpage();
        }
    }

    private void loadSubpage() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        subpageTitle.setText(doc.getString("title"));
                        subpageContent.setText(doc.getString("content"));
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error loading subpage", Toast.LENGTH_SHORT).show()
                );
    }

    private void saveAndExit() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        String title = subpageTitle.getText().toString().trim();
        String content = subpageContent.getText().toString().trim();

        // If both title and content are empty, just exit without saving
        if (title.isEmpty() && content.isEmpty()) {
            finish();
            return;
        }

        // Allow empty title OR empty content (basta may laman kahit isa)
        Map<String, Object> subpageData = new HashMap<>();
        subpageData.put("title", title);
        subpageData.put("content", content);
        subpageData.put("timestamp", System.currentTimeMillis());

        // Exit immediately, don't wait for Firebase
        finish();

        // Save in background (works offline too)
        if (subpageId == null) {
            // New subpage
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("subpages")
                    .add(subpageData)
                    .addOnSuccessListener(doc -> {
                        // Subpage saved successfully (pero user na naka-exit na)
                    })
                    .addOnFailureListener(e -> {
                        // Handled by Firebase offline persistence
                    });
        } else {
            // Update existing subpage
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("subpages").document(subpageId)
                    .set(subpageData)
                    .addOnSuccessListener(aVoid -> {
                        // Subpage updated successfully (pero user na naka-exit na)
                    })
                    .addOnFailureListener(e -> {
                        // Handled by Firebase offline persistence
                    });
        }
    }
}