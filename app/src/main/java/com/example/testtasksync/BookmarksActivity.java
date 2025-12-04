package com.example.testtasksync;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookmarksActivity extends AppCompatActivity implements BookmarkAdapter.OnBookmarkClickListener {

    private RecyclerView bookmarksRecyclerView;
    private BookmarkAdapter bookmarkAdapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String noteId;
    private ImageView backBtn;
    private LinearLayout emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);

        noteId = getIntent().getStringExtra("noteId");

        backBtn = findViewById(R.id.backBtn);
        bookmarksRecyclerView = findViewById(R.id.bookmarksRecyclerView);
        emptyView = findViewById(R.id.emptyView);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        backBtn.setOnClickListener(v -> finish());

        setupRecyclerView();
        loadBookmarks();
    }

    private void setupRecyclerView() {
        bookmarkAdapter = new BookmarkAdapter(this, this);
        bookmarksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        bookmarksRecyclerView.setAdapter(bookmarkAdapter);
    }

    private void loadBookmarks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks")
                .orderBy("timestamp")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading bookmarks", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null) {
                        List<Bookmark> bookmarks = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            Bookmark bookmark = doc.toObject(Bookmark.class);
                            bookmark.setId(doc.getId());
                            bookmarks.add(bookmark);
                        }

                        bookmarkAdapter.setBookmarks(bookmarks);

                        if (bookmarks.isEmpty()) {
                            emptyView.setVisibility(View.VISIBLE);
                            bookmarksRecyclerView.setVisibility(View.GONE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                            bookmarksRecyclerView.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }
    @Override
    public void onBookmarkClick(Bookmark bookmark) {
        // Create intent to go back to NoteActivity with scroll position
        Intent intent = new Intent(BookmarksActivity.this, NoteActivity.class);
        intent.putExtra("noteId", noteId);
        intent.putExtra("scrollToPosition", bookmark.getStartIndex());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish(); // Close BookmarksActivity
    }

    @Override
    public void onBookmarkMenuClick(Bookmark bookmark, View anchorView) {
        showUpdateBottomSheet(bookmark);
    }

    private void showUpdateBottomSheet(Bookmark bookmark) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bookmark_bottom_sheet_update, null);
        bottomSheet.setContentView(sheetView);

        // Color options
        View colorViolet = sheetView.findViewById(R.id.colorViolet);
        View colorYellow = sheetView.findViewById(R.id.colorYellow);
        View colorPink = sheetView.findViewById(R.id.colorPink);
        View colorGreen = sheetView.findViewById(R.id.colorGreen);
        View colorBlue = sheetView.findViewById(R.id.colorBlue);
        View colorOrange = sheetView.findViewById(R.id.colorOrange);
        View colorRed = sheetView.findViewById(R.id.colorRed);
        View colorCyan = sheetView.findViewById(R.id.colorCyan);

        // Set tags for each color view
        colorViolet.setTag("#E1BEE7");
        colorYellow.setTag("#FFF9C4");
        colorPink.setTag("#F8BBD0");
        colorGreen.setTag("#C8E6C9");
        colorBlue.setTag("#BBDEFB");
        colorOrange.setTag("#FFE0B2");
        colorRed.setTag("#FFCDD2");
        colorCyan.setTag("#B2EBF2");

        // Style options
        TextView styleHighlight = sheetView.findViewById(R.id.styleHighlight);
        TextView styleUnderline = sheetView.findViewById(R.id.styleUnderline);

        TextInputEditText noteInput = sheetView.findViewById(R.id.bookmarkNoteInput);
        TextView updateBtn = sheetView.findViewById(R.id.updateBtn);
        TextView deleteBtn = sheetView.findViewById(R.id.deleteBtn);

        final String[] selectedColor = {bookmark.getColor()};
        final String[] selectedStyle = {bookmark.getStyle()};

        // Pre-fill note
        noteInput.setText(bookmark.getNote());

        // Set initial color selection
        setColorScale(colorViolet, colorYellow, colorPink, colorGreen, colorBlue, colorOrange, colorRed, colorCyan, selectedColor[0]);

        // Set initial style selection
        if ("highlight".equals(selectedStyle[0])) {
            styleHighlight.setBackgroundResource(R.drawable.style_selected);
            styleHighlight.setTextColor(Color.parseColor("#ff9376")); // pastel orange text
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(Color.parseColor("#666666"));
        } else {
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(Color.parseColor("#ff9376")); // pastel orange text
            styleHighlight.setBackgroundResource(R.drawable.style_unselected);
            styleHighlight.setTextColor(Color.parseColor("#666666"));
        }

        // Color selection listeners
        View.OnClickListener colorListener = v -> {
            resetColorSelection(colorViolet, colorYellow, colorPink, colorGreen, colorBlue, colorOrange, colorRed, colorCyan);
            v.setScaleX(1.2f);
            v.setScaleY(1.2f);
            selectedColor[0] = (String) v.getTag();
        };

        colorViolet.setOnClickListener(colorListener);
        colorYellow.setOnClickListener(colorListener);
        colorPink.setOnClickListener(colorListener);
        colorGreen.setOnClickListener(colorListener);
        colorBlue.setOnClickListener(colorListener);
        colorOrange.setOnClickListener(colorListener);
        colorRed.setOnClickListener(colorListener);
        colorCyan.setOnClickListener(colorListener);

        // Style selection listeners
        styleHighlight.setOnClickListener(v -> {
            selectedStyle[0] = "highlight";
            styleHighlight.setBackgroundResource(R.drawable.style_selected);
            styleHighlight.setTextColor(Color.parseColor("#ff9376")); // pastel orange
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(Color.parseColor("#666666"));
        });

        styleUnderline.setOnClickListener(v -> {
            selectedStyle[0] = "underline";
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(Color.parseColor("#ff9376")); // pastel orange
            styleHighlight.setBackgroundResource(R.drawable.style_unselected);
            styleHighlight.setTextColor(Color.parseColor("#666666"));
        });

        updateBtn.setOnClickListener(v -> {
            String newNote = noteInput.getText().toString().trim();
            updateBookmark(bookmark.getId(), selectedColor[0], selectedStyle[0], newNote);
            bottomSheet.dismiss();
        });

        deleteBtn.setOnClickListener(v -> {
            showDeleteConfirmation(bookmark, bottomSheet);
        });

        bottomSheet.show();
    }

    private void showDeleteConfirmation(Bookmark bookmark, BottomSheetDialog parentSheet) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Bookmark")
                .setMessage("Are you sure you want to delete this bookmark?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteBookmark(bookmark);
                    parentSheet.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setColorScale(View violet, View yellow, View pink, View green, View blue, View orange, View red, View cyan, String currentColor) {
        resetColorSelection(violet, yellow, pink, green, blue, orange, red, cyan);

        View selectedView = null;
        switch (currentColor) {
            case "#E1BEE7": selectedView = violet; break;
            case "#FFF9C4": selectedView = yellow; break;
            case "#F8BBD0": selectedView = pink; break;
            case "#C8E6C9": selectedView = green; break;
            case "#BBDEFB": selectedView = blue; break;
            case "#FFE0B2": selectedView = orange; break;
            case "#FFCDD2": selectedView = red; break;
            case "#B2EBF2": selectedView = cyan; break;
        }

        if (selectedView != null) {
            selectedView.setScaleX(1.2f);
            selectedView.setScaleY(1.2f);
        }
    }

    private void resetColorSelection(View... views) {
        for (View v : views) {
            v.setScaleX(1.0f);
            v.setScaleY(1.0f);
        }
    }

    private void updateBookmark(String bookmarkId, String color, String style, String note) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("color", color);
        updates.put("style", style);
        updates.put("note", note);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").document(bookmarkId)
                .update(updates)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Bookmark updated", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error updating bookmark", Toast.LENGTH_SHORT).show());
    }

    private void deleteBookmark(Bookmark bookmark) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").document(bookmark.getId())
                .delete()
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Bookmark deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error deleting bookmark", Toast.LENGTH_SHORT).show());
    }
    //go lia
}