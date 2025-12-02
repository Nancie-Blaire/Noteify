package com.example.testtasksync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;


public class NoteActivity_1 extends AppCompatActivity {

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
    private boolean isProcessing = false;
    private Map<Integer, String> dividerStyles = new HashMap<>();
    private boolean isNumberedListMode = false;
    private boolean isBulletListMode = false;
    private int currentListNumber = 1;
    private boolean isToggleListMode = false;
    private boolean isTogglingState = false;
    private Map<Integer, Boolean> toggleStates = new HashMap<>(); // position -> isExpanded
    private Map<Integer, String> toggleContents = new HashMap<>(); // position -> content
    private Map<Integer, List<Bookmark>> hiddenBookmarksByToggle = new HashMap<>();
    private List<LinkWeblink> weblinks = new ArrayList<>();
    private Map<Long, View> weblinkViews = new HashMap<>();
    private ListenerRegistration weblinkListener; // ✅ Add this field at the top

    //toggle
    private Map<String, String> toggleContentsById = new HashMap<>(); // toggleId -> content
    private Map<String, Boolean> toggleStatesById = new HashMap<>(); // toggleId -> isExpanded
    private Map<String, List<Bookmark>> hiddenBookmarksByToggleId = new HashMap<>(); // toggleId -> bookmarks
    private Map<Integer, String> positionToToggleId = new HashMap<>(); // position -> toggleId (for lookup)
    // Para sa Camera at Gallery
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private Uri currentPhotoUri;
    //private boolean imagesLoaded = false;
    private static final int MAX_IMAGE_WIDTH = 1024;     // Max width/height to scale the image down (in pixels)
    private static final int MAX_IMAGE_HEIGHT = 1024;    //
    private static final int COMPRESSION_QUALITY = 80;   // JPEG compression quality (0-100)
    private static final int MAX_INLINE_IMAGE_KB = 700;  // Max size (in KB) for an image to be saved in one document
    private static final int CHUNK_SIZE = 50000;

    //Headings and fonts
    private Map<Integer, String> textStyles = new HashMap<>(); // position -> style type
    // Add these fields at the top of your class
    private final android.os.Handler styleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable styleRunnable = null;
    private static final long STYLE_APPLY_DELAY_MS = 300L;
    private boolean isApplyingStyles = false;
    private String currentActiveStyle = "normal";
    private boolean isStyleActive = false;

    //DRAG FUNCTIONALITY
    private boolean isDragging = false;
    private int dragStartLineIndex = -1;
    private int dragCurrentLineIndex = -1;
    private View dragOverlayView;
    private TextView dragFloatingText;
    private int dragTouchOffset = 0;
    private Handler dragHandler = new Handler(Looper.getMainLooper());

    // TABLE FUNCTIONALITY
    private static final int PICK_IMAGE_REQUEST = 1001;
    private ImageView selectedImageView;
    private View currentSelectedTable;
    private boolean isTableFullWidth = false;
    private List<Table> tables = new ArrayList<>();
    private Map<String, TableView> tableViews = new HashMap<>();
    private ListenerRegistration tableListener;
    private Map<String, SubpageView> subpageViews = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note1);

        // ✅ Initialize views (REMOVE addMenuBtn and addOptionsMenu)
        noteTitle = findViewById(R.id.noteTitle);
        noteContent = findViewById(R.id.noteContent);
        noteLayout = findViewById(R.id.noteLayout);
        colorPickerPanel = findViewById(R.id.colorPickerPanel);
        checkBtn = findViewById(R.id.checkBtn);
        bookmarksLink = findViewById(R.id.bookmarksLink);

        // ❌ REMOVE THESE TWO LINES:
        // addMenuBtn = findViewById(R.id.addMenuBtn);
        // addOptionsMenu = findViewById(R.id.addOptionsMenu);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        noteId = getIntent().getStringExtra("noteId");
        scrollToPosition = getIntent().getIntExtra("scrollToPosition", -1);

        // Add this in onCreate() method, before setupTextWatcher()
        setupImagePickers();

        //  CALLING METHODS
        loadNoteColor();
        setupColorPicker();

        // ✅ NEW METHOD (replaces setupAddMenuOptions)
        setupKeyboardToolbar();

        setupTextSelection();
        setupTextWatcher();
        setupStyleWatcher();
        setupNumberedListWatcher();
        setupBulletListWatcher();
        setupToggleListWatcher();
        setupCheckboxWatcher();
        setupCheckboxAutoWatcher();
        setupImageDeletion();

        setupDragFunctionality();

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

        checkBtn.setOnClickListener(v -> saveAndExit());

        // ❌ DELETE THIS LINE (causing the crash):
        // addMenuBtn.setOnClickListener(v -> toggleAddMenu());

        bookmarksLink.setOnClickListener(v -> openBookmarks());

        // ❌ DELETE THESE LINES TOO (moved to setupKeyboardToolbar):
        // findViewById(R.id.indentOption).setOnClickListener(v -> indentLine());
        // findViewById(R.id.outdentOption).setOnClickListener(v -> outdentLine());

        if (noteId != null) {
            loadNote(); // This now handles EVERYTHING including images

            noteContent.postDelayed(() -> setupBookmarkListener(), 800);

            loadWeblinks();
        }
    }

    //BUTTONS MENU
    private void setupKeyboardToolbar() {
        final View rootView = findViewById(android.R.id.content);
        final HorizontalScrollView keyboardToolbar = findViewById(R.id.keyboardToolbar);

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                android.graphics.Rect r = new android.graphics.Rect();
                rootView.getWindowVisibleDisplayFrame(r);

                int screenHeight = rootView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;

                if (keypadHeight > screenHeight * 0.15) {
                    if (keyboardToolbar.getVisibility() != View.VISIBLE) {
                        keyboardToolbar.setVisibility(View.VISIBLE);
                        keyboardToolbar.setAlpha(0f);
                        keyboardToolbar.animate()
                                .alpha(1f)
                                .setDuration(150)
                                .start();
                    }
                } else {
                    if (keyboardToolbar.getVisibility() == View.VISIBLE) {
                        keyboardToolbar.animate()
                                .alpha(0f)
                                .setDuration(150)
                                .withEndAction(() -> keyboardToolbar.setVisibility(View.GONE))
                                .start();
                    }
                }
            }
        });

        // Setup all toolbar buttons
        findViewById(R.id.headingsandfont).setOnClickListener(v -> showHeadingsAndFontsBottomSheet());
        findViewById(R.id.addDividerOption).setOnClickListener(v -> showDividerBottomSheet());
        findViewById(R.id.addBulletListOption).setOnClickListener(v -> insertBulletList());
        findViewById(R.id.addNumberedListOption).setOnClickListener(v -> insertNumberedList());
        findViewById(R.id.addToggleListOption).setOnClickListener(v -> insertToggleList());
        findViewById(R.id.addCheckboxOption).setOnClickListener(v -> insertCheckbox());
        findViewById(R.id.addLinkOption).setOnClickListener(v -> insertLink());
        findViewById(R.id.insertImage).setOnClickListener(v -> showInsertMediaBottomSheet());
        findViewById(R.id.addTableOption).setOnClickListener(v -> insertTable());
        findViewById(R.id.indentOption).setOnClickListener(v -> indentLine());
        findViewById(R.id.outdentOption).setOnClickListener(v -> outdentLine());
        findViewById(R.id.addThemeOption).setOnClickListener(v -> toggleColorPicker());
        findViewById(R.id.addSubpageOption).setOnClickListener(v -> openSubpage());

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

        // If content is null, collect from all EditTexts
        if (content == null) {
            content = collectAllNoteContent();
        }

        Map<String, String> dividerStylesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : dividerStyles.entrySet()) {
            dividerStylesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // ✅ OLD toggle data (for backward compatibility - will be deprecated)
        Map<String, Boolean> toggleStatesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, Boolean> entry : toggleStates.entrySet()) {
            toggleStatesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, String> toggleContentsForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : toggleContents.entrySet()) {
            toggleContentsForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, String> textStylesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : textStyles.entrySet()) {
            textStylesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("content", content);
        updates.put("timestamp", System.currentTimeMillis());
        updates.put("dividerStyles", dividerStylesForFirestore);
        updates.put("toggleStates", toggleStatesForFirestore);
        updates.put("toggleContents", toggleContentsForFirestore);
        updates.put("textStyles", textStylesForFirestore);

        // ✅ NEW: Save toggle data by ID
        updates.put("toggleContentsById", toggleContentsById);
        updates.put("toggleStatesById", toggleStatesById);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d("TOGGLE_DEBUG", "✅ Saved " + toggleContentsById.size() + " toggles by ID");
                })
                .addOnFailureListener(e -> {
                    Log.e("TOGGLE_DEBUG", "❌ Error saving toggles", e);
                    e.printStackTrace();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("IMAGE_DEBUG", "🔄 ===== onResume START =====");

        if (noteId != null) {
            // ✅ ALWAYS reload
            loadNote(); // This now calls loadTablesInline()

            // Only setup listeners once
            if (bookmarkListener == null) {
                noteContent.postDelayed(() -> setupBookmarkListener(), 800);
            }
            refreshSubpageTitles();

            if (scrollToPosition >= 0) {
                final int positionToScroll = scrollToPosition;
                scrollToPosition = -1;
                noteContent.postDelayed(() -> scrollToBookmark(positionToScroll), 1000);
            }
        }
    }

    private void refreshSubpageTitles() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) return;

        // Refresh all visible subpage titles
        for (Map.Entry<String, SubpageView> entry : subpageViews.entrySet()) {
            String subpageId = entry.getKey();
            SubpageView subpageView = entry.getValue();

            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("subpages").document(subpageId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Subpage updatedSubpage = doc.toObject(Subpage.class);
                            if (updatedSubpage != null) {
                                updatedSubpage.setId(subpageId);
                                subpageView.setSubpageData(updatedSubpage);
                            }
                        }
                    });
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
        String content = collectAllNoteContent();

        // Convert styles for Firestore
        Map<String, String> dividerStylesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : dividerStyles.entrySet()) {
            dividerStylesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, Boolean> toggleStatesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, Boolean> entry : toggleStates.entrySet()) {
            toggleStatesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, String> toggleContentsForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : toggleContents.entrySet()) {
            toggleContentsForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, String> textStylesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : textStyles.entrySet()) {
            textStylesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // ✅ Get lock state first
        db.collection("users").document(user.getUid())
                .collection("notes")
                .document(noteId)
                .get()
                .addOnSuccessListener(doc -> {
                    Boolean isLocked = doc.getBoolean("isLocked");

                    Map<String, Object> noteData = new HashMap<>();
                    noteData.put("title", title);
                    noteData.put("content", content);
                    noteData.put("color", currentNoteColor);
                    noteData.put("timestamp", System.currentTimeMillis());
                    noteData.put("dividerStyles", dividerStylesForFirestore);
                    noteData.put("toggleStates", toggleStatesForFirestore);
                    noteData.put("toggleContents", toggleContentsForFirestore);
                    noteData.put("textStyles", textStylesForFirestore);
                    noteData.put("toggleContentsById", toggleContentsById);
                    noteData.put("toggleStatesById", toggleStatesById);

                    // ✅ Preserve lock state
                    if (isLocked != null) {
                        noteData.put("isLocked", isLocked);
                    }

                    db.collection("users").document(user.getUid())
                            .collection("notes")
                            .document(noteId)
                            .update(noteData)  // Changed from .set() to .update()
                            .addOnSuccessListener(aVoid -> {
                                Log.d("NoteActivity", "✅ Auto-saved note with lock state");
                            })
                            .addOnFailureListener(e -> {
                                Log.e("NoteActivity", "❌ Auto-save failed", e);
                            });
                });
    }
    private void saveAndExit() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        String title = noteTitle.getText().toString();
        String content = collectAllNoteContent();

        // Convert styles for Firestore
        Map<String, String> dividerStylesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : dividerStyles.entrySet()) {
            dividerStylesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, String> textStylesForFirestore = new HashMap<>();
        for (Map.Entry<Integer, String> entry : textStyles.entrySet()) {
            textStylesForFirestore.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // ✅ STEP 1: Get current lock state from Firestore FIRST
        db.collection("users").document(user.getUid())
                .collection("notes")
                .document(noteId)
                .get()
                .addOnSuccessListener(doc -> {
                    Boolean isLocked = doc.getBoolean("isLocked");

                    // ✅ Create noteData HERE (inside the callback)
                    Map<String, Object> noteData = new HashMap<>();
                    noteData.put("title", title);
                    noteData.put("content", content);
                    noteData.put("color", currentNoteColor);
                    noteData.put("timestamp", System.currentTimeMillis());
                    noteData.put("dividerStyles", dividerStylesForFirestore);
                    noteData.put("textStyles", textStylesForFirestore);
                    noteData.put("toggleContentsById", toggleContentsById);
                    noteData.put("toggleStatesById", toggleStatesById);

                    // ✅ PRESERVE LOCK STATE
                    if (isLocked != null) {
                        noteData.put("isLocked", isLocked);
                    }

                    // ✅ Use update() instead of set()
                    db.collection("users").document(user.getUid())
                            .collection("notes")
                            .document(noteId)
                            .update(noteData)  // Changed from .set() to .update()
                            .addOnSuccessListener(aVoid -> finish())
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error saving note", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error checking lock state", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookmarkListener != null) bookmarkListener.remove();
        if (weblinkListener != null) weblinkListener.remove(); // ✅ Add this line
        if (tableListener != null) tableListener.remove(); // ✅ ADD THIS
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
                            loadAndDisplayImages();      showDeleteBookmarkConfirmation(bookmarkToDelete);
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

        Log.d("IMAGE_DEBUG", "📖 Loading note: " + noteId);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String title = doc.getString("title");
                        String content = doc.getString("content");

                        // Load divider styles
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

                        // ✅ Load toggle contents by ID
                        Map<String, Object> savedToggleContents = (Map<String, Object>) doc.get("toggleContentsById");
                        if (savedToggleContents != null) {
                            toggleContentsById.clear();
                            for (Map.Entry<String, Object> entry : savedToggleContents.entrySet()) {
                                String toggleId = entry.getKey();
                                String toggleContent = (String) entry.getValue();
                                toggleContentsById.put(toggleId, toggleContent);
                                Log.d("TOGGLE_DEBUG", "📦 Loaded content for toggle: " + toggleId);
                            }
                        }

                        // ✅ Load toggle states by ID
                        Map<String, Object> savedToggleStates = (Map<String, Object>) doc.get("toggleStatesById");
                        if (savedToggleStates != null) {
                            toggleStatesById.clear();
                            for (Map.Entry<String, Object> entry : savedToggleStates.entrySet()) {
                                String toggleId = entry.getKey();
                                Boolean state = (Boolean) entry.getValue();
                                toggleStatesById.put(toggleId, state);
                                Log.d("TOGGLE_DEBUG", "🔄 Loaded state for toggle: " + toggleId + " = " + state);
                            }
                        }

                        // ✅ Build position-to-ID map from content
                        if (content != null) {
                            positionToToggleId.clear();
                            java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("〔(toggle_\\d+_\\d+)〕");
                            java.util.regex.Matcher idMatcher = idPattern.matcher(content);

                            while (idMatcher.find()) {
                                String toggleId = idMatcher.group(1);
                                int toggleStart = idMatcher.start();

                                // Find the actual line start (before the arrow)
                                int lineStart = content.lastIndexOf('\n', toggleStart) + 1;
                                positionToToggleId.put(lineStart, toggleId);

                                Log.d("TOGGLE_DEBUG", "📍 Mapped position " + lineStart + " → " + toggleId);
                            }
                        }

                        // ✅ Load OLD toggle data (for backward compatibility - will be migrated)
                        Map<String, Object> savedOldToggleStates = (Map<String, Object>) doc.get("toggleStates");
                        if (savedOldToggleStates != null) {
                            toggleStates.clear();
                            for (Map.Entry<String, Object> entry : savedOldToggleStates.entrySet()) {
                                try {
                                    int position = Integer.parseInt(entry.getKey());
                                    Boolean state = (Boolean) entry.getValue();
                                    toggleStates.put(position, state);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        // ✅ Load OLD toggle contents (for backward compatibility)
                        Map<String, Object> savedOldToggleContents = (Map<String, Object>) doc.get("toggleContents");
                        if (savedOldToggleContents != null) {
                            toggleContents.clear();
                            for (Map.Entry<String, Object> entry : savedOldToggleContents.entrySet()) {
                                try {
                                    int position = Integer.parseInt(entry.getKey());
                                    String toggleContent = (String) entry.getValue();
                                    toggleContents.put(position, toggleContent);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        // Load text styles
                        Map<String, Object> savedTextStyles = (Map<String, Object>) doc.get("textStyles");
                        if (savedTextStyles != null) {
                            textStyles.clear();
                            for (Map.Entry<String, Object> entry : savedTextStyles.entrySet()) {
                                try {
                                    int position = Integer.parseInt(entry.getKey());
                                    String style = (String) entry.getValue();
                                    textStyles.put(position, style);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        // ✅ SET isUpdatingText BEFORE setText
                        isUpdatingText = true;

                        if (title != null) noteTitle.setText(title);
                        if (content != null) {
                            noteContent.setText(content);
                            lastSavedContent = content;
                            Log.d("IMAGE_DEBUG", "✅ Text set, length: " + content.length());
                        }

                        // ✅ Load in sequence: images → tables → styles → collapse toggles
                        noteContent.postDelayed(() -> {
                            Log.d("IMAGE_DEBUG", "🖼️ Starting image load...");
                            loadAndDisplayImages();

                            // ✅ Wait for images to load before tables
                            noteContent.postDelayed(() -> {
                                Log.d("TABLE_DEBUG", "📊 Starting table load...");
                                loadTablesInline();

                                // ✅ ADD THIS
                                noteContent.postDelayed(() -> {
                                    Log.d("SUBPAGE_DEBUG", "📄 Starting subpage load...");
                                    loadSubpagesInline();

                                    noteContent.postDelayed(() -> {
                                        applyTextStyles();
                                        collapseTogglesBasedOnSavedState();
                                        isUpdatingText = false;
                                    }, 200);
                                }, 300);
                            }, 300);
                        }, 100);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("IMAGE_DEBUG", "❌ Error loading note", e);
                    isUpdatingText = false;
                });
    }
    private void collapseTogglesBasedOnSavedState() {
        Log.d("TOGGLE_DEBUG", "🔽 Collapsing toggles based on saved state...");

        String content = noteContent.getText().toString();

        // Find all toggles in content
        java.util.regex.Pattern togglePattern = java.util.regex.Pattern.compile("^(\\s*)[▶▼]〔(toggle_\\d+_\\d+)〕\\s", java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher matcher = togglePattern.matcher(content);

        List<ToggleInfo> togglesToCollapse = new ArrayList<>();

        while (matcher.find()) {
            String toggleId = matcher.group(2);
            int toggleStart = matcher.start();

            // Check if this toggle should be collapsed
            Boolean isExpanded = toggleStatesById.get(toggleId);
            if (isExpanded != null && !isExpanded) {
                // This toggle should be collapsed
                String fullLine = content.substring(matcher.start(), content.indexOf('\n', matcher.start()));
                boolean currentlyExpanded = fullLine.contains("▼");

                if (currentlyExpanded) {
                    // Need to collapse it
                    togglesToCollapse.add(new ToggleInfo(toggleId, toggleStart));
                    Log.d("TOGGLE_DEBUG", "📌 Toggle " + toggleId + " needs collapsing");
                }
            }
        }

        // Collapse toggles (in reverse order to maintain positions)
        Collections.reverse(togglesToCollapse);

        for (ToggleInfo info : togglesToCollapse) {
            String currentContent = noteContent.getText().toString();

            // Find current position of this toggle
            int lineStart = currentContent.lastIndexOf('\n', info.position) + 1;
            if (lineStart > info.position) lineStart = 0;

            toggleToggleStateById(info.toggleId, lineStart, currentContent);
        }

        Log.d("TOGGLE_DEBUG", "✅ Collapsed " + togglesToCollapse.size() + " toggles");
    }

    // Helper class for toggle info
    private static class ToggleInfo {
        String toggleId;
        int position;

        ToggleInfo(String toggleId, int position) {
            this.toggleId = toggleId;
            this.position = position;
        }
    }
    private void loadAllStyles(com.google.firebase.firestore.DocumentSnapshot doc) {
        // Load divider styles
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

        // Load toggle states
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

        // Load toggle contents
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

        // Load text styles
        Map<String, Object> savedTextStyles = (Map<String, Object>) doc.get("textStyles");
        if (savedTextStyles != null) {
            textStyles.clear();
            for (Map.Entry<String, Object> entry : savedTextStyles.entrySet()) {
                try {
                    int position = Integer.parseInt(entry.getKey());
                    String style = (String) entry.getValue();
                    textStyles.put(position, style);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //DIVIDER
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    private void setupDividerClickListener() {
        noteContent.setOnTouchListener(new View.OnTouchListener() {
            private GestureDetector gestureDetector = new GestureDetector(NoteActivity_1.this,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onSingleTapUp(MotionEvent e) {
                            int cursorPos = noteContent.getSelectionStart();
                            String content = noteContent.getText().toString();
                            String dividerPlaceholder = "【DIVIDER】";

                            // Find all dividers and check if cursor is on one
                            int dividerIndex = content.indexOf(dividerPlaceholder);
                            while (dividerIndex != -1) {
                                int dividerEnd = dividerIndex + dividerPlaceholder.length();

                                // If cursor is within divider range
                                if (cursorPos >= dividerIndex && cursorPos <= dividerEnd) {
                                    hideKeyboard();
                                    showDividerActionMenu(dividerIndex);
                                    return true; // Consume the event
                                }

                                dividerIndex = content.indexOf(dividerPlaceholder, dividerEnd);
                            }
                            return false;
                        }
                    });

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false; // Allow EditText to handle normally
            }
        });
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
                if (isProcessing) return;
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
    private void insertDivider(String dividerStyle) {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();
        String dividerPlaceholder = "【DIVIDER】";

        String textToInsert;
        if (cursorPosition > 0 && currentText.charAt(cursorPosition - 1) != '\n') {
            textToInsert = "\n" + dividerPlaceholder;
        } else {
            textToInsert = dividerPlaceholder;
        }

        textToInsert += "\n";

        int insertLength = textToInsert.length();
        int leadingNewline = textToInsert.startsWith("\n") ? 1 : 0;

        FirebaseUser user = auth.getCurrentUser();

        if (user != null) {
            for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                int bStart = bookmark.getStartIndex();
                int bEnd = bookmark.getEndIndex();

                if (cursorPosition > bStart && cursorPosition < bEnd) {
                    int firstPartEnd = cursorPosition;
                    String firstPartText = currentText.substring(bStart, firstPartEnd).trim();

                    if (!firstPartText.isEmpty()) {
                        updateBookmarkInFirestore(bookmark.getId(), bStart, firstPartEnd, firstPartText);
                    }

                    int secondPartStart = cursorPosition + insertLength;
                    int secondPartEnd = bEnd + insertLength;
                    String secondPartText = currentText.substring(cursorPosition, bEnd).trim();

                    if (!secondPartText.isEmpty()) {
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

                    if (firstPartText.isEmpty()) {
                        deleteBookmarkFromFirestore(bookmark.getId());
                    }
                }
                else if (cursorPosition <= bStart) {
                    updateBookmarkInFirestore(bookmark.getId(),
                            bStart + insertLength,
                            bEnd + insertLength,
                            bookmark.getText());
                }
            }
        }

        if (insertLength != 0) {
            Map<Integer, String> updatedTextStyles = new HashMap<>();
            for (Map.Entry<Integer, String> entry : textStyles.entrySet()) {
                int pos = entry.getKey();
                String styleValue = entry.getValue();

                if (pos >= cursorPosition) {
                    updatedTextStyles.put(pos + insertLength, styleValue);
                } else {
                    updatedTextStyles.put(pos, styleValue);
                }
            }
            textStyles = updatedTextStyles;
        }

        if (insertLength != 0) {
            Map<Integer, Boolean> updatedToggleStates = new HashMap<>();
            for (Map.Entry<Integer, Boolean> entry : toggleStates.entrySet()) {
                int pos = entry.getKey();
                Boolean state = entry.getValue();

                if (pos >= cursorPosition) {
                    updatedToggleStates.put(pos + insertLength, state);
                } else {
                    updatedToggleStates.put(pos, state);
                }
            }
            toggleStates = updatedToggleStates;
        }

        if (insertLength != 0) {
            Map<Integer, String> updatedToggleContents = new HashMap<>();
            for (Map.Entry<Integer, String> entry : toggleContents.entrySet()) {
                int pos = entry.getKey();
                String content = entry.getValue();

                if (pos >= cursorPosition) {
                    updatedToggleContents.put(pos + insertLength, content);
                } else {
                    updatedToggleContents.put(pos, content);
                }
            }
            toggleContents = updatedToggleContents;
        }

        if (insertLength != 0) {
            Map<Integer, List<Bookmark>> updatedHiddenBookmarks = new HashMap<>();
            for (Map.Entry<Integer, List<Bookmark>> entry : hiddenBookmarksByToggle.entrySet()) {
                int pos = entry.getKey();
                List<Bookmark> bookmarks = entry.getValue();

                if (pos >= cursorPosition) {
                    updatedHiddenBookmarks.put(pos + insertLength, bookmarks);
                } else {
                    updatedHiddenBookmarks.put(pos, bookmarks);
                }
            }
            hiddenBookmarksByToggle = updatedHiddenBookmarks;
        }

        String newText = currentText.substring(0, cursorPosition) +
                textToInsert +
                currentText.substring(cursorPosition);

        int dividerStart = cursorPosition + leadingNewline;
        dividerStyles.put(dividerStart, dividerStyle);

        isUpdatingText = true;

        noteContent.setText(newText);

        noteContent.postDelayed(() -> {
            try {
                Editable editable = noteContent.getEditableText();
                int dividerEnd = dividerStart + dividerPlaceholder.length();

                if (dividerStart >= 0 && dividerEnd <= editable.length()) {
                    editable.setSpan(
                            new DividerSpan(dividerStyle, 0xFF666666),
                            dividerStart,
                            dividerEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    int newCursorPos = dividerEnd + 1;
                    if (newCursorPos <= editable.length()) {
                        noteContent.setSelection(newCursorPos);
                    } else {
                        noteContent.setSelection(editable.length());
                    }
                }

                applyBookmarksToText();

            } catch (Exception e) {
                Log.e("NoteActivity", "Error applying divider", e);
            } finally {
                isUpdatingText = false;
            }
        }, 100);

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

    //----------------------------------------------------------------//
    //IMAGES
    private void loadAndDisplayImages() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) {
            Log.e("IMAGE_DEBUG", "❌ User or noteId is null");
            isUpdatingText = false;
            return;
        }

        Log.d("IMAGE_DEBUG", "🔍 Querying images collection...");

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("images")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d("IMAGE_DEBUG", "✅ Query successful. Found " + querySnapshot.size() + " images");

                    if (querySnapshot.isEmpty()) {
                        Log.d("IMAGE_DEBUG", "🔭 No images to load");
                        finishImageLoading();
                        return;
                    }

                    // Process each image
                    int totalImages = querySnapshot.size();
                    int[] processedCount = {0};

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String imageId = doc.getString("imageId");
                        Boolean isChunked = doc.getBoolean("isChunked");

                        Log.d("IMAGE_DEBUG", "📷 Processing image: " + imageId + ", Chunked: " + isChunked);

                        if (imageId != null) {
                            if (isChunked != null && isChunked) {
                                loadChunkedImage(imageId, () -> {
                                    processedCount[0]++;
                                    Log.d("IMAGE_DEBUG", "✅ Chunked image loaded (" + processedCount[0] + "/" + totalImages + ")");
                                    if (processedCount[0] == totalImages) {
                                        finishImageLoading();
                                    }
                                });
                            } else {
                                String base64Data = doc.getString("base64Data");
                                if (base64Data != null) {
                                    Log.d("IMAGE_DEBUG", "📊 Base64 length: " + base64Data.length());

                                    // ✅ DELAY each image display slightly
                                    noteContent.postDelayed(() -> {
                                        displayImage(imageId, base64Data);
                                    }, 50 * processedCount[0]); // Stagger by 50ms each

                                } else {
                                    Log.e("IMAGE_DEBUG", "❌ No base64Data for image: " + imageId);
                                }
                                processedCount[0]++;

                                if (processedCount[0] == totalImages) {
                                    // ✅ Wait for last image to display
                                    noteContent.postDelayed(() -> {
                                        finishImageLoading();
                                    }, 100 + (50 * totalImages));
                                }
                            }
                        } else {
                            processedCount[0]++;
                            if (processedCount[0] == totalImages) {
                                finishImageLoading();
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("IMAGE_DEBUG", "❌ Error querying images", e);
                    finishImageLoading();
                });
    }
    private void finishImageLoading() {
        Log.d("IMAGE_DEBUG", "✅ All images processed!");

        noteContent.postDelayed(() -> {
            Log.d("IMAGE_DEBUG", "🎨 Applying bookmarks...");
            applyBookmarksToText();
            isUpdatingText = false;
            Log.d("IMAGE_DEBUG", "🎉 LOADING COMPLETE!");
        }, 150);
    }
    // In NoteActivity.java

    // REPLACE your current openSubpage() method with this:
    private void openSubpage() {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();

        // Create unique subpage ID
        String subpageId = "subpage_" + System.currentTimeMillis();

        // Save to Firestore first
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            Subpage subpage = new Subpage();
            subpage.setId(subpageId);
            subpage.setParentNoteId(noteId);
            subpage.setTitle("Untitled Subpage");
            subpage.setPosition(cursorPosition);
            subpage.setTimestamp(System.currentTimeMillis());

            Map<String, Object> subpageData = new HashMap<>();
            subpageData.put("subpageId", subpageId);
            subpageData.put("parentNoteId", noteId);
            subpageData.put("title", "Untitled Subpage");
            subpageData.put("position", cursorPosition);
            subpageData.put("timestamp", System.currentTimeMillis());

            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("subpages").document(subpageId)
                    .set(subpageData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("SUBPAGE_DEBUG", "✅ Subpage saved to Firestore");

                        // Insert subpage view inline
                        insertSubpageViewInline(subpageId, subpage, cursorPosition);

                        // Save note content with placeholder
                        noteContent.postDelayed(() -> {
                            String fullContent = collectAllNoteContent();
                            saveNoteContentToFirestore(fullContent);
                        }, 500);

                        Toast.makeText(this, "Subpage created - tap to open", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("SUBPAGE_DEBUG", "❌ Error saving subpage", e);
                        Toast.makeText(this, "Error creating subpage", Toast.LENGTH_SHORT).show();
                    });
        }
    }
    // ADD these new methods in NoteActivity:

    private void insertSubpageViewInline(String subpageId, Subpage subpageData, int cursorPosition) {
        Log.d("SUBPAGE_DEBUG", "📄 Inserting subpage inline at position: " + cursorPosition);

        try {
            LinearLayout noteContainer = findViewById(R.id.noteContainer);
            Editable currentEditable = noteContent.getEditableText();
            String currentText = currentEditable.toString();

            // Save ALL existing spans
            List<SpanInfo> allSavedSpans = saveAllSpansFromEditable(currentEditable);

            // Split text at cursor
            String beforeCursor = currentText.substring(0, cursorPosition);
            String afterCursor = currentText.substring(cursorPosition);

            // Update noteContent
            isUpdatingText = true;
            noteContent.setText(beforeCursor);

            // Restore spans for before section
            Editable beforeEditable = noteContent.getEditableText();
            for (SpanInfo info : allSavedSpans) {
                if (info.start >= 0 && info.end <= beforeCursor.length() && info.start < info.end) {
                    try {
                        beforeEditable.setSpan(
                                info.span,
                                info.start,
                                info.end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    } catch (Exception e) {
                        Log.e("SUBPAGE_DEBUG", "❌ Error restoring span", e);
                    }
                }
            }

            isUpdatingText = false;

            // Create SubpageView
            SubpageView subpageView = new SubpageView(this);
            subpageView.setSubpageData(subpageData);

            // Set layout params
            LinearLayout.LayoutParams subpageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subpageParams.setMargins(0, dpToPx(8), 0, dpToPx(8));
            subpageView.setLayoutParams(subpageParams);
            subpageView.setTag(subpageId);

            // Get index of noteContent in container
            int noteContentIndex = noteContainer.indexOfChild(noteContent);

            // Insert subpage after noteContent
            noteContainer.addView(subpageView, noteContentIndex + 1);

            // Store reference
            subpageViews.put(subpageId, subpageView);

            // Create EditText after subpage
            EditText afterEditText = createEditTextForNote();

            if (!afterCursor.isEmpty()) {
                SpannableString afterSpannable = new SpannableString(afterCursor);

                for (SpanInfo info : allSavedSpans) {
                    if (info.start >= cursorPosition && info.end <= currentText.length()) {
                        int newStart = info.start - cursorPosition;
                        int newEnd = info.end - cursorPosition;

                        if (newStart >= 0 && newEnd <= afterCursor.length() && newStart < newEnd) {
                            try {
                                afterSpannable.setSpan(
                                        info.span,
                                        newStart,
                                        newEnd,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                );
                            } catch (Exception e) {
                                Log.e("SUBPAGE_DEBUG", "❌ Error restoring after span", e);
                            }
                        }
                    }
                }

                afterEditText.setText(afterSpannable, TextView.BufferType.SPANNABLE);
            } else {
                afterEditText.setText("");
                afterEditText.setHint("Continue typing...");
            }

            setupEditTextListeners(afterEditText);
            noteContainer.addView(afterEditText, noteContentIndex + 2);

            afterEditText.requestFocus();
            afterEditText.setSelection(0);

            Log.d("SUBPAGE_DEBUG", "✅ Subpage inserted inline");

        } catch (Exception e) {
            Log.e("SUBPAGE_DEBUG", "❌ Error inserting subpage inline", e);
            e.printStackTrace();
        }
    }

    private void loadSubpagesInline() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) {
            Log.e("SUBPAGE_DEBUG", "❌ User or noteId is null");
            return;
        }

        Log.d("SUBPAGE_DEBUG", "📖 Loading subpages for note: " + noteId);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d("SUBPAGE_DEBUG", "✅ Found " + querySnapshot.size() + " subpages");

                    if (querySnapshot.isEmpty()) {
                        Log.d("SUBPAGE_DEBUG", "📭 No subpages to load");
                        return;
                    }

                    Map<String, Subpage> subpagesMap = new HashMap<>();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String subpageId = doc.getString("subpageId");
                        if (subpageId != null) {
                            Subpage subpage = doc.toObject(Subpage.class);
                            subpage.setId(subpageId);
                            subpagesMap.put(subpageId, subpage);
                            Log.d("SUBPAGE_DEBUG", "📄 Subpage loaded: " + subpageId + " - " + subpage.getTitle());
                        }
                    }

                    if (!subpagesMap.isEmpty()) {
                        reconstructNoteLayoutWithSubpages(subpagesMap);
                    }

                })
                .addOnFailureListener(e -> {
                    Log.e("SUBPAGE_DEBUG", "❌ Error loading subpages", e);
                    e.printStackTrace();
                });
    }

    private void reconstructNoteLayoutWithSubpages(Map<String, Subpage> subpagesMap) {
        String fullContent = noteContent.getText().toString();

        // Check if there are subpage placeholders
        if (!fullContent.contains("【SUBPAGE:")) {
            Log.d("SUBPAGE_DEBUG", "📭 No subpage placeholders found in content");
            return;
        }

        Log.d("SUBPAGE_DEBUG", "🔨 Reconstructing layout with subpages...");

        LinearLayout noteContainer = findViewById(R.id.noteContainer);

        // Find all subpage placeholders in content
        String pattern = "【SUBPAGE:(subpage_\\d+)】";
        java.util.regex.Pattern subpagePattern = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = subpagePattern.matcher(fullContent);

        List<SubpagePosition> subpagePositions = new ArrayList<>();

        while (matcher.find()) {
            String subpageId = matcher.group(1);
            int start = matcher.start();
            int end = matcher.end();

            Log.d("SUBPAGE_DEBUG", "🔍 Found placeholder: " + subpageId + " at position " + start);

            if (subpagesMap.containsKey(subpageId)) {
                subpagePositions.add(new SubpagePosition(subpageId, start, end));
            } else {
                Log.w("SUBPAGE_DEBUG", "⚠️ Subpage not found in map: " + subpageId);
            }
        }

        if (subpagePositions.isEmpty()) {
            Log.d("SUBPAGE_DEBUG", "📭 No valid subpage positions found");
            return;
        }

        // Sort by position
        Collections.sort(subpagePositions, (a, b) -> Integer.compare(a.start, b.start));

        // Save all existing spans before modifying
        Editable originalEditable = noteContent.getEditableText();
        List<SpanInfo> savedSpans = saveAllSpansFromEditable(originalEditable);
        Log.d("SUBPAGE_DEBUG", "💾 Saved " + savedSpans.size() + " spans");

        // Clear existing subpage views from container (but keep EditTexts and Tables)
        int noteContentIndex = noteContainer.indexOfChild(noteContent);
        for (int i = noteContainer.getChildCount() - 1; i > noteContentIndex; i--) {
            View child = noteContainer.getChildAt(i);
            if (child instanceof SubpageView) {
                noteContainer.removeViewAt(i);
                Log.d("SUBPAGE_DEBUG", "🗑️ Removed old SubpageView at index " + i);
            }
        }

        // Set content before first subpage
        SubpagePosition firstSubpage = subpagePositions.get(0);
        String beforeFirstSubpage = fullContent.substring(0, firstSubpage.start);

        isUpdatingText = true;
        noteContent.setText(beforeFirstSubpage);
        restoreSpansForSection(noteContent.getEditableText(), savedSpans, 0, firstSubpage.start);
        isUpdatingText = false;

        Log.d("SUBPAGE_DEBUG", "📝 Set content before first subpage: " + beforeFirstSubpage.length() + " chars");

        // Insert subpages and EditTexts between them
        for (int i = 0; i < subpagePositions.size(); i++) {
            SubpagePosition pos = subpagePositions.get(i);
            Subpage subpage = subpagesMap.get(pos.subpageId);

            // ✅ Create SubpageView using XML layout
            SubpageView subpageView = new SubpageView(this);
            subpageView.setSubpageData(subpage);

            // Set layout params with margins
            LinearLayout.LayoutParams subpageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subpageParams.setMargins(0, dpToPx(8), 0, dpToPx(8));
            subpageView.setLayoutParams(subpageParams);
            subpageView.setTag(pos.subpageId);

            // Add to container
            noteContainer.addView(subpageView);
            subpageViews.put(pos.subpageId, subpageView);

            Log.d("SUBPAGE_DEBUG", "➕ Added SubpageView: " + pos.subpageId);

            // ✅ Create EditText after subpage
            EditText afterSubpageEdit = createEditTextForNote();

            if (i < subpagePositions.size() - 1) {
                // Content between this subpage and next subpage
                SubpagePosition nextPos = subpagePositions.get(i + 1);
                String betweenContent = fullContent.substring(pos.end, nextPos.start);

                if (!betweenContent.isEmpty()) {
                    afterSubpageEdit.setText(betweenContent);
                    restoreSpansForSection(afterSubpageEdit.getEditableText(), savedSpans, pos.end, nextPos.start);
                    Log.d("SUBPAGE_DEBUG", "📝 Added content between subpages: " + betweenContent.length() + " chars");
                } else {
                    afterSubpageEdit.setText("");
                    afterSubpageEdit.setHint("");
                }
            } else {
                // Content after last subpage
                String afterContent = fullContent.substring(pos.end);

                if (!afterContent.trim().isEmpty()) {
                    afterSubpageEdit.setText(afterContent);
                    restoreSpansForSection(afterSubpageEdit.getEditableText(), savedSpans, pos.end, fullContent.length());
                    Log.d("SUBPAGE_DEBUG", "📝 Added content after last subpage: " + afterContent.length() + " chars");
                } else {
                    afterSubpageEdit.setText("");
                    afterSubpageEdit.setHint("");
                }
            }

            // Setup listeners for the EditText
            setupEditTextListeners(afterSubpageEdit);
            noteContainer.addView(afterSubpageEdit);
        }

        Log.d("SUBPAGE_DEBUG", "✅ Layout reconstructed with " + subpagePositions.size() + " subpages");
    }

    private static class SubpagePosition {
        String subpageId;
        int start;
        int end;

        SubpagePosition(String subpageId, int start, int end) {
            this.subpageId = subpageId;
            this.start = start;
            this.end = end;
        }
    }

    //----------------------------------------------------------------//
    //BOOKMARK FUNCTION
    private void updateBookmarkIndices(int changePosition, int lengthDiff) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (isUpdatingText || isTogglingState) return;
        String currentText = noteContent.getText().toString();

        if (currentText.equals(lastSavedContent)) return;

        if (lengthDiff < 0) {
            checkForDeletedToggles(currentText);
        }

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
            // ✅ FIX: Case 3: Edit RIGHT AT end boundary - DON'T expand for newlines
            else if (changePosition == end && lengthDiff > 0) {
                // Check if the inserted character is a newline
                if (changePosition < currentText.length()) {
                    int insStart = changePosition;
                    int insEnd = Math.min(currentText.length(), changePosition + lengthDiff);

                    if (insStart >= 0 && insEnd > insStart) {
                        String inserted = currentText.substring(insStart, insEnd);

                        // ✅ KEY FIX: Don't expand bookmark for newlines or whitespace-only
                        boolean isOnlyWhitespace = inserted.trim().isEmpty();

                        if (!isOnlyWhitespace) {
                            // Only expand if actual content was added
                            end += lengthDiff;
                            needsUpdate = true;
                        }
                        // If only whitespace/newline, do NOT expand the bookmark
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

        // ✅ CRITICAL: Update toggle positions whenever text length changes
        // This must happen REGARDLESS of bookmark updates
        if (lengthDiff != 0) {
            updateTogglePositionsAfterEdit(changePosition, lengthDiff);
        }
    }
    // Add this method after updateBookmarkIndices()
    private void updateToggleContentPositions(int changePosition, int lengthDiff) {
        if (lengthDiff == 0) return;

        Map<Integer, String> updatedToggleContents = new HashMap<>();
        for (Map.Entry<Integer, String> entry : toggleContents.entrySet()) {
            int togglePos = entry.getKey();
            String content = entry.getValue();

            // If toggle is after the change, shift its position
            if (togglePos >= changePosition) {
                updatedToggleContents.put(togglePos + lengthDiff, content);
            } else {
                updatedToggleContents.put(togglePos, content);
            }
        }
        toggleContents = updatedToggleContents;

        // Also update toggle states
        Map<Integer, Boolean> updatedToggleStates = new HashMap<>();
        for (Map.Entry<Integer, Boolean> entry : toggleStates.entrySet()) {
            int togglePos = entry.getKey();
            Boolean state = entry.getValue();

            if (togglePos >= changePosition) {
                updatedToggleStates.put(togglePos + lengthDiff, state);
            } else {
                updatedToggleStates.put(togglePos, state);
            }
        }
        toggleStates = updatedToggleStates;

        // Also update hidden bookmarks by toggle
        Map<Integer, List<Bookmark>> updatedHiddenBookmarks = new HashMap<>();
        for (Map.Entry<Integer, List<Bookmark>> entry : hiddenBookmarksByToggle.entrySet()) {
            int togglePos = entry.getKey();
            List<Bookmark> bookmarks = entry.getValue();

            if (togglePos >= changePosition) {
                updatedHiddenBookmarks.put(togglePos + lengthDiff, bookmarks);
            } else {
                updatedHiddenBookmarks.put(togglePos, bookmarks);
            }
        }
        hiddenBookmarksByToggle = updatedHiddenBookmarks;
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

        if (expandedStart < 0 || expandedEnd > currentText.length() || expandedStart >= expandedEnd) {
            Toast.makeText(this, "Invalid selection range", Toast.LENGTH_SHORT).show();
            return;
        }

        while (expandedStart < expandedEnd && expandedStart < currentText.length()
                && Character.isWhitespace(currentText.charAt(expandedStart))) {
            expandedStart++;
        }
        while (expandedEnd > expandedStart && expandedEnd > 0
                && Character.isWhitespace(currentText.charAt(expandedEnd - 1))) {
            expandedEnd--;
        }

        final int finalExpandedStart = expandedStart;
        final int finalExpandedEnd = expandedEnd;

        String expandedText = currentText.substring(finalExpandedStart, finalExpandedEnd);

        if (expandedText.contains("【DIVIDER】")) {
            Toast.makeText(this, "Cannot include dividers in bookmarks", Toast.LENGTH_SHORT).show();
            return;
        }

        if (expandedText.trim().isEmpty()) {
            Toast.makeText(this, "Cannot bookmark empty or blank lines", Toast.LENGTH_SHORT).show();
            return;
        }

        if (expandedText.replaceAll("[\\n\\r\\s]+", "").isEmpty()) {
            Toast.makeText(this, "Bookmark must contain text, not just blank lines", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalExpandedText = expandedText;

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

        // ✅ NEW: Prevent bookmarking toggle titles
        String textToBeMark = currentText.substring(startIndex, endIndex);
        if (textToBeMark.contains("▶") || textToBeMark.contains("▼")) {
            Toast.makeText(this, "Cannot bookmark toggle titles", Toast.LENGTH_SHORT).show();
            return;
        }

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
        if (content.isEmpty()) {
            isUpdatingText = false;
            return;
        }

        Editable editable = noteContent.getEditableText();
        String dividerPlaceholder = "【DIVIDER】";

        // ✅ STEP 1: Save ALL spans including heading styles
        List<SpanInfo> savedHeadingSpans = new ArrayList<>();
        List<SpanInfo> savedFontSpans = new ArrayList<>();

        // Save heading size spans
        android.text.style.RelativeSizeSpan[] sizeSpans = editable.getSpans(0, editable.length(), android.text.style.RelativeSizeSpan.class);
        for (android.text.style.RelativeSizeSpan span : sizeSpans) {
            savedHeadingSpans.add(new SpanInfo(span, editable.getSpanStart(span), editable.getSpanEnd(span)));
        }

        // Save heading/font bold spans
        android.text.style.StyleSpan[] styleSpans = editable.getSpans(0, editable.length(), android.text.style.StyleSpan.class);
        for (android.text.style.StyleSpan span : styleSpans) {
            savedFontSpans.add(new SpanInfo(span, editable.getSpanStart(span), editable.getSpanEnd(span)));
        }

        // Remove old divider spans
        DividerSpan[] oldDividers = editable.getSpans(0, editable.length(), DividerSpan.class);
        for (DividerSpan span : oldDividers) {
            editable.removeSpan(span);
        }

        // Apply dividers
        Map<Integer, String> newDividerStyles = new HashMap<>();
        int dividerIndex = 0;
        while ((dividerIndex = content.indexOf(dividerPlaceholder, dividerIndex)) != -1) {
            int dividerEnd = dividerIndex + dividerPlaceholder.length();

            String dividerStyle = "solid";
            for (Map.Entry<Integer, String> entry : dividerStyles.entrySet()) {
                int savedPos = entry.getKey();
                if (Math.abs(savedPos - dividerIndex) < 10) {
                    dividerStyle = entry.getValue();
                    break;
                }
            }

            newDividerStyles.put(dividerIndex, dividerStyle);

            editable.setSpan(
                    new DividerSpan(dividerStyle, 0xFF666666),
                    dividerIndex,
                    dividerEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            dividerIndex = dividerEnd;
        }

        dividerStyles = newDividerStyles;

        // ✅ STEP 2: Reapply saved heading/font spans
        for (SpanInfo info : savedHeadingSpans) {
            if (info.start >= 0 && info.end <= editable.length() && info.start < info.end) {
                editable.setSpan(info.span, info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        for (SpanInfo info : savedFontSpans) {
            if (info.start >= 0 && info.end <= editable.length() && info.start < info.end) {
                editable.setSpan(info.span, info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // Apply bookmarks (existing code)
        Set<String> hiddenBookmarkIds = new HashSet<>();
        for (List<Bookmark> hiddenList : hiddenBookmarksByToggle.values()) {
            for (Bookmark hidden : hiddenList) {
                hiddenBookmarkIds.add(hidden.getId());
            }
        }

        for (Bookmark b : currentBookmarks) {
            if (hiddenBookmarkIds.contains(b.getId())) {
                continue;
            }

            int s = b.getStartIndex();
            int e = b.getEndIndex();

            if (s < 0 || e > content.length() || s >= e) continue;

            String bookmarkText = content.substring(s, e);
            if (bookmarkText.contains(dividerPlaceholder)) continue;

            try {
                int color = Color.parseColor(b.getColor());
                if ("highlight".equals(b.getStyle())) {
                    editable.setSpan(
                            new android.text.style.BackgroundColorSpan(color),
                            s, e,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                } else if ("underline".equals(b.getStyle())) {
                    editable.setSpan(
                            new CustomUnderlineSpan(color, s, e),
                            s, e,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            } catch (Exception ignored) {}
        }

        isUpdatingText = false;
    }
    private void openBookmarks() {
        Intent i = new Intent(this, BookmarksActivity.class);
        i.putExtra("noteId", noteId);
        startActivity(i);
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

    //----------------------------------------------------------------//
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

    //----------------------------------------------------------------//
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

        // Check if it's a toggle line
        if (currentLine.matches("^\\s*[▶▼]\\s.*")) {
            newLine = "    " + currentLine;
        }
        // Check if it's a bullet line
        else if (currentLine.matches("^\\s*[●○■]\\s.*")) {
            newLine = indentBulletLine(currentLine);
        }
        // Check if it's a numbered line
        else if (currentLine.matches("^\\s*\\d+[.)]*\\s.*") ||
                currentLine.matches("^\\s*[a-z][.)]*\\s.*") ||
                currentLine.matches("^\\s*[ivx]+[.)]*\\s.*")) {
            newLine = indentNumberedLine(currentLine);
        }
        // Check if it's a checkbox line
        else if (currentLine.matches("^\\s*[☐☑]\\s.*")) {
            newLine = "    " + currentLine;
        }
        // Regular text - just add 4 spaces
        else {
            newLine = "    " + currentLine;
        }

        String newText = currentText.substring(0, lineStart) +
                newLine +
                currentText.substring(lineEnd);

        int addedChars = newLine.length() - currentLine.length();

        // ✅ CRITICAL: Save ALL spans before setText
        Editable editable = noteContent.getEditableText();
        List<SpanInfo> allSpans = saveAllSpans(editable);

        // ✅ Adjust span positions for the added characters
        for (SpanInfo info : allSpans) {
            if (info.start >= lineStart) {
                info.start += addedChars;
                info.end += addedChars;
            } else if (info.end > lineStart) {
                info.end += addedChars;
            }
        }

        isUpdatingText = true;
        noteContent.setText(newText);

        // ✅ Restore ALL spans
        restoreAllSpans(noteContent.getEditableText(), allSpans);

        isUpdatingText = false;

        noteContent.setSelection(cursorPosition + addedChars);
        saveNoteContentToFirestore(newText);
    }

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
        else if (currentLine.matches("^\\s*[●○■]\\s.*")) {
            newLine = outdentBulletLine(currentLine);
        }
        // Check if it's a numbered line
        else if (currentLine.matches("^\\s*\\d+[.)]*\\s.*") ||
                currentLine.matches("^\\s*[a-z][.)]*\\s.*") ||
                currentLine.matches("^\\s*[ivx]+[.)]*\\s.*")) {
            newLine = outdentNumberedLine(currentLine);
        }
        // Check if it's a checkbox line
        else if (currentLine.matches("^\\s*[☐☑]\\s.*")) {
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

        int removedChars = currentLine.length() - newLine.length();

        // ✅ CRITICAL: Save ALL spans before setText
        Editable editable = noteContent.getEditableText();
        List<SpanInfo> allSpans = saveAllSpans(editable);

        // ✅ Adjust span positions for the removed characters
        for (SpanInfo info : allSpans) {
            if (info.start >= lineStart) {
                info.start -= removedChars;
                info.end -= removedChars;
            } else if (info.end > lineStart) {
                info.end -= removedChars;
            }
        }

        isUpdatingText = true;
        noteContent.setText(newText);

        // ✅ Restore ALL spans
        restoreAllSpans(noteContent.getEditableText(), allSpans);

        isUpdatingText = false;

        noteContent.setSelection(Math.max(lineStart, cursorPosition - removedChars));
        saveNoteContentToFirestore(newText);
    }

    //----------------------------------------------------------------//
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

                if (count == 1 && start < s.length() && s.charAt(start) == '\n') {
                    isProcessing = true;

                    String textBeforeNewline = s.toString().substring(0, start);
                    int lastNewlineIndex = textBeforeNewline.lastIndexOf('\n');
                    String currentLine = textBeforeNewline.substring(lastNewlineIndex + 1);

                    if (currentLine.matches("^\\s*\\d+[.)]*\\s.*") ||
                            currentLine.matches("^\\s*[a-z][.)]*\\s.*") ||
                            currentLine.matches("^\\s*[ivx]+[.)]*\\s.*")) {

                        isNumberedListMode = true;

                        if (currentLine.matches("^\\s*\\d+[.)]*\\s*$") ||
                                currentLine.matches("^\\s*[a-z][.)]*\\s*$") ||
                                currentLine.matches("^\\s*[ivx]+[.)]*\\s*$")) {

                            isNumberedListMode = false;

                            String newText = s.toString().substring(0, lastNewlineIndex + 1) +
                                    s.toString().substring(start + 1);

                            noteContent.setText(newText);
                            noteContent.setSelection(lastNewlineIndex + 1);

                            // ✅ ADD THIS LINE!
                            isProcessing = false;
                            return; // ✅ EXIT IMMEDIATELY
                        } else {
                            String nextNumberText = getNextNumberFormat(currentLine);

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

    //----------------------------------------------------------------//
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

                if (count == 1 && start < s.length() && s.charAt(start) == '\n') {
                    isProcessing = true;

                    String textBeforeNewline = s.toString().substring(0, start);
                    int lastNewlineIndex = textBeforeNewline.lastIndexOf('\n');
                    String currentLine = textBeforeNewline.substring(lastNewlineIndex + 1);

                    if (currentLine.matches("^\\s*[●○■]\\s.*")) {
                        isBulletListMode = true;

                        if (currentLine.matches("^\\s*[●○■]\\s*$")) {
                            isBulletListMode = false;

                            String newText = s.toString().substring(0, lastNewlineIndex + 1) +
                                    s.toString().substring(start + 1);

                            noteContent.setText(newText);
                            noteContent.setSelection(lastNewlineIndex + 1);

                            // ✅ ADD THIS LINE!
                            isProcessing = false;
                            return; // ✅ EXIT IMMEDIATELY
                        } else {
                            String indentAndBullet = getBulletWithIndentation(currentLine);

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

    //----------------------------------------------------------------//
    //TOGGLE LIST
    private static final String CONTENT_PLACEHOLDER = "";
    private void setupToggleListWatcher() {
        noteContent.addTextChangedListener(new TextWatcher() {
            private boolean isProcessing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                if (isProcessing || isUpdatingText || isTogglingState) return;

                if (after == 1 && start < s.length() && s.charAt(start) == '\n') {
                    isProcessing = true;

                    String textBeforeNewline = s.toString().substring(0, start);
                    int lastNewlineIndex = textBeforeNewline.lastIndexOf('\n');
                    String currentLine = textBeforeNewline.substring(lastNewlineIndex + 1);

                    // ✅ Match toggle lines with ID
                    if (currentLine.matches("^\\s*[▶▼]〔toggle_\\d+_\\d+〕\\s.*")) {
                        isToggleListMode = true;

                        // ✅ Extract toggle ID from current line
                        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("〔(toggle_\\d+_\\d+)〕");
                        java.util.regex.Matcher idMatcher = idPattern.matcher(currentLine);
                        String currentToggleId = null;
                        if (idMatcher.find()) {
                            currentToggleId = idMatcher.group(1);
                        }

                        if (currentLine.matches("^\\s*[▶▼]〔toggle_\\d+_\\d+〕\\s*$")) {
                            // Empty toggle - delete it
                            isToggleListMode = false;
                            int toggleLineStart = lastNewlineIndex + 1;

                            if (currentToggleId != null) {
                                clearToggleDataById(currentToggleId);
                            }

                            String newText = s.toString().substring(0, lastNewlineIndex + 1) +
                                    s.toString().substring(start + 1);

                            Editable editable = noteContent.getEditableText();
                            List<SpanInfo> allSpans = saveAllSpans(editable);

                            isUpdatingText = true;
                            noteContent.setText(newText);
                            restoreAllSpans(noteContent.getEditableText(), allSpans);
                            noteContent.setSelection(lastNewlineIndex + 1);
                            isUpdatingText = false;

                            isProcessing = false;
                            return;

                        } else {
                            // Continue toggle - create new one
                            String indentAndToggle = getToggleWithIndentationAndNewId(currentLine);

                            String newText = s.toString().substring(0, start + 1) +
                                    indentAndToggle +
                                    s.toString().substring(start + 1);

                            int insertedLength = indentAndToggle.length();
                            int newTogglePos = start + 1;

                            Editable editable = noteContent.getEditableText();
                            List<SpanInfo> allSpans = saveAllSpans(editable);

                            for (SpanInfo info : allSpans) {
                                if (info.start > start) {
                                    info.start += insertedLength + 1;
                                    info.end += insertedLength + 1;
                                } else if (info.end > start) {
                                    info.end += insertedLength + 1;
                                }
                            }

                            isUpdatingText = true;
                            isTogglingState = true;
                            noteContent.setText(newText);
                            restoreAllSpans(noteContent.getEditableText(), allSpans);
                            noteContent.setSelection(start + 1 + indentAndToggle.length());

                            // ✅ Extract NEW toggle ID and initialize
                            java.util.regex.Matcher newIdMatcher = idPattern.matcher(indentAndToggle);
                            if (newIdMatcher.find()) {
                                String newToggleId = newIdMatcher.group(1);

                                int indentCount = 0;
                                for (char c : indentAndToggle.toCharArray()) {
                                    if (c == ' ') indentCount++;
                                    else break;
                                }

                                String emptyContent = "";
                                for (int i = 0; i < indentCount + 4; i++) {
                                    emptyContent += " ";
                                }

                                toggleContentsById.put(newToggleId, emptyContent);
                                toggleStatesById.put(newToggleId, false);
                                positionToToggleId.put(newTogglePos, newToggleId);

                                Log.d("TOGGLE_DEBUG", "✅ Created continuation toggle: " + newToggleId);
                            }

                            isUpdatingText = false;
                            isTogglingState = false;
                        }
                    }
                    // Content lines handling...
                    else if (currentLine.length() >= 4 && currentLine.substring(0, 4).equals("    ")) {
                        int currentIndent = 0;
                        for (char c : currentLine.toCharArray()) {
                            if (c == ' ') currentIndent++;
                            else break;
                        }

                        String trimmed = currentLine.trim();
                        boolean isCurrentEmpty = trimmed.isEmpty();

                        if (isCurrentEmpty) {
                            int prevLineStart = textBeforeNewline.lastIndexOf('\n', lastNewlineIndex - 1) + 1;
                            if (prevLineStart >= 0 && prevLineStart < lastNewlineIndex) {
                                String prevLine = textBeforeNewline.substring(prevLineStart, lastNewlineIndex);

                                if (prevLine.length() >= 4 && prevLine.substring(0, 4).equals("    ") && prevLine.trim().isEmpty()) {
                                    int parentIndent = 0;
                                    int searchPos = prevLineStart - 1;

                                    while (searchPos >= 0) {
                                        int searchLineStart = textBeforeNewline.lastIndexOf('\n', searchPos - 1) + 1;
                                        if (searchLineStart < 0) searchLineStart = 0;
                                        String searchLine = textBeforeNewline.substring(searchLineStart, Math.min(searchPos + 1, textBeforeNewline.length()));

                                        if (searchLine.matches("^\\s*[▶▼]〔toggle_\\d+_\\d+〕\\s.*")) {
                                            for (char c : searchLine.toCharArray()) {
                                                if (c == ' ') parentIndent++;
                                                else break;
                                            }
                                            break;
                                        }
                                        searchPos = searchLineStart - 1;
                                    }

                                    StringBuilder indent = new StringBuilder();
                                    for (int i = 0; i < parentIndent; i++) {
                                        indent.append(" ");
                                    }

                                    String newToggleId = "toggle_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
                                    String newToggle = indent.toString() + "▶〔" + newToggleId + "〕 ";

                                    String newText = s.toString().substring(0, lastNewlineIndex + 1) +
                                            newToggle +
                                            s.toString().substring(start + 1);

                                    Editable editable = noteContent.getEditableText();
                                    List<SpanInfo> allSpans = saveAllSpans(editable);

                                    isUpdatingText = true;
                                    isTogglingState = true;
                                    noteContent.setText(newText);
                                    restoreAllSpans(noteContent.getEditableText(), allSpans);
                                    noteContent.setSelection(lastNewlineIndex + 1 + newToggle.length());

                                    // Initialize new toggle
                                    String emptyContent = "";
                                    for (int i = 0; i < parentIndent + 4; i++) {
                                        emptyContent += " ";
                                    }
                                    toggleContentsById.put(newToggleId, emptyContent);
                                    toggleStatesById.put(newToggleId, false);
                                    positionToToggleId.put(lastNewlineIndex + 1, newToggleId);

                                    isUpdatingText = false;
                                    isTogglingState = false;

                                    isProcessing = false;
                                    return;
                                }
                            }
                        }

                        StringBuilder indent = new StringBuilder();
                        for (int i = 0; i < currentIndent; i++) {
                            indent.append(" ");
                        }

                        String newText = s.toString().substring(0, start + 1) +
                                indent.toString() +
                                s.toString().substring(start + 1);

                        Editable editable = noteContent.getEditableText();
                        List<SpanInfo> allSpans = saveAllSpans(editable);

                        isUpdatingText = true;
                        isTogglingState = true;
                        noteContent.setText(newText);
                        restoreAllSpans(noteContent.getEditableText(), allSpans);
                        noteContent.setSelection(start + 1 + indent.length());
                        isUpdatingText = false;
                        isTogglingState = false;
                    }

                    isProcessing = false;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    private String getToggleWithIndentationAndNewId(String line) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\s*)[▶▼]〔toggle_\\d+_\\d+〕\\s");
        java.util.regex.Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String indentation = matcher.group(1);
            String newToggleId = "toggle_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
            return indentation + "▶〔" + newToggleId + "〕 ";
        }

        String newToggleId = "toggle_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        return "▶〔" + newToggleId + "〕 ";
    }

    private void clearToggleDataById(String toggleId) {
        toggleContentsById.remove(toggleId);
        toggleStatesById.remove(toggleId);
        hiddenBookmarksByToggleId.remove(toggleId);

        // Remove from position map
        positionToToggleId.values().removeAll(Collections.singleton(toggleId));

        Log.d("TOGGLE_DEBUG", "✅ Cleared toggle data for: " + toggleId);
    }
    private void clearToggleDataAtPosition(int position) {
        // Remove from exact position
        toggleContents.remove(position);
        toggleStates.remove(position);
        hiddenBookmarksByToggle.remove(position);

        // Also check nearby positions (in case position shifted slightly)
        List<Integer> keysToRemove = new ArrayList<>();

        // Check toggleContents
        for (Integer key : toggleContents.keySet()) {
            if (Math.abs(key - position) <= 10) {
                keysToRemove.add(key);
            }
        }
        for (Integer key : keysToRemove) {
            toggleContents.remove(key);
        }

        keysToRemove.clear();

        // Check toggleStates
        for (Integer key : toggleStates.keySet()) {
            if (Math.abs(key - position) <= 10) {
                keysToRemove.add(key);
            }
        }
        for (Integer key : keysToRemove) {
            toggleStates.remove(key);
        }

        keysToRemove.clear();

        // Check hiddenBookmarksByToggle
        for (Integer key : hiddenBookmarksByToggle.keySet()) {
            if (Math.abs(key - position) <= 10) {
                keysToRemove.add(key);
            }
        }
        for (Integer key : keysToRemove) {
            hiddenBookmarksByToggle.remove(key);
        }

        Log.d("TOGGLE_DEBUG", "✅ Cleared toggle data at position: " + position);
    }
    // ✅ NEW HELPER METHOD: Get toggle with indentation (like bullets)
    private String getToggleWithIndentation(String line) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\s*)([▶▼])\\s");
        java.util.regex.Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String indentation = matcher.group(1);
            boolean isExpanded = matcher.group(2).equals("▼");

            // ✅ Keep same state (expanded/collapsed) for new toggle
            String toggleChar = isExpanded ? "▼" : "▶";
            return indentation + toggleChar + " ";
        }

        return "▶ ";
    }
    private void updateBookmarkIndicesForToggleNewline(int insertPosition, int indentLength, String newText) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        int totalInserted = 1 + indentLength; // +1 for newline, + indentLength for spaces

        // ✅ Update bookmarks
        for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
            int start = bookmark.getStartIndex();
            int end = bookmark.getEndIndex();
            boolean needsUpdate = false;

            if (start >= insertPosition) {
                start += totalInserted;
                end += totalInserted;
                needsUpdate = true;
            } else if (insertPosition > start && insertPosition <= end) {
                end += totalInserted;
                needsUpdate = true;
            }

            if (needsUpdate && start >= 0 && end <= newText.length() && start < end) {
                try {
                    String bookmarkText = newText.substring(start, end);
                    if (!bookmarkText.trim().isEmpty() && !bookmarkText.contains("【DIVIDER】")) {
                        bookmark.setStartIndex(start);
                        bookmark.setEndIndex(end);
                        bookmark.setText(bookmarkText);
                        updateBookmarkInFirestore(bookmark.getId(), start, end, bookmarkText);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // ✅ Update hidden bookmarks in collapsed toggles
        Map<Integer, List<Bookmark>> updatedHiddenBookmarks = new HashMap<>();
        for (Map.Entry<Integer, List<Bookmark>> entry : hiddenBookmarksByToggle.entrySet()) {
            int togglePos = entry.getKey();
            List<Bookmark> hiddenBookmarks = entry.getValue();

            if (togglePos >= insertPosition) {
                updatedHiddenBookmarks.put(togglePos + totalInserted, hiddenBookmarks);
            } else {
                updatedHiddenBookmarks.put(togglePos, hiddenBookmarks);
            }
        }
        hiddenBookmarksByToggle = updatedHiddenBookmarks;

        // ✅ Update toggle contents map
        Map<Integer, String> updatedToggleContents = new HashMap<>();
        for (Map.Entry<Integer, String> entry : toggleContents.entrySet()) {
            int togglePos = entry.getKey();
            String content = entry.getValue();

            if (togglePos >= insertPosition) {
                updatedToggleContents.put(togglePos + totalInserted, content);
            } else {
                updatedToggleContents.put(togglePos, content);
            }
        }
        toggleContents = updatedToggleContents;

        // ✅ Update toggle states map
        Map<Integer, Boolean> updatedToggleStates = new HashMap<>();
        for (Map.Entry<Integer, Boolean> entry : toggleStates.entrySet()) {
            int togglePos = entry.getKey();
            Boolean state = entry.getValue();

            if (togglePos >= insertPosition) {
                updatedToggleStates.put(togglePos + totalInserted, state);
            } else {
                updatedToggleStates.put(togglePos, state);
            }
        }
        toggleStates = updatedToggleStates;
    }
    // Replace your toggleToggleState method with this fixed version:
    private void toggleToggleState(int togglePosition, String fullContent) {
        // ✅ ADD THIS DEBUG LOGGING
        Log.d("TOGGLE_DEBUG", "🔄 Toggle state change at position: " + togglePosition);
        Log.d("TOGGLE_DEBUG", "📊 Current toggleContents keys: " + toggleContents.keySet());
        Log.d("TOGGLE_DEBUG", "📊 Current toggleStates keys: " + toggleStates.keySet());

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

            if (isExpanded) {
                // ========== COLLAPSE ==========
                String newToggleLine = toggleLine.replace("▼", "▶");

                // Find all content lines
                int contentStart = lineEnd + 1;
                int contentEnd = contentStart;
                StringBuilder savedContent = new StringBuilder();

                while (contentEnd < fullContent.length()) {
                    int nextLineEnd = fullContent.indexOf('\n', contentEnd);
                    if (nextLineEnd == -1) nextLineEnd = fullContent.length();

                    String nextLine = fullContent.substring(contentEnd, nextLineEnd);

                    int lineIndent = 0;
                    for (char c : nextLine.toCharArray()) {
                        if (c == ' ') lineIndent++;
                        else break;
                    }

                    if (lineIndent > toggleIndent) {
                        if (savedContent.length() > 0) savedContent.append("\n");
                        savedContent.append(nextLine);
                        contentEnd = nextLineEnd + 1;
                    } else if (nextLine.trim().isEmpty() && contentEnd < fullContent.length() - 1) {
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
                        break;
                    }
                }

                // Identify bookmarks that need to be hidden
                List<Bookmark> bookmarksToHideNow = new ArrayList<>();

                for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                    int bStart = bookmark.getStartIndex();
                    int bEnd = bookmark.getEndIndex();

                    // Only hide bookmarks that are COMPLETELY inside the content area
                    if (bStart >= contentStart && bEnd <= contentEnd) {
                        // Store relative offset from contentStart
                        int relativeStart = bStart - contentStart;
                        int relativeEnd = bEnd - contentStart;

                        // Create a copy to store
                        Bookmark hiddenBookmark = new Bookmark(
                                bookmark.getText(),
                                bookmark.getNote(),
                                bookmark.getColor(),
                                bookmark.getStyle(),
                                relativeStart,
                                relativeEnd
                        );
                        hiddenBookmark.setId(bookmark.getId());
                        bookmarksToHideNow.add(hiddenBookmark);
                    }
                }

                // Store hidden bookmarks in map
                if (!bookmarksToHideNow.isEmpty()) {
                    hiddenBookmarksByToggle.put(togglePosition, bookmarksToHideNow);
                }

                // Save content
                if (savedContent.length() > 0) {
                    toggleContents.put(togglePosition, savedContent.toString());
                } else {
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

                final String finalNewContent = result.toString();
                final int finalCursor = Math.min(togglePosition + newToggleLine.length(), finalNewContent.length());

                int removedLength = contentEnd - (lineEnd + 1);

                // Update bookmarks that come AFTER the collapsed content
                FirebaseUser user = auth.getCurrentUser();
                if (user != null && removedLength > 0) {
                    // Build set of hidden bookmark IDs
                    Set<String> hiddenIds = new HashSet<>();
                    for (Bookmark hidden : bookmarksToHideNow) {
                        hiddenIds.add(hidden.getId());
                    }

                    for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                        int oldStart = bookmark.getStartIndex();
                        int oldEnd = bookmark.getEndIndex();

                        // Skip bookmarks that are now hidden
                        if (hiddenIds.contains(bookmark.getId())) {
                            continue;
                        }

                        // Only shift bookmarks AFTER the collapsed content
                        if (oldStart >= contentEnd) {
                            int newStart = oldStart - removedLength;
                            int newEnd = oldEnd - removedLength;

                            if (newStart >= 0 && newEnd <= finalNewContent.length() && newStart < newEnd) {
                                try {
                                    String bookmarkText = finalNewContent.substring(newStart, newEnd);
                                    if (!bookmarkText.trim().isEmpty() && !bookmarkText.contains("【DIVIDER】")) {
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

                    // Store hidden bookmarks and remove from currentBookmarks
                    if (!bookmarksToHideNow.isEmpty()) {
                        hiddenBookmarksByToggle.put(togglePosition, bookmarksToHideNow);

                        Set<String> idsToRemove = new HashSet<>();
                        for (Bookmark hidden : bookmarksToHideNow) {
                            idsToRemove.add(hidden.getId());
                        }

                        currentBookmarks.removeIf(bookmark -> idsToRemove.contains(bookmark.getId()));
                    }
                }

                noteContent.setText(finalNewContent);
                noteContent.setSelection(finalCursor);

            } else {
                // ========== EXPAND ==========
                String newToggleLine = toggleLine.replace("▶", "▼");

                // ✅ FIX: Find saved content even if position shifted
                String savedContent = findToggleContentNearPosition(togglePosition);

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

                int addedLength = savedContent.length() + 1;

                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    // First, shift all bookmarks that come AFTER the toggle position
                    for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                        int oldStart = bookmark.getStartIndex();
                        int oldEnd = bookmark.getEndIndex();

                        // Only shift bookmarks after the toggle line
                        if (oldStart > lineEnd) {
                            int newStart = oldStart + addedLength;
                            int newEnd = oldEnd + addedLength;

                            if (newStart >= 0 && newEnd <= finalContent.length() && newStart < newEnd) {
                                try {
                                    String bookmarkText = finalContent.substring(newStart, newEnd);
                                    if (!bookmarkText.trim().isEmpty() && !bookmarkText.contains("【DIVIDER】")) {
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

                    // Now restore hidden bookmarks
                    List<Bookmark> hiddenBookmarks = hiddenBookmarksByToggle.get(togglePosition);

                    if (hiddenBookmarks != null && !hiddenBookmarks.isEmpty()) {
                        for (Bookmark hiddenBookmark : hiddenBookmarks) {
                            // Calculate absolute positions from relative positions
                            int absoluteStart = contentLineStart + hiddenBookmark.getStartIndex();
                            int absoluteEnd = contentLineStart + hiddenBookmark.getEndIndex();

                            if (absoluteStart >= 0 && absoluteEnd <= finalContent.length() && absoluteStart < absoluteEnd) {
                                try {
                                    String bookmarkText = finalContent.substring(absoluteStart, absoluteEnd);

                                    if (!bookmarkText.trim().isEmpty() && !bookmarkText.contains("【DIVIDER】")) {
                                        // Check if bookmark still exists
                                        boolean exists = false;
                                        for (Bookmark existing : currentBookmarks) {
                                            if (existing.getId().equals(hiddenBookmark.getId())) {
                                                updateBookmarkInFirestore(hiddenBookmark.getId(), absoluteStart, absoluteEnd, bookmarkText);
                                                existing.setStartIndex(absoluteStart);
                                                existing.setEndIndex(absoluteEnd);
                                                existing.setText(bookmarkText);
                                                exists = true;
                                                break;
                                            }
                                        }

                                        if (!exists) {
                                            Bookmark restoredBookmark = new Bookmark(
                                                    bookmarkText,
                                                    hiddenBookmark.getNote(),
                                                    hiddenBookmark.getColor(),
                                                    hiddenBookmark.getStyle(),
                                                    absoluteStart,
                                                    absoluteEnd
                                            );
                                            restoredBookmark.setId(hiddenBookmark.getId());

                                            updateBookmarkInFirestore(hiddenBookmark.getId(), absoluteStart, absoluteEnd, bookmarkText);
                                            currentBookmarks.add(restoredBookmark);
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        // Clear hidden bookmarks for this toggle
                        hiddenBookmarksByToggle.remove(togglePosition);
                    }
                }

                noteContent.setText(finalContent);
                noteContent.setSelection(finalCursor);
            }

            saveNoteContentToFirestore(noteContent.getText().toString());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            noteContent.postDelayed(new Runnable() {
                @Override
                public void run() {
                    applyBookmarksToText();
                    isUpdatingText = false;
                    isTogglingState = false;
                }
            }, 100);
        }
    }
    private String findToggleContentNearPosition(int togglePosition) {
        Log.d("TOGGLE_DEBUG", "🔍 Finding content for toggle at position: " + togglePosition);

        // First try exact match
        if (toggleContents.containsKey(togglePosition)) {
            String content = toggleContents.get(togglePosition);
            Log.d("TOGGLE_DEBUG", "✅ Found exact match, content length: " + content.length());
            return content;
        }

        // Get current text to analyze toggle lines
        String content = noteContent.getText().toString();

        // Find the toggle line at the given position
        int currentLineEnd = content.indexOf('\n', togglePosition);
        if (currentLineEnd == -1) currentLineEnd = content.length();

        if (togglePosition >= 0 && currentLineEnd <= content.length()) {
            String currentLine = content.substring(togglePosition, currentLineEnd);

            // Get current toggle's indentation
            int currentIndent = 0;
            for (char c : currentLine.toCharArray()) {
                if (c == ' ') currentIndent++;
                else break;
            }

            Log.d("TOGGLE_DEBUG", "🔍 Current toggle indent: " + currentIndent);

            // Search for saved content with matching indentation within ±100 characters
            String bestMatch = null;
            int bestMatchDistance = Integer.MAX_VALUE;

            for (Map.Entry<Integer, String> entry : toggleContents.entrySet()) {
                int savedPos = entry.getKey();
                int distance = Math.abs(savedPos - togglePosition);

                // Only consider positions within reasonable range
                if (distance <= 100) {
                    try {
                        int savedLineEnd = content.indexOf('\n', savedPos);
                        if (savedLineEnd == -1) savedLineEnd = content.length();

                        if (savedPos >= 0 && savedLineEnd <= content.length()) {
                            String savedLine = content.substring(savedPos, savedLineEnd);

                            // Get saved toggle's indentation
                            int savedIndent = 0;
                            for (char c : savedLine.toCharArray()) {
                                if (c == ' ') savedIndent++;
                                else break;
                            }

                            // Match by indentation level
                            if (savedIndent == currentIndent) {
                                if (distance < bestMatchDistance) {
                                    bestMatch = entry.getValue();
                                    bestMatchDistance = distance;

                                    Log.d("TOGGLE_DEBUG", "✅ Found potential match at distance " + distance +
                                            ", indent " + savedIndent);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("TOGGLE_DEBUG", "❌ Error checking saved position", e);
                    }
                }
            }

            if (bestMatch != null) {
                // Update the map with the correct position
                toggleContents.remove(togglePosition + bestMatchDistance);
                toggleContents.put(togglePosition, bestMatch);

                // Also update states if they exist
                if (toggleStates.containsKey(togglePosition + bestMatchDistance)) {
                    Boolean state = toggleStates.get(togglePosition + bestMatchDistance);
                    toggleStates.remove(togglePosition + bestMatchDistance);
                    toggleStates.put(togglePosition, state);
                }

                // Also update hidden bookmarks if they exist
                if (hiddenBookmarksByToggle.containsKey(togglePosition + bestMatchDistance)) {
                    List<Bookmark> bookmarks = hiddenBookmarksByToggle.get(togglePosition + bestMatchDistance);
                    hiddenBookmarksByToggle.remove(togglePosition + bestMatchDistance);
                    hiddenBookmarksByToggle.put(togglePosition, bookmarks);
                }

                Log.d("TOGGLE_DEBUG", "✅ Remapped content to correct position");
                return bestMatch;
            }
        }

        // Return empty content with proper indentation
        int lineEnd = content.indexOf('\n', togglePosition);
        if (lineEnd == -1) lineEnd = content.length();

        if (togglePosition >= 0 && lineEnd <= content.length()) {
            String toggleLine = content.substring(togglePosition, lineEnd);

            int toggleIndent = 0;
            for (char c : toggleLine.toCharArray()) {
                if (c == ' ') toggleIndent++;
                else break;
            }

            String emptyContent = "";
            for (int i = 0; i < toggleIndent + 4; i++) {
                emptyContent += " ";
            }

            Log.d("TOGGLE_DEBUG", "⚠️ No match found, returning empty content");
            return emptyContent;
        }

        return "    ";
    }
    private void insertToggleList() {
        String currentText = noteContent.getText().toString();
        int cursorPosition = noteContent.getSelectionStart();

        if (cursorPosition < 0) cursorPosition = 0;
        if (cursorPosition > currentText.length()) cursorPosition = currentText.length();

        int lineStart = currentText.lastIndexOf('\n', cursorPosition - 1) + 1;
        int lineEnd = currentText.indexOf('\n', cursorPosition);
        if (lineEnd == -1) lineEnd = currentText.length();

        String currentLine = currentText.substring(lineStart, lineEnd);

        int currentIndent = 0;
        for (char c : currentLine.toCharArray()) {
            if (c == ' ') currentIndent++;
            else break;
        }

        int toggleIndent = 0;
        if (currentIndent >= 4) {
            toggleIndent = currentIndent;
        }

        String indent = "";
        for (int i = 0; i < toggleIndent; i++) {
            indent += " ";
        }

        // ✅ CREATE UNIQUE ID for this toggle
        String toggleId = "toggle_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);

        String toggleItem;
        if (cursorPosition > 0 && currentText.charAt(cursorPosition - 1) != '\n') {
            toggleItem = "\n" + indent + "▶〔" + toggleId + "〕 ";
        } else {
            toggleItem = indent + "▶〔" + toggleId + "〕 ";
        }

        String newText = currentText.substring(0, cursorPosition) +
                toggleItem +
                currentText.substring(cursorPosition);

        final int finalCursorPos = cursorPosition + toggleItem.length();

        int newToggleStart = cursorPosition;
        if (toggleItem.startsWith("\n")) {
            newToggleStart = cursorPosition + 1;
        }

        noteContent.setText(newText);

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

        // ✅ Initialize NEW toggle with UNIQUE ID
        String emptyContent = "";
        for (int i = 0; i < toggleIndent + 4; i++) {
            emptyContent += " ";
        }

        toggleContentsById.put(toggleId, emptyContent);
        toggleStatesById.put(toggleId, false);
        positionToToggleId.put(newToggleStart, toggleId);

        Log.d("TOGGLE_DEBUG", "✅ Created new toggle: " + toggleId + " at position: " + newToggleStart);

        isToggleListMode = true;
    }
    private void checkForDeletedToggles(String currentText) {
        // Check each saved toggle position
        List<Integer> positionsToRemove = new ArrayList<>();

        for (Integer togglePos : new ArrayList<>(toggleContents.keySet())) {
            // Check if toggle still exists at this position
            if (togglePos >= currentText.length()) {
                positionsToRemove.add(togglePos);
                continue;
            }

            try {
                int lineEnd = currentText.indexOf('\n', togglePos);
                if (lineEnd == -1) lineEnd = currentText.length();

                if (togglePos >= 0 && lineEnd <= currentText.length()) {
                    String line = currentText.substring(togglePos, lineEnd);

                    // If line no longer contains toggle arrow, it was deleted
                    if (!line.contains("▶") && !line.contains("▼")) {
                        positionsToRemove.add(togglePos);
                    }
                }
            } catch (Exception e) {
                positionsToRemove.add(togglePos);
            }
        }

        // Clear data for deleted toggles
        for (Integer pos : positionsToRemove) {
            toggleContents.remove(pos);
            toggleStates.remove(pos);
            hiddenBookmarksByToggle.remove(pos);
            Log.d("TOGGLE_DEBUG", "🗑️ Detected deleted toggle at position: " + pos);
        }
    }
    private void updateTogglePositionsAfterEdit(int changePosition, int lengthDiff) {
        if (lengthDiff == 0) return;

        Log.d("TOGGLE_DEBUG", "Updating toggle positions: changePos=" + changePosition + ", diff=" + lengthDiff);

        // ✅ Update toggle contents
        Map<Integer, String> updatedToggleContents = new HashMap<>();
        for (Map.Entry<Integer, String> entry : toggleContents.entrySet()) {
            int oldPos = entry.getKey();
            String content = entry.getValue();

            if (oldPos >= changePosition) {
                int newPos = oldPos + lengthDiff;
                updatedToggleContents.put(newPos, content);
                Log.d("TOGGLE_DEBUG", "  📦 Moved toggle content: " + oldPos + " → " + newPos);
            } else {
                updatedToggleContents.put(oldPos, content);
            }
        }
        toggleContents = updatedToggleContents;

        // ✅ Update toggle states
        Map<Integer, Boolean> updatedToggleStates = new HashMap<>();
        for (Map.Entry<Integer, Boolean> entry : toggleStates.entrySet()) {
            int oldPos = entry.getKey();
            Boolean state = entry.getValue();

            if (oldPos >= changePosition) {
                int newPos = oldPos + lengthDiff;
                updatedToggleStates.put(newPos, state);
                Log.d("TOGGLE_DEBUG", "  🔄 Moved toggle state: " + oldPos + " → " + newPos);
            } else {
                updatedToggleStates.put(oldPos, state);
            }
        }
        toggleStates = updatedToggleStates;

        // ✅ Update hidden bookmarks by toggle
        Map<Integer, List<Bookmark>> updatedHiddenBookmarks = new HashMap<>();
        for (Map.Entry<Integer, List<Bookmark>> entry : hiddenBookmarksByToggle.entrySet()) {
            int oldPos = entry.getKey();
            List<Bookmark> bookmarks = entry.getValue();

            if (oldPos >= changePosition) {
                int newPos = oldPos + lengthDiff;
                updatedHiddenBookmarks.put(newPos, bookmarks);
                Log.d("TOGGLE_DEBUG", "  📚 Moved hidden bookmarks: " + oldPos + " → " + newPos);
            } else {
                updatedHiddenBookmarks.put(oldPos, bookmarks);
            }
        }
        hiddenBookmarksByToggle = updatedHiddenBookmarks;
    }
    //----------------------------------------------------------------//
    //INSERT LINK
    // Add this after your existing method declarations
    private String selectedLinkCaption = "";

    private void insertLink() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.link_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        TextInputEditText linkUrlInput = sheetView.findViewById(R.id.linkUrlInput);
        TextView createLinkBtn = sheetView.findViewById(R.id.createLinkBtn);

        createLinkBtn.setOnClickListener(v -> {
            String url = linkUrlInput.getText().toString().trim();

            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            createLinkWebView(url);
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }
    private void createLinkWebView(String url) {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();

        // Create link object
        LinkWeblink link = new LinkWeblink(url, extractTitle(url), "", "", "#FFFFFF", System.currentTimeMillis());

        // Save to Firestore
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("weblinks").add(link)
                    .addOnSuccessListener(doc -> {
                        Toast.makeText(this, "Link added", Toast.LENGTH_SHORT).show();
                        // ❌ REMOVE ALL THIS CODE BELOW - the listener will handle it!
                    /*
                    link.setId(doc.getId());
                    weblinks.add(link);

                    // Create and add the link view
                    View linkView = createLinkView(link);
                    weblinkViews.put(System.currentTimeMillis(), linkView);

                    // Add view to layout
                    LinearLayout container = findViewById(R.id.weblinksContainer);
                    if (container != null) {
                        container.addView(linkView);
                    }
                    */
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error adding link", Toast.LENGTH_SHORT).show();
                    });
        }
    }
    private String extractTitle(String url) {
        try {
            String domain = url.replace("https://", "").replace("http://", "");
            int slashIndex = domain.indexOf("/");
            if (slashIndex > 0) {
                domain = domain.substring(0, slashIndex);
            }

            // Capitalize first letter
            if (!domain.isEmpty()) {
                domain = domain.substring(0, 1).toUpperCase() + domain.substring(1);
            }

            return domain;
        } catch (Exception e) {
            return "Link";
        }
    }
    private View createLinkView(LinkWeblink link) {
        View linkView = getLayoutInflater().inflate(R.layout.link_web_view, null);

        // Set background color
        View cardView = linkView.findViewById(R.id.linkCardView);
        try {
            cardView.setBackgroundColor(Color.parseColor(link.getBackgroundColor()));
        } catch (Exception e) {
            cardView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        }

        // Set title
        TextView titleText = linkView.findViewById(R.id.linkTitle);
        titleText.setText(link.getTitle());

        // Set URL
        TextView urlText = linkView.findViewById(R.id.linkUrl);
        urlText.setText(link.getUrl());

        // Set description/caption if available
        TextView descText = linkView.findViewById(R.id.linkDescription);
        if (link.getDescription() != null && !link.getDescription().isEmpty()) {
            descText.setText(link.getDescription());
            descText.setVisibility(View.VISIBLE);
        } else {
            descText.setVisibility(View.GONE);
        }

        // Three dots menu
        ImageView menuBtn = linkView.findViewById(R.id.linkMenuBtn);
        menuBtn.setOnClickListener(v -> showLinkActionsSheet(link, linkView));

        // Click to open URL
        cardView.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link.getUrl()));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show();
            }
        });

        return linkView;
    }
    private void showLinkActionsSheet(LinkWeblink link, View linkView) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.link_actions_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        LinearLayout colorOption = sheetView.findViewById(R.id.linkColorOption);
        LinearLayout captionOption = sheetView.findViewById(R.id.linkCaptionOption);
        LinearLayout deleteOption = sheetView.findViewById(R.id.linkDeleteOption);

        colorOption.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showLinkColorSheet(link, linkView);
        });

        captionOption.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showLinkCaptionSheet(link, linkView);
        });

        deleteOption.setOnClickListener(v -> {
            bottomSheet.dismiss();
            deleteLinkWebView(link, linkView);
        });

        bottomSheet.show();
    }
    private void showLinkColorSheet(LinkWeblink link, View linkView) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.link_color_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        // Color options
        LinearLayout colorDefault = sheetView.findViewById(R.id.linkColorDefault);
        LinearLayout colorGray = sheetView.findViewById(R.id.linkColorGray);
        LinearLayout colorBrown = sheetView.findViewById(R.id.linkColorBrown);
        LinearLayout colorOrange = sheetView.findViewById(R.id.linkColorOrange);
        LinearLayout colorYellow = sheetView.findViewById(R.id.linkColorYellow);
        LinearLayout colorGreen = sheetView.findViewById(R.id.linkColorGreen);
        LinearLayout colorBlue = sheetView.findViewById(R.id.linkColorBlue);
        LinearLayout colorPurple = sheetView.findViewById(R.id.linkColorPurple);
        LinearLayout colorPink = sheetView.findViewById(R.id.linkColorPink);
        LinearLayout colorRed = sheetView.findViewById(R.id.linkColorRed);

        // Check marks
        ImageView checkDefault = sheetView.findViewById(R.id.checkDefault);
        ImageView checkGray = sheetView.findViewById(R.id.checkGray);
        ImageView checkBrown = sheetView.findViewById(R.id.checkBrown);
        ImageView checkOrange = sheetView.findViewById(R.id.checkOrange);
        ImageView checkYellow = sheetView.findViewById(R.id.checkYellow);
        ImageView checkGreen = sheetView.findViewById(R.id.checkGreen);
        ImageView checkBlue = sheetView.findViewById(R.id.checkBlue);
        ImageView checkPurple = sheetView.findViewById(R.id.checkPurple);
        ImageView checkPink = sheetView.findViewById(R.id.checkPink);
        ImageView checkRed = sheetView.findViewById(R.id.checkRed);

        // Hide all checks first
        checkDefault.setVisibility(View.GONE);
        checkGray.setVisibility(View.GONE);
        checkBrown.setVisibility(View.GONE);
        checkOrange.setVisibility(View.GONE);
        checkYellow.setVisibility(View.GONE);
        checkGreen.setVisibility(View.GONE);
        checkBlue.setVisibility(View.GONE);
        checkPurple.setVisibility(View.GONE);
        checkPink.setVisibility(View.GONE);
        checkRed.setVisibility(View.GONE);

        // Show current selection
        String currentColor = link.getBackgroundColor();
        switch (currentColor) {
            case "#FFFFFF": checkDefault.setVisibility(View.VISIBLE); break;
            case "#E0E0E0": checkGray.setVisibility(View.VISIBLE); break;
            case "#D7CCC8": checkBrown.setVisibility(View.VISIBLE); break;
            case "#FFE0B2": checkOrange.setVisibility(View.VISIBLE); break;
            case "#FFF9C4": checkYellow.setVisibility(View.VISIBLE); break;
            case "#C8E6C9": checkGreen.setVisibility(View.VISIBLE); break;
            case "#BBDEFB": checkBlue.setVisibility(View.VISIBLE); break;
            case "#E1BEE7": checkPurple.setVisibility(View.VISIBLE); break;
            case "#F8BBD0": checkPink.setVisibility(View.VISIBLE); break;
            case "#FFCDD2": checkRed.setVisibility(View.VISIBLE); break;
        }

        colorDefault.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#FFFFFF");
            bottomSheet.dismiss();
        });

        colorGray.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#E0E0E0");
            bottomSheet.dismiss();
        });

        colorBrown.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#D7CCC8");
            bottomSheet.dismiss();
        });

        colorOrange.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#FFE0B2");
            bottomSheet.dismiss();
        });

        colorYellow.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#FFF9C4");
            bottomSheet.dismiss();
        });

        colorGreen.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#C8E6C9");
            bottomSheet.dismiss();
        });

        colorBlue.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#BBDEFB");
            bottomSheet.dismiss();
        });

        colorPurple.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#E1BEE7");
            bottomSheet.dismiss();
        });

        colorPink.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#F8BBD0");
            bottomSheet.dismiss();
        });

        colorRed.setOnClickListener(v -> {
            updateLinkColor(link, linkView, "#FFCDD2");
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }
    private void updateLinkColor(LinkWeblink link, View linkView, String color) {
        link.setBackgroundColor(color);

        // Update view
        View cardView = linkView.findViewById(R.id.linkCardView);
        cardView.setBackgroundColor(Color.parseColor(color));

        // Update Firestore
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && link.getId() != null) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("weblinks").document(link.getId())
                    .update("backgroundColor", color)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Color updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error updating color", Toast.LENGTH_SHORT).show();
                    });
        }
    }
    private void showLinkCaptionSheet(LinkWeblink link, View linkView) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.link_caption_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        TextInputEditText captionInput = sheetView.findViewById(R.id.linkCaptionInput);
        TextView cancelBtn = sheetView.findViewById(R.id.cancelCaptionBtn);
        TextView saveBtn = sheetView.findViewById(R.id.saveCaptionBtn);

        // Pre-fill existing caption
        if (link.getDescription() != null && !link.getDescription().isEmpty()) {
            captionInput.setText(link.getDescription());
        }

        cancelBtn.setOnClickListener(v -> bottomSheet.dismiss());

        saveBtn.setOnClickListener(v -> {
            String caption = captionInput.getText().toString().trim();
            updateLinkCaption(link, linkView, caption);
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }
    private void updateLinkCaption(LinkWeblink link, View linkView, String caption) {
        link.setDescription(caption);

        // Update view
        TextView descText = linkView.findViewById(R.id.linkDescription);
        if (caption != null && !caption.isEmpty()) {
            descText.setText(caption);
            descText.setVisibility(View.VISIBLE);
        } else {
            descText.setVisibility(View.GONE);
        }

        // Update Firestore
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && link.getId() != null) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("weblinks").document(link.getId())
                    .update("description", caption)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Caption updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error updating caption", Toast.LENGTH_SHORT).show();
                    });
        }
    }
    private void deleteLinkWebView(LinkWeblink link, View linkView) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Link")
                .setMessage("Are you sure you want to delete this link?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Remove from Firestore
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null && link.getId() != null) {
                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("weblinks").document(link.getId())
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    // Remove from local list
                                    weblinks.remove(link);
                                    weblinkViews.remove(link.getPosition());

                                    // Remove view from layout
                                    LinearLayout container = findViewById(R.id.weblinksContainer);
                                    if (container != null) {
                                        container.removeView(linkView);
                                    }

                                    Toast.makeText(this, "Link deleted", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Error deleting link", Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void loadWeblinks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // ✅ Remove existing listener if any
        if (weblinkListener != null) {
            weblinkListener.remove();
        }

        weblinkListener = db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("weblinks")
                .orderBy("position")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading links", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null) {
                        // ✅ ADD THIS DEBUG LOG
                        Log.d("WEBLINKS", "Total documents in Firestore: " + value.size());
                        for (QueryDocumentSnapshot doc : value) {
                            Log.d("WEBLINKS", "Doc ID: " + doc.getId() + ", URL: " + doc.getString("url"));
                        }

                        weblinks.clear();
                        weblinkViews.clear();

                        LinearLayout container = findViewById(R.id.weblinksContainer);
                        if (container != null) {
                            container.removeAllViews();

                            for (QueryDocumentSnapshot doc : value) {
                                LinkWeblink link = doc.toObject(LinkWeblink.class);
                                link.setId(doc.getId());
                                weblinks.add(link);

                                View linkView = createLinkView(link);
                                weblinkViews.put(link.getPosition(), linkView);
                                container.addView(linkView);
                            }
                        }
                    }
                });
    }

    //----------------------------------------------------------------//
    //CHECKBOX
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
    private void setupCheckboxAutoWatcher() {
        noteContent.addTextChangedListener(new TextWatcher() {
            private boolean isProcessing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isProcessing || isUpdatingText || isTogglingState) return;

                if (count == 1 && start < s.length() && s.charAt(start) == '\n') {
                    isProcessing = true;

                    String textBeforeNewline = s.toString().substring(0, start);
                    int lastNewlineIndex = textBeforeNewline.lastIndexOf('\n');
                    String currentLine = textBeforeNewline.substring(lastNewlineIndex + 1);

                    if (currentLine.matches("^\\s*[☐☑]\\s.*")) {
                        // ✅ If empty checkbox line, stop checkbox mode
                        if (currentLine.matches("^\\s*[☐☑]\\s*$")) {
                            String newText = s.toString().substring(0, lastNewlineIndex + 1) +
                                    s.toString().substring(start + 1);

                            // ✅ Save ALL spans BEFORE setText
                            Editable editable = noteContent.getEditableText();
                            List<SpanInfo> allSpans = saveAllSpans(editable);

                            isUpdatingText = true;
                            noteContent.setText(newText);

                            // ✅ Restore spans AFTER setText
                            restoreAllSpans(noteContent.getEditableText(), allSpans);

                            noteContent.setSelection(lastNewlineIndex + 1);
                            isUpdatingText = false;

                            isProcessing = false;
                            return; // ✅ EXIT - Stop checkbox mode
                        } else {
                            // Continue checkbox mode - add new checkbox
                            String indentAndCheckbox = getCheckboxWithIndentation(currentLine);

                            String newText = s.toString().substring(0, start + 1) +
                                    indentAndCheckbox +
                                    s.toString().substring(start + 1);

                            int insertedLength = indentAndCheckbox.length();

                            // ✅ Save ALL spans BEFORE setText
                            Editable editable = noteContent.getEditableText();
                            List<SpanInfo> allSpans = saveAllSpans(editable);

                            // ✅ Adjust span positions for the inserted text
                            for (SpanInfo info : allSpans) {
                                if (info.start > start) {
                                    info.start += insertedLength + 1; // +1 for newline
                                    info.end += insertedLength + 1;
                                } else if (info.end > start) {
                                    info.end += insertedLength + 1;
                                }
                            }

                            isUpdatingText = true;
                            noteContent.setText(newText);

                            // ✅ Restore adjusted spans AFTER setText
                            restoreAllSpans(noteContent.getEditableText(), allSpans);

                            noteContent.setSelection(start + 1 + indentAndCheckbox.length());
                            isUpdatingText = false;
                        }
                    }

                    isProcessing = false;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    private String getCheckboxWithIndentation(String line) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\s*)([☐☑])\\s");
        java.util.regex.Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String indentation = matcher.group(1);
            // Always use unchecked checkbox for new line
            return indentation + "☐ ";
        }

        return "☐ ";
    }

    //----------------------------------------------------------------//
    //TOGGLE AND CHECKBOX WATCHER
    private void setupCheckboxWatcher() {
        noteContent.setOnClickListener(v -> {
            int cursorPos = noteContent.getSelectionStart();
            String content = noteContent.getText().toString();

            int lineStart = content.lastIndexOf('\n', cursorPos - 1) + 1;
            int lineEnd = content.indexOf('\n', cursorPos);
            if (lineEnd == -1) lineEnd = content.length();

            String currentLine = content.substring(lineStart, lineEnd);

            // ✅ Check if line contains toggle arrow WITH ID
            if (currentLine.matches("^\\s*[▶▼]〔toggle_\\d+_\\d+〕\\s.*")) {
                // Extract toggle ID
                java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("〔(toggle_\\d+_\\d+)〕");
                java.util.regex.Matcher idMatcher = idPattern.matcher(currentLine);

                if (idMatcher.find()) {
                    String toggleId = idMatcher.group(1);
                    int arrowPos = currentLine.indexOf('▶');
                    if (arrowPos == -1) arrowPos = currentLine.indexOf('▼');

                    if (arrowPos != -1) {
                        int absoluteArrowPos = lineStart + arrowPos;
                        if (Math.abs(cursorPos - absoluteArrowPos) <= 1) {
                            toggleToggleStateById(toggleId, lineStart, content);
                            return;
                        }
                    }
                }
            }

            // Checkbox handling...
            if (currentLine.contains("☐") || currentLine.contains("☑")) {
                int checkboxPos = -1;
                boolean isChecked = false;

                if (currentLine.contains("☐")) {
                    checkboxPos = lineStart + currentLine.indexOf("☐");
                    isChecked = false;
                } else if (currentLine.contains("☑")) {
                    checkboxPos = lineStart + currentLine.indexOf("☑");
                    isChecked = true;
                }

                if (checkboxPos != -1 && Math.abs(cursorPos - checkboxPos) <= 3) {
                    toggleCheckbox(lineStart, lineEnd, isChecked);
                }
            }
        });
    }
    private void toggleToggleStateById(String toggleId, int togglePosition, String fullContent) {
        Log.d("TOGGLE_DEBUG", "🔄 Toggling by ID: " + toggleId + " at position: " + togglePosition);

        isUpdatingText = true;
        isTogglingState = true;

        try {
            int lineEnd = fullContent.indexOf('\n', togglePosition);
            if (lineEnd == -1) lineEnd = fullContent.length();

            String toggleLine = fullContent.substring(togglePosition, lineEnd);
            boolean isExpanded = toggleLine.contains("▼");

            int toggleIndent = 0;
            for (char c : toggleLine.toCharArray()) {
                if (c == ' ') toggleIndent++;
                else break;
            }

            if (isExpanded) {
                // ========== COLLAPSE ==========
                String newToggleLine = toggleLine.replace("▼", "▶");

                int contentStart = lineEnd + 1;
                int contentEnd = contentStart;
                StringBuilder savedContent = new StringBuilder();

                while (contentEnd < fullContent.length()) {
                    int nextLineEnd = fullContent.indexOf('\n', contentEnd);
                    if (nextLineEnd == -1) nextLineEnd = fullContent.length();

                    String nextLine = fullContent.substring(contentEnd, nextLineEnd);

                    int lineIndent = 0;
                    for (char c : nextLine.toCharArray()) {
                        if (c == ' ') lineIndent++;
                        else break;
                    }

                    if (lineIndent > toggleIndent) {
                        if (savedContent.length() > 0) savedContent.append("\n");
                        savedContent.append(nextLine);
                        contentEnd = nextLineEnd + 1;
                    } else if (nextLine.trim().isEmpty() && contentEnd < fullContent.length() - 1) {
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
                        break;
                    }
                }

                // Save bookmarks for THIS toggle only
                List<Bookmark> bookmarksToHideNow = new ArrayList<>();
                for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                    int bStart = bookmark.getStartIndex();
                    int bEnd = bookmark.getEndIndex();

                    if (bStart >= contentStart && bEnd <= contentEnd) {
                        int relativeStart = bStart - contentStart;
                        int relativeEnd = bEnd - contentStart;

                        Bookmark hiddenBookmark = new Bookmark(
                                bookmark.getText(),
                                bookmark.getNote(),
                                bookmark.getColor(),
                                bookmark.getStyle(),
                                relativeStart,
                                relativeEnd
                        );
                        hiddenBookmark.setId(bookmark.getId());
                        bookmarksToHideNow.add(hiddenBookmark);
                    }
                }

                if (!bookmarksToHideNow.isEmpty()) {
                    hiddenBookmarksByToggleId.put(toggleId, bookmarksToHideNow);
                }

                // Save content for THIS toggle
                if (savedContent.length() > 0) {
                    toggleContentsById.put(toggleId, savedContent.toString());
                }

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

                toggleStatesById.put(toggleId, false);

                String finalNewContent = result.toString();
                int finalCursor = Math.min(togglePosition + newToggleLine.length(), finalNewContent.length());

                int removedLength = contentEnd - (lineEnd + 1);

                // Update OTHER bookmarks (not the hidden ones)
                FirebaseUser user = auth.getCurrentUser();
                if (user != null && removedLength > 0) {
                    Set<String> hiddenIds = new HashSet<>();
                    for (Bookmark hidden : bookmarksToHideNow) {
                        hiddenIds.add(hidden.getId());
                    }

                    for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                        int oldStart = bookmark.getStartIndex();
                        int oldEnd = bookmark.getEndIndex();

                        if (hiddenIds.contains(bookmark.getId())) {
                            continue;
                        }

                        if (oldStart >= contentEnd) {
                            int newStart = oldStart - removedLength;
                            int newEnd = oldEnd - removedLength;

                            if (newStart >= 0 && newEnd <= finalNewContent.length() && newStart < newEnd) {
                                try {
                                    String bookmarkText = finalNewContent.substring(newStart, newEnd);
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

                    // Remove hidden bookmarks from visible list
                    if (!bookmarksToHideNow.isEmpty()) {
                        Set<String> idsToRemove = new HashSet<>();
                        for (Bookmark hidden : bookmarksToHideNow) {
                            idsToRemove.add(hidden.getId());
                        }
                        currentBookmarks.removeIf(bookmark -> idsToRemove.contains(bookmark.getId()));
                    }
                }

                noteContent.setText(finalNewContent);
                noteContent.setSelection(finalCursor);

            } else {
                // ========== EXPAND ==========
                String newToggleLine = toggleLine.replace("▶", "▼");

                // Get saved content for THIS toggle ONLY
                String savedContent = toggleContentsById.get(toggleId);
                if (savedContent == null) {
                    savedContent = "";
                    for (int i = 0; i < toggleIndent + 4; i++) {
                        savedContent += " ";
                    }
                }

                StringBuilder result = new StringBuilder();
                result.append(fullContent.substring(0, togglePosition));
                result.append(newToggleLine);
                result.append("\n");
                result.append(savedContent);

                if (lineEnd < fullContent.length()) {
                    result.append(fullContent.substring(lineEnd));
                }

                toggleStatesById.put(toggleId, true);

                String finalContent = result.toString();
                int contentLineStart = togglePosition + newToggleLine.length() + 1;
                int finalCursor = Math.min(contentLineStart + savedContent.length(), finalContent.length());

                int addedLength = savedContent.length() + 1;

                // Update OTHER bookmarks
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                        int oldStart = bookmark.getStartIndex();
                        int oldEnd = bookmark.getEndIndex();

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

                    // Restore hidden bookmarks for THIS toggle ONLY
                    List<Bookmark> hiddenBookmarks = hiddenBookmarksByToggleId.get(toggleId);

                    if (hiddenBookmarks != null && !hiddenBookmarks.isEmpty()) {
                        for (Bookmark hiddenBookmark : hiddenBookmarks) {
                            int absoluteStart = contentLineStart + hiddenBookmark.getStartIndex();
                            int absoluteEnd = contentLineStart + hiddenBookmark.getEndIndex();

                            if (absoluteStart >= 0 && absoluteEnd <= finalContent.length() && absoluteStart < absoluteEnd) {
                                try {
                                    String bookmarkText = finalContent.substring(absoluteStart, absoluteEnd);

                                    if (!bookmarkText.trim().isEmpty() && !bookmarkText.contains("〔DIVIDER〕")) {
                                        boolean exists = false;
                                        for (Bookmark existing : currentBookmarks) {
                                            if (existing.getId().equals(hiddenBookmark.getId())) {
                                                updateBookmarkInFirestore(hiddenBookmark.getId(), absoluteStart, absoluteEnd, bookmarkText);
                                                existing.setStartIndex(absoluteStart);
                                                existing.setEndIndex(absoluteEnd);
                                                existing.setText(bookmarkText);
                                                exists = true;
                                                break;
                                            }
                                        }

                                        if (!exists) {
                                            Bookmark restoredBookmark = new Bookmark(
                                                    bookmarkText,
                                                    hiddenBookmark.getNote(),
                                                    hiddenBookmark.getColor(),
                                                    hiddenBookmark.getStyle(),
                                                    absoluteStart,
                                                    absoluteEnd
                                            );
                                            restoredBookmark.setId(hiddenBookmark.getId());

                                            updateBookmarkInFirestore(hiddenBookmark.getId(), absoluteStart, absoluteEnd, bookmarkText);
                                            currentBookmarks.add(restoredBookmark);
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        hiddenBookmarksByToggleId.remove(toggleId);
                    }
                }

                noteContent.setText(finalContent);
                noteContent.setSelection(finalCursor);
            }

            saveNoteContentToFirestore(noteContent.getText().toString());

        } catch (Exception e) {
            Log.e("TOGGLE_DEBUG", "❌ Error toggling", e);
            e.printStackTrace();
        } finally {
            noteContent.postDelayed(new Runnable() {
                @Override
                public void run() {
                    applyBookmarksToText();
                    isUpdatingText = false;
                    isTogglingState = false;
                }
            }, 100);
        }
    }
    private void toggleCheckbox(int lineStart, int lineEnd, boolean currentlyChecked) {
        isUpdatingText = true;

        String content = noteContent.getText().toString();
        String line = content.substring(lineStart, lineEnd);

        String newLine;
        if (currentlyChecked) {
            // Uncheck: ☑ -> ☐
            newLine = line.replace("☑", "☐");
        } else {
            // Check: ☐ -> ☑
            newLine = line.replace("☐", "☑");
        }

        String newContent = content.substring(0, lineStart) + newLine + content.substring(lineEnd);

        // Create spannable for styling
        android.text.SpannableString spannable = new android.text.SpannableString(newContent);

        // Apply strike-through and grey color to checked items
        applyCheckboxStyles(spannable, newContent);

        // Apply existing bookmarks and dividers
        applyBookmarksAndDividersToSpannable(spannable, newContent);

        int cursor = noteContent.getSelectionStart();
        noteContent.setText(spannable, android.widget.TextView.BufferType.SPANNABLE);

        if (cursor >= 0 && cursor <= newContent.length()) {
            noteContent.setSelection(cursor);
        }

        isUpdatingText = false;

        // Save to Firestore
        saveNoteContentToFirestore(newContent);
    }
    private void applyCheckboxStyles(android.text.Spannable spannable, String content) {
        String[] lines = content.split("\n");
        int currentPos = 0;

        // ✅ DON'T remove existing spans - just add checkbox styling

        for (String line : lines) {
            if (line.contains("☑")) {
                int checkboxIndex = line.indexOf("☑");
                int textStart = currentPos + checkboxIndex + 2; // After "☑ "
                int textEnd = currentPos + line.length();

                if (textStart < textEnd && textEnd <= content.length()) {
                    // Apply grey color (will stack with heading styles)
                    spannable.setSpan(
                            new android.text.style.ForegroundColorSpan(0xFF999999),
                            textStart,
                            textEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    // Apply strike-through (will stack with heading styles)
                    spannable.setSpan(
                            new android.text.style.StrikethroughSpan(),
                            textStart,
                            textEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }

            currentPos += line.length() + 1; // +1 for newline
        }
    }
    private void applyBookmarksAndDividersToSpannable(android.text.SpannableString spannable, String content) {
        String dividerPlaceholder = "〔DIVIDER〕";

        // Apply dividers
        int dividerIndex = 0;
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

            spannable.setSpan(
                    new DividerSpan(style, 0xFF666666),
                    dividerIndex,
                    dividerEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            dividerIndex = dividerEnd;
        }

        // Apply bookmarks
        Set<String> hiddenBookmarkIds = new HashSet<>();
        for (List<Bookmark> hiddenList : hiddenBookmarksByToggle.values()) {
            for (Bookmark hidden : hiddenList) {
                hiddenBookmarkIds.add(hidden.getId());
            }
        }

        for (Bookmark b : currentBookmarks) {
            if (hiddenBookmarkIds.contains(b.getId())) {
                continue;
            }

            int s = b.getStartIndex();
            int e = b.getEndIndex();

            if (s < 0 || e > content.length() || s >= e) continue;

            String bookmarkText = content.substring(s, e);
            if (bookmarkText.contains(dividerPlaceholder)) continue;

            try {
                int color = android.graphics.Color.parseColor(b.getColor());
                if ("highlight".equals(b.getStyle())) {
                    spannable.setSpan(
                            new android.text.style.BackgroundColorSpan(color),
                            s, e,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                } else if ("underline".equals(b.getStyle())) {
                    spannable.setSpan(
                            new CustomUnderlineSpan(color, s, e),
                            s, e,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            } catch (Exception ignored) {}
        }
    }

    // Insert Image and Take Photo Workflow
    // =========================================================================
    private static class SpanInfo {
        Object span;
        int start;
        int end;

        SpanInfo(Object span, int start, int end) {
            this.span = span;
            this.start = start;
            this.end = end;
        }
    }
    private void setupImagePickers() {
        // Gallery launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            uploadImageToFirebase(imageUri);
                        }
                    }
                }
        );

        // Camera launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (currentPhotoUri != null) {
                            // currentPhotoUri holds the URI of the captured image
                            uploadImageToFirebase(currentPhotoUri);
                        }
                    }
                }
        );

        // Permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required to take pictures",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showInsertMediaBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        // Assuming you have R.layout.insert_media_bottom_sheet
        View sheetView = getLayoutInflater().inflate(R.layout.insert_media_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        // Assuming these IDs are in your bottom sheet layout
        View openGallery = sheetView.findViewById(R.id.openGalleryOption);
        View takePicture = sheetView.findViewById(R.id.takePictureOption);

        openGallery.setOnClickListener(v -> {
            bottomSheet.dismiss();
            openGallery();
        });

        takePicture.setOnClickListener(v -> {
            bottomSheet.dismiss();
            checkCameraPermission();
        });

        // NOTE: You can remove R.id.cancelMediaBtn if the bottom sheet closes on swipe/tap outside.
        // If you need a separate cancel button:
        // TextView cancelBtn = sheetView.findViewById(R.id.cancelMediaBtn);
        // cancelBtn.setOnClickListener(v -> bottomSheet.dismiss());

        bottomSheet.show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void checkCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.CAMERA);
            } else {
                openCamera();
            }
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
                return;
            }

            if (photoFile != null) {
                // Use FileProvider to get a safe Uri
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                cameraLauncher.launch(takePictureIntent);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    // Image Processing and Upload Logic
    private void uploadImageToFirebase(Uri imageUri) {
        if (noteId == null) {
            Toast.makeText(this, "Please save the note first", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Processing image...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            progressDialog.dismiss();
            return;
        }

        // Remember cursor position BEFORE processing
        final int cursorPosition = noteContent.getSelectionStart();

        new Thread(() -> {
            try {
                // Get Bitmap from Uri
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

                // Scaling Logic (Good practice)
                int originalWidth = bitmap.getWidth();
                int originalHeight = bitmap.getHeight();
                float scale = Math.min(
                        MAX_IMAGE_WIDTH / (float) originalWidth,
                        MAX_IMAGE_HEIGHT / (float) originalHeight
                );

                if (scale < 1.0f) {
                    int newWidth = (int) (originalWidth * scale);
                    int newHeight = (int) (originalHeight * scale);
                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                }

                // Compression
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, baos);
                byte[] imageBytes = baos.toByteArray();

                // 🛠️ IMPORTANT FIX: Use Base64.NO_WRAP to prevent newlines
                String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);

                // Stats
                int originalSizeKB = imageBytes.length / 1024;
                int base64SizeKB = base64Image.length() / 1024;

                // Keep bitmap for inline display
                final Bitmap finalBitmap = bitmap;

                runOnUiThread(() -> {
                    progressDialog.dismiss();

                    // Check for chunking requirement
                    if (base64SizeKB > MAX_INLINE_IMAGE_KB) { // Assuming MAX_INLINE_IMAGE_KB = 700
                        Toast.makeText(this,
                                "Saving large image (" + originalSizeKB + " KB) in chunks...",
                                Toast.LENGTH_SHORT).show();
                        uploadImageInChunks(base64Image, originalSizeKB, cursorPosition, finalBitmap);
                    } else {
                        Toast.makeText(this,
                                "Saving image (" + originalSizeKB + " KB) inline...",
                                Toast.LENGTH_SHORT).show();
                        insertImageIntoNote(base64Image, false, originalSizeKB, cursorPosition, finalBitmap);
                    }
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error processing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Image too large for device memory", Toast.LENGTH_LONG).show();
                });
                e.printStackTrace();
            }
        }).start();
    }
    private void uploadImageInChunks(String base64Image, int sizeKB, int cursorPosition, Bitmap bitmap) {
        String imageId = System.currentTimeMillis() + "";
        int totalLength = base64Image.length();
        int chunkCount = (int) Math.ceil(totalLength / (double) CHUNK_SIZE);

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("imageId", imageId);
        metadata.put("isChunked", true);
        metadata.put("chunkCount", chunkCount);
        metadata.put("totalSize", totalLength);
        metadata.put("sizeKB", sizeKB);
        metadata.put("position", cursorPosition);
        metadata.put("timestamp", System.currentTimeMillis());

        // 1. Save metadata
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("images").document(imageId)
                .set(metadata)
                .addOnSuccessListener(aVoid -> {
                    // 2. Save chunks
                    for (int i = 0; i < chunkCount; i++) {
                        int start = i * CHUNK_SIZE;
                        int end = Math.min(start + CHUNK_SIZE, totalLength);
                        String chunk = base64Image.substring(start, end);

                        Map<String, Object> chunkData = new HashMap<>();
                        chunkData.put("data", chunk);
                        chunkData.put("chunkIndex", i);

                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("images").document(imageId)
                                .collection("chunks").document(String.valueOf(i))
                                .set(chunkData);
                    }

                    // 3. Insert inline placeholder
                    insertInlineImage(imageId, bitmap, cursorPosition);
                    Toast.makeText(this, "✅ Large image saved and inserted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save large image", Toast.LENGTH_SHORT).show();
                });
    }
    private void insertImageIntoNote(String base64Image, boolean isChunked, int sizeKB, int cursorPosition, Bitmap bitmap) {
        String imageId = System.currentTimeMillis() + "";

        Map<String, Object> imageData = new HashMap<>();
        if (!isChunked) {
            // Only include base64Data if not chunked
            imageData.put("base64Data", base64Image);
        }
        imageData.put("imageId", imageId);
        imageData.put("isChunked", isChunked);
        imageData.put("sizeKB", sizeKB);
        imageData.put("position", cursorPosition);
        imageData.put("timestamp", System.currentTimeMillis());

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("images").document(imageId)
                    .set(imageData)
                    .addOnSuccessListener(aVoid -> {
                        // Insert inline at cursor position
                        insertInlineImage(imageId, bitmap, cursorPosition);
                        Toast.makeText(this, "✅ Image saved and inserted", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
                    });
        }
    }
    // Display and Deletion Logic
    private void insertInlineImage(String imageId, Bitmap bitmap, int cursorPosition) {
        Log.d("IMAGE_DEBUG", "🖼 Inserting image: " + imageId + " at position: " + cursorPosition);

        isUpdatingText = true;
        isTogglingState = true;

        String currentText = noteContent.getText().toString();
        String imagePlaceholder = "【IMAGE:" + imageId + "】";

        int safeCursorPosition = Math.max(0, Math.min(cursorPosition, currentText.length()));

        String prefix = currentText.substring(0, safeCursorPosition);
        String suffix = currentText.substring(safeCursorPosition);

        String nlBefore = (prefix.length() > 0 && prefix.charAt(prefix.length() - 1) != '\n') ? "\n" : "";
        String nlAfter = (suffix.length() > 0 && suffix.charAt(0) != '\n') ? "\n" : "";

        String newText = prefix + nlBefore + imagePlaceholder + nlAfter + suffix;

        int totalInsertedLength = nlBefore.length() + imagePlaceholder.length() + nlAfter.length();

        // ✅ STEP 1: Save ALL existing spans (INCLUDING heading/font styles!)
        Editable oldEditable = noteContent.getEditableText();

        // Save ALL span types
        List<SpanInfo> imageSpans = new ArrayList<>();
        List<SpanInfo> dividerSpans = new ArrayList<>();
        List<SpanInfo> bookmarkSpans = new ArrayList<>();
        List<SpanInfo> headingSpans = new ArrayList<>();  // ✅ NEW
        List<SpanInfo> fontSpans = new ArrayList<>();     // ✅ NEW
        List<SpanInfo> checkboxSpans = new ArrayList<>();
        List<SpanInfo> otherSpans = new ArrayList<>();

        // Categorize all spans
        Object[] allSpansArray = oldEditable.getSpans(0, oldEditable.length(), Object.class);
        for (Object span : allSpansArray) {
            int start = oldEditable.getSpanStart(span);
            int end = oldEditable.getSpanEnd(span);

            if (start < 0 || end < 0 || start >= end) continue;

            SpanInfo info = new SpanInfo(span, start, end);

            if (span instanceof ImageSpan) {
                imageSpans.add(info);
            } else if (span instanceof DividerSpan) {
                dividerSpans.add(info);
            } else if (span instanceof BackgroundColorSpan || span instanceof CustomUnderlineSpan) {
                bookmarkSpans.add(info);
            } else if (span instanceof android.text.style.RelativeSizeSpan) {
                headingSpans.add(info);  // ✅ Preserve heading sizes
            } else if (span instanceof android.text.style.StyleSpan) {
                // Check if it's a checkbox style or font style
                String spannedText = "";
                try {
                    spannedText = currentText.substring(start, Math.min(end, currentText.length()));
                } catch (Exception e) {}

                if (spannedText.contains("☑")) {
                    checkboxSpans.add(info);
                } else {
                    fontSpans.add(info);  // ✅ Preserve font styles (bold/italic)
                }
            } else if (span instanceof android.text.style.ForegroundColorSpan ||
                    span instanceof android.text.style.StrikethroughSpan) {
                checkboxSpans.add(info);
            } else {
                otherSpans.add(info);
            }
        }

        // ✅ STEP 2: Adjust ALL span positions for the insertion
        adjustSpanPositions(imageSpans, safeCursorPosition, totalInsertedLength);
        adjustSpanPositions(dividerSpans, safeCursorPosition, totalInsertedLength);
        adjustSpanPositions(bookmarkSpans, safeCursorPosition, totalInsertedLength);
        adjustSpanPositions(headingSpans, safeCursorPosition, totalInsertedLength);  // ✅ NEW
        adjustSpanPositions(fontSpans, safeCursorPosition, totalInsertedLength);     // ✅ NEW
        adjustSpanPositions(checkboxSpans, safeCursorPosition, totalInsertedLength);
        adjustSpanPositions(otherSpans, safeCursorPosition, totalInsertedLength);

        // Update bookmarks in Firestore
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                int bStart = bookmark.getStartIndex();
                int bEnd = bookmark.getEndIndex();
                boolean needsUpdate = false;

                if (bStart >= safeCursorPosition) {
                    bStart += totalInsertedLength;
                    bEnd += totalInsertedLength;
                    needsUpdate = true;
                } else if (safeCursorPosition > bStart && safeCursorPosition < bEnd) {
                    bEnd += totalInsertedLength;
                    needsUpdate = true;
                }

                if (needsUpdate && bStart >= 0 && bEnd <= newText.length() && bStart < bEnd) {
                    try {
                        String bookmarkText = newText.substring(bStart, bEnd);
                        if (!bookmarkText.trim().isEmpty() && !bookmarkText.contains("【IMAGE:")) {
                            bookmark.setStartIndex(bStart);
                            bookmark.setEndIndex(bEnd);
                            bookmark.setText(bookmarkText);
                            updateBookmarkInFirestore(bookmark.getId(), bStart, bEnd, bookmarkText);
                        }
                    } catch (Exception e) {
                        Log.e("IMAGE_DEBUG", "Error updating bookmark", e);
                    }
                }
            }
        }

        // ✅ STEP 3: Create spannable for new text
        SpannableString spannable = new SpannableString(newText);

        // ✅ STEP 4: Restore ALL existing spans in correct order
        // Order matters: images first, then headings, then fonts, then bookmarks, then checkboxes
        restoreSpans(spannable, imageSpans);
        restoreSpans(spannable, dividerSpans);
        restoreSpans(spannable, headingSpans);     // ✅ Restore headings BEFORE other styles
        restoreSpans(spannable, fontSpans);        // ✅ Restore fonts
        restoreSpans(spannable, bookmarkSpans);
        restoreSpans(spannable, checkboxSpans);
        restoreSpans(spannable, otherSpans);

        // ✅ STEP 5: Add the NEW image span
        int placeholderStart = newText.indexOf(imagePlaceholder, prefix.length());
        int placeholderEnd = placeholderStart + imagePlaceholder.length();

        if (placeholderStart == -1) {
            Log.e("IMAGE_DEBUG", "❌ Placeholder not found after insertion");
            isUpdatingText = false;
            isTogglingState = false;
            return;
        }

        // Resize bitmap for display
        int maxWidth = noteContent.getWidth() - noteContent.getPaddingLeft() - noteContent.getPaddingRight();
        if (maxWidth <= 0) maxWidth = 800;

        int displayWidth = Math.min(bitmap.getWidth(), maxWidth);
        int displayHeight = (int) (bitmap.getHeight() * (displayWidth / (float) bitmap.getWidth()));

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, displayWidth, displayHeight, true);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), resizedBitmap);
        drawable.setBounds(0, 0, displayWidth, displayHeight);

        ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BASELINE);

        spannable.setSpan(
                imageSpan,
                placeholderStart,
                placeholderEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // ✅ STEP 6: Set the spannable text
        noteContent.setText(spannable, TextView.BufferType.SPANNABLE);

        // Set cursor position
        int newCursorPos = placeholderEnd + nlAfter.length();
        if (newCursorPos <= spannable.length()) {
            noteContent.setSelection(newCursorPos);
        } else {
            noteContent.setSelection(spannable.length());
        }

        Log.d("IMAGE_DEBUG", "✅ Image inserted successfully, all spans preserved");

        noteContent.postDelayed(() -> {
            isUpdatingText = false;
            isTogglingState = false;
            noteContent.invalidate();
        }, 500);

        saveNoteContentToFirestore(newText);
    }

    private void adjustSpanPositions(List<SpanInfo> spans, int insertPosition, int insertedLength) {
        for (SpanInfo info : spans) {
            if (info.start >= insertPosition) {
                info.start += insertedLength;
                info.end += insertedLength;
            } else if (info.end > insertPosition) {
                info.end += insertedLength;
            }
        }
    }

    // ✅ Helper method to restore spans
    private void restoreSpans(SpannableString spannable, List<SpanInfo> spans) {
        for (SpanInfo info : spans) {
            if (info.start >= 0 && info.end <= spannable.length() && info.start < info.end) {
                try {
                    spannable.setSpan(info.span, info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception e) {
                    // Ignore invalid spans
                }
            }
        }
    }

    private void displayImagesInNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) {
            Log.e("IMAGE_DEBUG", "User or noteId is null");
            return;
        }

        Log.d("IMAGE_DEBUG", "🔍 Loading images for note: " + noteId);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("images")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d("IMAGE_DEBUG", "✅ Found " + querySnapshot.size() + " images");

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String imageId = doc.getString("imageId");
                        Boolean isChunked = doc.getBoolean("isChunked");

                        Log.d("IMAGE_DEBUG", "📷 Image ID: " + imageId + ", Chunked: " + isChunked);

                        if (imageId != null) {
                            if (isChunked != null && isChunked) {
                                loadChunkedImageInline(imageId);
                            } else {
                                String base64Data = doc.getString("base64Data");
                                if (base64Data != null) {
                                    Log.d("IMAGE_DEBUG", "📊 Base64 size: " + base64Data.length());
                                    displayImage(imageId, base64Data);
                                } else {
                                    Log.e("IMAGE_DEBUG", "❌ No base64Data for image: " + imageId);
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("IMAGE_DEBUG", "❌ Error loading images", e);

                });
    }
    private void loadChunkedImageInline(String imageId) {
        loadChunkedImage(imageId, () -> {
            // No callback needed for this version
        });
    }
    private void loadChunkedImage(String imageId, ImageLoadCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onComplete();
            return;
        }

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("images").document(imageId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || doc.getLong("chunkCount") == null) {
                        callback.onComplete();
                        return;
                    }

                    int expectedChunkCount = doc.getLong("chunkCount").intValue();

                    db.collection("users").document(user.getUid())
                            .collection("notes").document(noteId)
                            .collection("images").document(imageId)
                            .collection("chunks")
                            .orderBy("chunkIndex")
                            .get()
                            .addOnSuccessListener(chunks -> {
                                if (chunks.size() != expectedChunkCount) {
                                    Log.w("IMAGE_DEBUG", "Missing chunks");
                                    callback.onComplete();
                                    return;
                                }

                                StringBuilder fullBase64 = new StringBuilder();
                                for (QueryDocumentSnapshot chunk : chunks) {
                                    String data = chunk.getString("data");
                                    if (data != null) {
                                        fullBase64.append(data);
                                    }
                                }

                                if (fullBase64.length() > 0) {
                                    displayImage(imageId, fullBase64.toString());
                                }
                                callback.onComplete();
                            })
                            .addOnFailureListener(e -> callback.onComplete());
                })
                .addOnFailureListener(e -> callback.onComplete());
    }
    private void displayImage(String imageId, String base64Data) {
        Log.d("IMAGE_DEBUG", "🎨 displayImage called for: " + imageId);

        // ✅ Run on main thread
        runOnUiThread(() -> {
            try {
                String content = noteContent.getText().toString();
                String placeholder = "【IMAGE:" + imageId + "】";

                Log.d("IMAGE_DEBUG", "🔍 Searching for: " + placeholder);

                int placeholderIndex = content.indexOf(placeholder);

                if (placeholderIndex == -1) {
                    Log.e("IMAGE_DEBUG", "❌ Placeholder NOT FOUND!");
                    Log.e("IMAGE_DEBUG", "Content preview: " + content.substring(0, Math.min(100, content.length())));
                    return;
                }

                Log.d("IMAGE_DEBUG", "✅ Placeholder found at index: " + placeholderIndex);

                // Decode Base64
                byte[] decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                if (bitmap == null) {
                    Log.e("IMAGE_DEBUG", "❌ Failed to decode bitmap!");
                    return;
                }

                Log.d("IMAGE_DEBUG", "✅ Bitmap decoded: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                // ✅ Check EditText width
                int viewWidth = noteContent.getWidth();
                if (viewWidth <= 0) {
                    Log.e("IMAGE_DEBUG", "❌ EditText width = 0, waiting...");
                    noteContent.postDelayed(() -> displayImage(imageId, base64Data), 100);
                    return;
                }

                // Resize bitmap
                int maxWidth = viewWidth - noteContent.getPaddingLeft() - noteContent.getPaddingRight();
                if (maxWidth <= 0) maxWidth = 800;

                int displayWidth = Math.min(bitmap.getWidth(), maxWidth);
                int displayHeight = (int) (bitmap.getHeight() * (displayWidth / (float) bitmap.getWidth()));

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, displayWidth, displayHeight, true);

                BitmapDrawable drawable = new BitmapDrawable(getResources(), resizedBitmap);
                drawable.setBounds(0, 0, displayWidth, displayHeight);

                ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BASELINE);

                // ✅ Get EDITABLE text
                Editable editable = noteContent.getEditableText();

                // ✅ Remove old spans
                ImageSpan[] existingSpans = editable.getSpans(
                        placeholderIndex,
                        placeholderIndex + placeholder.length(),
                        ImageSpan.class
                );

                for (ImageSpan span : existingSpans) {
                    editable.removeSpan(span);
                }

                // ✅ Apply new span
                editable.setSpan(
                        imageSpan,
                        placeholderIndex,
                        placeholderIndex + placeholder.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                Log.d("IMAGE_DEBUG", "✅ ImageSpan applied!");

                // ✅ Force redraw
                noteContent.invalidate();

            } catch (Exception e) {
                Log.e("IMAGE_DEBUG", "❌ Exception in displayImage", e);
                e.printStackTrace();
            }
        });
    }
    private interface ImageLoadCallback {
        void onComplete();
    }
    private void setupImageDeletion() {
        noteContent.setOnLongClickListener(v -> {
            int cursorPos = noteContent.getSelectionStart();
            String content = noteContent.getText().toString();

            // ✅ FIX: Use correct pattern with Chinese brackets
            String imagePattern = "【IMAGE:(\\d+)】";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(imagePattern);
            java.util.regex.Matcher matcher = pattern.matcher(content);

            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();

                if (cursorPos >= start && cursorPos <= end) {
                    String imageId = matcher.group(1);
                    showDeleteImageDialog(imageId);
                    return true;
                }
            }

            return false;
        });
    }
    private void activateStyleMode(String styleType) {
        currentActiveStyle = styleType;
        isStyleActive = true;

        // Focus on noteContent so user can start typing
        noteContent.requestFocus();

        // Get current cursor position
        int cursorPos = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();

        // Find current line
        int lineStart = currentText.lastIndexOf('\n', cursorPos - 1) + 1;
        int lineEnd = currentText.indexOf('\n', cursorPos);
        if (lineEnd == -1) lineEnd = currentText.length();

        String currentLine = currentText.substring(lineStart, lineEnd);

        // ✅ Check if we're on a bullet, number, toggle, or checkbox line
        boolean isSpecialLine = currentLine.matches("^\\s*[●○■]\\s.*") ||
                currentLine.matches("^\\s*\\d+[.)]*\\s.*") ||
                currentLine.matches("^\\s*[a-z][.)]*\\s.*") ||
                currentLine.matches("^\\s*[ivx]+[.)]*\\s.*") ||
                currentLine.matches("^\\s*[▶▼]\\s.*") ||
                currentLine.matches("^\\s*[☐☑]\\s.*");

        // ✅ For headings, apply to entire line immediately
        if (styleType.startsWith("h") && !isSpecialLine) {
            textStyles.put(lineStart, styleType);
            applyTextStyles();
        }
    }
    private void showDeleteImageDialog(String imageId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Image")
                .setMessage("Remove this image from the note?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteInlineImage(imageId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void deleteInlineImage(String imageId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Delete from Firestore
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("images").document(imageId)
                .get()
                .addOnSuccessListener(doc -> {
                    Boolean isChunked = doc.getBoolean("isChunked");

                    if (isChunked != null && isChunked) {
                        // Delete chunks
                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("images").document(imageId)
                                .collection("chunks")
                                .get()
                                .addOnSuccessListener(chunks -> {
                                    for (QueryDocumentSnapshot chunk : chunks) {
                                        chunk.getReference().delete();
                                    }
                                    deleteImageDocumentInline(imageId);
                                });
                    } else {
                        deleteImageDocumentInline(imageId);
                    }
                });
    }
    private void deleteImageDocumentInline(String imageId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("images").document(imageId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    String content = noteContent.getText().toString();

                    // ✅ FIX: Use correct placeholder format
                    String placeholderWithNewlines = "\n【IMAGE:" + imageId + "】\n";
                    String placeholder = "【IMAGE:" + imageId + "】";

                    // ... rest of your deletion code
                });
    }

    //----------------------------------------------------------------//
    //HEADINGS AND FONTS
    private void showHeadingsAndFontsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.headings_fonts_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        // Headings
        LinearLayout heading1 = sheetView.findViewById(R.id.heading1Option);
        LinearLayout heading2 = sheetView.findViewById(R.id.heading2Option);
        LinearLayout heading3 = sheetView.findViewById(R.id.heading3Option);

        // Font styles
        LinearLayout boldOption = sheetView.findViewById(R.id.boldOption);
        LinearLayout italicOption = sheetView.findViewById(R.id.italicOption);
        LinearLayout boldItalicOption = sheetView.findViewById(R.id.boldItalicOption);
        LinearLayout normalOption = sheetView.findViewById(R.id.normalOption);

        heading1.setOnClickListener(v -> {
            activateStyleMode("h1");
            bottomSheet.dismiss();
            Toast.makeText(this, "Heading 1 active - start typing", Toast.LENGTH_SHORT).show();
        });

        heading2.setOnClickListener(v -> {
            activateStyleMode("h2");
            bottomSheet.dismiss();
            Toast.makeText(this, "Heading 2 active - start typing", Toast.LENGTH_SHORT).show();
        });

        heading3.setOnClickListener(v -> {
            activateStyleMode("h3");
            bottomSheet.dismiss();
            Toast.makeText(this, "Heading 3 active - start typing", Toast.LENGTH_SHORT).show();
        });

        boldOption.setOnClickListener(v -> {
            activateStyleMode("bold");
            bottomSheet.dismiss();
            Toast.makeText(this, "Bold active - start typing", Toast.LENGTH_SHORT).show();
        });

        italicOption.setOnClickListener(v -> {
            activateStyleMode("italic");
            bottomSheet.dismiss();
            Toast.makeText(this, "Italic active - start typing", Toast.LENGTH_SHORT).show();
        });

        boldItalicOption.setOnClickListener(v -> {
            activateStyleMode("bold_italic");
            bottomSheet.dismiss();
            Toast.makeText(this, "Bold Italic active - start typing", Toast.LENGTH_SHORT).show();
        });

        normalOption.setOnClickListener(v -> {
            activateStyleMode("normal");
            bottomSheet.dismiss();
            Toast.makeText(this, "Normal style active", Toast.LENGTH_SHORT).show();
        });

        bottomSheet.show();
    }
    private void applyHeading(String headingType) {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();

        // Find current line boundaries
        int lineStart = currentText.lastIndexOf('\n', cursorPosition - 1) + 1;
        int lineEnd = currentText.indexOf('\n', cursorPosition);
        if (lineEnd == -1) lineEnd = currentText.length();

        String currentLine = currentText.substring(lineStart, lineEnd);

        // ✅ REMOVED restriction - now allows headings on toggles, bullets, numbers, checkboxes!
        // Only block dividers and images
        if (currentLine.contains("【DIVIDER】") || currentLine.contains("【IMAGE:")) {
            Toast.makeText(this, "Cannot apply heading to dividers or images", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Store heading style for THIS line
        textStyles.put(lineStart, headingType);

        // ✅ CRITICAL: Set flag BEFORE applying styles
        isUpdatingText = true;
        isApplyingStyles = false; // Reset this flag

        // ✅ Apply styles IMMEDIATELY (not in postDelayed)
        applyTextStyles();

        // ✅ Reset flag after styles are applied
        isUpdatingText = false;

        // Move cursor to end of line
        int newCursorPos = lineStart + currentLine.length();
        noteContent.setSelection(Math.min(newCursorPos, currentText.length()));

        // ✅ Save with ORIGINAL text (no changes)
        saveNoteContentToFirestore(currentText);
        Toast.makeText(this, "Heading applied", Toast.LENGTH_SHORT).show();
    }
    private void applyFontStyle(String fontStyle) {
        int start = noteContent.getSelectionStart();
        int end = noteContent.getSelectionEnd();

        if (start == end) {
            Toast.makeText(this, "Please select text first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }

        String currentText = noteContent.getText().toString();
        String selectedText = currentText.substring(start, end);

        // ✅ Only block dividers and images
        if (selectedText.contains("【DIVIDER】") || selectedText.contains("【IMAGE:")) {
            Toast.makeText(this, "Cannot style dividers or images", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Store style information for this range
        textStyles.put(start, fontStyle);
        textStyles.put(end, "end_" + fontStyle);

        final int finalStart = start;
        final int finalEnd = end;

        // ✅ CRITICAL: Set flags and apply immediately
        isUpdatingText = true;
        isApplyingStyles = false;

        // ✅ Apply styles IMMEDIATELY
        applyTextStyles();

        // ✅ Reset flag
        isUpdatingText = false;

        // Maintain selection
        noteContent.setSelection(finalStart, finalEnd);

        saveNoteContentToFirestore(currentText);
        Toast.makeText(this, "Style applied", Toast.LENGTH_SHORT).show();
    }
    private void applyTextStyles() {
        // ✅ Prevent recursive calls
        if (isApplyingStyles) {
            Log.d("NoteActivity", "Already applying styles, skipping...");
            return;
        }

        isApplyingStyles = true;
        Log.d("NoteActivity", "🎨 Starting applyTextStyles()");

        String content = noteContent.getText().toString();
        Editable editable = noteContent.getEditableText();

        // ✅ STEP 1: Save ALL important spans
        List<SpanInfo> savedImageSpans = new ArrayList<>();
        List<SpanInfo> savedDividerSpans = new ArrayList<>();
        List<SpanInfo> savedBookmarkHighlights = new ArrayList<>();
        List<SpanInfo> savedBookmarkUnderlines = new ArrayList<>();
        List<SpanInfo> savedCheckboxColors = new ArrayList<>();
        List<SpanInfo> savedCheckboxStrikes = new ArrayList<>();

        // Save ImageSpans - CRITICAL!
        ImageSpan[] imageSpans = editable.getSpans(0, editable.length(), ImageSpan.class);
        for (ImageSpan span : imageSpans) {
            savedImageSpans.add(new SpanInfo(span, editable.getSpanStart(span), editable.getSpanEnd(span)));
        }

        // Save DividerSpans
        DividerSpan[] dividerSpans = editable.getSpans(0, editable.length(), DividerSpan.class);
        for (DividerSpan span : dividerSpans) {
            savedDividerSpans.add(new SpanInfo(span, editable.getSpanStart(span), editable.getSpanEnd(span)));
        }

        // Save bookmark BackgroundColorSpans
        android.text.style.BackgroundColorSpan[] bgSpans = editable.getSpans(0, editable.length(), android.text.style.BackgroundColorSpan.class);
        for (android.text.style.BackgroundColorSpan span : bgSpans) {
            savedBookmarkHighlights.add(new SpanInfo(span, editable.getSpanStart(span), editable.getSpanEnd(span)));
        }

        // Save bookmark CustomUnderlineSpans
        CustomUnderlineSpan[] underlineSpans = editable.getSpans(0, editable.length(), CustomUnderlineSpan.class);
        for (CustomUnderlineSpan span : underlineSpans) {
            savedBookmarkUnderlines.add(new SpanInfo(span, editable.getSpanStart(span), editable.getSpanEnd(span)));
        }

        // Save checkbox ForegroundColorSpans (ONLY for checked items)
        android.text.style.ForegroundColorSpan[] colorSpans = editable.getSpans(0, editable.length(), android.text.style.ForegroundColorSpan.class);
        for (android.text.style.ForegroundColorSpan span : colorSpans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            if (spanStart >= 0 && spanEnd <= content.length()) {
                String spannedText = content.substring(spanStart, spanEnd);
                if (spannedText.contains("☑")) {
                    savedCheckboxColors.add(new SpanInfo(span, spanStart, spanEnd));
                }
            }
        }

        // Save checkbox StrikethroughSpans (ONLY for checked items)
        android.text.style.StrikethroughSpan[] strikeSpans = editable.getSpans(0, editable.length(), android.text.style.StrikethroughSpan.class);
        for (android.text.style.StrikethroughSpan span : strikeSpans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            if (spanStart >= 0 && spanEnd <= content.length()) {
                String spannedText = content.substring(spanStart, spanEnd);
                if (spannedText.contains("☑")) {
                    savedCheckboxStrikes.add(new SpanInfo(span, spanStart, spanEnd));
                }
            }
        }

        // ✅ STEP 2: Remove ONLY heading/font style spans (NOT checkbox styles!)
        android.text.style.RelativeSizeSpan[] sizeSpans = editable.getSpans(0, editable.length(), android.text.style.RelativeSizeSpan.class);
        for (android.text.style.RelativeSizeSpan span : sizeSpans) {
            editable.removeSpan(span);
        }

        android.text.style.StyleSpan[] styleSpans = editable.getSpans(0, editable.length(), android.text.style.StyleSpan.class);
        for (android.text.style.StyleSpan span : styleSpans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);

            if (spanStart >= 0 && spanEnd <= content.length()) {
                String spannedText = content.substring(spanStart, spanEnd);
                // ✅ CRITICAL: Keep checkbox StyleSpans (grey color from strikethrough)
                // Only remove heading/font StyleSpans
                if (!spannedText.contains("☑")) {
                    editable.removeSpan(span);
                }
            } else {
                editable.removeSpan(span);
            }
        }

        // ✅ STEP 3: Re-apply ALL saved spans FIRST (images MUST be restored first!)
        for (SpanInfo info : savedImageSpans) {
            if (info.start >= 0 && info.end <= editable.length() && info.start < info.end) {
                editable.setSpan(info.span, info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        for (SpanInfo info : savedDividerSpans) {
            if (info.start >= 0 && info.end <= editable.length() && info.start < info.end) {
                editable.setSpan(info.span, info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        for (SpanInfo info : savedBookmarkHighlights) {
            if (info.start >= 0 && info.end <= editable.length() && info.start < info.end) {
                editable.setSpan(info.span, info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        for (SpanInfo info : savedBookmarkUnderlines) {
            if (info.start >= 0 && info.end <= editable.length() && info.start < info.end) {
                editable.setSpan(info.span, info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        for (SpanInfo info : savedCheckboxColors) {
            if (info.start >= 0 && info.end <= editable.length() && info.start < info.end) {
                editable.setSpan(info.span, info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        for (SpanInfo info : savedCheckboxStrikes) {
            if (info.start >= 0 && info.end <= editable.length() && info.start < info.end) {
                editable.setSpan(info.span, info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // ✅ STEP 4: Apply NEW heading styles (line-based)
        String[] lines = content.split("\n", -1);
        int currentLinePos = 0;

        for (String line : lines) {
            int lineStart = currentLinePos;
            int lineEnd = lineStart + line.length();

            // ✅ CRITICAL: Skip image lines completely
            if (line.trim().matches("^〔IMAGE:\\d+〕$")) {
                currentLinePos = lineEnd + 1;
                continue;
            }

            // Check if this line has a heading style
            String headingStyle = textStyles.get(lineStart);

            if (headingStyle != null && !headingStyle.startsWith("end_")) {
                float size = 1.0f;
                int typeface = android.graphics.Typeface.NORMAL;

                switch (headingStyle) {
                    case "h1":
                        size = 1.8f;
                        typeface = android.graphics.Typeface.BOLD;
                        break;
                    case "h2":
                        size = 1.6f;
                        typeface = android.graphics.Typeface.BOLD;
                        break;
                    case "h3":
                        size = 1.4f;
                        typeface = android.graphics.Typeface.BOLD;
                        break;
                    case "h4":
                        size = 1.2f;
                        typeface = android.graphics.Typeface.BOLD;
                        break;
                }

                // Find where content actually starts (after markers)
                int contentStartIndex = findContentStartIndex(line);
                int spanStart = lineStart + contentStartIndex;
                int spanEnd = lineEnd;

                if (spanStart < spanEnd) {
                    Log.d("NoteActivity", "✅ Applying " + headingStyle + " (size: " + size + ") to line at " + lineStart);

                    editable.setSpan(
                            new android.text.style.RelativeSizeSpan(size),
                            spanStart, spanEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    editable.setSpan(
                            new android.text.style.StyleSpan(typeface),
                            spanStart, spanEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }

            currentLinePos = lineEnd + 1;
        }

        // ✅ STEP 5: Apply inline font styles (bold, italic, bold_italic)
        List<Integer> processedPositions = new ArrayList<>();

        for (Map.Entry<Integer, String> entry : textStyles.entrySet()) {
            int position = entry.getKey();
            String styleType = entry.getValue();

            if (styleType.startsWith("end_") || processedPositions.contains(position)) {
                continue;
            }

            if (styleType.equals("bold") || styleType.equals("italic") || styleType.equals("bold_italic")) {
                Integer endPosition = null;
                for (Map.Entry<Integer, String> endEntry : textStyles.entrySet()) {
                    if (endEntry.getValue().equals("end_" + styleType) && endEntry.getKey() > position) {
                        endPosition = endEntry.getKey();
                        break;
                    }
                }

                if (endPosition != null && position >= 0 && endPosition <= content.length() && position < endPosition) {
                    // ✅ Check if range contains image placeholder - skip if it does
                    String rangeText = content.substring(position, endPosition);
                    if (rangeText.contains("〔IMAGE:") && rangeText.contains("〕")) {
                        Log.d("NoteActivity", "⚠️ Skipping style on image placeholder");
                        processedPositions.add(position);
                        continue;
                    }

                    int finalTypeface = android.graphics.Typeface.NORMAL;
                    switch (styleType) {
                        case "bold": finalTypeface = android.graphics.Typeface.BOLD; break;
                        case "italic": finalTypeface = android.graphics.Typeface.ITALIC; break;
                        case "bold_italic": finalTypeface = android.graphics.Typeface.BOLD_ITALIC; break;
                    }

                    Log.d("NoteActivity", "✅ Applying " + styleType + " from " + position + " to " + endPosition);

                    editable.setSpan(
                            new android.text.style.StyleSpan(finalTypeface),
                            position, endPosition,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    processedPositions.add(position);
                }
            }
        }

        // ✅ Force EditText to refresh
        noteContent.invalidate();

        isApplyingStyles = false;
        Log.d("NoteActivity", "✅ applyTextStyles() complete");
    }
    private void applyInlineStyles(Editable editable, String content) {
        // ✅ Define patterns for inline styles
        java.util.regex.Pattern boldItalicPattern = java.util.regex.Pattern.compile("\\*\\*\\*(.+?)\\*\\*\\*");
        java.util.regex.Pattern boldPattern = java.util.regex.Pattern.compile("\\*\\*(.+?)\\*\\*");
        java.util.regex.Pattern italicPattern = java.util.regex.Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");

        // ✅ Bold + Italic (must check first to avoid conflicts)
        java.util.regex.Matcher matcher = boldItalicPattern.matcher(content);
        while (matcher.find()) {
            int contentStart = matcher.start() + 3;
            int contentEnd = matcher.end() - 3;

            if (contentStart < editable.length() && contentEnd <= editable.length() && contentStart < contentEnd) {
                // Apply bold+italic to content
                editable.setSpan(
                        new android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC),
                        contentStart, contentEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                // Hide opening markers ***
                if (matcher.start() + 3 <= editable.length()) {
                    editable.setSpan(
                            new android.text.style.ForegroundColorSpan(Color.TRANSPARENT),
                            matcher.start(), matcher.start() + 3,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
                // Hide closing markers ***
                if (matcher.end() <= editable.length() && matcher.end() - 3 >= 0) {
                    editable.setSpan(
                            new android.text.style.ForegroundColorSpan(Color.TRANSPARENT),
                            matcher.end() - 3, matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }
        }

        // ✅ Bold only (skip if part of ***)
        matcher = boldPattern.matcher(content);
        while (matcher.find()) {
            // Skip if this ** is part of ***
            boolean partOfTriple = false;
            if (matcher.start() > 0 && content.charAt(matcher.start() - 1) == '*') {
                partOfTriple = true;
            }
            if (matcher.end() < content.length() && content.charAt(matcher.end()) == '*') {
                partOfTriple = true;
            }

            if (partOfTriple) continue;

            int contentStart = matcher.start() + 2;
            int contentEnd = matcher.end() - 2;

            if (contentStart < editable.length() && contentEnd <= editable.length() && contentStart < contentEnd) {
                editable.setSpan(
                        new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        contentStart, contentEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                // Hide markers
                if (matcher.start() + 2 <= editable.length()) {
                    editable.setSpan(
                            new android.text.style.ForegroundColorSpan(Color.TRANSPARENT),
                            matcher.start(), matcher.start() + 2,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
                if (matcher.end() <= editable.length() && matcher.end() - 2 >= 0) {
                    editable.setSpan(
                            new android.text.style.ForegroundColorSpan(Color.TRANSPARENT),
                            matcher.end() - 2, matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }
        }

        // ✅ Italic only (skip if part of ** or ***)
        matcher = italicPattern.matcher(content);
        while (matcher.find()) {
            int contentStart = matcher.start() + 1;
            int contentEnd = matcher.end() - 1;

            if (contentStart < editable.length() && contentEnd <= editable.length() && contentStart < contentEnd) {
                editable.setSpan(
                        new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                        contentStart, contentEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                // Hide markers
                if (matcher.start() + 1 <= editable.length()) {
                    editable.setSpan(
                            new android.text.style.ForegroundColorSpan(Color.TRANSPARENT),
                            matcher.start(), matcher.start() + 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
                if (matcher.end() <= editable.length() && matcher.end() - 1 >= 0) {
                    editable.setSpan(
                            new android.text.style.ForegroundColorSpan(Color.TRANSPARENT),
                            matcher.end() - 1, matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }
        }
    }
    private void applyTextStylesDebounced() {
        // Cancel previous pending style application
        if (styleRunnable != null) {
            styleHandler.removeCallbacks(styleRunnable);
        }

        styleRunnable = () -> applyTextStyles();
        styleHandler.postDelayed(styleRunnable, STYLE_APPLY_DELAY_MS);
    }
    private void setupStyleWatcher() {
        noteContent.addTextChangedListener(new TextWatcher() {
            private int startPos = -1;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (isStyleActive && after > 0) {
                    startPos = start;
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                if (isUpdatingText || isTogglingState) return;

                // ✅ CHECK: If Enter was pressed (newline added)
                if (after == 1 && start < s.length() && s.charAt(start) == '\n') {
                    // Get the NEW line that was just created
                    String currentText = s.toString();
                    int newLineStart = start + 1; // Position after the newline

                    if (newLineStart < currentText.length()) {
                        int newLineEnd = currentText.indexOf('\n', newLineStart);
                        if (newLineEnd == -1) newLineEnd = currentText.length();

                        String newLine = currentText.substring(newLineStart, newLineEnd);

                        // ✅ CHECK: If new line starts with special markers
                        boolean isSpecialLine = newLine.matches("^\\s*[●○■]\\s.*") ||      // Bullet
                                newLine.matches("^\\s*\\d+[.)]\\s.*") ||      // Number
                                newLine.matches("^\\s*[a-z][.)]\\s.*") ||     // Letter
                                newLine.matches("^\\s*[ivx]+[.)]\\s.*") ||    // Roman
                                newLine.matches("^\\s*[▶▼]\\s.*") ||          // Toggle
                                newLine.matches("^\\s*[☐☑]\\s.*");            // Checkbox

                        // ✅ If it's a special line, RESET heading mode
                        if (isSpecialLine && currentActiveStyle.startsWith("h")) {
                            isStyleActive = false;
                            currentActiveStyle = "normal";
                            return; // Don't apply heading to this line
                        }
                    }
                }

                if (isStyleActive && after > 0 && startPos >= 0) {
                    String currentText = s.toString();

                    // Safety check for empty text
                    if (currentText.isEmpty() || start >= currentText.length()) return;

                    // Find current line boundaries with safety checks
                    int lineStart = currentText.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
                    int lineEnd = currentText.indexOf('\n', start);
                    if (lineEnd == -1) lineEnd = currentText.length();

                    // Ensure valid range
                    if (lineStart < 0) lineStart = 0;
                    if (lineEnd > currentText.length()) lineEnd = currentText.length();
                    if (lineStart >= lineEnd) return;

                    String currentLine = currentText.substring(lineStart, lineEnd);

                    // ✅ For HEADINGS: Apply IMMEDIATELY to current line
                    if (currentActiveStyle.startsWith("h")) {
                        textStyles.put(lineStart, currentActiveStyle);
                        applyTextStyles();
                    }
                    // ✅ For INLINE STYLES (bold, italic): Apply IMMEDIATELY
                    else if (!currentActiveStyle.equals("normal")) {
                        int endPos = start + after;
                        textStyles.put(startPos, currentActiveStyle);
                        textStyles.put(endPos, "end_" + currentActiveStyle);
                        applyTextStyles();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    //--------------------------------------------//
    //INSERT TABLE
    private void insertTable() {
        int cursorPosition = noteContent.getSelectionStart();
        String currentText = noteContent.getText().toString();

        // Create unique table ID
        String tableId = System.currentTimeMillis() + "";

        // Save table to Firestore first
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            Table table = new Table(cursorPosition, 4, 3);
            table.setId(tableId);

            Map<String, Object> tableData = new HashMap<>();
            tableData.put("tableId", tableId);
            tableData.put("rowCount", table.getRowCount());
            tableData.put("columnCount", table.getColumnCount());
            tableData.put("cellContents", table.getCellContents());
            tableData.put("cellColors", table.getCellColors());
            tableData.put("position", cursorPosition);
            tableData.put("timestamp", System.currentTimeMillis());

            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("tables").document(tableId)
                    .set(tableData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("TABLE_DEBUG", "✅ Table saved to Firestore");

                        // Insert table view directly into layout
                        insertTableViewInline(tableId, table, cursorPosition);

                        // ✅ NEW: Save note content with table placeholder
                        noteContent.postDelayed(() -> {
                            String fullContent = collectAllNoteContent();
                            saveNoteContentToFirestore(fullContent);
                        }, 500);

                        Toast.makeText(this, "Table inserted - tap cells to edit", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("TABLE_DEBUG", "❌ Error saving table", e);
                        Toast.makeText(this, "Error creating table", Toast.LENGTH_SHORT).show();
                    });
        }
    }


    // ============================================================
// NEW METHOD: Insert table directly into note container
// ============================================================
    private void insertTableViewInline(String tableId, Table tableData, int cursorPosition) {
        Log.d("TABLE_DEBUG", "📊 Inserting table inline at position: " + cursorPosition);

        try {
            LinearLayout noteContainer = findViewById(R.id.noteContainer);

            // Get EDITABLE, not just string
            Editable currentEditable = noteContent.getEditableText();
            String currentText = currentEditable.toString();

            // Save ALL existing spans BEFORE any modifications
            List<SpanInfo> allSavedSpans = saveAllSpansFromEditable(currentEditable);
            Log.d("TABLE_DEBUG", "💾 Saved " + allSavedSpans.size() + " spans before table insertion");

            // Split text at cursor position
            String beforeCursor = currentText.substring(0, cursorPosition);
            String afterCursor = currentText.substring(cursorPosition);

            // Update noteContent with spans preserved
            isUpdatingText = true;

            // Set text for before cursor
            noteContent.setText(beforeCursor);

            // Restore all spans that fall within beforeCursor range
            Editable beforeEditable = noteContent.getEditableText();
            for (SpanInfo info : allSavedSpans) {
                if (info.start >= 0 && info.end <= beforeCursor.length() && info.start < info.end) {
                    try {
                        beforeEditable.setSpan(
                                info.span,
                                info.start,
                                info.end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    } catch (Exception e) {
                        Log.e("TABLE_DEBUG", "❌ Error restoring span", e);
                    }
                }
            }

            isUpdatingText = false;

            // Create TableView
            TableView tableView = new TableView(this);
            tableView.setTableData(tableData);

            // Set up auto-save listener
            tableView.setOnTableChangeListener(table -> {
                saveTableToFirestore(table);
            });

            // Set layout params
            LinearLayout.LayoutParams tableParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tableParams.setMargins(0, dpToPx(2), 0, dpToPx(2));
            tableView.setLayoutParams(tableParams);
            tableView.setId(View.generateViewId());
            tableView.setTag(tableId);

            // Get index of noteContent in container
            int noteContentIndex = noteContainer.indexOfChild(noteContent);

            // Insert table after noteContent
            noteContainer.addView(tableView, noteContentIndex + 1);

            // Store reference
            tableViews.put(tableId, tableView);

            // ✅ ALWAYS create an EditText after the table
            EditText afterEditText = createEditTextForNote();

            if (!afterCursor.isEmpty()) {
                // If there's content after cursor, restore it with spans
                SpannableString afterSpannable = new SpannableString(afterCursor);

                // Restore spans that fall within afterCursor range
                for (SpanInfo info : allSavedSpans) {
                    if (info.start >= cursorPosition && info.end <= currentText.length()) {
                        int newStart = info.start - cursorPosition;
                        int newEnd = info.end - cursorPosition;

                        if (newStart >= 0 && newEnd <= afterCursor.length() && newStart < newEnd) {
                            try {
                                afterSpannable.setSpan(
                                        info.span,
                                        newStart,
                                        newEnd,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                );
                            } catch (Exception e) {
                                Log.e("TABLE_DEBUG", "❌ Error restoring after span", e);
                            }
                        }
                    }
                }

                afterEditText.setText(afterSpannable, TextView.BufferType.SPANNABLE);
            } else {
                // ✅ Even if empty, create EditText for user to type
                afterEditText.setText("");
                afterEditText.setHint("Continue typing...");
            }

            // Setup listeners
            setupEditTextListeners(afterEditText);

            // Insert after table
            noteContainer.addView(afterEditText, noteContentIndex + 2);

            // ✅ Focus on the new EditText so user can type immediately
            afterEditText.requestFocus();
            afterEditText.setSelection(0);

            Log.d("TABLE_DEBUG", "✅ Table inserted inline with all spans preserved");

        } catch (Exception e) {
            Log.e("TABLE_DEBUG", "❌ Error inserting table inline", e);
            e.printStackTrace();
        }
    }

    // ============================================================
// NEW METHOD: Create EditText with proper styling
// ============================================================
    private EditText createEditTextForNote() {
        EditText editText = new EditText(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        editText.setLayoutParams(params);
        editText.setBackground(null);
        editText.setGravity(Gravity.TOP);
        editText.setHint("Continue typing...");
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setMinHeight(dpToPx(200));
        editText.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        editText.setTextColor(Color.parseColor("#333333"));
        editText.setTextSize(16);
        editText.setTextIsSelectable(true);

        return editText;
    }

    // ============================================================
// NEW METHOD: Setup listeners for additional EditTexts
// ============================================================
    private void setupEditTextListeners(EditText editText) {
        // Add text watcher for auto-save
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                if (!isUpdatingText) {
                    // Trigger auto-save with debounce
                    saveNoteContentDebounced(collectAllNoteContent());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // ============================================================
// NEW METHOD: Collect content from all EditTexts and TableViews
// ============================================================
    private String collectAllNoteContent() {
        LinearLayout noteContainer = findViewById(R.id.noteContainer);
        StringBuilder fullContent = new StringBuilder();

        for (int i = 0; i < noteContainer.getChildCount(); i++) {
            View child = noteContainer.getChildAt(i);

            if (child instanceof EditText) {
                EditText editText = (EditText) child;

                // ✅ CRITICAL FIX: Skip the title EditText
                if (editText.getId() == R.id.noteTitle) {
                    continue; // Skip title, don't include it in content
                }

                String text = editText.getText().toString();

                if (!text.isEmpty()) {
                    if (fullContent.length() > 0 && !fullContent.toString().endsWith("\n")) {
                        fullContent.append("\n");
                    }
                    fullContent.append(text);
                }
                // MODIFY your existing collectAllNoteContent() to include:

            } else if (child instanceof SubpageView) { // ✅ ADD THIS BLOCK
                SubpageView subpageView = (SubpageView) child;
                String subpageId = (String) subpageView.getTag();
                if (subpageId != null) {
                    if (fullContent.length() > 0 && !fullContent.toString().endsWith("\n")) {
                        fullContent.append("\n");
                    }
                    fullContent.append("【SUBPAGE:").append(subpageId).append("】");
                    fullContent.append("\n");
                }
            } else if (child instanceof TableView) {
                TableView tableView = (TableView) child;
                String tableId = (String) tableView.getTag();
                if (tableId != null) {
                    if (fullContent.length() > 0 && !fullContent.toString().endsWith("\n")) {
                        fullContent.append("\n");
                    }
                    fullContent.append("【TABLE:").append(tableId).append("】");
                    fullContent.append("\n");
                }
            }
        }

        return fullContent.toString();
    }

    private void saveTableToFirestore(Table table) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null || table.getId() == null) return;

        Log.d("TABLE_DEBUG", "💾 Saving table: " + table.getId());

        Map<String, Object> tableData = new HashMap<>();
        tableData.put("rowCount", table.getRowCount());
        tableData.put("columnCount", table.getColumnCount());
        tableData.put("cellContents", table.getCellContents());
        tableData.put("cellColors", table.getCellColors());
        tableData.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("tables").document(table.getId())
                .update(tableData)
                .addOnSuccessListener(aVoid -> {
                    Log.d("TABLE_DEBUG", "✅ Table saved successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("TABLE_DEBUG", "❌ Error saving table", e);
                });
    }

    // ============================================================
// NEW METHOD: Load tables and reconstruct note layout
// ============================================================
    private void loadTablesInline() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) {
            Log.e("TABLE_DEBUG", "❌ User or noteId is null");
            return;
        }

        Log.d("TABLE_DEBUG", "📖 Loading tables for note: " + noteId);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("tables")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d("TABLE_DEBUG", "✅ Found " + querySnapshot.size() + " tables");

                    Map<String, Table> tablesMap = new HashMap<>();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String tableId = doc.getString("tableId");
                        if (tableId != null) {
                            Table table = new Table();
                            table.setId(tableId);
                            table.setRowCount(doc.getLong("rowCount").intValue());
                            table.setColumnCount(doc.getLong("columnCount").intValue());

                            // Load cell contents
                            Map<String, Object> contents = (Map<String, Object>) doc.get("cellContents");
                            if (contents != null) {
                                Map<String, String> cellContents = new HashMap<>();
                                for (Map.Entry<String, Object> entry : contents.entrySet()) {
                                    cellContents.put(entry.getKey(), (String) entry.getValue());
                                }
                                table.setCellContents(cellContents);
                            }

                            // Load cell colors
                            Map<String, Object> colors = (Map<String, Object>) doc.get("cellColors");
                            if (colors != null) {
                                Map<String, Integer> cellColors = new HashMap<>();
                                for (Map.Entry<String, Object> entry : colors.entrySet()) {
                                    cellColors.put(entry.getKey(), ((Long) entry.getValue()).intValue());
                                }
                                table.setCellColors(cellColors);
                            }

                            tablesMap.put(tableId, table);
                            Log.d("TABLE_DEBUG", "📊 Table loaded: " + tableId);
                        }
                    }

                    // Reconstruct layout with tables
                    reconstructNoteLayout(tablesMap);

                })
                .addOnFailureListener(e -> {
                    Log.e("TABLE_DEBUG", "❌ Error loading tables", e);
                });
    }

    // ============================================================
// NEW METHOD: Reconstruct note layout with tables inline
// ============================================================
    private void reconstructNoteLayout(Map<String, Table> tablesMap) {
        if (tablesMap.isEmpty()) {
            Log.d("TABLE_DEBUG", "No tables to display");
            return;
        }

        String fullContent = noteContent.getText().toString();

        if (!fullContent.contains("【TABLE:")) {
            Log.d("TABLE_DEBUG", "No table placeholders found in content");
            return;
        }

        LinearLayout noteContainer = findViewById(R.id.noteContainer);

        String pattern = "【TABLE:(\\d+)】";
        java.util.regex.Pattern tablePattern = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = tablePattern.matcher(fullContent);

        List<TablePosition> tablePositions = new ArrayList<>();

        while (matcher.find()) {
            String tableId = matcher.group(1);
            int start = matcher.start();
            int end = matcher.end();

            if (tablesMap.containsKey(tableId)) {
                tablePositions.add(new TablePosition(tableId, start, end));
            }
        }

        if (tablePositions.isEmpty()) {
            return;
        }

        Collections.sort(tablePositions, (a, b) -> Integer.compare(a.start, b.start));

        Editable originalEditable = noteContent.getEditableText();
        List<SpanInfo> savedSpans = saveAllSpansFromEditable(originalEditable);

        int noteContentIndex = noteContainer.indexOfChild(noteContent);
        for (int i = noteContainer.getChildCount() - 1; i > noteContentIndex; i--) {
            noteContainer.removeViewAt(i);
        }

        TablePosition firstTable = tablePositions.get(0);
        String beforeFirstTable = fullContent.substring(0, firstTable.start);

        isUpdatingText = true;
        noteContent.setText(beforeFirstTable);
        restoreSpansForSection(noteContent.getEditableText(), savedSpans, 0, firstTable.start);
        isUpdatingText = false;

        for (int i = 0; i < tablePositions.size(); i++) {
            TablePosition pos = tablePositions.get(i);
            Table table = tablesMap.get(pos.tableId);

            // Insert table
            TableView tableView = new TableView(this);
            tableView.setTableData(table);
            tableView.setOnTableChangeListener(t -> saveTableToFirestore(t));

            LinearLayout.LayoutParams tableParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tableParams.setMargins(0, dpToPx(8), 0, dpToPx(8));
            tableView.setLayoutParams(tableParams);
            tableView.setTag(pos.tableId);

            noteContainer.addView(tableView);
            tableViews.put(pos.tableId, tableView);

            // ✅ ALWAYS add EditText after table
            EditText afterTableEdit = createEditTextForNote();

            if (i < tablePositions.size() - 1) {
                // Content between this table and next table
                TablePosition nextPos = tablePositions.get(i + 1);
                String betweenContent = fullContent.substring(pos.end, nextPos.start);

                afterTableEdit.setText(betweenContent);
                restoreSpansForSection(afterTableEdit.getEditableText(), savedSpans, pos.end, nextPos.start);
            } else {
                // Content after last table
                String afterContent = fullContent.substring(pos.end);

                if (!afterContent.trim().isEmpty()) {
                    afterTableEdit.setText(afterContent);
                    restoreSpansForSection(afterTableEdit.getEditableText(), savedSpans, pos.end, fullContent.length());
                } else {
                    // ✅ Even if empty, add EditText for typing
                    afterTableEdit.setText("");
                    afterTableEdit.setHint("");
                }
            }

            setupEditTextListeners(afterTableEdit);
            noteContainer.addView(afterTableEdit);
        }

        Log.d("TABLE_DEBUG", "✅ Layout reconstructed with EditTexts after each table");
    }

    // ============================================================
// ✅ NEW METHOD: Save all spans from Editable
// ============================================================
    private List<SpanInfo> saveAllSpansFromEditable(Editable editable) {
        List<SpanInfo> allSpans = new ArrayList<>();

        Object[] spans = editable.getSpans(0, editable.length(), Object.class);
        for (Object span : spans) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);

            if (start >= 0 && end >= start) {
                allSpans.add(new SpanInfo(span, start, end));
            }
        }

        Log.d("TABLE_DEBUG", "💾 Saved " + allSpans.size() + " spans");
        return allSpans;
    }
    // ============================================================
// ✅ NEW METHOD: Restore spans for a specific text section
// ============================================================
    private void restoreSpansForSection(Editable editable, List<SpanInfo> savedSpans,
                                        int sectionStart, int sectionEnd) {
        int sectionLength = sectionEnd - sectionStart;

        for (SpanInfo info : savedSpans) {
            // Check if this span falls within the section
            if (info.start >= sectionStart && info.end <= sectionEnd) {
                // Adjust positions relative to new section
                int newStart = info.start - sectionStart;
                int newEnd = info.end - sectionStart;

                // Validate bounds
                if (newStart >= 0 && newEnd <= editable.length() && newStart < newEnd) {
                    try {
                        editable.setSpan(
                                info.span,
                                newStart,
                                newEnd,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                        Log.d("TABLE_DEBUG", "✅ Restored span at " + newStart + "-" + newEnd);
                    } catch (Exception e) {
                        Log.e("TABLE_DEBUG", "❌ Error restoring span", e);
                    }
                }
            }
        }
    }
    private static class TablePosition {
        String tableId;
        int start;
        int end;

        TablePosition(String tableId, int start, int end) {
            this.tableId = tableId;
            this.start = start;
            this.end = end;
        }
    }



    private void setupTableListener() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (tableListener != null) {
            tableListener.remove();
        }

        tableListener = db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("tables")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("TABLE_DEBUG", "❌ Table listener error", error);
                        return;
                    }

                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            String tableId = doc.getString("tableId");
                            if (tableId != null) {
                                // Update existing table view
                                TableView existingView = tableViews.get(tableId);
                                if (existingView != null) {
                                    Table table = new Table();
                                    table.setId(tableId);
                                    table.setRowCount(doc.getLong("rowCount").intValue());
                                    table.setColumnCount(doc.getLong("columnCount").intValue());

                                    Map<String, Object> contents = (Map<String, Object>) doc.get("cellContents");
                                    if (contents != null) {
                                        Map<String, String> cellContents = new HashMap<>();
                                        for (Map.Entry<String, Object> entry : contents.entrySet()) {
                                            cellContents.put(entry.getKey(), (String) entry.getValue());
                                        }
                                        table.setCellContents(cellContents);
                                    }

                                    existingView.setTableData(table);
                                }
                            }
                        }
                    }
                });
    }

    private void deleteTable(String tableId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Table")
                .setMessage("Remove this table from the note?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("tables").document(tableId)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    // Remove from content
                                    String content = noteContent.getText().toString();
                                    String placeholder = "\n【TABLE:" + tableId + "】\n";
                                    String newContent = content.replace(placeholder, "\n");

                                    noteContent.setText(newContent);
                                    tableViews.remove(tableId);

                                    Toast.makeText(this, "Table deleted", Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Helper method to convert dp to pixels
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    //----------------------------------------------------------------//
    //DRAG FUNCTIONALITY
    private void setupDragFunctionality() {
        // Create invisible overlay for drag feedback
        createDragOverlay();

        noteContent.setOnTouchListener(new View.OnTouchListener() {
            private GestureDetector gestureDetector = new GestureDetector(NoteActivity_1.this,
                    new GestureDetector.SimpleOnGestureListener() {

                        // ✅ Handle single tap for dividers
                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            int cursorPos = noteContent.getSelectionStart();
                            String content = noteContent.getText().toString();
                            String dividerPlaceholder = "【DIVIDER】";

                            // Find all dividers and check if cursor is on one
                            int dividerIndex = content.indexOf(dividerPlaceholder);
                            while (dividerIndex != -1) {
                                int dividerEnd = dividerIndex + dividerPlaceholder.length();

                                if (cursorPos >= dividerIndex && cursorPos <= dividerEnd) {
                                    hideKeyboard();
                                    showDividerActionMenu(dividerIndex);
                                    return true;
                                }

                                dividerIndex = content.indexOf(dividerPlaceholder, dividerEnd);
                            }
                            return false;
                        }

                        // ✅ Keep existing long press for drag
                        @Override
                        public void onLongPress(MotionEvent e) {
                            if (!isDragging) {
                                startDragMode(e);
                            }
                        }
                    });

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Let gesture detector handle taps and long press
                gestureDetector.onTouchEvent(event);

                if (isDragging) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_MOVE:
                            handleDragMove(event);
                            return true;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            finishDrag();
                            return true;
                    }
                    return true;
                }

                return false; // Allow normal EditText behavior when not dragging
            }
        });
    }

    private void createDragOverlay() {
        // Create overlay container
        dragOverlayView = new FrameLayout(this);
        dragOverlayView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        dragOverlayView.setVisibility(View.GONE);
        dragOverlayView.setElevation(dpToPx(8));

        // Create floating text view
        dragFloatingText = new TextView(this);
        dragFloatingText.setBackgroundColor(Color.parseColor("#F0F0F0"));
        dragFloatingText.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        dragFloatingText.setTextColor(Color.parseColor("#333333"));
        dragFloatingText.setTextSize(16);
        dragFloatingText.setElevation(dpToPx(4));
        dragFloatingText.setShadowLayer(8, 0, 4, Color.parseColor("#40000000"));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#FFFFFF"));
        bg.setCornerRadius(dpToPx(8));
        bg.setStroke(dpToPx(2), Color.parseColor("#4CAF50"));
        dragFloatingText.setBackground(bg);

        ((FrameLayout) dragOverlayView).addView(dragFloatingText);

        // Add to root layout
        ((ViewGroup) findViewById(android.R.id.content)).addView(dragOverlayView);
    }

    private void startDragMode(MotionEvent e) {
        String content = noteContent.getText().toString();
        if (content.isEmpty()) return;

        // Get line at touch position
        Layout layout = noteContent.getLayout();
        if (layout == null) return;

        int offset = noteContent.getOffsetForPosition(e.getX(), e.getY());
        int line = layout.getLineForOffset(offset);

        int lineStart = layout.getLineStart(line);
        int lineEnd = layout.getLineEnd(line);

        if (lineStart >= content.length()) return;

        String lineText = content.substring(lineStart, Math.min(lineEnd, content.length())).trim();

        // Don't allow dragging empty lines or dividers
        if (lineText.isEmpty() || lineText.contains("〔DIVIDER〕") || lineText.contains("【IMAGE:")) {
            return;
        }

        isDragging = true;
        dragStartLineIndex = line;
        dragCurrentLineIndex = line;

        // Calculate touch offset within the line
        int lineTop = layout.getLineTop(line);
        dragTouchOffset = (int) (e.getY() - lineTop - noteContent.getScrollY());

        // Show floating text
        dragFloatingText.setText(lineText);
        dragFloatingText.setMaxWidth(noteContent.getWidth() - dpToPx(32));
        dragOverlayView.setVisibility(View.VISIBLE);

        // Position floating text
        updateFloatingTextPosition(e.getRawY());

        // Highlight source line
        highlightDragLine(lineStart, lineEnd);

        // Haptic feedback
        noteContent.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

        Toast.makeText(this, "Drag to reorder", Toast.LENGTH_SHORT).show();
    }

    private void handleDragMove(MotionEvent e) {
        if (!isDragging) return;

        // Update floating text position
        updateFloatingTextPosition(e.getRawY());

        // Determine current line under finger
        Layout layout = noteContent.getLayout();
        if (layout == null) return;

        // Convert screen Y to EditText coordinates
        int[] location = new int[2];
        noteContent.getLocationOnScreen(location);
        float localY = e.getRawY() - location[1] + noteContent.getScrollY();

        // Find line at this position
        int currentLine = layout.getLineForVertical((int) localY);

        if (currentLine != dragCurrentLineIndex && currentLine >= 0) {
            dragCurrentLineIndex = currentLine;

            // Visual feedback - highlight drop target
            String content = noteContent.getText().toString();
            int targetStart = layout.getLineStart(currentLine);
            int targetEnd = layout.getLineEnd(currentLine);

            if (targetStart < content.length()) {
                highlightDropTarget(targetStart, Math.min(targetEnd, content.length()));
            }

            // Haptic feedback
            noteContent.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private void updateFloatingTextPosition(float rawY) {
        int[] location = new int[2];
        noteContent.getLocationOnScreen(location);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dragFloatingText.getLayoutParams();
        params.leftMargin = dpToPx(16);
        params.topMargin = (int) (rawY - location[1] - dragTouchOffset);
        params.width = noteContent.getWidth() - dpToPx(32);
        params.height = FrameLayout.LayoutParams.WRAP_CONTENT;

        dragFloatingText.setLayoutParams(params);
    }

    private void highlightDragLine(int start, int end) {
        SpannableString spannable = new SpannableString(noteContent.getText());

        // Add semi-transparent highlight
        spannable.setSpan(
                new BackgroundColorSpan(Color.parseColor("#80BBDEFB")),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        isUpdatingText = true;
        noteContent.setText(spannable, TextView.BufferType.SPANNABLE);
        isUpdatingText = false;
    }

    private void highlightDropTarget(int start, int end) {
        SpannableString spannable = new SpannableString(noteContent.getText());

        // Add green highlight for drop target
        spannable.setSpan(
                new BackgroundColorSpan(Color.parseColor("#80C8E6C9")),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        isUpdatingText = true;
        noteContent.setText(spannable, TextView.BufferType.SPANNABLE);

        // Reapply bookmarks after short delay
        noteContent.postDelayed(() -> {
            applyBookmarksToText();
            isUpdatingText = false;
        }, 50);
    }

    private void finishDrag() {
        if (!isDragging) return;

        isDragging = false;
        dragOverlayView.setVisibility(View.GONE);

        // Perform the reordering
        if (dragCurrentLineIndex != dragStartLineIndex && dragCurrentLineIndex >= 0) {
            reorderLines(dragStartLineIndex, dragCurrentLineIndex);
        } else {
            // Just remove highlights
            applyBookmarksToText();
        }

        dragStartLineIndex = -1;
        dragCurrentLineIndex = -1;

        // Haptic feedback
        noteContent.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void reorderLines(int fromLine, int toLine) {
        String content = noteContent.getText().toString();
        Layout layout = noteContent.getLayout();
        if (layout == null) return;

        String[] lines = content.split("\n", -1);

        if (fromLine >= lines.length || toLine >= lines.length) return;

        String draggedLine = lines[fromLine];
        String targetLine = lines[toLine];

        // Detect line types
        LineInfo fromInfo = analyzeLineType(draggedLine);
        LineInfo toInfo = analyzeLineType(targetLine);

        // ✅ KEY FIX: TRUE SWAP - Exchange positions AND formats
        List<String> linesList = new ArrayList<>(Arrays.asList(lines));

        // Process dragged line to take target's format
        String processedDraggedLine = processLineForInsertion(draggedLine, fromInfo, toInfo, linesList, toLine);

        // Process target line to take dragged's format (swap!)
        String processedTargetLine = processLineForInsertion(targetLine, toInfo, fromInfo, linesList, fromLine);

        // Perform the swap
        linesList.set(fromLine, processedTargetLine);
        linesList.set(toLine, processedDraggedLine);

        // Renumber affected lists
        int minLine = Math.min(fromLine, toLine);
        int maxLine = Math.max(fromLine, toLine);
        renumberAffectedLists(linesList, minLine, maxLine + 1);

        // Rebuild content
        StringBuilder newContent = new StringBuilder();
        for (int i = 0; i < linesList.size(); i++) {
            newContent.append(linesList.get(i));
            if (i < linesList.size() - 1) {
                newContent.append("\n");
            }
        }

        // Update bookmarks positions
        updateBookmarksAfterReorder(content, newContent.toString(), fromLine, toLine);

        // Apply new content
        isUpdatingText = true;
        noteContent.setText(newContent.toString());

        // Reapply styling
        noteContent.postDelayed(() -> {
            applyBookmarksToText();
            isUpdatingText = false;
            saveNoteContentToFirestore(newContent.toString());
        }, 100);

        Toast.makeText(this, "Lines swapped", Toast.LENGTH_SHORT).show();
    }

    private LineInfo analyzeLineType(String line) {
        LineInfo info = new LineInfo();

        // Count leading spaces
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') indent++;
            else break;
        }
        info.indent = indent;

        String trimmed = line.trim();

        // Bullet list
        if (trimmed.matches("^[●○■]\\s.*")) {
            info.type = LineType.BULLET;
            info.bullet = trimmed.charAt(0);
            info.content = trimmed.substring(2);
        }
        // Numbered list
        else if (trimmed.matches("^\\d+\\.\\s.*")) {
            info.type = LineType.NUMBERED;
            info.content = trimmed.replaceFirst("^\\d+\\.\\s", "");
        }
        // Lettered list
        else if (trimmed.matches("^[a-z]\\.\\s.*")) {
            info.type = LineType.LETTERED;
            info.content = trimmed.replaceFirst("^[a-z]\\.\\s", "");
        }
        // Roman numeral list
        else if (trimmed.matches("^[ivx]+\\.\\s.*")) {
            info.type = LineType.ROMAN;
            info.content = trimmed.replaceFirst("^[ivx]+\\.\\s", "");
        }
        // Toggle
        else if (trimmed.matches("^[▶▼]\\s.*")) {
            info.type = LineType.TOGGLE;
            info.content = trimmed.substring(2);
        }
        // Checkbox
        else if (trimmed.matches("^[☐☑]\\s.*")) {
            info.type = LineType.CHECKBOX;
            info.content = trimmed.substring(2);
        }
        // Regular text
        else {
            info.type = LineType.TEXT;
            info.content = trimmed;
        }

        return info;
    }

    private String processLineForInsertion(String originalLine, LineInfo fromInfo, LineInfo toInfo,
                                           List<String> lines, int insertIndex) {
        StringBuilder result = new StringBuilder();

        // ✅ KEY FIX: When swapping, the dragged item should TAKE THE EXACT FORMAT of target
        // Use target indentation
        int targetIndent = toInfo.indent;

        // Add indentation
        for (int i = 0; i < targetIndent; i++) {
            result.append(" ");
        }

        // ✅ CRITICAL: Use the TARGET'S list format, not the source
        if (toInfo.type != LineType.TEXT) {
            // Use the TARGET's list marker
            switch (toInfo.type) {
                case BULLET:
                    result.append(getBulletForIndent(targetIndent)).append(" ");
                    break;
                case NUMBERED:
                    result.append("1. "); // Will be renumbered to target position
                    break;
                case LETTERED:
                    result.append("a. "); // Will be renumbered to target position
                    break;
                case ROMAN:
                    result.append("i. "); // Will be renumbered to target position
                    break;
                case TOGGLE:
                    result.append("▶ ");
                    break;
                case CHECKBOX:
                    result.append("☐ ");
                    break;
                default:
                    result.append(fromInfo.content);
                    return result.toString();
            }
        } else {
            // If target is plain text, keep original format
            String trimmed = originalLine.trim();
            result.append(trimmed);
            return result.toString();
        }

        result.append(fromInfo.content);
        return result.toString();
    }

    private char getBulletForIndent(int indent) {
        if (indent == 0) return '●';
        if (indent == 4) return '○';
        return '■';
    }

    private void renumberAffectedLists(List<String> lines, int startIndex, int endIndex) {
        // Expand range to include full list blocks
        int actualStart = findListStart(lines, startIndex);
        int actualEnd = findListEnd(lines, endIndex);

        for (int i = actualStart; i <= actualEnd && i < lines.size(); i++) {
            String line = lines.get(i);
            LineInfo info = analyzeLineType(line);

            if (info.type == LineType.NUMBERED || info.type == LineType.LETTERED || info.type == LineType.ROMAN) {
                // Find consecutive items at same indentation
                int number = 1;
                int currentIndent = info.indent;

                // Count backwards to find starting number
                for (int j = i - 1; j >= 0; j--) {
                    LineInfo prevInfo = analyzeLineType(lines.get(j));
                    if (prevInfo.indent == currentIndent && prevInfo.type == info.type) {
                        number++;
                    } else if (prevInfo.indent < currentIndent) {
                        break;
                    }
                }

                // Renumber this item
                String indent = "";
                for (int k = 0; k < currentIndent; k++) indent += " ";

                String newLine = indent;
                switch (info.type) {
                    case NUMBERED:
                        newLine += number + ". " + info.content;
                        break;
                    case LETTERED:
                        newLine += ((char)('a' + number - 1)) + ". " + info.content;
                        break;
                    case ROMAN:
                        newLine += convertToRoman(number) + ". " + info.content;
                        break;
                }

                lines.set(i, newLine);
            }
        }
    }

    private int findListStart(List<String> lines, int fromIndex) {
        if (fromIndex <= 0) return 0;

        LineInfo currentInfo = analyzeLineType(lines.get(fromIndex));
        if (currentInfo.type == LineType.TEXT) return fromIndex;

        for (int i = fromIndex - 1; i >= 0; i--) {
            LineInfo info = analyzeLineType(lines.get(i));
            if (info.type != currentInfo.type || info.indent < currentInfo.indent) {
                return i + 1;
            }
        }

        return 0;
    }

    private int findListEnd(List<String> lines, int fromIndex) {
        if (fromIndex >= lines.size() - 1) return lines.size() - 1;

        LineInfo currentInfo = analyzeLineType(lines.get(fromIndex));
        if (currentInfo.type == LineType.TEXT) return fromIndex;

        for (int i = fromIndex + 1; i < lines.size(); i++) {
            LineInfo info = analyzeLineType(lines.get(i));
            if (info.type != currentInfo.type || info.indent < currentInfo.indent) {
                return i - 1;
            }
        }

        return lines.size() - 1;
    }

    private String convertToRoman(int number) {
        String[] romans = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
        if (number > 0 && number <= romans.length) {
            return romans[number - 1];
        }
        return "i";
    }

    private void updateBookmarksAfterReorder(String oldContent, String newContent, int fromLine, int toLine) {
        // Calculate position changes
        Layout layout = noteContent.getLayout();
        if (layout == null) return;

        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        // Build position map
        Map<Integer, Integer> positionMap = new HashMap<>();

        int oldPos = 0;
        int newPos = 0;

        for (int i = 0; i < Math.max(oldLines.length, newLines.length); i++) {
            if (i < oldLines.length) {
                positionMap.put(oldPos, newPos);
                oldPos += oldLines[i].length() + 1;
            }
            if (i < newLines.length) {
                newPos += newLines[i].length() + 1;
            }
        }

        // Update bookmarks
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            for (Bookmark bookmark : new ArrayList<>(currentBookmarks)) {
                int oldStart = bookmark.getStartIndex();
                int oldEnd = bookmark.getEndIndex();

                // Find closest mapped position
                Integer newStart = findClosestMappedPosition(positionMap, oldStart);
                Integer newEnd = findClosestMappedPosition(positionMap, oldEnd);

                if (newStart != null && newEnd != null && newStart < newEnd) {
                    try {
                        String bookmarkText = newContent.substring(newStart, Math.min(newEnd, newContent.length()));
                        if (!bookmarkText.trim().isEmpty()) {
                            updateBookmarkInFirestore(bookmark.getId(), newStart, newEnd, bookmarkText.trim());
                            bookmark.setStartIndex(newStart);
                            bookmark.setEndIndex(newEnd);
                            bookmark.setText(bookmarkText.trim());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private Integer findClosestMappedPosition(Map<Integer, Integer> positionMap, int oldPos) {
        if (positionMap.containsKey(oldPos)) {
            return positionMap.get(oldPos);
        }

        // Find closest position
        int closest = -1;
        int minDiff = Integer.MAX_VALUE;

        for (Map.Entry<Integer, Integer> entry : positionMap.entrySet()) {
            int diff = Math.abs(entry.getKey() - oldPos);
            if (diff < minDiff) {
                minDiff = diff;
                closest = entry.getValue();
            }
        }

        return closest >= 0 ? closest : null;
    }

    // Helper classes
    private enum LineType {
        TEXT, BULLET, NUMBERED, LETTERED, ROMAN, TOGGLE, CHECKBOX
    }

    private static class LineInfo {
        LineType type = LineType.TEXT;
        int indent = 0;
        String content = "";
        char bullet = '●';
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("IMAGE_DEBUG", "⏩ onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("IMAGE_DEBUG", "⏸️ onStop");
    }

    private int findContentStartIndex(String line) {
        // ✅ CRITICAL: Return 0 for image lines - don't process them
        if (isImageLine(line)) {
            return 0;
        }

        // 1. Check for Toggle List (▶ or ▼ followed by space)
        java.util.regex.Matcher toggleMatcher = java.util.regex.Pattern.compile("^\\s*[▶▼]\\s").matcher(line);
        if (toggleMatcher.find()) {
            return toggleMatcher.end();
        }

        // 2. Check for Bullet List (●, ○, or ■ followed by space)
        java.util.regex.Matcher bulletMatcher = java.util.regex.Pattern.compile("^\\s*[●○■]\\s").matcher(line);
        if (bulletMatcher.find()) {
            return bulletMatcher.end();
        }

        // 3. Check for Checkbox List (☐ or ☑ followed by space)
        java.util.regex.Matcher checkboxMatcher = java.util.regex.Pattern.compile("^\\s*[☐☑]\\s").matcher(line);
        if (checkboxMatcher.find()) {
            return checkboxMatcher.end();
        }

        // 4. Check for Numbered/Lettered/Roman List
        java.util.regex.Matcher numberedMatcher = java.util.regex.Pattern.compile("^\\s*([0-9a-zA-Zivx]+\\.)\\s").matcher(line);
        if (numberedMatcher.find()) {
            return numberedMatcher.end();
        }

        // Default: find the first non-whitespace character
        java.util.regex.Matcher whitespaceMatcher = java.util.regex.Pattern.compile("^\\s*").matcher(line);
        if (whitespaceMatcher.find()) {
            return whitespaceMatcher.end();
        }

        return 0;
    }

    // ✅ NEW: Save ALL spans from Editable
    private List<SpanInfo> saveAllSpans(Editable editable) {
        List<SpanInfo> allSpans = new ArrayList<>();

        // Save ALL span types
        Object[] spans = editable.getSpans(0, editable.length(), Object.class);
        for (Object span : spans) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start >= 0 && end >= start) {
                allSpans.add(new SpanInfo(span, start, end));
            }
        }

        return allSpans;
    }

    // ✅ NEW: Restore ALL spans to Editable
    private void restoreAllSpans(Editable editable, List<SpanInfo> spans) {
        for (SpanInfo info : spans) {
            if (info.start >= 0 && info.end <= editable.length() && info.start < info.end) {
                try {
                    editable.setSpan(
                            info.span,
                            info.start,
                            info.end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                } catch (Exception e) {
                    // Ignore invalid spans
                }
            }
        }
    }
    private boolean containsImagePlaceholder(String text) {
        return text.contains("〔IMAGE:") && text.contains("〕");
    }

    private boolean isImageLine(String line) {
        return line.trim().matches("^〔IMAGE:\\d+〕$");
    }

    //


}