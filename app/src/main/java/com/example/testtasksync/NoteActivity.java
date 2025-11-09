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
    private ImageView checkBtn, backBtn, addSubpageBtn;
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

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        noteId = getIntent().getStringExtra("noteId");
        scrollToPosition = getIntent().getIntExtra("scrollToPosition", -1);

        loadNoteColor();
        colorPaletteBtn.setOnClickListener(v -> toggleColorPicker());
        setupColorPicker();

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

        String currentText = noteContent.getText().toString();

        for (Bookmark bookmark : currentBookmarks) {
            int start = bookmark.getStartIndex();
            int end = bookmark.getEndIndex();
            boolean needsUpdate = false;
            boolean shouldDelete = false;

            if (changePosition < start) {
                start += lengthDiff;
                end += lengthDiff;
                needsUpdate = true;
            } else if (changePosition >= start && changePosition <= end) {
                end += lengthDiff;
                needsUpdate = true;
                if (end <= start || start < 0 || end > currentText.length()) shouldDelete = true;
            } else if (changePosition == end || changePosition == start - 1) {
                if (lengthDiff > 0 && changePosition >= 0 && changePosition < currentText.length()) {
                    char c = currentText.charAt(changePosition);
                    if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                        if (changePosition == end) end += lengthDiff;
                        else if (changePosition == start - 1) start -= lengthDiff;
                        needsUpdate = true;
                    }
                }
            }

            if (start < 0 || end > currentText.length() || start >= end) shouldDelete = true;

            if (shouldDelete) deleteBookmarkFromFirestore(bookmark.getId());
            else if (needsUpdate) {
                String updatedText = currentText.substring(start, end);
                updateBookmarkInFirestore(bookmark.getId(), start, end, updatedText);
            }
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

    @Override
    protected void onResume() {
        super.onResume();

        if (noteId != null) {
            if (currentBookmarks.isEmpty()) {
                loadNote();
                noteContent.postDelayed(() -> setupBookmarkListener(), 300);
            } else applyBookmarksToText();

            loadSubpages();

            if (scrollToPosition >= 0) {
                noteContent.postDelayed(() -> scrollToBookmark(scrollToPosition), 600);
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
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0, R.id.menu_bookmark, 0, "Bookmark");
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == R.id.menu_bookmark) {
                    int start = noteContent.getSelectionStart();
                    int end = noteContent.getSelectionEnd();

                    if (start < end) {
                        String selectedText = noteContent.getText().toString().substring(start, end);
                        if (isTextAlreadyBookmarked(start, end)) {
                            Toast.makeText(NoteActivity.this,
                                    "This text is already marked. Go to bookmarks to edit.",
                                    Toast.LENGTH_LONG).show();
                        } else showBookmarkBottomSheet(selectedText, start, end);
                    }
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override public void onDestroyActionMode(ActionMode mode) { actionMode = null; }
        });
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

                    currentBookmarks.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Bookmark b = doc.toObject(Bookmark.class);
                            b.setId(doc.getId());
                            currentBookmarks.add(b);
                        }
                        bookmarksLink.setVisibility(currentBookmarks.isEmpty() ? View.GONE : View.VISIBLE);
                        applyBookmarksToText();
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

                        if (title != null) noteTitle.setText(title);
                        if (content != null) noteContent.setText(content);

                        noteContent.postDelayed(this::applyBookmarksToText, 400);
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
