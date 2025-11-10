package com.example.testtasksync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NoteActivity extends AppCompatActivity {

    private EditText noteTitle, noteContent;
    private ImageView checkBtn, backBtn, addSubpageBtn, dividerBtn;
    private LinearLayout addSubpageContainer;
    private RelativeLayout noteLayout;
    private View colorPickerPanel;
    private ImageView colorPaletteBtn;
    private TextView bookmarksLink;
    private String currentColor = "#FAFAFA";
    private RecyclerView subpagesRecyclerView;
    private SubpageAdapter subpageAdapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String noteId = null;
    private boolean hasSubpages = false;
    private String currentNoteColor = "#FAFAFA";
    private ActionMode actionMode;
    private List<Bookmark> currentBookmarks = new ArrayList<>();
    private ListenerRegistration bookmarkListener;
    private boolean isUpdatingText = false;
    private String lastSavedContent = "";
    private int scrollToPosition = -1;
    private final android.os.Handler bookmarkSaveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable bookmarkSaveRunnable = null;
    private static final long BOOKMARK_SAVE_DELAY_MS = 600L;

    // Divider constants
    private static final String DIVIDER_LINE = "─────────────────────";
    private static final String DIVIDER_BREAK = "- - - - - - - - - - - -";
    private static final String DIVIDER_DOTS = "• • • • • • • • • •";
    private static final String DIVIDER_STARS = "✦ ✦ ✦ ✦ ✦ ✦ ✦";
    private static final String DIVIDER_WAVE = "～～～～～～～～";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);

        noteTitle = findViewById(R.id.noteTitle);
        noteContent = findViewById(R.id.noteContent);
        noteLayout = findViewById(R.id.noteLayout);
        colorPickerPanel = findViewById(R.id.colorPickerPanel);
        colorPaletteBtn = findViewById(R.id.colorPaletteBtn);
        checkBtn = findViewById(R.id.checkBtn);
        backBtn = findViewById(R.id.backBtn);
        addSubpageBtn = findViewById(R.id.addSubpageBtn);
        addSubpageContainer = findViewById(R.id.addSubpageContainer);
        subpagesRecyclerView = findViewById(R.id.subpagesRecyclerView);
        bookmarksLink = findViewById(R.id.bookmarksLink);
        dividerBtn = findViewById(R.id.dividerBtn);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        noteId = getIntent().getStringExtra("noteId");
        scrollToPosition = getIntent().getIntExtra("scrollToPosition", -1);

        loadNoteColor();
        colorPaletteBtn.setOnClickListener(v -> toggleColorPicker());
        setupColorPicker();
        dividerBtn.setOnClickListener(v -> showDividerOptions());

        if (noteId == null) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                noteId = db.collection("users").document(user.getUid())
                        .collection("notes").document().getId();

                Map<String, Object> newNote = new HashMap<>();
                newNote.put("title", "");
                newNote.put("content", "");
                newNote.put("timestamp", System.currentTimeMillis());

                db.collection("users").document(user.getUid())
                        .collection("notes").document(noteId)
                        .set(newNote);
            }
        }

        setupRecyclerView();
        checkBtn.setOnClickListener(v -> saveAndExit());
        backBtn.setOnClickListener(v -> saveAndExit());
        addSubpageBtn.setOnClickListener(v -> openSubpage());
        bookmarksLink.setOnClickListener(v -> openBookmarks());
        setupTextSelection();
        setupTextWatcher();

        if (noteId != null) {
            loadNote();
            loadSubpages();
        }
    }

    private void showDividerOptions() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.divider_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        LinearLayout dividerLine = sheetView.findViewById(R.id.dividerLine);
        LinearLayout dividerDots = sheetView.findViewById(R.id.dividerDots);
        LinearLayout dividerStars = sheetView.findViewById(R.id.dividerStars);
        LinearLayout dividerWave = sheetView.findViewById(R.id.dividerWave);
        LinearLayout dividerBreak = sheetView.findViewById(R.id.dividerBreak);

        dividerLine.setOnClickListener(v -> {
            insertDivider(DIVIDER_LINE);
            bottomSheet.dismiss();
        });

        dividerBreak.setOnClickListener(view -> {
            insertDivider(DIVIDER_BREAK);
            bottomSheet.dismiss();
        });

        dividerDots.setOnClickListener(v -> {
            insertDivider(DIVIDER_DOTS);
            bottomSheet.dismiss();
        });

        dividerStars.setOnClickListener(v -> {
            insertDivider(DIVIDER_STARS);
            bottomSheet.dismiss();
        });

        dividerWave.setOnClickListener(v -> {
            insertDivider(DIVIDER_WAVE);
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }

    private void insertDivider(String dividerStyle) {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();

        // Add newlines before and after divider for proper spacing
        String dividerText = "\n" + dividerStyle + "\n";

        // Insert divider at cursor position
        String newText = currentText.substring(0, cursorPosition) +
                dividerText +
                currentText.substring(cursorPosition);

        noteContent.setText(newText);

        // Position cursor after the divider
        int newPosition = cursorPosition + dividerText.length();
        if (newPosition <= newText.length()) {
            noteContent.setSelection(newPosition);
        }

        Toast.makeText(this, "Divider inserted", Toast.LENGTH_SHORT).show();
    }

    private void setupTextWatcher() {
        noteContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int after) {
                if (!isUpdatingText && !currentBookmarks.isEmpty()) {
                    int lengthDiff = after - before;
                    if (lengthDiff != 0) updateBookmarkIndices(start, lengthDiff);
                }
            }
            @Override public void afterTextChanged(Editable s) {
                if (!isUpdatingText) noteContent.postDelayed(() -> applyBookmarksToText(), 50);
            }
        });
    }

    private void updateBookmarkIndices(int changePosition, int lengthDiff) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (isUpdatingText) return;

        String currentText = noteContent.getText().toString();
        if (currentText.equals(lastSavedContent)) return;

        boolean anyBookmarkUpdated = false;
        List<Bookmark> bookmarksCopy = new ArrayList<>(currentBookmarks);

        for (Bookmark bookmark : bookmarksCopy) {
            int start = bookmark.getStartIndex();
            int end = bookmark.getEndIndex();
            boolean needsUpdate = false;
            boolean shouldDelete = false;

            // Case 1: edit before the bookmark → shift entire range
            if (changePosition < start) {
                start += lengthDiff;
                end += lengthDiff;
                needsUpdate = true;
            }
            // Case 2: edit inside the highlight → adjust end only
            else if (changePosition > start && changePosition < end) {
                end += lengthDiff;
                needsUpdate = true;
            }
            // Case 3: edit AT the start boundary (typing before bookmark)
            else if (changePosition == start && lengthDiff > 0) {
                // Shift the entire bookmark forward, don't expand
                start += lengthDiff;
                end += lengthDiff;
                needsUpdate = true;
            }
            // Case 4: deletion that affects the bookmark
            else if (lengthDiff < 0) {
                int deleteStart = changePosition;
                int deleteEnd = changePosition - lengthDiff;

                // If deletion overlaps with bookmark
                if (deleteEnd > start && deleteStart < end) {
                    // If entire bookmark is deleted
                    if (deleteStart <= start && deleteEnd >= end) {
                        shouldDelete = true;
                    } else {
                        end += lengthDiff;
                        needsUpdate = true;
                    }
                }
            }

            // Safety: validate bounds and check if bookmark text still matches
            if (start < 0 || end > currentText.length() || start >= end) {
                shouldDelete = true;
            } else if (needsUpdate) {
                // Verify the text at the new position matches somewhat
                try {
                    String newText = currentText.substring(start, end);
                    // If the text is completely different or too short, delete
                    if (newText.trim().isEmpty() || newText.length() < 2) {
                        shouldDelete = true;
                    }
                } catch (Exception e) {
                    shouldDelete = true;
                }
            }

            if (shouldDelete) {
                deleteBookmarkFromFirestore(bookmark.getId());
                anyBookmarkUpdated = true;
            } else if (needsUpdate) {
                String updatedText;
                try {
                    updatedText = currentText.substring(start, end);
                } catch (Exception e) {
                    continue;
                }

                updateBookmarkInFirestore(bookmark.getId(), start, end, updatedText);
                bookmark.setStartIndex(start);
                bookmark.setEndIndex(end);
                bookmark.setText(updatedText);
                anyBookmarkUpdated = true;
            }
        }

        if (anyBookmarkUpdated) {
            saveNoteContentDebounced(currentText);
        }
    }

    private void updateBookmarkInFirestore(String bookmarkId, int newStart, int newEnd, String newText) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("startIndex", newStart);
        updates.put("endIndex", newEnd);
        updates.put("text", newText);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").document(bookmarkId)
                .update(updates);
    }

    private void deleteBookmarkFromFirestore(String bookmarkId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").document(bookmarkId)
                .delete();
    }

    private void saveNoteContentDebounced(String content) {
        // cancel previous pending save
        if (bookmarkSaveRunnable != null) {
            bookmarkSaveHandler.removeCallbacks(bookmarkSaveRunnable);
        }

        bookmarkSaveRunnable = () -> saveNoteContentToFirestore(content);
        bookmarkSaveHandler.postDelayed(bookmarkSaveRunnable, BOOKMARK_SAVE_DELAY_MS);
    }

    private void saveNoteContentToFirestore(String content) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("content", content);
        updates.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .update(updates)
                .addOnFailureListener(e -> e.printStackTrace());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (noteId != null) {
            // Only load note if bookmarks aren't already loaded
            if (currentBookmarks.isEmpty()) {
                loadNote();
                setupBookmarkListener();
            } else {
                // Just reapply the bookmarks without reloading
                applyBookmarksToText();
            }

            loadSubpages();

            // Scroll to bookmark after everything is loaded
            if (scrollToPosition >= 0) {
                final int positionToScroll = scrollToPosition;
                scrollToPosition = -1; // Reset immediately to prevent multiple scrolls
                noteContent.postDelayed(() -> scrollToBookmark(positionToScroll), 800);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookmarkListener != null) bookmarkListener.remove();
    }

    private void setupTextSelection() {
        noteContent.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                int start = noteContent.getSelectionStart();
                int end = noteContent.getSelectionEnd();

                // Check if selection is within any bookmark
                Bookmark selectedBookmark = getBookmarkAtSelection(start, end);

                if (selectedBookmark != null) {
                    // Selection is within a bookmark - show expand option
                    menu.clear();
                    menu.add(0, 1, 0, "Expand Bookmark");
                    menu.add(0, 2, 0, "Update Color/Style");
                    menu.add(0, 3, 0, "Delete Bookmark");
                } else {
                    // Normal selection - show bookmark option
                    menu.clear();
                    menu.add(0, 0, 0, "Bookmark");
                }

                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int start = noteContent.getSelectionStart();
                int end = noteContent.getSelectionEnd();

                switch (item.getItemId()) {
                    case 0: // Bookmark (new)
                        String selectedText = noteContent.getText().toString().substring(start, end);
                        showBookmarkBottomSheet(selectedText, start, end);
                        mode.finish();
                        return true;

                    case 1: // Expand Bookmark
                        Bookmark bookmarkToExpand = getBookmarkAtSelection(start, end);
                        if (bookmarkToExpand != null) {
                            expandBookmark(bookmarkToExpand, start, end);
                        }
                        mode.finish();
                        return true;

                    case 2: // Update Color/Style
                        Bookmark bookmarkToUpdate = getBookmarkAtSelection(start, end);
                        if (bookmarkToUpdate != null) {
                            showUpdateBookmarkBottomSheet(bookmarkToUpdate);
                        }
                        mode.finish();
                        return true;

                    case 3: // Delete Bookmark
                        Bookmark bookmarkToDelete = getBookmarkAtSelection(start, end);
                        if (bookmarkToDelete != null) {
                            showDeleteBookmarkConfirmation(bookmarkToDelete);
                        }
                        mode.finish();
                        return true;
                }
                return false;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }

    // Check if the selection overlaps with any bookmark
    private Bookmark getBookmarkAtSelection(int start, int end) {
        for (Bookmark bookmark : currentBookmarks) {
            // Check if selection is within or overlaps the bookmark
            if (start >= bookmark.getStartIndex() && start < bookmark.getEndIndex()) {
                return bookmark;
            }
            if (end > bookmark.getStartIndex() && end <= bookmark.getEndIndex()) {
                return bookmark;
            }
            // Also check if selection contains the entire bookmark
            if (start <= bookmark.getStartIndex() && end >= bookmark.getEndIndex()) {
                return bookmark;
            }
        }
        return null;
    }

    // Expand the bookmark to include the new selection
    private void expandBookmark(Bookmark bookmark, int newStart, int newEnd) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Calculate the expanded range
        int expandedStart = Math.min(bookmark.getStartIndex(), newStart);
        int expandedEnd = Math.max(bookmark.getEndIndex(), newEnd);

        String currentText = noteContent.getText().toString();

        // Trim spaces from both ends
        while (expandedStart < expandedEnd && expandedStart < currentText.length()
                && currentText.charAt(expandedStart) == ' ') {
            expandedStart++;
        }
        while (expandedEnd > expandedStart && expandedEnd > 0
                && currentText.charAt(expandedEnd - 1) == ' ') {
            expandedEnd--;
        }

        if (expandedStart >= expandedEnd || expandedStart < 0 || expandedEnd > currentText.length()) {
            Toast.makeText(this, "Invalid selection", Toast.LENGTH_SHORT).show();
            return;
        }

        String expandedText = currentText.substring(expandedStart, expandedEnd);

        // ✅ Create final variables for use in lambda
        final int finalExpandedStart = expandedStart;
        final int finalExpandedEnd = expandedEnd;
        final String finalExpandedText = expandedText;

        // Update the bookmark with new indices
        Map<String, Object> updates = new HashMap<>();
        updates.put("startIndex", finalExpandedStart);
        updates.put("endIndex", finalExpandedEnd);
        updates.put("text", finalExpandedText);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").document(bookmark.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // ✅ Update the local bookmark object immediately
                    bookmark.setStartIndex(finalExpandedStart);
                    bookmark.setEndIndex(finalExpandedEnd);
                    bookmark.setText(finalExpandedText);

                    Toast.makeText(this, "Bookmark expanded", Toast.LENGTH_SHORT).show();
                    applyBookmarksToText(); // Refresh to show updated bookmark
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error expanding bookmark", Toast.LENGTH_SHORT).show();
                });
    }

    // Show update bottom sheet (same as before, but now callable from selection)
    private void showUpdateBookmarkBottomSheet(Bookmark bookmark) {
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
            styleHighlight.setTextColor(Color.parseColor("#4CAF50"));
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(Color.parseColor("#666666"));
        } else {
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(Color.parseColor("#4CAF50"));
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
            styleHighlight.setTextColor(Color.parseColor("#4CAF50"));
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(Color.parseColor("#666666"));
        });

        styleUnderline.setOnClickListener(v -> {
            selectedStyle[0] = "underline";
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(Color.parseColor("#4CAF50"));
            styleHighlight.setBackgroundResource(R.drawable.style_unselected);
            styleHighlight.setTextColor(Color.parseColor("#666666"));
        });

        updateBtn.setOnClickListener(v -> {
            String newNote = noteInput.getText().toString().trim();

            // Update color, style, and note in Firestore
            Map<String, Object> updates = new HashMap<>();
            updates.put("color", selectedColor[0]);
            updates.put("style", selectedStyle[0]);
            updates.put("note", newNote);

            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                db.collection("users").document(user.getUid())
                        .collection("notes").document(noteId)
                        .collection("bookmarks").document(bookmark.getId())
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            // ✅ Update the local bookmark object immediately
                            bookmark.setColor(selectedColor[0]);
                            bookmark.setStyle(selectedStyle[0]);
                            bookmark.setNote(newNote);

                            Toast.makeText(this, "Bookmark updated", Toast.LENGTH_SHORT).show();
                            applyBookmarksToText(); // Refresh visual display
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Error updating bookmark", Toast.LENGTH_SHORT).show();
                        });
            }

            bottomSheet.dismiss();
        });

        deleteBtn.setOnClickListener(v -> {
            showDeleteBookmarkConfirmation(bookmark);
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }

    // Add this helper method
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

    // Delete bookmark confirmation
    private void showDeleteBookmarkConfirmation(Bookmark bookmark) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Bookmark")
                .setMessage("Are you sure you want to delete this bookmark?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteBookmarkFromFirestore(bookmark.getId());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void showBookmarkBottomSheet(String selectedText, int startIndex, int endIndex) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bookmark_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

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
        TextInputEditText noteInput = sheetView.findViewById(R.id.bookmarkNoteInput);
        TextView cancelBtn = sheetView.findViewById(R.id.cancelBtn);
        TextView okBtn = sheetView.findViewById(R.id.okBtn);

        final String[] selectedColor = {"#E1BEE7"};
        final String[] selectedStyle = {"highlight"};

        colorViolet.setScaleX(1.2f);
        colorViolet.setScaleY(1.2f);
        styleHighlight.setBackgroundResource(R.drawable.style_selected);
        styleHighlight.setTextColor(Color.parseColor("#4CAF50"));

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

        styleHighlight.setOnClickListener(v -> {
            selectedStyle[0] = "highlight";
            styleHighlight.setBackgroundResource(R.drawable.style_selected);
            styleHighlight.setTextColor(Color.parseColor("#4CAF50"));
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(Color.parseColor("#666666"));
        });

        styleUnderline.setOnClickListener(v -> {
            selectedStyle[0] = "underline";
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(Color.parseColor("#4CAF50"));
            styleHighlight.setBackgroundResource(R.drawable.style_unselected);
            styleHighlight.setTextColor(Color.parseColor("#666666"));
        });

        cancelBtn.setOnClickListener(v -> bottomSheet.dismiss());
        okBtn.setOnClickListener(v -> {
            String note = noteInput.getText().toString().trim();
            createBookmark(selectedText, note, selectedColor[0], selectedStyle[0], startIndex, endIndex);
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }

    private void resetColorSelection(View... views) {
        for (View v : views) { v.setScaleX(1f); v.setScaleY(1f); }
    }

    private void createBookmark(String text, String note, String color, String style, int startIndex, int endIndex) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Bookmark bookmark = new Bookmark(text, note, color, style, startIndex, endIndex);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").add(bookmark)
                .addOnSuccessListener(doc -> Toast.makeText(this, "Bookmark created", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error creating bookmark", Toast.LENGTH_SHORT).show());
    }

    private void setupBookmarkListener() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (bookmarkListener != null) bookmarkListener.remove();

        bookmarkListener = db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    // ✅ Set flag to prevent text watcher interference
                    isUpdatingText = true;

                    currentBookmarks.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Bookmark b = doc.toObject(Bookmark.class);
                            b.setId(doc.getId());
                            currentBookmarks.add(b);
                        }
                        bookmarksLink.setVisibility(currentBookmarks.isEmpty() ? View.GONE : View.VISIBLE);

                        // Apply bookmarks after a delay
                        noteContent.postDelayed(() -> {
                            applyBookmarksToText();
                            isUpdatingText = false;
                        }, 100);
                    }
                });
    }

    private void applyBookmarksToText() {
        isUpdatingText = true;
        String content = noteContent.getText().toString();
        if (content.isEmpty()) { isUpdatingText = false; return; }

        SpannableString span = new SpannableString(content);

        for (Bookmark b : currentBookmarks) {
            int s = b.getStartIndex(), e = b.getEndIndex();
            if (s >= 0 && e <= content.length() && s < e) {
                try {
                    int color = Color.parseColor(b.getColor());
                    if ("highlight".equals(b.getStyle()))
                        span.setSpan(new BackgroundColorSpan(color), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    else if ("underline".equals(b.getStyle()))
                        span.setSpan(new CustomUnderlineSpan(color, s, e), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception ignored) {}
            }
        }

        int sel = noteContent.getSelectionStart();
        noteContent.setText(span, TextView.BufferType.SPANNABLE);
        if (sel >= 0 && sel <= content.length()) noteContent.setSelection(sel);

        lastSavedContent = content;
        isUpdatingText = false;
    }

    private void openBookmarks() {
        Intent i = new Intent(this, BookmarksActivity.class);
        i.putExtra("noteId", noteId);
        startActivity(i);
    }

    private void setupRecyclerView() {
        subpageAdapter = new SubpageAdapter(this, noteId);
        subpagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        subpagesRecyclerView.setAdapter(subpageAdapter);
    }

    private void loadNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String title = doc.getString("title");
                        String content = doc.getString("content");

                        // ✅ Set flag to prevent text watcher from running
                        isUpdatingText = true;

                        if (title != null) noteTitle.setText(title);
                        if (content != null) noteContent.setText(content);

                        lastSavedContent = content != null ? content : "";

                        // ✅ Reset flag after a short delay
                        noteContent.postDelayed(() -> {
                            isUpdatingText = false;
                            applyBookmarksToText();
                        }, 100);
                    }
                });
    }

    private void scrollToBookmark(int position) {
        noteContent.postDelayed(() -> {
            try {
                noteContent.requestFocus();
                noteContent.setSelection(position);
                android.text.Layout layout = noteContent.getLayout();
                if (layout != null) {
                    int line = layout.getLineForOffset(position);
                    int scrollY = layout.getLineTop(line);
                    noteContent.scrollTo(0, Math.max(0, scrollY - 200));
                }
                noteContent.postDelayed(this::applyBookmarksToText, 400);
            } catch (Exception ignored) {}
        }, 300);
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

                        subpageAdapter.setSubpages(subpages);
                        hasSubpages = !subpages.isEmpty();
                        addSubpageContainer.setVisibility(hasSubpages ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void openSubpage() {
        Intent intent = new Intent(this, SubpageActivity.class);
        intent.putExtra("noteId", noteId);
        startActivity(intent);
    }

    private void saveAndExit() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        String title = noteTitle.getText().toString();
        String content = noteContent.getText().toString();

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("title", title);
        noteData.put("content", content);
        noteData.put("color", currentNoteColor);
        noteData.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes")
                .document(noteId)
                .set(noteData)
                .addOnSuccessListener(aVoid -> finish())
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error saving note", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }
    private void toggleColorPicker() {
        if (colorPickerPanel.getVisibility() == View.VISIBLE) {
            colorPickerPanel.setVisibility(View.GONE);
        } else {
            colorPickerPanel.setVisibility(View.VISIBLE);
        }
    }

    private void setupColorPicker() {
        findViewById(R.id.colorDefault).setOnClickListener(v -> changeNoteColor("#FAFAFA"));
        findViewById(R.id.colorRed).setOnClickListener(v -> changeNoteColor("#FFCDD2"));
        findViewById(R.id.colorYellow).setOnClickListener(v -> changeNoteColor("#FFF9C4"));
        findViewById(R.id.colorGreen).setOnClickListener(v -> changeNoteColor("#C8E6C9"));
        findViewById(R.id.colorBlue).setOnClickListener(v -> changeNoteColor("#BBDEFB"));
        findViewById(R.id.colorPurple).setOnClickListener(v -> changeNoteColor("#E1BEE7"));
        findViewById(R.id.colorOrange).setOnClickListener(v -> changeNoteColor("#FFE0B2"));
        findViewById(R.id.colorGrey).setOnClickListener(v -> changeNoteColor("#E0E0E0"));
    }

    private void changeNoteColor(String color) {
        noteLayout.setBackgroundColor(Color.parseColor(color));
        currentNoteColor = color;
        colorPickerPanel.setVisibility(View.GONE);
        saveNoteColor(color);
    }

    private void saveNoteColor(String color) {
        SharedPreferences prefs = getSharedPreferences("NotePrefs", MODE_PRIVATE);
        prefs.edit().putString("noteColor_" + noteId, color).apply();
    }

    private void loadNoteColor() {
        SharedPreferences prefs = getSharedPreferences("NotePrefs", MODE_PRIVATE);
        currentColor = prefs.getString("noteColor_" + noteId, "#FAFAFA");
        noteLayout.setBackgroundColor(Color.parseColor(currentColor));
    }
}
