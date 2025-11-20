package com.example.testtasksync;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.HorizontalScrollView;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class NoteActivity extends AppCompatActivity {

    private EditText noteTitle, noteContent;
    private ImageView checkBtn, addMenuBtn;
    private HorizontalScrollView addOptionsMenu;
    private boolean isMenuOpen = false;
    private RelativeLayout noteLayout;
    private View colorPickerPanel;
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
    // new fields for debounced saving
    private final android.os.Handler bookmarkSaveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable bookmarkSaveRunnable = null;
    private static final long BOOKMARK_SAVE_DELAY_MS = 600L; // debounce delay
    private Map<Integer, String> dividerStyles = new HashMap<>();
    private boolean isNumberedListMode = false;
    private boolean isBulletListMode = false;
    private int currentListNumber = 1;
    private boolean isToggleListMode = false;
    private boolean isTogglingState = false;
    private Map<Integer, Boolean> toggleStates = new HashMap<>(); // position -> isExpanded
    private Map<Integer, String> toggleContents = new HashMap<>(); // position -> content


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);

        noteTitle = findViewById(R.id.noteTitle);
        noteContent = findViewById(R.id.noteContent);
        noteLayout = findViewById(R.id.noteLayout);
        colorPickerPanel = findViewById(R.id.colorPickerPanel);
        checkBtn = findViewById(R.id.checkBtn);
        addOptionsMenu = findViewById(R.id.addOptionsMenu);
        subpagesRecyclerView = findViewById(R.id.subpagesRecyclerView);
        bookmarksLink = findViewById(R.id.bookmarksLink);
        addMenuBtn = findViewById(R.id.addMenuBtn);


        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        noteId = getIntent().getStringExtra("noteId");
        scrollToPosition = getIntent().getIntExtra("scrollToPosition", -1);

        //  CALLING METHODS
        loadNoteColor();
        setupColorPicker();
        setupAddMenuOptions();
        setupTextSelection();
        setupTextWatcher();
        setupNumberedListWatcher();
        setupBulletListWatcher();
        setupToggleListWatcher();
        setupToggleClickListener();

        // Create Firestore note if new
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

        // LISTENERS:
        setupRecyclerView();
        checkBtn.setOnClickListener(v -> saveAndExit());
        addMenuBtn.setOnClickListener(v -> toggleAddMenu());
        bookmarksLink.setOnClickListener(v -> openBookmarks());
        findViewById(R.id.indentOption).setOnClickListener(v -> indentLine());
        findViewById(R.id.outdentOption).setOnClickListener(v -> outdentLine());

        if (noteId != null) {
            loadNote();
            noteContent.postDelayed(() -> setupBookmarkListener(), 400);
            loadSubpages();
        }
    }

    private void setupTextWatcher() {
        final String dividerPlaceholder = "〔DIVIDER〕";

        noteContent.addTextChangedListener(new TextWatcher() {
            private String textBefore = "";
            private int cursorBefore = 0;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                textBefore = s.toString();
                cursorBefore = noteContent.getSelectionStart();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                if (isUpdatingText) return;

                String currentText = s.toString();
                int dividerIndex = currentText.indexOf(dividerPlaceholder);

                // Run check only if we actually have dividers
                if (dividerIndex == -1) return;

                // Track all divider positions for line-based detection
                List<Integer> dividerPositions = new ArrayList<>();
                while (dividerIndex != -1) {
                    dividerPositions.add(dividerIndex);
                    dividerIndex = currentText.indexOf(dividerPlaceholder, dividerIndex + dividerPlaceholder.length());
                }

                // Check if user typed within or on a divider
                for (int i = 0; i < dividerPositions.size(); i++) {
                    int startIndex = dividerPositions.get(i);
                    int endIndex = startIndex + dividerPlaceholder.length();

                    if (start >= startIndex && start <= endIndex) {
                        isUpdatingText = true;
                        noteContent.setText(textBefore);

                        // Move cursor to next safe (non-divider) line
                        int safeCursor = findNextNonDividerLine(textBefore, endIndex, dividerPlaceholder);

                        // Set cursor safely
                        if (safeCursor < textBefore.length()) {
                            noteContent.setSelection(safeCursor);
                        } else {
                            // If we reached EOF, move to before the first divider
                            noteContent.setSelection(Math.max(0, startIndex - 1));
                        }

                        applyBookmarksToText();
                        isUpdatingText = false;
                        return;
                    }
                }
            }

            /**
             * Moves the cursor downward until it finds a line that doesn't contain a divider.
             */
            private int findNextNonDividerLine(String text, int fromIndex, String dividerPlaceholder) {
                int cursor = fromIndex;

                while (cursor < text.length()) {
                    int nextLineBreak = text.indexOf('\n', cursor);
                    if (nextLineBreak == -1) nextLineBreak = text.length();

                    String currentLine = text.substring(cursor, Math.min(nextLineBreak, text.length()));

                    // If current line has NO divider, stop here
                    if (!currentLine.contains(dividerPlaceholder)) {
                        return cursor;
                    }

                    // Otherwise, jump past this line
                    cursor = nextLineBreak + 1;
                }

                return text.length();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Your existing bookmark TextWatcher
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



    private void showDividerBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.divider_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        // Divider style options
        LinearLayout dividerSolid = sheetView.findViewById(R.id.dividerSolid);
        LinearLayout dividerDashed = sheetView.findViewById(R.id.dividerDashed);
        LinearLayout dividerDotted = sheetView.findViewById(R.id.dividerDotted);
        LinearLayout dividerDouble = sheetView.findViewById(R.id.dividerDouble);
        LinearLayout dividerArrows = sheetView.findViewById(R.id.dividerArrows);
        LinearLayout dividerStars = sheetView.findViewById(R.id.dividerStars);
        LinearLayout dividerWave = sheetView.findViewById(R.id.dividerWave);
        LinearLayout dividerDiamond = sheetView.findViewById(R.id.dividerDiamond);

        dividerSolid.setOnClickListener(v -> {
            insertDivider("solid");
            bottomSheet.dismiss();
        });

        dividerDashed.setOnClickListener(v -> {
            insertDivider("dashed");
            bottomSheet.dismiss();
        });

        dividerDotted.setOnClickListener(v -> {
            insertDivider("dotted");
            bottomSheet.dismiss();
        });

        dividerDouble.setOnClickListener(v -> {
            insertDivider("double");
            bottomSheet.dismiss();
        });

        dividerArrows.setOnClickListener(v -> {
            insertDivider("arrows");
            bottomSheet.dismiss();
        });

        dividerStars.setOnClickListener(v -> {
            insertDivider("stars");
            bottomSheet.dismiss();
        });

        dividerWave.setOnClickListener(v -> {
            insertDivider("wave");
            bottomSheet.dismiss();
        });

        dividerDiamond.setOnClickListener(v -> {
            insertDivider("diamond");
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }

    private boolean isDividerLine(String line) {
        return line.contains("〔DIVIDER〕");
    }

    private int[] getLineBounds(String content, int pos) {
        // Find start of line (character after previous newline, or 0)
        int start = content.lastIndexOf('\n', pos - 1) + 1;

        // Find end of line (position of next newline, or end of content)
        int end = content.indexOf('\n', pos);
        if (end == -1) end = content.length();

        return new int[]{start, end};
    }


    private void insertDivider(String style) {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();
        String dividerPlaceholder = "〔DIVIDER〕";

        // Add newlines around divider for proper spacing
        String textToInsert;

        // Check if we need leading newline
        if (cursorPosition > 0 && currentText.charAt(cursorPosition - 1) != '\n') {
            textToInsert = "\n" + dividerPlaceholder;
        } else {
            textToInsert = dividerPlaceholder;
        }

        // Always add trailing newline
        textToInsert += "\n";

        int insertLength = textToInsert.length();
        int leadingNewline = textToInsert.startsWith("\n") ? 1 : 0;

        FirebaseUser user = auth.getCurrentUser();

        // ✅ SPLIT OR UPDATE BOOKMARKS
        if (user != null) {
            for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                int bStart = bookmark.getStartIndex();
                int bEnd = bookmark.getEndIndex();

                // Case 1: Divider inserted INSIDE bookmark - SPLIT IT
                if (cursorPosition > bStart && cursorPosition < bEnd) {
                    // Create first part (before divider)
                    int firstPartEnd = cursorPosition;
                    String firstPartText = currentText.substring(bStart, firstPartEnd).trim();

                    if (!firstPartText.isEmpty()) {
                        // Update existing bookmark to be the first part
                        updateBookmarkInFirestore(bookmark.getId(), bStart, firstPartEnd, firstPartText);
                    }

                    // Create second part (after divider)
                    int secondPartStart = cursorPosition + insertLength;
                    int secondPartEnd = bEnd + insertLength;
                    String secondPartText = currentText.substring(cursorPosition, bEnd).trim();

                    if (!secondPartText.isEmpty()) {
                        // Create new bookmark for second part
                        Bookmark newBookmark = new Bookmark(
                                secondPartText,
                                bookmark.getNote(),
                                bookmark.getColor(),
                                bookmark.getStyle(),
                                secondPartStart,
                                secondPartEnd
                        );

                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("bookmarks").add(newBookmark);
                    }

                    // If first part is empty, delete the original bookmark
                    if (firstPartText.isEmpty()) {
                        deleteBookmarkFromFirestore(bookmark.getId());
                    }
                }
                // Case 2: Divider inserted BEFORE bookmark - SHIFT bookmark
                else if (cursorPosition <= bStart) {
                    updateBookmarkInFirestore(bookmark.getId(),
                            bStart + insertLength,
                            bEnd + insertLength,
                            bookmark.getText());
                }
                // Case 3: Divider inserted AFTER bookmark - NO CHANGE needed
            }
        }

        String newText = currentText.substring(0, cursorPosition) +
                textToInsert +
                currentText.substring(cursorPosition);

        // Temporarily disable text watcher
        isUpdatingText = true;

        // Create spannable with divider
        android.text.SpannableString spannable = new android.text.SpannableString(newText);
        int dividerStart = cursorPosition + leadingNewline;
        int dividerEnd = dividerStart + dividerPlaceholder.length();

        dividerStyles.put(dividerStart, style);

        // Apply the divider span
        spannable.setSpan(
                new DividerSpan(style, 0xFF666666),
                dividerStart,
                dividerEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        noteContent.setText(spannable);

        // Move cursor after the divider
        int newCursorPos = dividerEnd + 1;
        noteContent.setSelection(newCursorPos);

        // Re-apply existing bookmarks
        noteContent.postDelayed(() -> {
            applyBookmarksToText();
            isUpdatingText = false;
        }, 100);

        // Save the divider to Firestore
        saveNoteContentToFirestore(newText);

        Toast.makeText(this, "Divider added", Toast.LENGTH_SHORT).show();
    }
    private void showDividerActionMenu(int dividerPosition) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.divider_action_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        LinearLayout moveUpBtn = sheetView.findViewById(R.id.moveUpBtn);
        LinearLayout moveDownBtn = sheetView.findViewById(R.id.moveDownBtn);
        LinearLayout duplicateBtn = sheetView.findViewById(R.id.duplicateBtn);
        LinearLayout deleteBtn = sheetView.findViewById(R.id.deleteBtn);

        moveUpBtn.setOnClickListener(v -> {
            moveDivider(dividerPosition, true);
            bottomSheet.dismiss();
        });

        moveDownBtn.setOnClickListener(v -> {
            moveDivider(dividerPosition, false);
            bottomSheet.dismiss();
        });

        duplicateBtn.setOnClickListener(v -> {
            duplicateDivider(dividerPosition);
            bottomSheet.dismiss();
        });

        deleteBtn.setOnClickListener(v -> {
            deleteDivider(dividerPosition);
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }
    private void rebuildDividerStyles(String content) {
        String dividerPlaceholder = "〔DIVIDER〕";

        // Store old styles in order they appear
        List<String> stylesInOrder = new ArrayList<>();
        List<Integer> oldPositions = new ArrayList<>(dividerStyles.keySet());
        java.util.Collections.sort(oldPositions);

        for (int pos : oldPositions) {
            stylesInOrder.add(dividerStyles.get(pos));
        }

        // Clear and rebuild
        dividerStyles.clear();

        int searchPos = 0;
        int styleIndex = 0;
        while ((searchPos = content.indexOf(dividerPlaceholder, searchPos)) != -1) {
            String style = styleIndex < stylesInOrder.size() ? stylesInOrder.get(styleIndex) : "solid";
            dividerStyles.put(searchPos, style);
            searchPos += dividerPlaceholder.length();
            styleIndex++;
        }
    }
    private void moveDivider(int dividerPos, boolean moveUp) {
        String content = noteContent.getText().toString();
        String dividerPlaceholder = "〔DIVIDER〕";
        String style = dividerStyles.get(dividerPos);

        int[] bounds = getLineBounds(content, dividerPos);
        int lineStart = bounds[0];
        int lineEnd = bounds[1];

        int targetStart, targetEnd;
        if (moveUp) {
            if (lineStart == 0) {
                Toast.makeText(this, "Already at top", Toast.LENGTH_SHORT).show();
                return;
            }
            targetEnd = lineStart - 1;
            if (targetEnd < 0) {
                Toast.makeText(this, "Already at top", Toast.LENGTH_SHORT).show();
                return;
            }
            targetStart = content.lastIndexOf('\n', targetEnd - 1) + 1;
        } else {
            if (lineEnd >= content.length() - 1) {
                Toast.makeText(this, "Already at bottom", Toast.LENGTH_SHORT).show();
                return;
            }
            targetStart = lineEnd + 1;
            if (targetStart >= content.length()) {
                Toast.makeText(this, "Already at bottom", Toast.LENGTH_SHORT).show();
                return;
            }
            targetEnd = content.indexOf('\n', targetStart);
            if (targetEnd == -1) targetEnd = content.length();
        }

        String dividerLine = content.substring(lineStart, lineEnd);
        String targetLine = content.substring(targetStart, targetEnd);

        // ✅ STEP 1: Build new content FIRST
        StringBuilder newContent = new StringBuilder();

        if (moveUp) {
            newContent.append(content.substring(0, targetStart));
            newContent.append(dividerLine);
            newContent.append("\n");
            newContent.append(targetLine);
            newContent.append(content.substring(lineEnd));
        } else {
            newContent.append(content.substring(0, lineStart));
            newContent.append(targetLine);
            newContent.append("\n");
            newContent.append(dividerLine);
            newContent.append(content.substring(targetEnd));
        }

        String finalNewContent = newContent.toString();

        // ✅ STEP 2: Calculate position changes
        int dividerLength = dividerLine.length() + 1;
        int targetLength = targetLine.length() + 1;

        // ✅ STEP 3: Update text
        isUpdatingText = true;
        noteContent.setText(finalNewContent);
        isUpdatingText = false;

        // ✅ STEP 4: Find new divider position
        int newDividerPos = finalNewContent.indexOf(dividerPlaceholder);
        int[] newDividerBounds = getLineBounds(finalNewContent, newDividerPos);
        int newDividerStart = newDividerBounds[0];
        int newDividerEnd = newDividerBounds[1];

        // ✅ STEP 5: Update ALL bookmarks based on NEW content
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            List<Bookmark> bookmarksToSplit = new ArrayList<>();
            Map<Bookmark, Bookmark> bookmarksToMerge = new LinkedHashMap<>(); // Keep order

            for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                int oldStart = bookmark.getStartIndex();
                int oldEnd = bookmark.getEndIndex();
                int newStart = oldStart;
                int newEnd = oldEnd;
                boolean needsUpdate = false;

                if (moveUp) {
                    // Target line moved down (after divider now)
                    if (oldStart >= targetStart && oldEnd <= targetEnd) {
                        newStart = oldStart + dividerLength;
                        newEnd = oldEnd + dividerLength;
                        needsUpdate = true;
                    }
                    // Between old target and divider
                    else if (oldStart >= targetEnd + 1 && oldStart < lineStart) {
                        newStart = oldStart + dividerLength - targetLength;
                        newEnd = oldEnd + dividerLength - targetLength;
                        needsUpdate = true;
                    }
                } else {
                    // Target line moved up (before divider now)
                    if (oldStart >= targetStart && oldEnd <= targetEnd) {
                        newStart = oldStart - dividerLength;
                        newEnd = oldEnd - dividerLength;
                        needsUpdate = true;
                    }
                    // Between divider and old target
                    else if (oldStart > lineEnd && oldStart < targetStart) {
                        newStart = oldStart + targetLength - dividerLength;
                        newEnd = oldEnd + targetLength - dividerLength;
                        needsUpdate = true;
                    }
                }

                // ✅ CHECK: Does divider now split this bookmark?
                if (newStart < newDividerStart && newEnd > newDividerEnd) {
                    bookmarksToSplit.add(bookmark);
                    continue;
                }

                if (needsUpdate && newStart >= 0 && newEnd <= finalNewContent.length() && newStart < newEnd) {
                    try {
                        String newText = finalNewContent.substring(newStart, newEnd).trim();
                        if (!newText.isEmpty() && !newText.contains(dividerPlaceholder)) {
                            updateBookmarkInFirestore(bookmark.getId(), newStart, newEnd, newText);
                            bookmark.setStartIndex(newStart);
                            bookmark.setEndIndex(newEnd);
                            bookmark.setText(newText);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            // ✅ STEP 6: Check for bookmarks that should be MERGED
            // Find bookmarks that are now adjacent (no divider between them anymore)
            List<Bookmark> sortedBookmarks = new ArrayList<>(currentBookmarks);
            java.util.Collections.sort(sortedBookmarks, new java.util.Comparator<Bookmark>() {
                @Override
                public int compare(Bookmark b1, Bookmark b2) {
                    return Integer.compare(b1.getStartIndex(), b2.getStartIndex());
                }
            });
            for (int i = 0; i < sortedBookmarks.size() - 1; i++) {
                Bookmark first = sortedBookmarks.get(i);
                Bookmark second = sortedBookmarks.get(i + 1);

                // Check if they have the same color and style
                if (!first.getColor().equals(second.getColor()) ||
                        !first.getStyle().equals(second.getStyle())) {
                    continue;
                }

                // Check if they are adjacent (separated only by whitespace/newline)
                int gapStart = first.getEndIndex();
                int gapEnd = second.getStartIndex();

                if (gapStart < gapEnd && gapEnd <= finalNewContent.length()) {
                    try {
                        String between = finalNewContent.substring(gapStart, gapEnd);
                        // If only whitespace/newlines between them, AND no divider, merge them
                        if (between.replaceAll("\\s", "").isEmpty() &&
                                !between.contains(dividerPlaceholder)) {
                            bookmarksToMerge.put(first, second);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            // ✅ STEP 7: Perform merges
            for (Map.Entry<Bookmark, Bookmark> entry : bookmarksToMerge.entrySet()) {
                Bookmark first = entry.getKey();
                Bookmark second = entry.getValue();

                // Merge into the first bookmark
                int mergedStart = first.getStartIndex();
                int mergedEnd = second.getEndIndex();

                try {
                    String mergedText = finalNewContent.substring(mergedStart, mergedEnd).trim();

                    if (!mergedText.isEmpty() && !mergedText.contains(dividerPlaceholder)) {
                        // Update first bookmark
                        updateBookmarkInFirestore(first.getId(), mergedStart, mergedEnd, mergedText);
                        first.setStartIndex(mergedStart);
                        first.setEndIndex(mergedEnd);
                        first.setText(mergedText);

                        // Delete second bookmark
                        deleteBookmarkFromFirestore(second.getId());
                        currentBookmarks.remove(second);

                        Toast.makeText(this, "Bookmarks merged", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // ✅ STEP 8: Split bookmarks if divider is between them
            for (Bookmark bookmark : bookmarksToSplit) {
                int bStart = bookmark.getStartIndex();
                int bEnd = bookmark.getEndIndex();

                // Recalculate positions based on move
                if (moveUp) {
                    if (bStart >= targetStart && bStart < lineStart) {
                        bStart += dividerLength - targetLength;
                        bEnd += dividerLength - targetLength;
                    }
                } else {
                    if (bStart > lineEnd && bStart < targetStart) {
                        bStart += targetLength - dividerLength;
                        bEnd += targetLength - dividerLength;
                    }
                }

                // Create first part (before divider)
                int firstPartEnd = newDividerStart - 1;
                if (firstPartEnd > bStart) {
                    String firstPartText = finalNewContent.substring(bStart, firstPartEnd).trim();
                    if (!firstPartText.isEmpty()) {
                        updateBookmarkInFirestore(bookmark.getId(), bStart, firstPartEnd, firstPartText);
                        bookmark.setStartIndex(bStart);
                        bookmark.setEndIndex(firstPartEnd);
                        bookmark.setText(firstPartText);
                    }
                }

                // Create second part (after divider)
                int secondPartStart = newDividerEnd + 1;
                if (secondPartStart < bEnd && secondPartStart < finalNewContent.length()) {
                    String secondPartText = finalNewContent.substring(secondPartStart, Math.min(bEnd, finalNewContent.length())).trim();
                    if (!secondPartText.isEmpty()) {
                        Bookmark newBookmark = new Bookmark(
                                secondPartText,
                                bookmark.getNote(),
                                bookmark.getColor(),
                                bookmark.getStyle(),
                                secondPartStart,
                                Math.min(bEnd, finalNewContent.length())
                        );
                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("bookmarks").add(newBookmark);
                    }
                }
            }
        }

        // ✅ STEP 9: Rebuild divider styles
        rebuildDividerStyles(finalNewContent);

        // ✅ STEP 10: Reapply highlights
        noteContent.postDelayed(() -> {
            applyBookmarksToText();
        }, 150);

        saveNoteContentToFirestore(finalNewContent);
    }
    private void duplicateDivider(int dividerPos) {
        String content = noteContent.getText().toString();
        String dividerPlaceholder = "〔DIVIDER〕";
        String style = dividerStyles.get(dividerPos);

        int[] bounds = getLineBounds(content, dividerPos);
        int lineEnd = bounds[1];

        // Insert new divider one line below
        String newContent = content.substring(0, lineEnd) +
                "\n" + dividerPlaceholder + "\n" +
                content.substring(lineEnd);

        isUpdatingText = true;
        noteContent.setText(newContent.replaceAll("\n{2,}", "\n"));
        isUpdatingText = false;

        String updatedText = noteContent.getText().toString();

        // ✅ FIX: Add the new divider style before rebuilding
        int newDividerPos = updatedText.indexOf(dividerPlaceholder, lineEnd + 1);
        if (newDividerPos != -1) {
            dividerStyles.put(newDividerPos, style);
        }
        rebuildDividerStyles(updatedText);

        applyBookmarksToText();
        saveNoteContentToFirestore(updatedText);
        Toast.makeText(this, "Divider duplicated", Toast.LENGTH_SHORT).show();
    }

    private void deleteDivider(int dividerPos) {
        String content = noteContent.getText().toString();
        String dividerPlaceholder = "〔DIVIDER〕";

        int[] bounds = getLineBounds(content, dividerPos);
        int lineStart = bounds[0];
        int lineEnd = bounds[1];

        mergeSplitBookmarks(lineStart, lineEnd, () -> {

            String currentContent = noteContent.getText().toString();
            int[] currentBounds = getLineBounds(currentContent, dividerPos);
            int currentLineStart = currentBounds[0];
            int currentLineEnd = currentBounds[1];

            int removeLength = currentLineEnd - currentLineStart + 1;

            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                    if (bookmark.getStartIndex() > currentLineEnd) {
                        updateBookmarkInFirestore(bookmark.getId(),
                                bookmark.getStartIndex() - removeLength,
                                bookmark.getEndIndex() - removeLength,
                                bookmark.getText());
                    }
                }
            }

            String newContent = currentContent.substring(0, Math.max(0, currentLineStart - 1)) +
                    currentContent.substring(Math.min(currentLineEnd + 1, currentContent.length()));

            isUpdatingText = true;
            noteContent.setText(newContent.replaceAll("\n{2,}", "\n"));
            isUpdatingText = false;

            rebuildDividerStyles(noteContent.getText().toString());

            // ✅ Reapply after deletion
            noteContent.postDelayed(() -> {
                applyBookmarksToText();
            }, 200);

            saveNoteContentToFirestore(noteContent.getText().toString());
            Toast.makeText(this, "Divider deleted", Toast.LENGTH_SHORT).show();
        });
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

        Map<String, String> dividerStylesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : dividerStyles.entrySet()) {
            dividerStylesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // Add toggle states
        Map<String, Boolean> toggleStatesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, Boolean> entry : toggleStates.entrySet()) {
            toggleStatesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // ✅ ADD THIS: Save toggle contents
        Map<String, String> toggleContentsForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : toggleContents.entrySet()) {
            toggleContentsForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("content", content);
        updates.put("timestamp", System.currentTimeMillis());
        updates.put("dividerStyles", dividerStylesForFirestore);
        updates.put("toggleStates", toggleStatesForFirestore);
        updates.put("toggleContents", toggleContentsForFirestore); // ✅ ADD THIS LINE

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
    protected void onPause() {
        super.onPause();
        autoSaveNote();
    }
    private void autoSaveNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) return;

        String title = noteTitle.getText().toString();
        String content = noteContent.getText().toString();

        // Convert divider styles for Firestore
        Map<String, String> dividerStylesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : dividerStyles.entrySet()) {
            dividerStylesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // Convert toggle states for Firestore
        Map<String, Boolean> toggleStatesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, Boolean> entry : toggleStates.entrySet()) {
            toggleStatesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // ✅ ADD THIS: Convert toggle contents for Firestore
        Map<String, String> toggleContentsForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : toggleContents.entrySet()) {
            toggleContentsForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("title", title);
        noteData.put("content", content);
        noteData.put("color", currentNoteColor);
        noteData.put("timestamp", System.currentTimeMillis());
        noteData.put("dividerStyles", dividerStylesForFirestore);
        noteData.put("toggleStates", toggleStatesForFirestore);
        noteData.put("toggleContents", toggleContentsForFirestore); // ✅ ADD THIS LINE

        db.collection("users").document(user.getUid())
                .collection("notes")
                .document(noteId)
                .set(noteData)
                .addOnSuccessListener(aVoid -> {
                    Log.d("NoteActivity", "✅ Auto-saved note");
                })
                .addOnFailureListener(e -> {
                    Log.e("NoteActivity", "❌ Auto-save failed", e);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookmarkListener != null) bookmarkListener.remove();
    }
    private void setupTextSelection() {
        noteContent.setOnLongClickListener(v -> {
            int cursorPos = noteContent.getSelectionStart();

            // ✅ Check if long-press is on a divider
            String content = noteContent.getText().toString();
            String dividerPlaceholder = "〔DIVIDER〕";

            int dividerIndex = content.indexOf(dividerPlaceholder);
            while (dividerIndex != -1) {
                int dividerEnd = dividerIndex + dividerPlaceholder.length();

                if (cursorPos >= dividerIndex && cursorPos <= dividerEnd) {
                    // Show divider action menu
                    showDividerActionMenu(dividerIndex);
                    return true; // Consume the event
                }

                dividerIndex = content.indexOf(dividerPlaceholder, dividerEnd);
            }

            return false; // Let default behavior happen
        });

        noteContent.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                int start = noteContent.getSelectionStart();
                int end = noteContent.getSelectionEnd();

                String selectedText = noteContent.getText().toString().substring(start, end);

                // ✅ Check if selection contains divider
                if (selectedText.contains("〔DIVIDER〕")) {
                    noteContent.setSelection(start);
                    return false;
                }

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

                        // ✅ Load divider styles
                        Map<String, Object> savedStyles = (Map<String, Object>) doc.get("dividerStyles");
                        if (savedStyles != null) {
                            dividerStyles.clear();
                            for (Map.Entry<String, Object> entry : savedStyles.entrySet()) {
                                try {
                                    int position = Integer.parseInt(entry.getKey());
                                    String style = (String) entry.getValue();
                                    dividerStyles.put(position, style);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        // ✅ Load toggle states
                        Map<String, Object> savedToggleStates = (Map<String, Object>) doc.get("toggleStates");
                        if (savedToggleStates != null) {
                            toggleStates.clear();
                            for (Map.Entry<String, Object> entry : savedToggleStates.entrySet()) {
                                try {
                                    int position = Integer.parseInt(entry.getKey());
                                    Boolean state = (Boolean) entry.getValue();
                                    toggleStates.put(position, state);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        // ✅ ADD THIS: Load toggle contents
                        Map<String, Object> savedToggleContents = (Map<String, Object>) doc.get("toggleContents");
                        if (savedToggleContents != null) {
                            toggleContents.clear();
                            for (Map.Entry<String, Object> entry : savedToggleContents.entrySet()) {
                                try {
                                    int position = Integer.parseInt(entry.getKey());
                                    String toggleContent = (String) entry.getValue();
                                    toggleContents.put(position, toggleContent);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

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
                        // ✅ FIX: Show/hide the RecyclerView instead of the add button
                        subpagesRecyclerView.setVisibility(hasSubpages ? View.VISIBLE : View.GONE);
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

        // ✅ Convert Integer keys to String keys for Firestore
        Map<String, String> dividerStylesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : dividerStyles.entrySet()) {
            dividerStylesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("title", title);
        noteData.put("content", content);
        noteData.put("color", currentNoteColor);
        noteData.put("timestamp", System.currentTimeMillis());
        noteData.put("dividerStyles", dividerStylesForFirestore);

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

//BOOKMARK FUNCTION
private void updateBookmarkIndices(int changePosition, int lengthDiff) {
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) return;

    if (isUpdatingText || isTogglingState) return;
    String currentText = noteContent.getText().toString();

    if (currentText.equals(lastSavedContent)) return;

    String dividerPlaceholder = "〔DIVIDER〕";

    // ✅ Check if a divider was just inserted
    boolean dividerInserted = false;
    int dividerLength = 0;
    if (lengthDiff > 0 && changePosition + lengthDiff <= currentText.length()) {
        String inserted = currentText.substring(changePosition, changePosition + lengthDiff);
        if (inserted.contains(dividerPlaceholder)) {
            dividerInserted = true;
            dividerLength = inserted.length();
        }
    }

    boolean anyBookmarkUpdated = false;
    List<Bookmark> bookmarksCopy = new ArrayList<>(currentBookmarks);

    for (Bookmark bookmark : bookmarksCopy) {
        int start = bookmark.getStartIndex();
        int end = bookmark.getEndIndex();
        boolean needsUpdate = false;
        boolean shouldDelete = false;

        // ✅ If divider inserted, just shift bookmarks - DON'T modify their range
        if (dividerInserted) {
            if (changePosition <= start) {
                start += dividerLength;
                end += dividerLength;
                needsUpdate = true;
            } else if (changePosition >= end) {
                // No change
            } else if (changePosition > start && changePosition < end) {
                end += dividerLength;
                needsUpdate = true;
            }
        }
        // ✅ Case 1: Edit BEFORE bookmark → shift entire range
        else if (changePosition < start) {
            start += lengthDiff;
            end += lengthDiff;
            needsUpdate = true;
        }
        // ✅ Case 2: Edit INSIDE bookmark → adjust end
        else if (changePosition >= start && changePosition < end) {
            end += lengthDiff;
            needsUpdate = true;

            // ✅ Check if bookmark became invalid (too short or deleted)
            if (end <= start) {
                shouldDelete = true;
            }
        }
        // ✅ Case 3: Edit RIGHT AT end boundary
        else if (changePosition == end && lengthDiff > 0) {
            // Only expand if inserting non-whitespace
            if (changePosition < currentText.length()) {
                int insStart = changePosition;
                int insEnd = Math.min(currentText.length(), changePosition + lengthDiff);

                if (insStart >= 0 && insEnd > insStart) {
                    String inserted = currentText.substring(insStart, insEnd);
                    boolean hasNonWhitespace = !inserted.trim().isEmpty();

                    if (hasNonWhitespace) {
                        end += lengthDiff;
                        needsUpdate = true;
                    }
                }
            }
        }

        // ✅ Validate bounds
        if (start < 0 || end > currentText.length() || start >= end) {
            shouldDelete = true;
        }

        // ✅ Extract and validate text
        String updatedText = "";
        if (!shouldDelete && start >= 0 && end <= currentText.length() && start < end) {
            try {
                updatedText = currentText.substring(start, end);

                // Delete if contains divider
                if (updatedText.contains(dividerPlaceholder)) {
                    shouldDelete = true;
                }
                // Delete if only whitespace
                else if (updatedText.trim().isEmpty()) {
                    shouldDelete = true;
                }
            } catch (Exception e) {
                shouldDelete = true;
            }
        }

        if (shouldDelete) {
            deleteBookmarkFromFirestore(bookmark.getId());
            currentBookmarks.remove(bookmark);
            anyBookmarkUpdated = true;
        } else if (needsUpdate) {
            // ✅ Update both Firestore AND local object immediately
            updateBookmarkInFirestore(bookmark.getId(), start, end, updatedText.trim());
            bookmark.setStartIndex(start);
            bookmark.setEndIndex(end);
            bookmark.setText(updatedText.trim());
            anyBookmarkUpdated = true;
        }
    }

    if (anyBookmarkUpdated) {
        saveNoteContentDebounced(currentText);
        // ✅ Reapply visuals immediately
        noteContent.postDelayed(() -> applyBookmarksToText(), 100);
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
    private void expandBookmark(Bookmark bookmark, int newStart, int newEnd) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String currentText = noteContent.getText().toString();
        int expandedStart = Math.min(bookmark.getStartIndex(), newStart);
        int expandedEnd = Math.max(bookmark.getEndIndex(), newEnd);

        // Bounds safety
        if (expandedStart < 0 || expandedEnd > currentText.length() || expandedStart >= expandedEnd) {
            Toast.makeText(this, "Invalid selection range", Toast.LENGTH_SHORT).show();
            return;
        }

        // Trim outer spaces
        while (expandedStart < expandedEnd && expandedStart < currentText.length()
                && Character.isWhitespace(currentText.charAt(expandedStart))) {
            expandedStart++;
        }
        while (expandedEnd > expandedStart && expandedEnd > 0
                && Character.isWhitespace(currentText.charAt(expandedEnd - 1))) {
            expandedEnd--;
        }

        // Extract and clean the selected text
        String expandedText = currentText.substring(expandedStart, expandedEnd);
        // ✅ ADD THIS CHECK
        if (expandedText.contains("〔DIVIDER〕")) {
            Toast.makeText(this, "Cannot include dividers in bookmarks", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Validate: must contain *at least one visible character*
        if (expandedText.trim().isEmpty()) {
            Toast.makeText(this, "Cannot bookmark empty or blank lines", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Ensure no full-line-only newlines
        if (expandedText.replaceAll("[\\n\\r\\s]+", "").isEmpty()) {
            Toast.makeText(this, "Bookmark must contain text, not just blank lines", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Create final variables for lambda
        final int finalExpandedStart = expandedStart;
        final int finalExpandedEnd = expandedEnd;
        final String finalExpandedText = expandedText;

        // Save update to Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put("startIndex", finalExpandedStart);
        updates.put("endIndex", finalExpandedEnd);
        updates.put("text", finalExpandedText);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").document(bookmark.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    bookmark.setStartIndex(finalExpandedStart);
                    bookmark.setEndIndex(finalExpandedEnd);
                    bookmark.setText(finalExpandedText);

                    Toast.makeText(this, "Bookmark expanded", Toast.LENGTH_SHORT).show();
                    applyBookmarksToText();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error expanding bookmark", Toast.LENGTH_SHORT).show();
                });
    }
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
    private void setColorScale(View violet, View yellow, View pink, View green, View blue, View orange, View red, View cyan, String currentColor) {
        resetColorSelection(violet, yellow, pink, green, blue, orange, red, cyan);

        View selectedView = null;
        switch (currentColor) {
            case "#FFCDD2": selectedView = red; break;
            case "#F8BBD0": selectedView = pink; break;
            case "#E1BEE7": selectedView = violet; break;
            case "#BBDEFB": selectedView = blue; break;
            case "#B2EBF2": selectedView = cyan; break;
            case "#C8E6C9": selectedView = green; break;
            case "#FFF9C4": selectedView = yellow; break;
            case "#FFE0B2": selectedView = orange; break;
        }

        if (selectedView != null) {
            selectedView.setScaleX(1.2f);
            selectedView.setScaleY(1.2f);
        }
    }
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
    private boolean isTextAlreadyBookmarked(int start, int end) {
        for (Bookmark bookmark : currentBookmarks) {
            if ((start >= bookmark.getStartIndex() && start < bookmark.getEndIndex()) ||
                    (end > bookmark.getStartIndex() && end <= bookmark.getEndIndex()) ||
                    (start <= bookmark.getStartIndex() && end >= bookmark.getEndIndex())) return true;
        }
        return false;
    }
    private void showBookmarkBottomSheet(String selectedText, int startIndex, int endIndex) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bookmark_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        View colorRed = sheetView.findViewById(R.id.colorRed);
        View colorPink = sheetView.findViewById(R.id.colorPink);
        View colorViolet = sheetView.findViewById(R.id.colorViolet);
        View colorBlue = sheetView.findViewById(R.id.colorBlue);
        View colorCyan = sheetView.findViewById(R.id.colorCyan);
        View colorGreen = sheetView.findViewById(R.id.colorGreen);
        View colorYellow = sheetView.findViewById(R.id.colorYellow);
        View colorOrange = sheetView.findViewById(R.id.colorOrange);


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

        // ✅ Trim whitespace and newlines from selection
        String currentText = noteContent.getText().toString();

        // Trim from start
        while (startIndex < endIndex && startIndex < currentText.length()) {
            char c = currentText.charAt(startIndex);
            if (Character.isWhitespace(c) || c == '\n' || c == '\r' || c == '\t') {
                startIndex++;
            } else {
                break;
            }
        }

        // Trim from end
        while (endIndex > startIndex && endIndex > 0) {
            char c = currentText.charAt(endIndex - 1);
            if (Character.isWhitespace(c) || c == '\n' || c == '\r' || c == '\t') {
                endIndex--;
            } else {
                break;
            }
        }

        // Validate trimmed range
        if (startIndex >= endIndex || startIndex < 0 || endIndex > currentText.length()) {
            Toast.makeText(this, "Invalid selection - only whitespace selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the trimmed text
        String trimmedText = currentText.substring(startIndex, endIndex);
        // ✅ ADD THIS CHECK
        if (trimmedText.contains("〔DIVIDER〕")) {
            Toast.makeText(this, "Cannot bookmark divider lines", Toast.LENGTH_SHORT).show();
            return;
        }

        Bookmark bookmark = new Bookmark(trimmedText, note, color, style, startIndex, endIndex);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").add(bookmark)
                .addOnSuccessListener(doc -> {
                    Toast.makeText(this, "Bookmark created", Toast.LENGTH_SHORT).show();
                    // ✅ IMPORTANT: Save note content immediately after creating bookmark
                    saveNoteContentToFirestore(currentText);
                })
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

        android.text.SpannableString span = new android.text.SpannableString(content);

        String dividerPlaceholder = "〔DIVIDER〕";

        // ✅ Collect all divider positions first
        List<int[]> dividerRanges = new ArrayList<>();
        int dividerIndex = 0;
        while ((dividerIndex = content.indexOf(dividerPlaceholder, dividerIndex)) != -1) {
            dividerRanges.add(new int[]{dividerIndex, dividerIndex + dividerPlaceholder.length()});
            dividerIndex += dividerPlaceholder.length();
        }

        // Apply all dividers with their saved styles
        Map<Integer, String> newDividerStyles = new HashMap<>();
        dividerIndex = 0;
        while ((dividerIndex = content.indexOf(dividerPlaceholder, dividerIndex)) != -1) {
            int dividerEnd = dividerIndex + dividerPlaceholder.length();

            String style = "solid";
            for (Map.Entry<Integer, String> entry : dividerStyles.entrySet()) {
                int savedPos = entry.getKey();
                if (Math.abs(savedPos - dividerIndex) < 10) {
                    style = entry.getValue();
                    break;
                }
            }

            newDividerStyles.put(dividerIndex, style);

            span.setSpan(
                    new DividerSpan(style, 0xFF666666),
                    dividerIndex,
                    dividerEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            dividerIndex = dividerEnd;
        }

        dividerStyles = newDividerStyles;

        // ✅ Apply bookmarks (AVOID divider areas completely)
        for (Bookmark b : currentBookmarks) {
            int s = b.getStartIndex();
            int e = b.getEndIndex();

            // Validate bounds
            if (s < 0 || e > content.length() || s >= e) continue;

            String bookmarkText = content.substring(s, e);

            // ✅ Skip if bookmark contains divider placeholder
            if (bookmarkText.contains(dividerPlaceholder)) continue;

            // ✅ Check if bookmark overlaps with any divider range
            boolean overlaps = false;
            for (int[] dividerRange : dividerRanges) {
                int dStart = dividerRange[0];
                int dEnd = dividerRange[1];

                // Check if bookmark overlaps with this divider
                if ((s >= dStart && s < dEnd) || (e > dStart && e <= dEnd) ||
                        (s < dStart && e > dEnd)) {
                    overlaps = true;
                    break;
                }
            }

            if (overlaps) continue;

            try {
                int color = android.graphics.Color.parseColor(b.getColor());
                if ("highlight".equals(b.getStyle()))
                    span.setSpan(new android.text.style.BackgroundColorSpan(color), s, e, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                else if ("underline".equals(b.getStyle()))
                    span.setSpan(new CustomUnderlineSpan(color, s, e), s, e, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignored) {}
        }

        int sel = noteContent.getSelectionStart();
        noteContent.setText(span, android.widget.TextView.BufferType.SPANNABLE);
        if (sel >= 0 && sel <= content.length()) noteContent.setSelection(sel);

        lastSavedContent = content;
        isUpdatingText = false;

        // Apply toggle arrow colors based on content
        String[] lines = content.split("\n");
        int currentPos = 0;

        for (String line : lines) {
            if (line.matches("^\\s*[▶▼]\\s.*")) {
                int arrowPos = currentPos + line.indexOf("▶") >= 0 ?
                        line.indexOf("▶") : line.indexOf("▼");

                // Check if toggle has content
                boolean hasContent = false;
                int nextLinePos = currentPos + line.length() + 1;

                if (nextLinePos < content.length()) {
                    int nextLineEnd = content.indexOf('\n', nextLinePos);
                    if (nextLineEnd == -1) nextLineEnd = content.length();

                    if (nextLineEnd > nextLinePos) {
                        String nextLine = content.substring(nextLinePos, nextLineEnd);
                        hasContent = nextLine.startsWith("    ") &&
                                !nextLine.trim().equals("Empty toggle");
                    }
                }

                // Set arrow color: grey if empty, black if has content
                int arrowColor = hasContent ? 0xFF000000 : 0xFF999999;
                span.setSpan(
                        new android.text.style.ForegroundColorSpan(arrowColor),
                        currentPos + arrowPos,
                        currentPos + arrowPos + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }

            currentPos += line.length() + 1;
        }
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
    private void mergeSplitBookmarks(int dividerLineStart, int dividerLineEnd, Runnable onComplete) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        String content = noteContent.getText().toString();

        // Find bookmarks immediately before and after the divider
        Bookmark bookmarkBefore = null;
        Bookmark bookmarkAfter = null;

        for (Bookmark bookmark : currentBookmarks) {
            // Bookmark ends right before divider line
            if (bookmark.getEndIndex() <= dividerLineStart && bookmark.getEndIndex() >= dividerLineStart - 50) {
                if (bookmarkBefore == null || bookmark.getEndIndex() > bookmarkBefore.getEndIndex()) {
                    bookmarkBefore = bookmark;
                }
            }
            // Bookmark starts right after divider line
            if (bookmark.getStartIndex() >= dividerLineEnd && bookmark.getStartIndex() <= dividerLineEnd + 50) {
                if (bookmarkAfter == null || bookmark.getStartIndex() < bookmarkAfter.getStartIndex()) {
                    bookmarkAfter = bookmark;
                }
            }
        }

        // If we found matching bookmarks with same color and style, merge them
        if (bookmarkBefore != null && bookmarkAfter != null &&
                bookmarkBefore.getColor().equals(bookmarkAfter.getColor()) &&
                bookmarkBefore.getStyle().equals(bookmarkAfter.getStyle())) {

            // Calculate new positions BEFORE divider removal
            int dividerLength = dividerLineEnd - dividerLineStart + 1;

            int mergedStart = bookmarkBefore.getStartIndex();
            int mergedEnd = bookmarkAfter.getEndIndex() - dividerLength;

            String beforeText = content.substring(bookmarkBefore.getStartIndex(), bookmarkBefore.getEndIndex());
            String afterText = content.substring(bookmarkAfter.getStartIndex(), bookmarkAfter.getEndIndex());
            String mergedText = beforeText + afterText;

            final Bookmark finalBookmarkBefore = bookmarkBefore;
            final Bookmark finalBookmarkAfter = bookmarkAfter;
            final int finalMergedStart = mergedStart;
            final int finalMergedEnd = mergedEnd;
            final String finalMergedText = mergedText;

            Map<String, Object> updates = new HashMap<>();
            updates.put("startIndex", finalMergedStart);
            updates.put("endIndex", finalMergedEnd);
            updates.put("text", finalMergedText);

            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("bookmarks").document(finalBookmarkBefore.getId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Update local bookmark immediately
                        finalBookmarkBefore.setStartIndex(finalMergedStart);
                        finalBookmarkBefore.setEndIndex(finalMergedEnd);
                        finalBookmarkBefore.setText(finalMergedText);

                        // Delete the second bookmark
                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("bookmarks").document(finalBookmarkAfter.getId())
                                .delete()
                                .addOnSuccessListener(aVoid2 -> {
                                    // Remove from local list
                                    currentBookmarks.remove(finalBookmarkAfter);

                                    // ✅ Run callback after both operations complete
                                    if (onComplete != null) {
                                        onComplete.run();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (onComplete != null) onComplete.run();
                                });
                    })
                    .addOnFailureListener(e -> {
                        if (onComplete != null) onComplete.run();
                    });
        } else {
            // No merge needed, run callback immediately
            if (onComplete != null) onComplete.run();
        }
    }


//COLORS
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
        findViewById(R.id.colorPink).setOnClickListener(v -> changeNoteColor("#F8BBD0"));
        findViewById(R.id.colorPurple).setOnClickListener(v -> changeNoteColor("#E1BEE7"));
        findViewById(R.id.colorBlue).setOnClickListener(v -> changeNoteColor("#BBDEFB"));
        findViewById(R.id.colorCyan).setOnClickListener(v -> changeNoteColor("#B2EBF2"));
        findViewById(R.id.colorGreen).setOnClickListener(v -> changeNoteColor("#C8E6C9"));
        findViewById(R.id.colorYellow).setOnClickListener(v -> changeNoteColor("#FFF9C4"));
        findViewById(R.id.colorOrange).setOnClickListener(v -> changeNoteColor("#FFE0B2"));
        findViewById(R.id.colorBrown).setOnClickListener(v -> changeNoteColor("#D7CCC8"));
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

// UNIVERSAL INDENT - Works for bullets, numbers, and regular text
    private void indentLine() {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();

        // Find the start of the current line
        int lineStart = currentText.lastIndexOf('\n', cursorPosition - 1) + 1;
        int lineEnd = currentText.indexOf('\n', cursorPosition);
        if (lineEnd == -1) lineEnd = currentText.length();

        String currentLine = currentText.substring(lineStart, lineEnd);
        String newLine;
        //  Check if it's a toggle line
        if (currentLine.matches("^\\s*[▶▼]\\s.*")) {
            newLine = "    " + currentLine;
        }
        // Check if it's a bullet line
        if (currentLine.matches("^\\s*[●○■]\\s.*")) {
            newLine = indentBulletLine(currentLine);
        }
        // Check if it's a numbered line
        else if (currentLine.matches("^\\s*\\d+[.)]*\\s.*") ||
                currentLine.matches("^\\s*[a-z][.)]*\\s.*") ||
                currentLine.matches("^\\s*[ivx]+[.)]*\\s.*")) {
            newLine = indentNumberedLine(currentLine);
        }
        // Regular text - just add 4 spaces
        else {
            newLine = "    " + currentLine;
        }

        String newText = currentText.substring(0, lineStart) +
                newLine +
                currentText.substring(lineEnd);

        noteContent.setText(newText);
        int addedChars = newLine.length() - currentLine.length();
        noteContent.setSelection(cursorPosition + addedChars);
    }
// UNIVERSAL OUTDENT - Works for bullets, numbers, and regular text
    private void outdentLine() {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();

        // Find the start of the current line
        int lineStart = currentText.lastIndexOf('\n', cursorPosition - 1) + 1;
        int lineEnd = currentText.indexOf('\n', cursorPosition);
        if (lineEnd == -1) lineEnd = currentText.length();

        String currentLine = currentText.substring(lineStart, lineEnd);
        String newLine;
        // Check if it's a toggle line
        if (currentLine.matches("^\\s*[▶▼]\\s.*")) {
            if (currentLine.startsWith("    ")) {
                newLine = currentLine.substring(4);
            } else if (currentLine.startsWith("  ")) {
                newLine = currentLine.substring(2);
            } else if (currentLine.startsWith(" ")) {
                newLine = currentLine.substring(1);
            } else {
                newLine = currentLine;
            }
        }
        // Check if it's a bullet line
        if (currentLine.matches("^\\s*[●○■]\\s.*")) {
            newLine = outdentBulletLine(currentLine);
        }
        // Check if it's a numbered line
        else if (currentLine.matches("^\\s*\\d+[.)]*\\s.*") ||
                currentLine.matches("^\\s*[a-z][.)]*\\s.*") ||
                currentLine.matches("^\\s*[ivx]+[.)]*\\s.*")) {
            newLine = outdentNumberedLine(currentLine);
        }
        // Regular text - remove 4 spaces if possible
        else {
            if (currentLine.startsWith("    ")) {
                newLine = currentLine.substring(4);
            } else if (currentLine.startsWith("  ")) {
                newLine = currentLine.substring(2);
            } else if (currentLine.startsWith(" ")) {
                newLine = currentLine.substring(1);
            } else {
                newLine = currentLine;
            }
        }

        String newText = currentText.substring(0, lineStart) +
                newLine +
                currentText.substring(lineEnd);

        noteContent.setText(newText);
        int removedChars = currentLine.length() - newLine.length();
        noteContent.setSelection(Math.max(lineStart, cursorPosition - removedChars));
    }

//NUMBERED LIST
    private void insertNumberedList() {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();
        String numberedPoint = "\n1. ";

        String newText = currentText.substring(0, cursorPosition) +
                numberedPoint +
                currentText.substring(cursorPosition);

        noteContent.setText(newText);
        noteContent.setSelection(cursorPosition + numberedPoint.length());

        // Enable numbered list mode
        isNumberedListMode = true;
    }
    private void setupNumberedListWatcher() {
        noteContent.addTextChangedListener(new TextWatcher() {
            private boolean isProcessing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isProcessing || isUpdatingText || isTogglingState) return;
                //if (isProcessing) return;

                // Check if user pressed Enter (added newline)
                if (count == 1 && start < s.length() && s.charAt(start) == '\n') {
                    isProcessing = true;

                    // Get the line before the newline
                    String textBeforeNewline = s.toString().substring(0, start);
                    int lastNewlineIndex = textBeforeNewline.lastIndexOf('\n');
                    String currentLine = textBeforeNewline.substring(lastNewlineIndex + 1);

                    // Check if the current line is a numbered item (with or without content)
                    // Updated regex to handle different number formats and indentations
                    if (currentLine.matches("^\\s*\\d+[.)]*\\s.*") ||
                            currentLine.matches("^\\s*[a-z][.)]*\\s.*") ||
                            currentLine.matches("^\\s*[ivx]+[.)]*\\s.*")) {

                        // Re-enable numbered list mode if it was disabled
                        isNumberedListMode = true;

                        // Check if the current line is an empty numbered item
                        if (currentLine.matches("^\\s*\\d+[.)]*\\s*$") ||
                                currentLine.matches("^\\s*[a-z][.)]*\\s*$") ||
                                currentLine.matches("^\\s*[ivx]+[.)]*\\s*$")) {
                            // Double enter detected - exit numbered list mode
                            isNumberedListMode = false;

                            // Remove the empty numbered line
                            String newText = s.toString().substring(0, lastNewlineIndex + 1) +
                                    s.toString().substring(start + 1);
                            noteContent.setText(newText);
                            noteContent.setSelection(lastNewlineIndex + 1);
                        } else {
                            // Get the next number format based on indentation
                            String nextNumberText = getNextNumberFormat(currentLine);

                            // Add next numbered item with same indentation
                            String newText = s.toString().substring(0, start + 1) +
                                    nextNumberText +
                                    s.toString().substring(start + 1);

                            noteContent.setText(newText);
                            noteContent.setSelection(start + 1 + nextNumberText.length());
                        }
                    }

                    isProcessing = false;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

// Helper method to get the next number format based on current line
    private String getNextNumberFormat(String currentLine) {
        // Level 0: Regular numbers (1. 2. 3.)
        if (currentLine.matches("^\\d+\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d+)\\.");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                int currentNumber = Integer.parseInt(matcher.group(1));
                return (currentNumber + 1) + ". ";
            }
            return "1. ";
        }

        // Level 1: Lowercase letters (a. b. c.) with 4 spaces
        if (currentLine.matches("^\\s{4}[a-z]\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\s{4}([a-z])\\.");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                char currentLetter = matcher.group(1).charAt(0);
                char nextLetter = (char) (currentLetter + 1);
                if (nextLetter > 'z') nextLetter = 'a';
                return "    " + nextLetter + ". ";
            }
            return "    a. ";
        }

        // Level 2: Roman numerals (i. ii. iii.) with 8 spaces
        if (currentLine.matches("^\\s{8}[ivx]+\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\s{8}([ivx]+)\\.");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                String currentRoman = matcher.group(1);
                String nextRoman = getNextRoman(currentRoman);
                return "        " + nextRoman + ". ";
            }
            return "        i. ";
        }

        // Level 3+: Continue with numbers but more indentation
        if (currentLine.matches("^\\s{12,}\\d+\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\s+)(\\d+)\\.");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                String indent = matcher.group(1);
                int currentNumber = Integer.parseInt(matcher.group(2));
                return indent + (currentNumber + 1) + ". ";
            }
            return "            1. ";
        }

        return "1. ";
    }

// Helper method for roman numeral increment
    private String getNextRoman(String current) {
        String[] romans = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
        for (int i = 0; i < romans.length - 1; i++) {
            if (romans[i].equals(current)) {
                return romans[i + 1];
            }
        }
        return "i";
    }
// Helper method to indent numbered lines - RESETS TO 1/a/i based on level
    private String indentNumberedLine(String currentLine) {
        String contentAfterNumber;

        // Level 0: Regular numbers -> Level 1: Letters
        if (currentLine.matches("^\\d+\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\d+\\.\\s(.*)");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                contentAfterNumber = matcher.group(1);
                return "    a. " + contentAfterNumber;
            }
        }

        // Level 1: Letters -> Level 2: Roman numerals
        if (currentLine.matches("^\\s{4}[a-z]\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\s{4}[a-z]\\.\\s(.*)");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                contentAfterNumber = matcher.group(1);
                return "        i. " + contentAfterNumber;
            }
        }

        // Level 2: Roman numerals -> Level 3: Numbers again
        if (currentLine.matches("^\\s{8}[ivx]+\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\s{8}[ivx]+\\.\\s(.*)");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                contentAfterNumber = matcher.group(1);
                return "            1. " + contentAfterNumber;
            }
        }

        // Level 3+: Just add more indentation
        if (currentLine.matches("^\\s{12,}\\d+\\.\\s.*")) {
            return "    " + currentLine;
        }

        return "    " + currentLine;
    }
// Helper method to outdent numbered lines - RESETS TO LAST NUMBER OF PREVIOUS LEVEL
    private String outdentNumberedLine(String currentLine) {
        String contentAfterNumber;

        // Level 3+: Deep indentation -> Roman numerals
        if (currentLine.matches("^\\s{12,}\\d+\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\s+\\d+\\.\\s(.*)");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                contentAfterNumber = matcher.group(1);
                return "        i. " + contentAfterNumber;
            }
        }

        // Level 2: Roman numerals -> Letters
        if (currentLine.matches("^\\s{8}[ivx]+\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\s{8}[ivx]+\\.\\s(.*)");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                contentAfterNumber = matcher.group(1);
                return "    a. " + contentAfterNumber;
            }
        }

        // Level 1: Letters -> Regular numbers
        if (currentLine.matches("^\\s{4}[a-z]\\.\\s.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\s{4}[a-z]\\.\\s(.*)");
            java.util.regex.Matcher matcher = pattern.matcher(currentLine);
            if (matcher.find()) {
                contentAfterNumber = matcher.group(1);
                return "1. " + contentAfterNumber;
            }
        }

        // Level 0: Can't outdent further, just remove spaces if any
        if (currentLine.startsWith("    ")) {
            return currentLine.substring(4);
        }

        return currentLine;
    }

//BULLET LIST
    private void insertBulletList() {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();
        String bulletPoint = "\n● ";

        String newText = currentText.substring(0, cursorPosition) +
                bulletPoint +
                currentText.substring(cursorPosition);

        noteContent.setText(newText);
        noteContent.setSelection(cursorPosition + bulletPoint.length());

        // Enable bullet list mode
        isBulletListMode = true;
    }
    private void setupBulletListWatcher() {
        noteContent.addTextChangedListener(new TextWatcher() {
            private boolean isProcessing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isProcessing || isUpdatingText || isTogglingState) return;
                //if (isProcessing) return;

                // Check if user pressed Enter (added newline)
                if (count == 1 && start < s.length() && s.charAt(start) == '\n') {
                    isProcessing = true;

                    // Get the line before the newline
                    String textBeforeNewline = s.toString().substring(0, start);
                    int lastNewlineIndex = textBeforeNewline.lastIndexOf('\n');
                    String currentLine = textBeforeNewline.substring(lastNewlineIndex + 1);

                    // Check if the current line is a bullet item
                    if (currentLine.matches("^\\s*[●○■]\\s.*")) {
                        // Re-enable bullet list mode if it was disabled
                        isBulletListMode = true;

                        // Check if the current line is an empty bullet item
                        if (currentLine.matches("^\\s*[●○■]\\s*$")) {
                            // Double enter detected - exit bullet list mode
                            isBulletListMode = false;

                            // Remove the empty bullet line
                            String newText = s.toString().substring(0, lastNewlineIndex + 1) +
                                    s.toString().substring(start + 1);
                            noteContent.setText(newText);
                            noteContent.setSelection(lastNewlineIndex + 1);
                        } else {
                            // Get the indentation and bullet type from current line
                            String indentAndBullet = getBulletWithIndentation(currentLine);

                            // Add next bullet item with same indentation
                            String newText = s.toString().substring(0, start + 1) +
                                    indentAndBullet +
                                    s.toString().substring(start + 1);

                            noteContent.setText(newText);
                            noteContent.setSelection(start + 1 + indentAndBullet.length());
                        }
                    }

                    isProcessing = false;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    private String getBulletWithIndentation(String line) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\s*)([●○■])\\s");
        java.util.regex.Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String indentation = matcher.group(1);
            String bulletChar = matcher.group(2);
            return indentation + bulletChar + " ";
        }

        return "● ";
    }
    private String indentBulletLine(String currentLine) {
        String contentAfterBullet;

        if (currentLine.matches("^\\s{0,2}●\\s.*")) {
            int bulletIndex = currentLine.indexOf("●");
            contentAfterBullet = currentLine.substring(bulletIndex + 2);
            return "    ○ " + contentAfterBullet;
        } else if (currentLine.matches("^\\s{4}○\\s.*")) {
            contentAfterBullet = currentLine.substring(6);
            return "        ■ " + contentAfterBullet;
        } else if (currentLine.matches("^\\s{8}■\\s.*")) {
            contentAfterBullet = currentLine.substring(10);
            return "            ■ " + contentAfterBullet;
        } else {
            return "    " + currentLine;
        }
    }
    private String outdentBulletLine(String currentLine) {
        String contentAfterBullet;

        if (currentLine.matches("^\\s{12}■\\s.*")) {
            contentAfterBullet = currentLine.substring(14);
            return "        ■ " + contentAfterBullet;
        } else if (currentLine.matches("^\\s{8}■\\s.*")) {
            contentAfterBullet = currentLine.substring(10);
            return "    ○ " + contentAfterBullet;
        } else if (currentLine.matches("^\\s{4}○\\s.*")) {
            contentAfterBullet = currentLine.substring(6);
            return "● " + contentAfterBullet;
        } else if (currentLine.startsWith("    ")) {
            return currentLine.substring(4);
        } else {
            return currentLine;
        }
    }

//TOGGLE LIST
    private static final String CONTENT_PLACEHOLDER = "";
    private void setupToggleListWatcher() {
        noteContent.addTextChangedListener(new TextWatcher() {
            private boolean isProcessing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isProcessing || isUpdatingText || isTogglingState) return;

                // Check if user pressed Enter (added newline)
                if (count == 1 && start < s.length() && s.charAt(start) == '\n') {
                    isProcessing = true;

                    // Get the line before the newline
                    String textBeforeNewline = s.toString().substring(0, start);
                    int lastNewlineIndex = textBeforeNewline.lastIndexOf('\n');
                    String currentLine = textBeforeNewline.substring(lastNewlineIndex + 1);

                    // Check if cursor is on toggle title (▶ or ▼ present)
                    if (currentLine.matches("^\\s*[▶▼]\\s.*")) {
                        isToggleListMode = true;

                        // Check if the toggle title is empty (no content after arrow)
                        if (currentLine.matches("^\\s*[▶▼]\\s*$")) {
                            // Double enter detected - exit toggle list mode
                            isToggleListMode = false;

                            // Remove the empty toggle line
                            String newText = s.toString().substring(0, lastNewlineIndex + 1) +
                                    s.toString().substring(start + 1);
                            noteContent.setText(newText);
                            noteContent.setSelection(lastNewlineIndex + 1);
                        } else {
                            // ✅ Check if toggle is EXPANDED (▼) or COLLAPSED (▶)
                            boolean isExpanded = currentLine.contains("▼");

                            // Get THIS toggle's indentation
                            int toggleIndent = 0;
                            for (char c : currentLine.toCharArray()) {
                                if (c == ' ') toggleIndent++;
                                else break;
                            }

                            if (isExpanded) {
                                // ✅ Toggle is EXPANDED - create content line (toggle indent + 4)
                                StringBuilder indent = new StringBuilder();
                                for (int i = 0; i < toggleIndent + 4; i++) {
                                    indent.append(" ");
                                }

                                String newText = s.toString().substring(0, start + 1) +
                                        indent.toString() +
                                        s.toString().substring(start + 1);

                                noteContent.setText(newText);
                                noteContent.setSelection(start + 1 + indent.length());
                            } else {
                                // ✅ Toggle is COLLAPSED - create new toggle at SAME level
                                StringBuilder indent = new StringBuilder();
                                for (int i = 0; i < toggleIndent; i++) {
                                    indent.append(" ");
                                }
                                String newToggle = indent.toString() + "▶ ";

                                String newText = s.toString().substring(0, start + 1) +
                                        newToggle +
                                        s.toString().substring(start + 1);

                                noteContent.setText(newText);
                                noteContent.setSelection(start + 1 + newToggle.length());
                            }
                        }
                    }
                    // ✅ Check if cursor is on ANY indented content (4+ spaces)
                    else if (currentLine.length() >= 4 && currentLine.substring(0, 4).equals("    ")) {
                        // Get current indentation
                        int currentIndent = 0;
                        for (char c : currentLine.toCharArray()) {
                            if (c == ' ') currentIndent++;
                            else break;
                        }

                        String trimmedLine = currentLine.trim();

                        // Check if current line is empty
                        boolean isCurrentEmpty = trimmedLine.isEmpty();

                        if (isCurrentEmpty) {
                            // Check if previous line was also empty content
                            int prevLineStart = textBeforeNewline.lastIndexOf('\n', lastNewlineIndex - 1) + 1;
                            if (prevLineStart >= 0 && prevLineStart < lastNewlineIndex) {
                                String prevLine = textBeforeNewline.substring(prevLineStart, lastNewlineIndex);

                                if (prevLine.length() >= 4 && prevLine.substring(0, 4).equals("    ") && prevLine.trim().isEmpty()) {
                                    // ✅ Find PARENT toggle indentation by going backwards
                                    int parentIndent = 0;
                                    int searchPos = prevLineStart - 1;

                                    while (searchPos >= 0) {
                                        int searchLineStart = textBeforeNewline.lastIndexOf('\n', searchPos - 1) + 1;
                                        if (searchLineStart < 0) searchLineStart = 0;
                                        String searchLine = textBeforeNewline.substring(searchLineStart, Math.min(searchPos + 1, textBeforeNewline.length()));

                                        if (searchLine.matches("^\\s*[▶▼]\\s.*")) {
                                            for (char c : searchLine.toCharArray()) {
                                                if (c == ' ') parentIndent++;
                                                else break;
                                            }
                                            break;
                                        }
                                        searchPos = searchLineStart - 1;
                                    }

                                    // Create new toggle at SAME level as parent
                                    StringBuilder indent = new StringBuilder();
                                    for (int i = 0; i < parentIndent; i++) {
                                        indent.append(" ");
                                    }
                                    String newToggle = indent.toString() + "▶ ";

                                    String newText = s.toString().substring(0, lastNewlineIndex + 1) +
                                            newToggle +
                                            s.toString().substring(start + 1);
                                    noteContent.setText(newText);
                                    noteContent.setSelection(lastNewlineIndex + 1 + newToggle.length());
                                    isProcessing = false;
                                    return;
                                }
                            }
                        }

                        // ✅ Add new content line with SAME indentation (stay inside toggle)
                        StringBuilder indent = new StringBuilder();
                        for (int i = 0; i < currentIndent; i++) {
                            indent.append(" ");
                        }

                        String newText = s.toString().substring(0, start + 1) +
                                indent.toString() +
                                s.toString().substring(start + 1);

                        noteContent.setText(newText);
                        noteContent.setSelection(start + 1 + indent.length());
                    }

                    isProcessing = false;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    private void setupToggleClickListener() {
        noteContent.setOnClickListener(v -> {
            int cursorPos = noteContent.getSelectionStart();
            String content = noteContent.getText().toString();

            // Find current line boundaries
            int lineStart = content.lastIndexOf('\n', cursorPos - 1) + 1;
            int lineEnd = content.indexOf('\n', cursorPos);
            if (lineEnd == -1) lineEnd = content.length();

            String currentLine = content.substring(lineStart, lineEnd);

            // Check if line contains toggle arrow
            if (currentLine.matches("^\\s*[▶▼]\\s.*")) {
                // Find the position of the arrow in the line
                int arrowPos = -1;
                for (int i = 0; i < currentLine.length(); i++) {
                    char c = currentLine.charAt(i);
                    if (c == '▶' || c == '▼') {
                        arrowPos = lineStart + i;
                        break;
                    }
                }

                // ✅ CHANGED: Only toggle if cursor is EXACTLY on the arrow (within 1 character)
                if (arrowPos != -1 && Math.abs(cursorPos - arrowPos) <= 1) {
                    toggleToggleState(lineStart, content);
                }
            }
        });
    }
    private void toggleToggleState(int togglePosition, String fullContent) {
        // ✅ CRITICAL: Set this flag FIRST to prevent TextWatcher interference
        isUpdatingText = true;
        isTogglingState = true;

        try {
            // Find the toggle line
            int lineEnd = fullContent.indexOf('\n', togglePosition);
            if (lineEnd == -1) lineEnd = fullContent.length();

            String toggleLine = fullContent.substring(togglePosition, lineEnd);
            boolean isExpanded = toggleLine.contains("▼");

            // Get toggle indentation level
            int toggleIndent = 0;
            for (char c : toggleLine.toCharArray()) {
                if (c == ' ') toggleIndent++;
                else break;
            }

            // ✅ Store old bookmark positions before making changes
            Map<String, int[]> oldBookmarkPositions = new HashMap<>();
            for (Bookmark bookmark : currentBookmarks) {
                oldBookmarkPositions.put(bookmark.getId(), new int[]{bookmark.getStartIndex(), bookmark.getEndIndex()});
            }

            if (isExpanded) {
                // ========== COLLAPSE ==========
                String newToggleLine = toggleLine.replace("▼", "▶");

                // Find all content lines (must be indented MORE than toggle)
                int contentStart = lineEnd + 1;
                int contentEnd = contentStart;
                StringBuilder savedContent = new StringBuilder();

                while (contentEnd < fullContent.length()) {
                    int nextLineEnd = fullContent.indexOf('\n', contentEnd);
                    if (nextLineEnd == -1) nextLineEnd = fullContent.length();

                    String nextLine = fullContent.substring(contentEnd, nextLineEnd);

                    // Count indentation
                    int lineIndent = 0;
                    for (char c : nextLine.toCharArray()) {
                        if (c == ' ') lineIndent++;
                        else break;
                    }

                    // ✅ CRITICAL: Line must be indented MORE than the toggle itself
                    if (lineIndent > toggleIndent) {
                        if (savedContent.length() > 0) savedContent.append("\n");
                        savedContent.append(nextLine);
                        contentEnd = nextLineEnd + 1;
                    } else if (nextLine.trim().isEmpty() && contentEnd < fullContent.length() - 1) {
                        // Peek ahead to see if more content follows
                        int peekPos = nextLineEnd + 1;
                        if (peekPos < fullContent.length()) {
                            int peekEnd = fullContent.indexOf('\n', peekPos);
                            if (peekEnd == -1) peekEnd = fullContent.length();

                            if (peekPos < peekEnd) {
                                String peekLine = fullContent.substring(peekPos, peekEnd);
                                int peekIndent = 0;
                                for (char c : peekLine.toCharArray()) {
                                    if (c == ' ') peekIndent++;
                                    else break;
                                }

                                if (peekIndent > toggleIndent) {
                                    // Empty line is part of content
                                    if (savedContent.length() > 0) savedContent.append("\n");
                                    savedContent.append(nextLine);
                                    contentEnd = nextLineEnd + 1;
                                    continue;
                                }
                            }
                        }
                        contentEnd = nextLineEnd + 1;
                        break;
                    } else {
                        // Found a line at same or less indentation - stop
                        break;
                    }
                }

                // Save the content
                if (savedContent.length() > 0) {
                    toggleContents.put(togglePosition, savedContent.toString());
                } else {
                    // ✅ Save proper indentation for empty content
                    String emptyContent = "";
                    for (int i = 0; i < toggleIndent + 4; i++) {
                        emptyContent += " ";
                    }
                    toggleContents.put(togglePosition, emptyContent);
                }

                // Build new content
                StringBuilder result = new StringBuilder();
                result.append(fullContent.substring(0, togglePosition));
                result.append(newToggleLine);

                if (contentEnd < fullContent.length()) {
                    String afterContent = fullContent.substring(contentEnd);
                    if (!afterContent.startsWith("\n")) {
                        result.append("\n");
                    }
                    result.append(afterContent);
                } else if (savedContent.length() > 0) {
                    result.append("\n");
                }

                toggleStates.put(togglePosition, false);

                final String finalContent = result.toString();
                final int finalCursor = Math.min(togglePosition + newToggleLine.length(), finalContent.length());

                // ✅ Calculate how much content was removed
                int removedLength = contentEnd - (lineEnd + 1);

                // ✅ Update all bookmarks that come AFTER the collapsed content
                FirebaseUser user = auth.getCurrentUser();
                if (user != null && removedLength > 0) {
                    for (Bookmark bookmark : currentBookmarks) {
                        int oldStart = bookmark.getStartIndex();
                        int oldEnd = bookmark.getEndIndex();

                        // If bookmark is completely AFTER the collapsed content
                        if (oldStart >= contentEnd) {
                            int newStart = oldStart - removedLength;
                            int newEnd = oldEnd - removedLength;

                            if (newStart >= 0 && newEnd <= finalContent.length() && newStart < newEnd) {
                                String bookmarkText = finalContent.substring(newStart, newEnd);
                                if (!bookmarkText.trim().isEmpty()) {
                                    updateBookmarkInFirestore(bookmark.getId(), newStart, newEnd, bookmarkText);
                                    bookmark.setStartIndex(newStart);
                                    bookmark.setEndIndex(newEnd);
                                    bookmark.setText(bookmarkText);
                                }
                            }
                        }
                        // If bookmark is partially or completely INSIDE collapsed content - hide it
                        else if ((oldStart >= contentStart && oldStart < contentEnd) ||
                                (oldEnd > contentStart && oldEnd <= contentEnd) ||
                                (oldStart < contentStart && oldEnd > contentEnd)) {
                            // Bookmark is inside collapsed content - it will be restored on expand
                            // No action needed here
                        }
                    }
                }

                noteContent.setText(finalContent);
                noteContent.setSelection(finalCursor);

            } else {
                // ========== EXPAND ==========
                String newToggleLine = toggleLine.replace("▶", "▼");

                // ✅ Get saved content or create default with proper indentation
                String savedContent = toggleContents.get(togglePosition);
                if (savedContent == null || savedContent.trim().isEmpty()) {
                    savedContent = "";
                    for (int i = 0; i < toggleIndent + 4; i++) {
                        savedContent += " ";
                    }
                }

                // Build new content
                StringBuilder result = new StringBuilder();
                result.append(fullContent.substring(0, togglePosition));
                result.append(newToggleLine);
                result.append("\n");
                result.append(savedContent);

                if (lineEnd < fullContent.length()) {
                    result.append(fullContent.substring(lineEnd));
                }

                toggleStates.put(togglePosition, true);

                final String finalContent = result.toString();
                final int contentLineStart = togglePosition + newToggleLine.length() + 1;
                final int finalCursor = Math.min(contentLineStart + savedContent.length(), finalContent.length());

                // ✅ Calculate how much content was added
                int addedLength = savedContent.length() + 1; // +1 for newline

                // ✅ Update all bookmarks that come AFTER the expanded toggle
                FirebaseUser user = auth.getCurrentUser();
                if (user != null && addedLength > 0) {
                    for (Bookmark bookmark : currentBookmarks) {
                        int[] oldPos = oldBookmarkPositions.get(bookmark.getId());
                        if (oldPos == null) continue;

                        int oldStart = oldPos[0];
                        int oldEnd = oldPos[1];

                        // If bookmark is completely AFTER the toggle line
                        if (oldStart > lineEnd) {
                            int newStart = oldStart + addedLength;
                            int newEnd = oldEnd + addedLength;

                            if (newStart >= 0 && newEnd <= finalContent.length() && newStart < newEnd) {
                                try {
                                    String bookmarkText = finalContent.substring(newStart, newEnd);
                                    if (!bookmarkText.trim().isEmpty() && !bookmarkText.contains("〔DIVIDER〕")) {
                                        updateBookmarkInFirestore(bookmark.getId(), newStart, newEnd, bookmarkText);
                                        bookmark.setStartIndex(newStart);
                                        bookmark.setEndIndex(newEnd);
                                        bookmark.setText(bookmarkText);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }

                noteContent.setText(finalContent);
                noteContent.setSelection(finalCursor);
            }

            // ✅ Save changes to Firestore
            saveNoteContentToFirestore(noteContent.getText().toString());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // ✅ Re-enable watchers after a delay
            noteContent.postDelayed(new Runnable() {
                @Override
                public void run() {
                    applyBookmarksToText();
                    isUpdatingText = false;
                    isTogglingState = false;
                }
            }, 200);
        }
    }
    private void insertToggleList() {
        String currentText = noteContent.getText().toString();
        int cursorPosition = noteContent.getSelectionStart();

        // Safety check
        if (cursorPosition < 0) cursorPosition = 0;
        if (cursorPosition > currentText.length()) cursorPosition = currentText.length();

        // ✅ Detect if we're inside a toggle's content
        int lineStart = currentText.lastIndexOf('\n', cursorPosition - 1) + 1;
        int lineEnd = currentText.indexOf('\n', cursorPosition);
        if (lineEnd == -1) lineEnd = currentText.length();

        String currentLine = currentText.substring(lineStart, lineEnd);

        // Count current indentation
        int currentIndent = 0;
        for (char c : currentLine.toCharArray()) {
            if (c == ' ') currentIndent++;
            else break;
        }

        // ✅ FIXED: If we're inside content (4+ spaces), nested toggle should be at content level + 4
        // If we're at root level (0 spaces), stay at root
        int toggleIndent = 0;
        if (currentIndent >= 4) {
            // We're inside a toggle's content, nest deeper
            toggleIndent = currentIndent;
        }

        String indent = "";
        for (int i = 0; i < toggleIndent; i++) {
            indent += " ";
        }

        // Check if we need a leading newline
        String toggleItem;
        if (cursorPosition > 0 && currentText.charAt(cursorPosition - 1) != '\n') {
            toggleItem = "\n" + indent + "▶ ";
        } else {
            toggleItem = indent + "▶ ";
        }

        // Build new text
        String newText = currentText.substring(0, cursorPosition) +
                toggleItem +
                currentText.substring(cursorPosition);

        // Calculate final cursor position BEFORE setting text
        final int finalCursorPos = cursorPosition + toggleItem.length();

        // Set text
        noteContent.setText(newText);

        // Set cursor in post() with safety checks
        noteContent.post(new Runnable() {
            @Override
            public void run() {
                try {
                    int textLength = noteContent.getText().length();
                    int safePos = Math.min(finalCursorPos, textLength);
                    safePos = Math.max(0, safePos);

                    if (safePos <= textLength && safePos >= 0) {
                        noteContent.setSelection(safePos);
                    }
                } catch (Exception e) {
                    try {
                        noteContent.setSelection(noteContent.getText().length());
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        isToggleListMode = true;
    }
// + BUTTON MENU
    private void setupAddMenuOptions() {
        // Subpage
        findViewById(R.id.addSubpageOption).setOnClickListener(v -> {
            openSubpage();
            closeAddMenu();
        });

        // Theme
        findViewById(R.id.addThemeOption).setOnClickListener(v -> {
            toggleColorPicker();
            closeAddMenu();
        });

        // Divider
        findViewById(R.id.addDividerOption).setOnClickListener(v -> {
            showDividerBottomSheet();
            closeAddMenu();
        });

        // Link
        findViewById(R.id.addLinkOption).setOnClickListener(v -> {
            insertLink();
            closeAddMenu();
        });

        // Bullet List
        findViewById(R.id.addBulletListOption).setOnClickListener(v -> {
            insertBulletList();
            closeAddMenu();
        });

        // Numbered List
        findViewById(R.id.addNumberedListOption).setOnClickListener(v -> {
            insertNumberedList();
            closeAddMenu();
        });

        // Toggle List
        findViewById(R.id.addToggleListOption).setOnClickListener(v -> {
            insertToggleList();
            closeAddMenu();
        });

        // Checkbox
        findViewById(R.id.addCheckboxOption).setOnClickListener(v -> {
            insertCheckbox();
            closeAddMenu();
        });
    }
    private void toggleAddMenu() {
        if (isMenuOpen) {
            closeAddMenu();
        } else {
            openAddMenu();
        }
    }
    private void openAddMenu() {
        addOptionsMenu.setVisibility(View.VISIBLE);
        isMenuOpen = true;

        // Rotate the + icon
        addMenuBtn.animate().rotation(45f).setDuration(200).start();
    }
    private void closeAddMenu() {
        addOptionsMenu.setVisibility(View.GONE);
        isMenuOpen = false;

        // Reset the + icon rotation
        addMenuBtn.animate().rotation(0f).setDuration(200).start();
    }
// Placeholder methods - implement these based on your needs
    private void insertLink() {
        Toast.makeText(this, "Insert Link - Coming soon", Toast.LENGTH_SHORT).show();
    }
    private void insertCheckbox() {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();
        String checkbox = "\n☐ ";

        String newText = currentText.substring(0, cursorPosition) +
                checkbox +
                currentText.substring(cursorPosition);

        noteContent.setText(newText);
        noteContent.setSelection(cursorPosition + checkbox.length());
    }
}