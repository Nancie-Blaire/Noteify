package com.example.testtasksync;

import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subpage);

        // Initialize views
        subpageTitle = findViewById(R.id.subpageTitle);
        subpageContent = findViewById(R.id.subpageContent);
        checkBtn = findViewById(R.id.checkBtn);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Get parent note ID and subpage ID
        noteId = getIntent().getStringExtra("noteId");
        subpageId = getIntent().getStringExtra("subpageId");
        String initialTitle = getIntent().getStringExtra("subpageTitle");

        if (noteId == null || subpageId == null) {
            Toast.makeText(this, "Error: Invalid subpage data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set initial title if provided
        if (initialTitle != null && !initialTitle.equals("Untitled Subpage")) {
            subpageTitle.setText(initialTitle);
        }

        // Button listeners
        checkBtn.setOnClickListener(v -> saveAndExit());

        // Load existing subpage data
        loadSubpage();
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
                        String title = doc.getString("title");
                        String content = doc.getString("content");

                        if (title != null && !title.isEmpty()) {
                            subpageTitle.setText(title);
                        }
                        if (content != null && !content.isEmpty()) {
                            subpageContent.setText(content);
                        }
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

        // ✅ Determine what to display in NoteActivity
        // If user typed a title → use it
        // If no title → show "Untitled Subpage"
        String displayTitle = title.isEmpty() ? "Untitled Subpage" : title;

        // Save subpage data
        Map<String, Object> subpageData = new HashMap<>();
        subpageData.put("title", title);
        subpageData.put("content", content);
        subpageData.put("parentNoteId", noteId);
        subpageData.put("timestamp", System.currentTimeMillis());

        // Save to subpages collection
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .set(subpageData)
                .addOnSuccessListener(aVoid -> {
                    // ✅ Update the parent block's content (what shows in NoteActivity)
                    updateParentBlockTitle(user.getUid(), displayTitle);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error saving subpage", Toast.LENGTH_SHORT).show();
                });

        // Exit immediately
        finish();
    }

    /**
     * Updates the NoteBlock's content (which displays as the subpage title in NoteActivity)
     */
    private void updateParentBlockTitle(String userId, String newTitle) {
        if (subpageId == null) {
            android.util.Log.e("SubpageActivity", "subpageId is null, cannot update block");
            return;
        }

        android.util.Log.d("SubpageActivity", "Updating block with subpageId: " + subpageId + " to title: " + newTitle);

        // Find and update the block that references this subpage
        db.collection("users").document(userId)
                .collection("notes").document(noteId)
                .collection("blocks")
                .whereEqualTo("subpageId", subpageId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        android.util.Log.d("SubpageActivity", "Found " + querySnapshot.size() + " blocks with this subpageId");

                        // Update the first matching block's content
                        querySnapshot.getDocuments().get(0).getReference()
                                .update("content", newTitle)
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("SubpageActivity", "✅ Successfully updated block title to: " + newTitle);
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("SubpageActivity", "❌ Error updating block title", e);
                                });
                    } else {
                        android.util.Log.e("SubpageActivity", "❌ No block found with subpageId: " + subpageId);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("SubpageActivity", "❌ Error finding block", e);
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Auto-save when leaving the activity
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String title = subpageTitle.getText().toString().trim();
        String content = subpageContent.getText().toString().trim();
        String displayTitle = title.isEmpty() ? "Untitled Subpage" : title;

        Map<String, Object> subpageData = new HashMap<>();
        subpageData.put("title", title);
        subpageData.put("content", content);
        subpageData.put("parentNoteId", noteId);
        subpageData.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .set(subpageData);

        updateParentBlockTitle(user.getUid(), displayTitle);
    }
}