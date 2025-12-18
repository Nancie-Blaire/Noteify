package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubpageBookmarksActivity extends AppCompatActivity {

    private RecyclerView bookmarksRecyclerView;
    private LinearLayout emptyView;
    private ImageView backBtn;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String noteId;
    private String subpageId;

    private List<BookmarkWithPosition> bookmarks = new ArrayList<>();
    private SubpageBookmarksAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // ✅ Enable offline persistence
        db.getFirestoreSettings();

        // Get noteId and subpageId from intent
        noteId = getIntent().getStringExtra("noteId");
        subpageId = getIntent().getStringExtra("subpageId");

        if (noteId == null || subpageId == null) {
            finish();
            return;
        }

        // Initialize views
        bookmarksRecyclerView = findViewById(R.id.bookmarksRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        backBtn = findViewById(R.id.backBtn);

        // Setup RecyclerView
        adapter = new SubpageBookmarksAdapter(bookmarks, new SubpageBookmarksAdapter.OnBookmarkClickListener() {
            @Override
            public void onBookmarkClick(BookmarkWithPosition bookmark) {
                // When bookmark is clicked, go back to subpage with scroll position
                Intent resultIntent = new Intent();
                resultIntent.putExtra("scrollToBlockPosition", bookmark.blockPosition);
                resultIntent.putExtra("scrollToTextPosition", bookmark.bookmark.getStartIndex());
                setResult(RESULT_OK, resultIntent);
                finish();
            }

            @Override
            public void onBookmarkMenuClick(BookmarkWithPosition bookmark) {
                showBookmarkMenu(bookmark);
            }
        });

        bookmarksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        bookmarksRecyclerView.setAdapter(adapter);

        // Back button
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }

        // Load bookmarks
        loadBookmarks();
    }

    private void loadBookmarks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // ✅ OFFLINE-FIRST: Try cache first, then server
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("blocks")
                .orderBy("order")
                .get(Source.CACHE) // ✅ Try cache first
                .addOnSuccessListener(blocksSnapshot -> {
                    loadBookmarksWithBlocks(blocksSnapshot);
                })
                .addOnFailureListener(e -> {
                    // ✅ If cache fails, try server
                    db.collection("users").document(user.getUid())
                            .collection("notes").document(noteId)
                            .collection("subpages").document(subpageId)
                            .collection("blocks")
                            .orderBy("order")
                            .get(Source.SERVER)
                            .addOnSuccessListener(this::loadBookmarksWithBlocks)
                            .addOnFailureListener(serverError -> showEmptyState());
                });
    }

    private void loadBookmarksWithBlocks(com.google.firebase.firestore.QuerySnapshot blocksSnapshot) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Build blockId -> position map
        Map<String, Integer> blockPositionMap = new HashMap<>();
        int position = 0;
        for (QueryDocumentSnapshot blockDoc : blocksSnapshot) {
            blockPositionMap.put(blockDoc.getId(), position);
            position++;
        }

        // ✅ OFFLINE-FIRST: Load bookmarks from cache first
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("bookmarks")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get(Source.CACHE) // ✅ Try cache first
                .addOnSuccessListener(bookmarksSnapshot -> {
                    processBookmarks(bookmarksSnapshot, blockPositionMap);
                })
                .addOnFailureListener(e -> {
                    // ✅ If cache fails, try server
                    db.collection("users").document(user.getUid())
                            .collection("notes").document(noteId)
                            .collection("subpages").document(subpageId)
                            .collection("bookmarks")
                            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .get(Source.SERVER)
                            .addOnSuccessListener(serverSnapshot -> {
                                processBookmarks(serverSnapshot, blockPositionMap);
                            })
                            .addOnFailureListener(serverError -> showEmptyState());
                });
    }

    private void processBookmarks(com.google.firebase.firestore.QuerySnapshot bookmarksSnapshot,
                                  Map<String, Integer> blockPositionMap) {
        bookmarks.clear();

        if (bookmarksSnapshot.isEmpty()) {
            showEmptyState();
            return;
        }

        for (QueryDocumentSnapshot doc : bookmarksSnapshot) {
            Bookmark bookmark = doc.toObject(Bookmark.class);
            bookmark.setId(doc.getId());

            String blockId = bookmark.getBlockId();
            Integer blockPos = blockPositionMap.get(blockId);

            if (blockPos != null) {
                bookmarks.add(new BookmarkWithPosition(bookmark, blockPos));
            }
        }

        if (bookmarks.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
            adapter.notifyDataSetChanged();
        }
    }

    private void showEmptyState() {
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
        }
        if (bookmarksRecyclerView != null) {
            bookmarksRecyclerView.setVisibility(View.GONE);
        }
    }

    private void hideEmptyState() {
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (bookmarksRecyclerView != null) {
            bookmarksRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookmarks();
    }

    private void showBookmarkMenu(BookmarkWithPosition bookmarkWithPos) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bookmark_bottom_sheet_update, null);
        bottomSheet.setContentView(sheetView);

        Bookmark bookmark = bookmarkWithPos.bookmark;

        // Color options
        View colorViolet = sheetView.findViewById(R.id.colorViolet);
        View colorYellow = sheetView.findViewById(R.id.colorYellow);
        View colorPink = sheetView.findViewById(R.id.colorPink);
        View colorGreen = sheetView.findViewById(R.id.colorGreen);
        View colorBlue = sheetView.findViewById(R.id.colorBlue);
        View colorOrange = sheetView.findViewById(R.id.colorOrange);
        View colorRed = sheetView.findViewById(R.id.colorRed);
        View colorCyan = sheetView.findViewById(R.id.colorCyan);

        colorViolet.setTag("#E1BEE7");
        colorYellow.setTag("#FFF9C4");
        colorPink.setTag("#F8BBD0");
        colorGreen.setTag("#C8E6C9");
        colorBlue.setTag("#BBDEFB");
        colorOrange.setTag("#FFE0B2");
        colorRed.setTag("#FFCDD2");
        colorCyan.setTag("#B2EBF2");

        TextView styleHighlight = sheetView.findViewById(R.id.styleHighlight);
        TextView styleUnderline = sheetView.findViewById(R.id.styleUnderline);
        com.google.android.material.textfield.TextInputEditText noteInput =
                sheetView.findViewById(R.id.bookmarkNoteInput);
        TextView updateBtn = sheetView.findViewById(R.id.updateBtn);
        TextView deleteBtn = sheetView.findViewById(R.id.deleteBtn);

        final String[] selectedColor = {bookmark.getColor()};
        final String[] selectedStyle = {bookmark.getStyle()};

        if (noteInput != null) {
            noteInput.setText(bookmark.getNote());
        }

        setColorScale(colorViolet, colorYellow, colorPink, colorGreen, colorBlue,
                colorOrange, colorRed, colorCyan, selectedColor[0]);

        if ("highlight".equals(selectedStyle[0])) {
            styleHighlight.setBackgroundResource(R.drawable.style_selected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#666666"));
        } else {
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleHighlight.setBackgroundResource(R.drawable.style_unselected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#666666"));
        }

        View.OnClickListener colorListener = v -> {
            resetColorSelection(colorViolet, colorYellow, colorPink, colorGreen,
                    colorBlue, colorOrange, colorRed, colorCyan);
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

        styleHighlight.setOnClickListener(v -> {
            selectedStyle[0] = "highlight";
            styleHighlight.setBackgroundResource(R.drawable.style_selected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#666666"));
        });

        styleUnderline.setOnClickListener(v -> {
            selectedStyle[0] = "underline";
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleHighlight.setBackgroundResource(R.drawable.style_unselected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#666666"));
        });

        if (updateBtn != null) {
            updateBtn.setOnClickListener(v -> {
                String newNote = noteInput.getText().toString().trim();
                updateBookmark(bookmark, selectedColor[0], selectedStyle[0], newNote);
                bottomSheet.dismiss();
            });
        }

        if (deleteBtn != null) {
            deleteBtn.setOnClickListener(v -> {
                bottomSheet.dismiss();
                showDeleteConfirmation(bookmark);
            });
        }

        bottomSheet.show();
    }

    private void setColorScale(View violet, View yellow, View pink, View green,
                               View blue, View orange, View red, View cyan, String currentColor) {
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

    private void updateBookmark(Bookmark bookmark, String color, String style, String note) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("color", color);
        updates.put("style", style);
        updates.put("note", note);

        // ✅ Update locally first for instant feedback
        bookmark.setColor(color);
        bookmark.setStyle(style);
        bookmark.setNote(note);
        adapter.notifyDataSetChanged();

        // ✅ Then sync to Firestore (works offline with cache)
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("bookmarks").document(bookmark.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    //android.widget.Toast.makeText(this, "Bookmark updated",
                           // android.widget.Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    // ✅ Still works offline - will sync when online
                    //android.widget.Toast.makeText(this, "Saved offline - will sync when online",
                    //   android.widget.Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteConfirmation(Bookmark bookmark) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Bookmark")
                .setMessage("Are you sure you want to delete this bookmark?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteBookmark(bookmark);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteBookmark(Bookmark bookmark) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // ✅ Remove from list first for instant feedback
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).bookmark.getId().equals(bookmark.getId())) {
                bookmarks.remove(i);
                adapter.notifyItemRemoved(i);
                break;
            }
        }

        if (bookmarks.isEmpty()) {
            showEmptyState();
        }

        // ✅ Then delete from Firestore (works offline with cache)
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("bookmarks").document(bookmark.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    //android.widget.Toast.makeText(this, "Bookmark deleted",
                            //android.widget.Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    // ✅ Still works offline - will sync when online
                    //android.widget.Toast.makeText(this, "Deleted offline - will sync when online",
                          //  android.widget.Toast.LENGTH_SHORT).show();
                });
    }
}